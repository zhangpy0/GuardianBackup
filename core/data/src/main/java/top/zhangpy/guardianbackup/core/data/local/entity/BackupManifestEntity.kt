package top.zhangpy.guardianbackup.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "backup_manifests")
data class BackupManifestEntity(
    @PrimaryKey
    val dirName: String, // 使用目录名作为主键
    val appVersion: String,
    val creationDate: Long
)
