package top.zhangpy.guardianbackup.core.domain.usecase

import android.net.Uri
import top.zhangpy.guardianbackup.core.domain.model.BackupRecord
import top.zhangpy.guardianbackup.core.domain.repository.BackupRepository
import top.zhangpy.guardianbackup.core.domain.service.BackupService

class BackupUseCase(
        private val backupService: BackupService,
        private val backupRepository: BackupRepository
) {
    suspend operator fun invoke(
            sourceUris: Map<Uri, String>,
            destinationUri: Uri,
            key: String?,
            isFileKey: Boolean,
            onProgress: (String, Int, Int) -> Unit
    ): Boolean {
        val success = backupService.backup(sourceUris, destinationUri, key, isFileKey, onProgress)
        if (success) {
            // Save history
            // Note: File size and count might need to be returned by backupService to be accurate.
            // For now, we record what we can or estimate.
            // Or better, backupService should return a BackupResult with metadata.
            // Simplified: Just record success timestamp for now.
            val record =
                    BackupRecord(
                            filePath = destinationUri.toString(),
                            timestamp = System.currentTimeMillis(),
                            sizeBytes =
                                    0, // Placeholder, implementing real size would require File API
                            // access here or from Service
                            fileCount = sourceUris.size
                    )
            backupRepository.addBackupRecord(record)
        }
        return success
    }
}
