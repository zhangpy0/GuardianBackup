package top.zhangpy.guardianbackup.core.domain.service

import android.net.Uri

data class RestoreResultDomain(
        val isSuccess: Boolean,
        val restoredFilesCount: Int,
        val totalFilesCount: Int,
        val corruptedFiles: List<String>
)

interface RestoreService {
    suspend fun restore(
            sourceUri: Uri,
            destinationUri: Uri,
            key: String?,
            isFileKey: Boolean,
            onProgress: (String, Int, Int) -> Unit
    ): RestoreResultDomain
}
