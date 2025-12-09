package top.zhangpy.guardianbackup.core.domain.usecase

import android.net.Uri
import com.google.gson.Gson
import top.zhangpy.guardianbackup.core.domain.model.BackupRecord
import top.zhangpy.guardianbackup.core.domain.repository.BackupRepository
import top.zhangpy.guardianbackup.core.domain.service.BackupService

class BackupUseCase(
        private val backupService: BackupService,
        private val backupRepository: BackupRepository
) {
        private val gson = Gson()

        suspend operator fun invoke(
                sourceUris: Map<Uri, String>,
                destinationUri: Uri,
                key: String?,
                isFileKey: Boolean,
                sourcePath: String? = null,
                sourceDirName: String? = null,
                onProgress: (String, Int, Int) -> Unit
        ): Boolean {
                val result =
                        backupService.backup(
                                sourceUris,
                                destinationUri,
                                key,
                                isFileKey,
                                sourcePath,
                                sourceDirName,
                                onProgress
                        )
                if (result.isSuccess) {
                        // Save history
                        val record =
                                BackupRecord(
                                        filePath = destinationUri.toString(),
                                        timestamp = System.currentTimeMillis(),
                                        sizeBytes = result.sizeBytes,
                                        fileCount = result.fileCount,
                                        displayPath = result.displayPath,
                                        manifestJson =
                                                if (result.manifest != null)
                                                        gson.toJson(result.manifest)
                                                else null
                                )
                        backupRepository.addBackupRecord(record)
                }
                return result.isSuccess
        }
}
