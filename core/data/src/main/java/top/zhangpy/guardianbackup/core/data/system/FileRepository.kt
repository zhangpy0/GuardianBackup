package top.zhangpy.guardianbackup.core.data.system

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * 仓库层：封装所有与设备存储的直接交互。
 * 职责：提供对 MediaStore 和 SAF 的原子化访问接口。
 */
class FileRepository(private val context: Context) {

    private val tag : String = "FileRepository"
    private val contentResolver: ContentResolver = context.contentResolver

    // --- 文件发现 ---

    fun getPhotoFolders(): Map<String, List<Uri>> {
        return getMediaFolders(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
    }

    fun getVideoFolders(): Map<String, List<Uri>> {
        return getMediaFolders(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
    }

    fun listFilesWithRelativePaths(baseUri: Uri): Map<Uri, String> {
        val baseDocFile = DocumentFile.fromTreeUri(context, baseUri)
            ?: DocumentFile.fromSingleUri(context, baseUri)
            ?: return emptyMap()

        val fileMap = mutableMapOf<Uri, String>()
        if (baseDocFile.isDirectory) {
            recursiveListHelper(baseDocFile, "", fileMap)
        } else {
            baseDocFile.name?.let { fileMap[baseDocFile.uri] = it }
        }
        return fileMap
    }

    fun listFilesWithRelativePaths(baseUri: Uri, regex: Regex): Map<Uri, String> {
        val baseDocFile = DocumentFile.fromTreeUri(context, baseUri)
            ?: DocumentFile.fromSingleUri(context, baseUri)
            ?: return emptyMap()

        val fileMap = mutableMapOf<Uri, String>()
        if (baseDocFile.isDirectory) {
            recursiveListHelper(baseDocFile, "", fileMap)
        } else {
            baseDocFile.name?.let { fileMap[baseDocFile.uri] = it }
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
            getOutputStream(destinationUri).use { output ->
                input.copyTo(output)
            }
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
                val lastModified = try {
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
        return Triple(docFile?.name ?: getFileName(uri), docFile?.length() ?: -1L, docFile?.lastModified() ?: -1L)
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

            currentDir = when {
                // 目录已存在，直接进入
                nextDir != null && nextDir.isDirectory -> nextDir

                // 目录不存在，创建它
                nextDir == null -> currentDir.createDirectory(component)
                    ?: return null.also { Log.e(tag, "Failed to create directory: $component in ${currentDir.uri}") }

                // 存在一个同名的文件，这是冲突，无法创建目录
                else -> return null.also { Log.e(tag, "A file with the name '$component' already exists, cannot create directory.") }
            }
        }
        return currentDir
    }

    // --- 私有辅助方法 ---
    private fun getMediaFolders(contentUri: Uri): Map<String, List<Uri>> {
        val folders = mutableMapOf<String, MutableList<Uri>>()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID, // 使用 ID 来构造 Uri
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME // 文件夹名
        )

        context.contentResolver.query(contentUri, projection, null, null, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")
            ?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val bucketColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
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
        // 遍历当前目录下的所有文件和子目录
        for (file in currentDirectory.listFiles()) {
            val fileName = file.name ?: continue // 如果文件名为空则跳过

            // 构建下一级的相对路径
            val nextRelativePath = if (currentRelativePath.isEmpty()) {
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
}