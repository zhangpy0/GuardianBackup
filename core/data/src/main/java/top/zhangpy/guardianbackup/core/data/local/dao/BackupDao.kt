package top.zhangpy.guardianbackup.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import top.zhangpy.guardianbackup.core.data.local.entity.BackupManifestEntity
import top.zhangpy.guardianbackup.core.data.local.entity.BackupManifestWithFiles
import top.zhangpy.guardianbackup.core.data.local.entity.FileMetadataEntity

@Dao
interface BackupDao {

    /**
     * 插入一个清单及其所有文件元数据。
     * 这需要在一个事务中完成，以保证数据一致性。
     */
    @Transaction
    suspend fun insertBackupManifestWithFiles(manifest: BackupManifestEntity, files: List<FileMetadataEntity>) {
        insertManifest(manifest)
        insertAllFileMetadata(files)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertManifest(manifest: BackupManifestEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllFileMetadata(files: List<FileMetadataEntity>)

    /**
     * 根据 dirName 查询一个完整的备份清单（包含所有文件）。
     */
    @Transaction
    @Query("SELECT * FROM backup_manifests WHERE dirName = :dirName")
    fun getBackupManifestWithFiles(dirName: String): Flow<BackupManifestWithFiles?>

    /**
     * 查询所有的备份清单（包含所有文件）。
     */
    @Transaction
    @Query("SELECT * FROM backup_manifests")
    fun getAllBackupManifestsWithFiles(): Flow<List<BackupManifestWithFiles>>

    /**
     * 根据 dirName 删除一个清单（由于设置了级联删除，关联的文件元数据也会被删除）。
     */
    @Query("DELETE FROM backup_manifests WHERE dirName = :dirName")
    suspend fun deleteManifestByDirName(dirName: String)
}