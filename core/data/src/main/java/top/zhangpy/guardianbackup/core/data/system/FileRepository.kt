package top.zhangpy.guardianbackup.core.data.system

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment.DIRECTORY_DOWNLOADS
import android.os.Environment.getExternalStoragePublicDirectory
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/** 仓库层：封装所有与设备存储的直接交互。 职责：提供对 MediaStore 和 SAF 的原子化访问接口。 */
class FileRepository(private val context: Context) {

    private val tag: String = "FileRepository"
    private val contentResolver: ContentResolver = context.contentResolver

    // --- 文件发现 ---

    fun getPhotoFolders(): Map<String, List<Uri>> {
        return getMediaFolders(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
    }

    fun getVideoFolders(): Map<String, List<Uri>> {
        return getMediaFolders(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
    }

    fun listFilesWithRelativePaths(baseUri: Uri): Map<Uri, String> {
        val baseDocFile =
                DocumentFile.fromTreeUri(context, baseUri)
                        ?: DocumentFile.fromSingleUri(context, baseUri) ?: return emptyMap()

        val fileMap = mutableMapOf<Uri, String>()
        if (baseDocFile.isDirectory) {
            recursiveListHelper(baseDocFile, "", fileMap)
        } else {
            baseDocFile.name?.let { fileMap[baseDocFile.uri] = it }
        }
        return fileMap
    }

    /**
     * 【已修改】递归地列出给定根目录下所有文件，并返回一个包含每个文件 Uri 及其相对路径的 Map。 新增功能：只包含文件名与给定正则表达式匹配的文件。
     *
     * @param baseUri 要开始遍历的根目录（或单个文件）的 Uri。
     * @param regex 用于过滤文件名的正则表达式。
     * @return 一个 Map，其中键是文件的 Uri，值是该文件相对于 baseUri 的路径字符串。
     */
    fun listFilesWithRelativePaths(baseUri: Uri, regex: Regex): Map<Uri, String> {
        val baseDocFile =
                DocumentFile.fromTreeUri(context, baseUri)
                        ?: DocumentFile.fromSingleUri(context, baseUri) // 同样支持选择单个文件
                         ?: return emptyMap()

        val fileMap = mutableMapOf<Uri, String>()

        if (baseDocFile.isDirectory) {
            // 如果是目录，启动递归辅助函数，并将 regex 传递下去
            recursiveListHelper(baseDocFile, "", fileMap, regex)
        } else {
            // 如果用户只选择了一个文件，同样需要用 regex 进行检查
            baseDocFile.name?.let { fileName ->
                if (regex.matches(fileName)) {
                    // 文件名匹配，则添加到 map 中
                    fileMap[baseDocFile.uri] = fileName
                }
            }
        }

        return fileMap
    }

    // --- 底层 I/O 操作 ---

    fun getInputStream(uri: Uri): InputStream {
        return contentResolver.openInputStream(uri)
                ?: throw IOException("Failed to open InputStream for URI: $uri")
    }

    fun getOutputStream(uri: Uri, mode: String = "w"): OutputStream {
        return contentResolver.openOutputStream(uri, mode)
                ?: throw IOException("Failed to open OutputStream for URI: $uri")
    }

    fun copyUriContent(sourceUri: Uri, destinationUri: Uri) {
        getInputStream(sourceUri).use { input ->
            getOutputStream(destinationUri).use { output -> input.copyTo(output) }
        }
    }

    fun getFileName(uri: Uri): String {
        var fileName: String? = null
        if (ContentResolver.SCHEME_CONTENT == uri.scheme) {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex > -1) {
                        fileName = cursor.getString(nameIndex)
                    }
                }
            }
        }
        if (fileName == null) {
            fileName = uri.lastPathSegment
        }
        return fileName ?: UUID.randomUUID().toString()
    }

    fun getMetadataFromUri(uri: Uri): Triple<String, Long, Long> {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                // 最后修改时间不一定在所有 provider 中都可用
                val lastModified =
                        try {
                            cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                                    .let { cursor.getLong(it) * 1000 }
                        } catch (e: Exception) {
                            System.currentTimeMillis()
                        }

                val name = if (nameIndex > -1) cursor.getString(nameIndex) else getFileName(uri)
                val size = if (sizeIndex > -1) cursor.getLong(sizeIndex) else -1L

                return Triple(name, size, lastModified)
            }
        }
        // 回退方案
        val docFile = DocumentFile.fromSingleUri(context, uri)
        return Triple(
                docFile?.name ?: getFileName(uri),
                docFile?.length() ?: -1L,
                docFile?.lastModified() ?: -1L
        )
    }

    fun findOrCreateDirectoryPath(baseDir: DocumentFile, relativePath: String): DocumentFile? {
        // 使用 java.io.File 纯粹是为了方便地解析路径，它不会进行任何文件IO操作
        val pathParser = File(relativePath)

        // 如果文件就在根目录下，没有父路径，直接返回 baseDir
        val parentPath = pathParser.parent ?: return baseDir

        val pathComponents = parentPath.split(File.separatorChar).filter { it.isNotEmpty() }
        var currentDir = baseDir

        for (component in pathComponents) {
            val nextDir = currentDir.findFile(component)

            currentDir =
                    when {
                        // 目录已存在，直接进入
                        nextDir != null && nextDir.isDirectory -> nextDir

                        // 目录不存在，创建它
                        nextDir == null -> currentDir.createDirectory(component)
                                        ?: return null.also {
                                            Log.e(
                                                    tag,
                                                    "Failed to create directory: $component in ${currentDir.uri}"
                                            )
                                        }

                        // 存在一个同名的文件，这是冲突，无法创建目录
                        else ->
                                return null.also {
                                    Log.e(
                                            tag,
                                            "A file with the name '$component' already exists, cannot create directory."
                                    )
                                }
                    }
        }
        return currentDir
    }

    fun findOrCreateDirectory(baseDir: DocumentFile, relativePath: String): DocumentFile? {
        // 替换 java.io.File.separatorChar 为标准 /
        val normalizedPath = relativePath.replace(File.separatorChar, '/')

        // 按 / 分割，并移除空字符串（例如由 "a//b" 或 "/a" "a/" 产生）
        val pathComponents = normalizedPath.split('/').filter { it.isNotEmpty() }

        var currentDir = baseDir

        for (component in pathComponents) {
            val nextDir = currentDir.findFile(component)

            currentDir =
                    when {
                        // 目录已存在，直接进入
                        nextDir != null && nextDir.isDirectory -> nextDir

                        // 目录不存在，创建它
                        nextDir == null -> currentDir.createDirectory(component)
                                        ?: return null.also {
                                            Log.e(
                                                    tag,
                                                    "Failed to create directory: $component in ${currentDir.uri}"
                                            )
                                        }

                        // 存在一个同名的文件，这是冲突，无法创建目录
                        else ->
                                return null.also {
                                    Log.e(
                                            tag,
                                            "A file with the name '$component' already exists, cannot create directory."
                                    )
                                }
                    }
        }
        return currentDir // 现在 currentDir 是路径的最后一部分
    }

    fun getNewFileUriInDownloads(
            fileName: String,
            mimeType: String = "application/octet-stream"
    ): Uri? {
        val contentValues =
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        put(MediaStore.Downloads.RELATIVE_PATH, "Download/GuardianBackup/")
                        put(MediaStore.Downloads.IS_PENDING, 1) // 标记为正在创建
                    }
                }

        var uri: Uri? = null
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // 对于 Android 10 及以上版本，使用 MediaStore API
                uri =
                        contentResolver.insert(
                                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                contentValues
                        )
                if (uri == null) {
                    Log.e(tag, "Failed to create new file in Downloads: $fileName")
                    return null
                } else {
                    Log.d(tag, "Created new file URI in Downloads: $uri (Pending)")
                }
                // No longer setting IS_PENDING to 0 here. It must be done manually via
                // publishFile()
            } else {
                // 对于 Android 9 及以下版本，直接创建在 Download 目录
                val downloadsDir = getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS)
                val guardianDir = File(downloadsDir, "GuardianBackup")
                if (!guardianDir.exists()) {
                    if (!guardianDir.mkdirs()) {
                        Log.e(tag, "Failed to create GuardianBackup directory in Downloads")
                        return null
                    }
                }
                val newFile = File(guardianDir, fileName)
                if (newFile.exists()) {
                    Log.w(
                            tag,
                            "File already exists and will be overwritten: ${newFile.absolutePath}"
                    )
                    // 如果文件已存在，可以选择删除或覆盖
                    if (!newFile.delete()) {
                        Log.e(tag, "Failed to delete existing file: ${newFile.absolutePath}")
                        return null
                    }
                }
                uri = Uri.fromFile(newFile)
                Log.d(tag, "Created new file URI in Downloads: $uri")
            }
        } catch (e: Exception) {
            Log.e(tag, "Error creating new file in Downloads", e)
            uri = null
        }
        return uri
    }

    fun publishFile(uri: Uri) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            try {
                val contentValues =
                        ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                contentResolver.update(uri, contentValues, null, null)
                Log.d(tag, "Published file: $uri")
            } catch (e: Exception) {
                Log.e(tag, "Failed to publish file: $uri", e)
            }
        }
    }

    /**
     * 在给定的父 DocumentFile 目录下创建一个新的子目录。
     *
     * @param context Context 对象。
     * @param parentDirectoryUri 代表父目录的 DocumentFile 对象 Uri。
     * @param newDirectoryName 要创建的新目录的名称。
     * @return 如果成功创建或目录已存在，则返回新目录的 Uri；如果失败，则返回 null。
     */
    fun createSubDirectory(parentDirectoryUri: Uri, newDirectoryName: String): Uri? {
        val parentDirectory =
                DocumentFile.fromTreeUri(context, parentDirectoryUri)
                        ?: run {
                            Log.e(tag, "无法解析 parentDirectoryUri: $parentDirectoryUri")
                            return null
                        }
        // 1. 前置检查：确保父目录有效且可写
        if (!parentDirectory.isDirectory) {
            Log.e(tag, "提供的 parentDirectory 不是一个目录")
            return null
        }
        if (!parentDirectory.canWrite()) {
            Log.e(tag, "没有写入权限: ${parentDirectory.uri}")
            return null
        }
        if (newDirectoryName.isBlank()) {
            Log.e(tag, "新目录名称不能为空")
            return null
        }

        // 2. 检查同名文件或目录是否已存在
        val existingEntry = parentDirectory.findFile(newDirectoryName)
        if (existingEntry != null) {
            return if (existingEntry.isDirectory) {
                // 目录已经存在，直接返回它的 Uri，视为成功
                Log.d(tag, "目录 '$newDirectoryName' 已存在，直接返回其 Uri")
                existingEntry.uri
            } else {
                // 已存在同名文件，无法创建目录
                Log.e(tag, "创建失败：已存在同名文件 '$newDirectoryName'")
                null
            }
        }

        // 3. 创建新目录
        val newDirectory = parentDirectory.createDirectory(newDirectoryName)

        return if (newDirectory != null) {
            // 4. 创建成功，返回新目录的 Uri
            Log.d(tag, "成功创建目录 '$newDirectoryName'，Uri: ${newDirectory.uri}")
            newDirectory.uri
        } else {
            // 5. 创建失败
            Log.e(tag, "创建目录 '$newDirectoryName' 失败")
            null
        }
    }

    // --- 私有辅助方法 ---
    private fun getMediaFolders(contentUri: Uri): Map<String, List<Uri>> {
        val folders = mutableMapOf<String, MutableList<Uri>>()
        val projection =
                arrayOf(
                        MediaStore.MediaColumns._ID, // 使用 ID 来构造 Uri
                        MediaStore.MediaColumns.BUCKET_DISPLAY_NAME // 文件夹名
                )

        context.contentResolver.query(
                        contentUri,
                        projection,
                        null,
                        null,
                        "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                )
                ?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val bucketColumn =
                            cursor.getColumnIndexOrThrow(
                                    MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
                            )
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val bucket = cursor.getString(bucketColumn)
                        val uri = ContentUris.withAppendedId(contentUri, id)
                        folders.getOrPut(bucket) { mutableListOf() }.add(uri)
                    }
                }
        return folders
    }

    private fun recursiveListHelper(
            currentDirectory: DocumentFile,
            currentRelativePath: String,
            fileMap: MutableMap<Uri, String>
    ) {
        val entries = currentDirectory.listFiles()
        if (entries.isEmpty() && currentRelativePath.isNotEmpty()) {
            // 这是一个空目录（且不是根目录），添加它以便备份
            fileMap[currentDirectory.uri] = currentRelativePath
        }
        // 遍历当前目录下的所有文件和子目录
        for (file in entries) {
            val fileName = file.name ?: continue // 如果文件名为空则跳过

            // 构建下一级的相对路径
            val nextRelativePath =
                    if (currentRelativePath.isEmpty()) {
                        fileName
                    } else {
                        "$currentRelativePath/$fileName"
                    }

            if (file.isDirectory) {
                // 如果是目录，继续向下一层递归
                recursiveListHelper(file, nextRelativePath, fileMap)
            } else {
                // 如果是文件，将其 Uri 和计算出的相对路径存入 Map
                fileMap[file.uri] = nextRelativePath
            }
        }
    }

    /**
     * `listFilesWithRelativePaths` 的递归辅助函数。
     *
     * @param currentDirectory 当前正在遍历的目录的 DocumentFile。
     * @param currentRelativePath 从根目录到 currentDirectory 的路径。
     * @param fileMap 用于累积结果的 Map。
     * @param regex 用于过滤文件名的正则表达式。
     */
    private fun recursiveListHelper(
            currentDirectory: DocumentFile,
            currentRelativePath: String,
            fileMap: MutableMap<Uri, String>,
            regex: Regex // 接收 regex 参数
    ) {
        // 遍历当前目录下的所有文件和子目录
        for (file in currentDirectory.listFiles()) {
            val fileName = file.name ?: continue // 如果文件名为空则跳过

            // 构建下一级的相对路径
            val nextRelativePath =
                    if (currentRelativePath.isEmpty()) {
                        fileName
                    } else {
                        "$currentRelativePath/$fileName"
                    }

            if (file.isDirectory) {
                // 如果是目录，继续向下一层递归
                recursiveListHelper(file, nextRelativePath, fileMap, regex)
            } else {
                // 【核心修改】如果是文件，先检查文件名是否匹配正则表达式
                if (regex.matches(fileName)) {
                    // 如果匹配，才将其 Uri 和计算出的相对路径存入 Map
                    fileMap[file.uri] = nextRelativePath
                }
            }
        }
    }
}
