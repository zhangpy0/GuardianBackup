package top.zhangpy.guardianbackup.core.data.model

import android.content.Context
import android.net.Uri
import top.zhangpy.guardianbackup.core.data.system.FileSystemSource

/**
 * 使用建造者模式来配置和创建 BackupRequest
 *
 * @param context Android上下文，用于访问ContentResolver等系统服务
 */
class BackupRequestBuilder(private val context: Context) {
    private var sourceUrisAndPath: Map<Uri, String> = emptyMap()
    private var destinationUri: Uri? = null
    private var isZipped: Boolean = false
    private var encryptionAlgorithm: String? = null
    private var password: CharArray? = null
    private var progressCallback: (String, Int, Int) -> Unit = { _, _, _ -> }

    /**
     * 设置要备份的源文件/文件夹的 Uri 列表
     */
    fun sourceUrisAndPath(uris: Map<Uri, String>): BackupRequestBuilder {
        this.sourceUrisAndPath = uris
        return this // 返回自身以实现链式调用
    }

    /**
     * 设置备份文件的目标存储位置 Uri
     */
    fun destinationUri(uri: Uri): BackupRequestBuilder {
        this.destinationUri = uri
        return this
    }

    /**
     * 配置是否对源文件进行压缩
     * @param enabled 如果为 true，则将所有源文件打包成一个 zip 压缩包
     */
    fun zip(enabled: Boolean): BackupRequestBuilder {
        this.isZipped = enabled
        return this
    }

    /**
     * 配置加密选项
     * @param algorithm 加密算法 (当前仅支持 "AES")
     * @param key 用于加密的密码
     */
    fun encrypt(algorithm: String, key: CharArray): BackupRequestBuilder {
        // 在实际应用中，可以根据 algorithm 参数选择不同的加密实现
        this.encryptionAlgorithm = algorithm
        this.password = key
        return this
    }

    /**
     * 设置备份进度的回调函数
     */
    fun onProgress(callback: (fileName: String, current: Int, total: Int) -> Unit): BackupRequestBuilder {
        this.progressCallback = callback
        return this
    }

    /**
     * 验证配置并构建最终的 BackupRequest 对象。
     * 类似于您示例中的 .pack()
     */
    fun build(): BackupRequest {
        // 进行必要的参数校验
        if (sourceUrisAndPath.isEmpty()) {
            throw IllegalArgumentException("Source URIs cannot be empty.")
        }
        if (destinationUri == null) {
            throw IllegalArgumentException("Destination URI must be set.")
        }
        if (encryptionAlgorithm != null && password == null) {
            throw IllegalArgumentException("Password is required for encryption.")
        }

        return BackupRequest(
            context = context, // 传递上下文
            sourceUrisAndPath = this.sourceUrisAndPath,
            destinationUri = this.destinationUri!!,
            isZipped = this.isZipped,
            encryptionAlgorithm = this.encryptionAlgorithm,
            password = this.password,
            progressCallback = this.progressCallback
        )
    }
}

/**
 * 备份请求的配置对象
 * @param sourcePaths 要备份的源文件/文件夹列表
 * @param destinationFile 备份后生成的加密压缩包文件
 * @param password 用于加密的密码
 * @param progressCallback 一个回调函数，用于报告备份进度 (当前处理的文件名, 当前文件索引, 总文件数)
 */
data class BackupRequest(
    internal val context: Context, // 内部使用，用于执行操作
    val sourceUrisAndPath: Map<Uri, String>,
    val destinationUri: Uri,
    val isZipped: Boolean,
    val encryptionAlgorithm: String?,
    val password: CharArray?,
    val progressCallback: (String, Int, Int) -> Unit
) {
    // 为了简化，我们重写 equals 和 hashCode，因为 CharArray 的默认比较是基于引用的
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as BackupRequest
        return sourceUrisAndPath == other.sourceUrisAndPath &&
                destinationUri == other.destinationUri &&
                isZipped == other.isZipped &&
                encryptionAlgorithm == other.encryptionAlgorithm &&
                password.contentEquals(other.password)
    }

    override fun hashCode(): Int {
        var result = sourceUrisAndPath.hashCode()
        result = 31 * result + destinationUri.hashCode()
        result = 31 * result + isZipped.hashCode()
        result = 31 * result + (encryptionAlgorithm?.hashCode() ?: 0)
        result = 31 * result + (password?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * 为 BackupRequest 添加一个执行备份的扩展函数
 * 这就是您期望的 .backUp()
 */
fun BackupRequest.execute(): Boolean {
    // 将具体的备份逻辑委托给 FileSystemSource
    return FileSystemSource(context).backup(this)
}

class RestoreRequestBuilder(private val context: Context) {
    private var sourceBackupUri: Uri? = null
    private var destinationDirectoryUri: Uri? = null
    private var password: CharArray? = null
    private var progressCallback: (String, Int, Int) -> Unit = { _, _, _ -> }

    fun source(uri: Uri): RestoreRequestBuilder {
        this.sourceBackupUri = uri
        return this
    }

    fun destination(uri: Uri): RestoreRequestBuilder {
        this.destinationDirectoryUri = uri
        return this
    }

    fun withPassword(key: CharArray): RestoreRequestBuilder {
        this.password = key
        return this
    }

    fun onProgress(callback: (fileName: String, current: Int, total: Int) -> Unit): RestoreRequestBuilder {
        this.progressCallback = callback
        return this
    }

    fun build(): RestoreRequest {
        return RestoreRequest(
            context = context,
            sourceBackupUri = sourceBackupUri ?: throw IllegalArgumentException("Source backup URI must be set."),
            destinationDirectoryUri = destinationDirectoryUri ?: throw IllegalArgumentException("Destination directory URI must be set."),
            password = password,
            progressCallback = progressCallback
        )
    }
}
/**
 * 恢复请求的配置对象
 * @param sourceBackupUri 备份包文件的 Uri
 * @param destinationDirectoryUri 恢复后文件存放的目标目录 Uri
 * @param password 用于解密的密码
 * @param progressCallback 一个回调函数，用于报告恢复进度 (当前处理的文件名, 当前文件索引, 总文件数)
 */
data class RestoreRequest(
    internal val context: Context,
    val sourceBackupUri: Uri,
    val destinationDirectoryUri: Uri,
    val password: CharArray?,
    val progressCallback: (String, Int, Int) -> Unit
)

fun RestoreRequest.execute(): RestoreResult {
    return FileSystemSource(context).restore(this)
}

/**
 * 恢复操作的结果
 * @param isSuccess 操作是否完全成功
 * @param restoredFilesCount 成功恢复的文件数量
 * @param totalFilesCount 备份包中的总文件数
 * @param errorMessage 如果失败，则包含错误信息
 * @param corruptedFiles 恢复期间发现的损坏（校验和不匹配）的文件列表
 */
data class RestoreResult(
    val isSuccess: Boolean,
    val restoredFilesCount: Int = 0,
    val totalFilesCount: Int = 0,
    val errorMessage: String? = null,
    val corruptedFiles: List<String> = emptyList()
)

/**
 * 用于存储在备份包内的文件元数据
 * @param originalPath 文件的原始绝对路径
 * @param pathInArchive 文件在压缩包内的相对路径
 * @param size 文件大小
 * @param lastModified 最后修改时间
 * @param sha256Checksum 文件的 SHA-256 校验和，用于保证完整性
 */
data class FileMetadata(
    val originalPath: String,
    val pathInArchive: String,
    val size: Long,
    val lastModified: Long,
    val sha256Checksum: String
)

/**
 * 包含所有文件元数据的清单
 */
data class BackupManifest(
    val dirName: String,
    val appVersion: String, // 用于未来可能的迁移
    val creationDate: Long,
    val files: List<FileMetadata>
)