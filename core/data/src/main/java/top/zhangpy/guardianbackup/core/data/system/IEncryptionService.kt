package top.zhangpy.guardianbackup.core.data.system

import android.net.Uri

interface IEncryptionService {

    companion object {
        const val BUFFER_SIZE = 8192 // copyTo 的默认缓冲区大小
        const val GCM_CHUNK_SIZE = 8 * 1024 * 1024 // 8 MiB per chunk
        const val SALT_SIZE = 16
        const val IV_SIZE = 12 // AES/GCM 标准 IV 大小为 12 字节
        const val CBC_IV_SIZE = 16 // CBC IV 大小
        const val PBE_ITERATION_COUNT = 65536
        const val AES_KEY_SIZE = 256
        const val GCM_TAG_LENGTH = 128
        const val ALGORITHM = "AES"
        const val TRANSFORMATION_GCM = "AES/GCM/NoPadding"
        const val TRANSFORMATION_CBC = "AES/CBC/PKCS5Padding"
        const val KEY_FACTORY_ALGORITHM = "PBKDF2WithHmacSHA256"

        // Headers
        const val HEADER_GCM_CHUNKED = "EZGCM1"
        const val HEADER_CBC = "EZCBC1"
    }

    fun encryptFile(inputFileUri: Uri, outputFileUri: Uri, password: CharArray)

    fun decryptFile(inputFileUri: Uri, outputFileUri: Uri, password: CharArray)
}
