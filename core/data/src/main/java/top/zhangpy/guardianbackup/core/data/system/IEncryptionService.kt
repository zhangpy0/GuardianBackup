package top.zhangpy.guardianbackup.core.data.system

import android.net.Uri

interface IEncryptionService {

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

    fun encryptFile(inputFileUri: Uri, outputFileUri: Uri, password: CharArray)

    fun decryptFile(inputFileUri: Uri, outputFileUri: Uri, password: CharArray)
}