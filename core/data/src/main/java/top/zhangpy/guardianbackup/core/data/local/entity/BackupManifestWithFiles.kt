package top.zhangpy.guardianbackup.core.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class BackupManifestWithFiles(
    @Embedded
    val manifest: BackupManifestEntity,

    @Relation(
        parentColumn = "dirName", // 主表 (BackupManifestEntity) 的关联列
        entityColumn = "manifestDirName" // 关联表 (FileMetadataEntity) 的关联列
    )
    val files: List<FileMetadataEntity>
)
