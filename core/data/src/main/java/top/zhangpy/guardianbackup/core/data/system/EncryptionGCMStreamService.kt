package top.zhangpy.guardianbackup.core.data.system

import android.net.Uri
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 服务层：负责数据的加密与解密。
 * 职责：使用 AES-256-GCM 算法对文件流进行处理。
 *
 *
 *  GCM 流式加密会生成AHEAD标签并附加在密文末尾，所以流式加密失效
 */
class EncryptionGCMStreamService(private val fileRepository: FileRepository) : IEncryptionService {

    companion object {
        private const val GCM_CHUNK_SIZE = 8 * 1024 * 1024 // 8 MiB per chunk
        private const val BUFFER_SIZE = 8192 // copyTo 的默认缓冲区大小
        private const val SALT_SIZE = 16
        private const val IV_SIZE = 12 // AES/GCM 标准 IV 大小为 12 字节
        private const val PBE_ITERATION_COUNT = 65536
        private const val AES_KEY_SIZE = 256
        private const val GCM_TAG_LENGTH = 128
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_FACTORY_ALGORITHM = "PBKDF2WithHmacSHA256"
    }

    /**
     * 使用 CipherOutputStream 流式加密文件，内存占用极低
     */
    override fun encryptFile(inputFileUri: Uri, outputFileUri: Uri, password: CharArray) {
        // 1. 生成 Salt 和 Key
        val salt = ByteArray(SALT_SIZE).apply { SecureRandom().nextBytes(this) }
        val keySpec = PBEKeySpec(password, salt, PBE_ITERATION_COUNT, AES_KEY_SIZE)
        val keyFactory = SecretKeyFactory.getInstance(KEY_FACTORY_ALGORITHM)
        val secretKey = SecretKeySpec(keyFactory.generateSecret(keySpec).encoded, ALGORITHM)

        // 2. 初始化 Cipher 并获取 IV
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        fileRepository.getOutputStream(outputFileUri).use { fos ->
            // 写文件头: magic + salt + chunkSize(int)
            fos.write("EZGCM1".toByteArray(Charsets.UTF_8))
            fos.write(salt)
            fos.writeIntBigEndian(GCM_CHUNK_SIZE) // 写 chunk 长度，解密时使用（可选，供兼容）

            fileRepository.getInputStream(inputFileUri).use { fis ->
                val buffer = ByteArray(GCM_CHUNK_SIZE)
                var read: Int
                while (true) {
                    read = fis.read(buffer)
                    if (read <= 0) break
                    val chunk = if (read == buffer.size) buffer else buffer.copyOf(read)

                    // 每块单独 IV（12 bytes）
                    val iv = ByteArray(IV_SIZE).apply { SecureRandom().nextBytes(this) }

                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                    cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

                    // 对单块 doFinal —— 内存占用受控（chunk 大小）
                    val cipherChunk = cipher.doFinal(chunk)

                    // 写入块头：iv + cipherChunk.length(int) + cipherChunk
                    fos.write(iv)
                    fos.writeIntBigEndian(cipherChunk.size)
                    fos.write(cipherChunk)
                }
            }
        }
    }

    /**
     * 使用 CipherInputStream 流式解密文件，内存占用极低
     */
    override fun decryptFile(inputFileUri: Uri, outputFileUri: Uri, password: CharArray) {
        fileRepository.getInputStream(inputFileUri).use { fis ->
            // 读文件头
            val magic = ByteArray(6)
            if (fis.read(magic) != magic.size) throw IOException("Invalid file format (magic)")
            if (!magic.toString(Charsets.UTF_8)
                    .startsWith("EZGCM1")
            ) throw IOException("Unsupported format")

            val salt = ByteArray(SALT_SIZE)
            if (fis.read(salt) != SALT_SIZE) throw IOException("Failed to read salt")

            // 读 chunk size（可选）
            val declaredChunkSize = fis.readIntBigEndian()

            val keySpec = PBEKeySpec(password, salt, PBE_ITERATION_COUNT, AES_KEY_SIZE)
            val keyFactory = SecretKeyFactory.getInstance(KEY_FACTORY_ALGORITHM)
            val secretKey = SecretKeySpec(keyFactory.generateSecret(keySpec).encoded, ALGORITHM)

            fileRepository.getOutputStream(outputFileUri).use { fos ->
                while (true) {
                    // 读 iv
                    val iv = ByteArray(IV_SIZE)
                    val nIv = fis.read(iv)
                    if (nIv == -1) break // 到文件末尾
                    if (nIv != IV_SIZE) throw IOException("Truncated IV")

                    // 读 cipherBlockLength
                    val len = fis.readIntBigEndian()

                    // 读 cipher bytes
                    val cipherBytes = ByteArray(len)
                    var readTotal = 0
                    while (readTotal < len) {
                        val r = fis.read(cipherBytes, readTotal, len - readTotal)
                        if (r == -1) throw IOException("Truncated cipher block")
                        readTotal += r
                    }

                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

                    // doFinal on single chunk -> returns plaintext or throws AEADBadTagException if tampered
                    val plainChunk = cipher.doFinal(cipherBytes)
                    fos.write(plainChunk)
                }
            }
        }
    }

    // 写 int big-endian
    private fun OutputStream.writeIntBigEndian(value: Int) {
        val b1 = (value shr 24 and 0xFF)
        val b2 = (value shr 16 and 0xFF)
        val b3 = (value shr 8 and 0xFF)
        val b4 = (value and 0xFF)
        write(byteArrayOf(b1.toByte(), b2.toByte(), b3.toByte(), b4.toByte()))
    }

    // 从 InputStream 读 4 字节 big-endian int；若 EOF 抛异常或返回 -1 视为结束
    private fun InputStream.readIntBigEndian(): Int {
        val buf = ByteArray(4)
        var read = 0
        while (read < 4) {
            val r = read(buf, read, 4 - read)
            if (r == -1) throw IOException("Unexpected EOF while reading int")
            read += r
        }
        return ((buf[0].toInt() and 0xFF) shl 24) or
                ((buf[1].toInt() and 0xFF) shl 16) or
                ((buf[2].toInt() and 0xFF) shl 8) or
                (buf[3].toInt() and 0xFF)
    }

}