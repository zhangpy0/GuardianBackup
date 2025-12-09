package top.zhangpy.guardianbackup.core.domain.model

import android.net.Uri

data class BackupResult(
        val isSuccess: Boolean,
        val destinationUri: Uri,
        val sizeBytes: Long,
        val fileCount: Int,
        val displayPath: String? = null,
        val manifest: BackupManifest? = null,
        val errorMessage: String? = null
)
