package top.zhangpy.guardianbackup.core.domain.model

data class BackupRecord(
        val id: Long = 0,
        val filePath: String,
        val timestamp: Long,
        val sizeBytes: Long,
        val fileCount: Int,
        val displayPath: String? = null,
        val manifestJson: String? = null
)
