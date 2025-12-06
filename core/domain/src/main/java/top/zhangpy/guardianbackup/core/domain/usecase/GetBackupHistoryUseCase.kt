package top.zhangpy.guardianbackup.core.domain.usecase

import kotlinx.coroutines.flow.Flow
import top.zhangpy.guardianbackup.core.domain.model.BackupRecord
import top.zhangpy.guardianbackup.core.domain.repository.BackupRepository

class GetBackupHistoryUseCase(private val repository: BackupRepository) {
    operator fun invoke(): Flow<List<BackupRecord>> {
        return repository.getAllBackupHistory()
    }
}
