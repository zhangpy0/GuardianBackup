package top.zhangpy.guardianbackup.core.data.model

import java.io.File

/**
 * 备份请求的配置对象
 * @param sourcePaths 要备份的源文件/文件夹列表
 * @param destinationFile 备份后生成的加密压缩包文件
 * @param password 用于加密的密码
 * @param progressCallback 一个回调函数，用于报告备份进度 (当前处理的文件名, 当前文件索引, 总文件数)
 */
data class BackupRequest(
    val sourcePaths: List<File>,
    val destinationFile: File,
    val password: CharArray,
    val progressCallback: (String, Int, Int) -> Unit = { _, _, _ -> }
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BackupRequest

        if (sourcePaths != other.sourcePaths) return false
        if (destinationFile != other.destinationFile) return false
        if (!password.contentEquals(other.password)) return false
        if (progressCallback != other.progressCallback) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sourcePaths.hashCode()
        result = 31 * result + destinationFile.hashCode()
        result = 31 * result + password.contentHashCode()
        result = 31 * result + progressCallback.hashCode()
        return result
    }
}

/**
 * 恢复操作的结果
 * @param isSuccess 操作是否完全成功
 * @param restoredFilesCount 成功恢复的文件数量
 * @param totalFilesCount 备份包中的总文件数
 * @param errorMessage 如果失败，则包含错误信息
 * @param corruptedFiles 恢复期间发现的损坏（校验和不匹配）的文件列表
 */
data class RestoreResult(
    val isSuccess: Boolean,
    val restoredFilesCount: Int = 0,
    val totalFilesCount: Int = 0,
    val errorMessage: String? = null,
    val corruptedFiles: List<String> = emptyList()
)

/**
 * 用于存储在备份包内的文件元数据
 * @param originalPath 文件的原始绝对路径
 * @param pathInArchive 文件在压缩包内的相对路径
 * @param size 文件大小
 * @param lastModified 最后修改时间
 * @param sha256Checksum 文件的 SHA-256 校验和，用于保证完整性
 */
internal data class FileMetadata(
    val originalPath: String,
    val pathInArchive: String,
    val size: Long,
    val lastModified: Long,
    val sha256Checksum: String
)

/**
 * 包含所有文件元数据的清单
 */
internal data class BackupManifest(
    val appVersion: String, // 用于未来可能的迁移
    val creationDate: Long,
    val files: List<FileMetadata>
)