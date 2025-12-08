package top.zhangpy.guardianbackup.core.data.repository

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import top.zhangpy.guardianbackup.core.data.database.AppDatabase
import top.zhangpy.guardianbackup.core.data.database.entity.BackupHistoryEntity
import top.zhangpy.guardianbackup.core.domain.model.BackupRecord
import top.zhangpy.guardianbackup.core.domain.repository.BackupRepository

class BackupRepositoryImpl(private val context: Context) : BackupRepository {
    private val dao = AppDatabase.getDatabase(context).backupHistoryDao()

    override fun getAllBackupHistory(): Flow<List<BackupRecord>> {
        return dao.getAllHistory().map { entities ->
            entities.map { entity ->
                BackupRecord(
                        id = entity.id,
                        filePath = entity.filePath,
                        timestamp = entity.timestamp,
                        sizeBytes = entity.sizeBytes,
                        fileCount = entity.fileCount,
                        manifestJson = entity.manifestJson
                )
            }
        }
    }

    override suspend fun addBackupRecord(record: BackupRecord) {
        dao.insert(
                BackupHistoryEntity(
                        id = record.id,
                        filePath = record.filePath,
                        timestamp = record.timestamp,
                        sizeBytes = record.sizeBytes,
                        fileCount = record.fileCount,
                        manifestJson = record.manifestJson
                )
        )
    }

    override suspend fun deleteBackupRecord(record: BackupRecord) {
        dao.delete(
                BackupHistoryEntity(
                        id = record.id,
                        filePath = record.filePath,
                        timestamp = record.timestamp,
                        sizeBytes = record.sizeBytes,
                        fileCount = record.fileCount,
                        manifestJson = record.manifestJson
                )
        )
    }
}
