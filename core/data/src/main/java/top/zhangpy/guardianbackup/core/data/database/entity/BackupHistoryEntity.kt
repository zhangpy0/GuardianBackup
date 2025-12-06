package top.zhangpy.guardianbackup.core.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "backup_history")
data class BackupHistoryEntity(
        @PrimaryKey(autoGenerate = true) val id: Long = 0,
        val filePath: String,
        val timestamp: Long,
        val sizeBytes: Long,
        val fileCount: Int
)
