package top.zhangpy.guardianbackup.core.domain.repository

import kotlinx.coroutines.flow.Flow
import top.zhangpy.guardianbackup.core.domain.model.BackupRecord

interface BackupRepository {
    fun getAllBackupHistory(): Flow<List<BackupRecord>>
    suspend fun addBackupRecord(record: BackupRecord)
    suspend fun deleteBackupRecord(record: BackupRecord)
}
