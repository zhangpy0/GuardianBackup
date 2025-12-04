package top.zhangpy.guardianbackup.core.data.system

import android.net.Uri
import java.security.MessageDigest

/**
 * 服务层：负责数据完整性校验。
 * 职责：计算文件的 SHA-256 校验和。
 */
class IntegrityService(private val fileRepository: FileRepository) {
    companion object {
        private const val BUFFER_SIZE = 8192
    }

    fun calculateSHA256(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fileRepository.getInputStream(uri).use { fis ->
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}