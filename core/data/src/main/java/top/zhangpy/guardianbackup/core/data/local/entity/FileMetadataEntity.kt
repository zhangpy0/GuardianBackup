package top.zhangpy.guardianbackup.core.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "file_metadata",
    foreignKeys = [
        ForeignKey(
            entity = BackupManifestEntity::class,
            parentColumns = ["dirName"], // BackupManifestEntity 的主键
            childColumns = ["manifestDirName"], // 本实体中用于关联的外键
            onDelete = ForeignKey.CASCADE // 当主表记录删除时，级联删除所有关联的元数据
        )
    ],
    // 为外键创建索引以提高查询性能
    indices = [Index("manifestDirName")]
)
data class FileMetadataEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0, // 建议为元数据添加一个自增主键
    val manifestDirName: String, // 外键，关联到 BackupManifestEntity.dirName
    val originalPath: String,
    val pathInArchive: String,
    val size: Long,
    val lastModified: Long,
    val sha256Checksum: String
)
