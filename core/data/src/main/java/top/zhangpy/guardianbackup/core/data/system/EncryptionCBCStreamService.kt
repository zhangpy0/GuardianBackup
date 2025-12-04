package top.zhangpy.guardianbackup.core.data.system

import android.net.Uri
import java.io.File
import java.io.IOException
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
class EncryptionCBCStreamService(private val fileRepository: FileRepository) : IEncryptionService {

    companion object {
        private const val BUFFER_SIZE = 8192 // copyTo 的默认缓冲区大小
        private const val SALT_SIZE = 16
        private const val IV_SIZE = 12 // AES/GCM 标准 IV 大小为 12 字节
        private const val PBE_ITERATION_COUNT = 65536
        private const val AES_KEY_SIZE = 256
        private const val GCM_TAG_LENGTH = 128
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
        private const val KEY_FACTORY_ALGORITHM = "PBKDF2WithHmacSHA256"
    }

    /**
     * 使用 CipherOutputStream 流式加密文件，内存占用极低
     */
    override fun encryptFile(inputFileUri: Uri, outputFileUri: Uri, password: CharArray) {
        val salt = ByteArray(SALT_SIZE).apply { SecureRandom().nextBytes(this) }
        val keySpec = PBEKeySpec(password, salt, PBE_ITERATION_COUNT, AES_KEY_SIZE)
        val keyFactory = SecretKeyFactory.getInstance(KEY_FACTORY_ALGORITHM)
        val secretKey = SecretKeySpec(keyFactory.generateSecret(keySpec).encoded, ALGORITHM)

        // CBC 使用 16 字节 IV
        val iv = ByteArray(16).apply { SecureRandom().nextBytes(this) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, javax.crypto.spec.IvParameterSpec(iv))

        fileRepository.getOutputStream(outputFileUri).use { fos ->
            // 写入文件头: magic + salt + iv
            fos.write("EZCBC1".toByteArray(Charsets.UTF_8)) // magic/version 标记
            fos.write(salt)
            fos.write(iv)

            CipherOutputStream(fos, cipher).use { cos ->
                fileRepository.getInputStream(inputFileUri).use { fis ->
                    fis.copyTo(cos, BUFFER_SIZE)
                }
            }
        }
    }

    /**
     * 使用 CipherInputStream 流式解密文件，内存占用极低
     */
    override fun decryptFile(inputFileUri: Uri, outputFileUri: Uri, password: CharArray) {
        fileRepository.getInputStream(inputFileUri).use { fis ->
            // 读取并校验 magic
            val magic = ByteArray(6)
            if (fis.read(magic) != magic.size) throw IOException("Invalid file format (magic)")
            if (!magic.toString(Charsets.UTF_8).startsWith("EZCBC1")) throw IOException("Unsupported format")

            val salt = ByteArray(SALT_SIZE)
            if (fis.read(salt) != SALT_SIZE) throw IOException("Failed to read salt")
            val iv = ByteArray(16)
            if (fis.read(iv) != iv.size) throw IOException("Failed to read IV")

            val keySpec = PBEKeySpec(password, salt, PBE_ITERATION_COUNT, AES_KEY_SIZE)
            val keyFactory = SecretKeyFactory.getInstance(KEY_FACTORY_ALGORITHM)
            val secretKey = SecretKeySpec(keyFactory.generateSecret(keySpec).encoded, ALGORITHM)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, javax.crypto.spec.IvParameterSpec(iv))

            CipherInputStream(fis, cipher).use { cis ->
                fileRepository.getOutputStream(outputFileUri).use { fos ->
                    cis.copyTo(fos, BUFFER_SIZE)
                }
            }
        }
    }
}