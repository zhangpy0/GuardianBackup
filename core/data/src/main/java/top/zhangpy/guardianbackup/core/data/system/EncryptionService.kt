package top.zhangpy.guardianbackup.core.data.system

import android.net.Uri
import java.io.IOException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 服务层：负责数据的加密与解密。
 * 职责：使用 AES-256-GCM 算法对文件流进行处理。
 */
class EncryptionService(private val fileRepository: FileRepository) : IEncryptionService {

    companion object {
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


    override fun encryptFile(inputFileUri: Uri, outputFileUri: Uri, password: CharArray) {
        val salt = ByteArray(SALT_SIZE).apply { java.security.SecureRandom().nextBytes(this) }
        val keySpec = PBEKeySpec(password, salt,
            PBE_ITERATION_COUNT,
            AES_KEY_SIZE
        )
        val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val secretKey = SecretKeySpec(keyFactory.generateSecret(keySpec).encoded, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv

        fileRepository.getOutputStream(outputFileUri).use { fos ->
            // Write salt and IV at the beginning of the file
            fos.write(salt)
            fos.write(iv)

            fileRepository.getInputStream(inputFileUri).use { fis ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    val encryptedData = cipher.update(buffer, 0, bytesRead)
                    if (encryptedData != null) {
                        fos.write(encryptedData)
                    }
                }
                val finalBytes = cipher.doFinal()
                if (finalBytes != null) {
                    fos.write(finalBytes)
                }
            }
        }
    }

    override fun decryptFile(inputFileUri: Uri, outputFileUri: Uri, password: CharArray) {
        fileRepository.getInputStream(inputFileUri).use { fis ->
            val salt = ByteArray(SALT_SIZE)
            if (fis.read(salt) != SALT_SIZE) {
                throw IOException("Failed to read salt from file")
            }
            val iv = ByteArray(IV_SIZE)
            if (fis.read(iv) != IV_SIZE) {
                throw IOException("Failed to read IV from file")
            }

            val keySpec = PBEKeySpec(password, salt,
                PBE_ITERATION_COUNT,
                AES_KEY_SIZE
            )
            val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val secretKey = SecretKeySpec(keyFactory.generateSecret(keySpec).encoded, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))

            fileRepository.getOutputStream(outputFileUri).use { fos ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    val decryptedData = cipher.update(buffer, 0, bytesRead)
                    if (decryptedData != null) {
                        fos.write(decryptedData)
                    }
                }
                val finalBytes = cipher.doFinal()
                if (finalBytes != null) {
                    fos.write(finalBytes)
                }
            }
        }
    }
}