package top.zhangpy.guardianbackup.core.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import top.zhangpy.guardianbackup.core.data.database.dao.BackupHistoryDao
import top.zhangpy.guardianbackup.core.data.database.entity.BackupHistoryEntity

@Database(entities = [BackupHistoryEntity::class], version = 3)
abstract class AppDatabase : RoomDatabase() {
    abstract fun backupHistoryDao(): BackupHistoryDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE
                    ?: synchronized(this) {
                        val instance =
                                Room.databaseBuilder(
                                                context.applicationContext,
                                                AppDatabase::class.java,
                                                "guardian_backup_db"
                                        )
                                        .fallbackToDestructiveMigration()
                                        .build()
                        INSTANCE = instance
                        instance
                    }
        }
    }
}
