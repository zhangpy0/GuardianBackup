package top.zhangpy.guardianbackup.core.domain.usecase

import android.net.Uri
import top.zhangpy.guardianbackup.core.domain.service.RestoreResultDomain
import top.zhangpy.guardianbackup.core.domain.service.RestoreService

class RestoreUseCase(private val restoreService: RestoreService) {
    suspend operator fun invoke(
            sourceUri: Uri,
            destinationUri: Uri,
            key: String?,
            isFileKey: Boolean,
            onProgress: (String, Int, Int) -> Unit
    ): RestoreResultDomain {
        return restoreService.restore(sourceUri, destinationUri, key, isFileKey, onProgress)
    }
}
