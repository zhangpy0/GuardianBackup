package top.zhangpy.guardianbackup.core.domain.service

import android.net.Uri

interface BackupService {
    suspend fun backup(
            sourceUris: Map<Uri, String>,
            destinationUri: Uri,
            key: String?,
            isFileKey: Boolean,
            onProgress: (String, Int, Int) -> Unit
    ): Boolean
}
