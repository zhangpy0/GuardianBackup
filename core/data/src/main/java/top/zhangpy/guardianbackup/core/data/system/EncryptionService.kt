package top.zhangpy.guardianbackup.core.data.system

import android.net.Uri
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** 服务层：负责数据的加密与解密。 职责：使用 AES-256-GCM 算法对文件流进行处理。 */
class EncryptionService(private val fileRepository: FileRepository) : IEncryptionService {

    /** Encrypts the file using Chunked GCM (EZGCM1) to prevent OOM and ensure integrity. */
    override fun encryptFile(inputFileUri: Uri, outputFileUri: Uri, password: CharArray) {
        // 1. Generate Salt and Key
        val salt = ByteArray(IEncryptionService.SALT_SIZE).apply { SecureRandom().nextBytes(this) }
        val keySpec =
                PBEKeySpec(
                        password,
                        salt,
                        IEncryptionService.PBE_ITERATION_COUNT,
                        IEncryptionService.AES_KEY_SIZE
                )
        val keyFactory = SecretKeyFactory.getInstance(IEncryptionService.KEY_FACTORY_ALGORITHM)
        val secretKey =
                SecretKeySpec(
                        keyFactory.generateSecret(keySpec).encoded,
                        IEncryptionService.ALGORITHM
                )

        // 2. Prepare Cipher (init for validation, real init happens per chunk for GCM usually, but
        // here we use new IV per chunk)
        // We will generate a fresh IV for each chunk to be safe and simple with stream handling.

        fileRepository.getOutputStream(outputFileUri).use { fos ->
            // Write Header: Magic + Salt + ChunkSize (optional but good for compat)
            fos.write(IEncryptionService.HEADER_GCM_CHUNKED.toByteArray(Charsets.UTF_8))
            fos.write(salt)
            fos.writeIntBigEndian(IEncryptionService.GCM_CHUNK_SIZE)

            fileRepository.getInputStream(inputFileUri).use { fis ->
                val buffer = ByteArray(IEncryptionService.GCM_CHUNK_SIZE)
                var read: Int
                while (true) {
                    read = fis.read(buffer)
                    if (read <= 0) break
                    val chunk = if (read == buffer.size) buffer else buffer.copyOf(read)

                    // Generate unique IV for this chunk
                    val iv =
                            ByteArray(IEncryptionService.IV_SIZE).apply {
                                SecureRandom().nextBytes(this)
                            }

                    // Init Cipher for this chunk
                    val cipher = Cipher.getInstance(IEncryptionService.TRANSFORMATION_GCM)
                    val gcmSpec = GCMParameterSpec(IEncryptionService.GCM_TAG_LENGTH, iv)
                    cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

                    val cipherChunk = cipher.doFinal(chunk)

                    // Write Block: IV + Length + CipherText
                    fos.write(iv)
                    fos.writeIntBigEndian(cipherChunk.size)
                    fos.write(cipherChunk)
                }
            }
        }
    }

    /**
     * Decrypts the file by detecting the header format. Supports:
     * - EZGCM1: Chunked GCM (New Standard)
     * - EZCBC1: Legacy CBC
     * - No Header: Fallback to Legacy GCM (Safe-ish, or fail)
     */
    override fun decryptFile(inputFileUri: Uri, outputFileUri: Uri, password: CharArray) {
        // We need to read the header first. simpler to open input stream, peek, then decide.
        // However, we can't easily peek with basic generic InputStream unless we wrap it.
        // Or we just read the first few bytes.

        fileRepository.getInputStream(inputFileUri).use { fis ->
            // Use a BufferedInputStream or PushbackInputStream if we needed advanced peeking,
            // but here we can just read the header bytes.
            // Be careful not to consume bytes if we need to fallback to "No Header" (Legacy GCM
            // without magic).
            // Actually, Legacy GCM (original EncryptionService) wrote Salt (16) + IV (12)
            // immediately. No Magic.

            // To handle the "No Magic" case properly without resetting the stream (which might not
            // be supported),
            // we can read the first 6 bytes.
            // If they match EZGCM1 or EZCBC1, we proceed.
            // If not, we assume it's Legacy GCM (Salt start). In that case, those 6 bytes are part
            // of the Salt.
            // We need to construct the full Salt.

            val headerBuffer = ByteArray(6)
            val headerRead = fis.read(headerBuffer)

            if (headerRead != 6) {
                // File too short, possibly empty or verify short legacy file.
                // Let's assume legacy or broken.
                throw IOException("File too short to identify format.")
            }

            val headerStr = headerBuffer.toString(Charsets.UTF_8)

            when {
                headerStr.startsWith(IEncryptionService.HEADER_GCM_CHUNKED) -> {
                    decryptGCMChunked(fis, outputFileUri, password)
                }
                headerStr.startsWith(IEncryptionService.HEADER_CBC) -> {
                    decryptCBC(fis, outputFileUri, password)
                }
                else -> {
                    // Legacy GCM (No Magic). The bytes we read are the start of the Salt.
                    // We need to handle this carefully.
                    decryptLegacyGCM(fis, outputFileUri, password, headerBuffer)
                }
            }
        }
    }

    private fun decryptGCMChunked(fis: InputStream, outputFileUri: Uri, password: CharArray) {
        val salt = ByteArray(IEncryptionService.SALT_SIZE)
        if (fis.read(salt) != IEncryptionService.SALT_SIZE) throw IOException("Failed to read salt")

        val chunkSize = fis.readIntBigEndian() // Read recorded chunk size

        val keySpec =
                PBEKeySpec(
                        password,
                        salt,
                        IEncryptionService.PBE_ITERATION_COUNT,
                        IEncryptionService.AES_KEY_SIZE
                )
        val keyFactory = SecretKeyFactory.getInstance(IEncryptionService.KEY_FACTORY_ALGORITHM)
        val secretKey =
                SecretKeySpec(
                        keyFactory.generateSecret(keySpec).encoded,
                        IEncryptionService.ALGORITHM
                )

        fileRepository.getOutputStream(outputFileUri).use { fos ->
            while (true) {
                val iv = ByteArray(IEncryptionService.IV_SIZE)
                val ivRead = fis.read(iv)
                if (ivRead == -1) break
                if (ivRead != IEncryptionService.IV_SIZE) throw IOException("Truncated IV in chunk")

                val len = fis.readIntBigEndian()
                val cipherBytes = ByteArray(len)
                var readTotal = 0
                while (readTotal < len) {
                    val r = fis.read(cipherBytes, readTotal, len - readTotal)
                    if (r == -1) throw IOException("Truncated cipher block")
                    readTotal += r
                }

                val cipher = Cipher.getInstance(IEncryptionService.TRANSFORMATION_GCM)
                val gcmSpec = GCMParameterSpec(IEncryptionService.GCM_TAG_LENGTH, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

                val plainChunk = cipher.doFinal(cipherBytes)
                fos.write(plainChunk)
            }
        }
    }

    private fun decryptCBC(fis: InputStream, outputFileUri: Uri, password: CharArray) {
        val salt = ByteArray(IEncryptionService.SALT_SIZE)
        if (fis.read(salt) != IEncryptionService.SALT_SIZE) throw IOException("Failed to read salt")

        val iv = ByteArray(IEncryptionService.CBC_IV_SIZE)
        if (fis.read(iv) != IEncryptionService.CBC_IV_SIZE) throw IOException("Failed to read IV")

        val keySpec =
                PBEKeySpec(
                        password,
                        salt,
                        IEncryptionService.PBE_ITERATION_COUNT,
                        IEncryptionService.AES_KEY_SIZE
                )
        val secretKey =
                SecretKeySpec(
                        SecretKeyFactory.getInstance(IEncryptionService.KEY_FACTORY_ALGORITHM)
                                .generateSecret(keySpec)
                                .encoded,
                        IEncryptionService.ALGORITHM
                )

        val cipher = Cipher.getInstance(IEncryptionService.TRANSFORMATION_CBC)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, javax.crypto.spec.IvParameterSpec(iv))

        CipherInputStream(fis, cipher).use { cis ->
            fileRepository.getOutputStream(outputFileUri).use { fos ->
                cis.copyTo(fos, IEncryptionService.BUFFER_SIZE)
            }
        }
    }

    private fun decryptLegacyGCM(
            fis: InputStream,
            outputFileUri: Uri,
            password: CharArray,
            initialBytes: ByteArray
    ) {
        // Reconstruct salt: initialBytes (6) + next 10 bytes = 16 bytes
        val remainingSalt = ByteArray(IEncryptionService.SALT_SIZE - initialBytes.size) // 10 bytes
        if (fis.read(remainingSalt) != remainingSalt.size)
                throw IOException("Failed to read legacy salt")

        val salt = initialBytes + remainingSalt

        val iv = ByteArray(IEncryptionService.IV_SIZE)
        if (fis.read(iv) != IEncryptionService.IV_SIZE)
                throw IOException("Failed to read legacy IV")

        val keySpec =
                PBEKeySpec(
                        password,
                        salt,
                        IEncryptionService.PBE_ITERATION_COUNT,
                        IEncryptionService.AES_KEY_SIZE
                )
        val secretKey =
                SecretKeySpec(
                        SecretKeyFactory.getInstance(IEncryptionService.KEY_FACTORY_ALGORITHM)
                                .generateSecret(keySpec)
                                .encoded,
                        IEncryptionService.ALGORITHM
                )

        val cipher = Cipher.getInstance(IEncryptionService.TRANSFORMATION_GCM)
        cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey,
                GCMParameterSpec(IEncryptionService.GCM_TAG_LENGTH, iv)
        )

        fileRepository.getOutputStream(outputFileUri).use { fos ->
            val buffer = ByteArray(IEncryptionService.BUFFER_SIZE)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                val decryptedData = cipher.update(buffer, 0, bytesRead)
                if (decryptedData != null) fos.write(decryptedData)
            }
            val finalBytes = cipher.doFinal()
            if (finalBytes != null) fos.write(finalBytes)
        }
    }

    private fun OutputStream.writeIntBigEndian(value: Int) {
        write(value shr 24)
        write(value shr 16)
        write(value shr 8)
        write(value)
    }

    private fun InputStream.readIntBigEndian(): Int {
        val b1 = read()
        val b2 = read()
        val b3 = read()
        val b4 = read()
        if ((b1 or b2 or b3 or b4) < 0) throw EOFException()
        return (b1 shl 24) or (b2 shl 16) or (b3 shl 8) or b4
    }
}
