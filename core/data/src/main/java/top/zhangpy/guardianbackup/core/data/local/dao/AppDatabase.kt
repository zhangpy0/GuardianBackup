package top.zhangpy.guardianbackup.core.data.local.dao

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import top.zhangpy.guardianbackup.core.data.local.entity.BackupManifestEntity
import top.zhangpy.guardianbackup.core.data.local.entity.FileMetadataEntity

@Database(
    entities = [BackupManifestEntity::class, FileMetadataEntity::class],
    version = 1 // 每次修改表结构时，都需要增加版本号
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun backupDao(): BackupDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "backup_database" // 数据库文件名
                )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}