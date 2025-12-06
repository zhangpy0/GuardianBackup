package top.zhangpy.guardianbackup.core.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import top.zhangpy.guardianbackup.core.data.database.entity.BackupHistoryEntity

@Dao
interface BackupHistoryDao {
    @Query("SELECT * FROM backup_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<BackupHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(entity: BackupHistoryEntity)

    @Delete suspend fun delete(entity: BackupHistoryEntity)
}
