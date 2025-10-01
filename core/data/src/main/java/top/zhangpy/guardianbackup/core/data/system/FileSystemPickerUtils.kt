package top.zhangpy.guardianbackup.core.data.system

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.core.content.edit

/**
 * 一个用于处理 Android 存储访问框架 (SAF) 目录选择的工具类。
 *
 * 封装了以下功能：
 * 1. 创建用于打开目录选择器的 Intent。
 * 2. 处理从选择器返回的结果，并获取持久化权限。
 * 3. 使用 SharedPreferences 持久化存储和加载目录 URI。
 * 4. 提供一个使用 DocumentFile 与所选目录内容交互的示例方法。
 */
object FileSystemPickerUtils {

    private const val PREFS_NAME = "DirectoryPickerPrefs"
    private const val KEY_DIRECTORY_URI = "directoryUri"
    private const val TAG = "DirectoryPickerUtils"

    /**
     * 创建一个用于启动系统目录选择器的 Intent。
     * @return 用于 ActivityResultLauncher.launch() 的 Intent。
     */
    fun createDirectoryPickerIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
    }

    fun createFilePickerIntent(mimeTypes: Array<String>? = null): Intent {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (mimeTypes != null && mimeTypes.size == 1) {
                mimeTypes[0]
            } else {
                "*/*" // 支持多种类型时，使用通配符
            }
            if (mimeTypes != null && mimeTypes.size > 1) {
                putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
            }
        }
        return intent
    }

    /**
     * 在 ActivityResultCallback 中调用此方法来处理返回的结果。
     * 如果用户成功选择了一个目录，此方法会获取持久化权限并调用 onDirectorySelected 回调。
     *
     * @param context Context 对象
     * @param resultCode 来自 onActivityResult 或 ActivityResultCallback 的结果码。
     * @param data 来自 onActivityResult 或 ActivityResultCallback 的 Intent 数据。
     * @param onDirectorySelected 一个高阶函数，当目录成功被选择并获取权限后，会携带 Uri 被调用。
     */
    fun handleDirectoryPickerResult(
        context: Context,
        resultCode: Int,
        data: Intent?,
        onDirectorySelected: (uri: Uri) -> Unit
    ) {
        if (resultCode == Activity.RESULT_OK) {
            val uri: Uri? = data?.data
            if (uri != null) {
                // 获取持久化权限
                val contentResolver = context.applicationContext.contentResolver
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                try {
                    contentResolver.takePersistableUriPermission(uri, takeFlags)
                    Log.d(TAG, "成功获取目录的持久化权限: $uri")
                    // 通过回调返回成功的 URI
                    onDirectorySelected(uri)
                } catch (e: SecurityException) {
                    Log.e(TAG, "获取持久化权限失败", e)
                }
            }
        } else {
            Log.w(TAG, "用户取消了目录选择")
        }
    }

    fun handleFilePickerResult(
        context: Context,
        resultCode: Int,
        data: Intent?,
        onFileSelected: (uri: Uri) -> Unit
    ) {
        if (resultCode == Activity.RESULT_OK) {
            val uri: Uri? = data?.data
            if (uri != null) {
                // 获取持久化权限
                val contentResolver = context.applicationContext.contentResolver
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                try {
                    contentResolver.takePersistableUriPermission(uri, takeFlags)
                    Log.d(TAG, "成功获取文件的持久化权限: $uri")
                    // 通过回调返回成功的 URI
                    onFileSelected(uri)
                } catch (e: SecurityException) {
                    Log.e(TAG, "获取持久化权限失败", e)
                }
            }
        } else {
            Log.w(TAG, "用户取消了文件选择")
        }
    }

    /**
     * 将获取到的目录 Uri 保存到 SharedPreferences 中。
     * @param context Context 对象
     * @param uri 要保存的目录 Uri。
     */
    fun saveDirectoryUri(context: Context, uri: Uri) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putString(KEY_DIRECTORY_URI, uri.toString()) }
        Log.d(TAG, "目录 URI 已保存: $uri")
    }

    /**
     * 从 SharedPreferences 加载之前保存的目录 Uri。
     * 此方法会验证应用是否仍然拥有该 Uri 的访问权限。
     * @param context Context 对象
     * @return 如果存在且权限有效，则返回 Uri；否则返回 null。
     */
    fun loadSavedDirectoryUri(context: Context): Uri? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uriString = prefs.getString(KEY_DIRECTORY_URI, null)
        if (uriString != null) {
            val uri = Uri.parse(uriString)
            // 验证我们是否仍然有权限访问此 URI
            val persistedPermissions = context.contentResolver.persistedUriPermissions
            val hasPermission = persistedPermissions.any { it.uri == uri && (it.isReadPermission || it.isWritePermission) }

            return if (hasPermission) {
                Log.d(TAG, "已加载并验证有效的 URI: $uri")
                uri
            } else {
                Log.w(TAG, "权限已被撤销，无法加载 URI: $uri")
                // 清除无效的 URI
                prefs.edit().remove(KEY_DIRECTORY_URI).apply()
                null
            }
        }
        return null
    }

    /**
     * 使用 DocumentFile 列出指定目录 Uri 下的文件和子目录。
     * @param context Context 对象
     * @param directoryUri 目录的 Tree Uri。
     * @return DocumentFile 对象列表，如果出错则返回空列表。
     */
    fun listFilesInDirectory(context: Context, directoryUri: Uri): List<DocumentFile> {
        val directory = DocumentFile.fromTreeUri(context, directoryUri)
        if (directory == null || !directory.isDirectory) {
            Log.w(TAG, "提供的 URI 不是一个有效的目录")
            return emptyList()
        }
        return directory.listFiles().toList()
    }

    fun createFileInDirectory(context: Context, directoryUri: Uri, fileName: String, mimeType: String): Uri? {
        val directory = DocumentFile.fromTreeUri(context, directoryUri)
        if (directory == null || !directory.isDirectory || !directory.canWrite()) {
            Log.w(TAG, "提供的 URI 不是一个有效的目录")
            throw IllegalArgumentException("Invalid directory URI or no write permission")
        }
        return directory.createFile(mimeType, fileName)?.uri
    }
}