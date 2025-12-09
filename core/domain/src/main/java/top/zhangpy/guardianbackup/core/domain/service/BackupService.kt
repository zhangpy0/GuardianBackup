package top.zhangpy.guardianbackup.core.domain.service

import android.net.Uri
import top.zhangpy.guardianbackup.core.domain.model.BackupResult

interface BackupService {
    suspend fun backup(
            sourceUris: Map<Uri, String>,
            destinationUri: Uri,
            key: String?,
            isFileKey: Boolean,
            sourcePath: String? = null,
            sourceDirName: String? = null,
            onProgress: (String, Int, Int) -> Unit
    ): BackupResult
}
