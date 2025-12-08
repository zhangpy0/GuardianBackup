package top.zhangpy.guardianbackup.core.domain.model

/**
 * 用于存储在备份包内的文件元数据
 * @param originalPath 文件的原始绝对路径
 * @param pathInArchive 文件在压缩包内的相对路径
 * @param size 文件大小
 * @param lastModified 最后修改时间
 * @param sha256Checksum 文件的 SHA-256 校验和，用于保证完整性
 */
data class FileMetadata(
        val originalPath: String,
        val pathInArchive: String,
        val size: Long,
        val lastModified: Long,
        val sha256Checksum: String
)

/** 包含所有文件元数据的清单 */
data class BackupManifest(
        val dirName: String,
        val appVersion: String, // 用于未来可能的迁移
        val creationDate: Long,
        val files: List<FileMetadata>
)
