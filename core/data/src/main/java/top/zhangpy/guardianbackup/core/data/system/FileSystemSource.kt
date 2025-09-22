package top.zhangpy.guardianbackup.core.data.system

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import top.zhangpy.guardianbackup.core.data.model.BackupManifest
import top.zhangpy.guardianbackup.core.data.model.BackupRequest // 假设这个类也被修改以接受 Uri
import top.zhangpy.guardianbackup.core.data.model.FileMetadata
import top.zhangpy.guardianbackup.core.data.model.RestoreResult
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec


/**
 * 提供文件系统相关的数据源功能。
 * 负责备份和恢复文件，包括照片、视频和用户指定的文件/文件夹。
 *
 * 功能特性:
 * - 完整性校验 (SHA-256)
 * - 加密 (AES-256-GCM with PBKDF2)
 * - 压缩 (ZIP)
 * - 元数据存储
 *
 * **修改**: 所有外部文件操作已重构为使用 Uri，以兼容 Android 分区存储和 SAF。
 */
class FileSystemSource(private val context: Context) {

    private val gson = Gson()
    private val tag = "FileSystemSource"

    companion object {
        private const val MANIFEST_FILENAME = "manifest.json"
        private const val BUFFER_SIZE = 8192
        private const val SALT_SIZE = 16
        private const val IV_SIZE = 12 // AES/GCM standard IV size is 12 bytes
        private const val PBE_ITERATION_COUNT = 65536
        private const val AES_KEY_SIZE = 256
        private const val GCM_tag_LENGTH = 128
    }

    // =================================================================================
    // Public API - 文件发现
    // =================================================================================

    /**
     * 【保留，但内部不再主要依赖】
     * 将给定的 Uri 转换为一个 java.io.File 对象，通过复制到缓存实现。
     * 在新架构中，此方法主要用于兼容需要 File 对象的旧库或特定场景，应避免用于主要 I/O 流程。
     */
    fun getFileFromUri(context: Context, uri: Uri): File? {
        if (ContentResolver.SCHEME_FILE == uri.scheme) {
            return uri.path?.let { File(it) }
        }
        if (ContentResolver.SCHEME_CONTENT == uri.scheme) {
            val contentResolver = context.contentResolver
            val fileName = getFileName(context, uri)
            val tempFile = File(context.cacheDir, fileName)
            try {
                contentResolver.openInputStream(uri).use { inputStream ->
                    FileOutputStream(tempFile).use { outputStream ->
                        inputStream?.copyTo(outputStream)
                            ?: throw IOException("InputStream is null for URI: $uri")
                    }
                }
                return tempFile
            } catch (e: IOException) {
                Log.e(tag, "Failed to copy URI content to cache file", e)
                tempFile.delete()
                return null
            }
        }
        return null
    }

    /**
     * 尝试从 Uri 获取原始文件名。
     */
    private fun getFileName(context: Context, uri: Uri): String {
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

    /**
     * 【已修改】获取设备上所有照片，按文件夹（Bucket）分组。
     * @return Map<文件夹名称, Uri 列表>
     */
    fun getPhotoFolders(): Map<String, List<Uri>> {
        return getMediaFolders(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
    }

    /**
     * 【已修改】获取设备上所有视频，按文件夹（Bucket）分组。
     * @return Map<文件夹名称, Uri 列表>
     */
    fun getVideoFolders(): Map<String, List<Uri>> {
        return getMediaFolders(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
    }

    // =================================================================================
    // Public API - 备份与恢复
    // =================================================================================

    /**
     * 【已修改】执行备份操作。
     * @param request 包含源 Uri 列表和目标 Uri 的备份请求。
     */
    fun backup(request: BackupRequest): Boolean { // 假设 BackupRequest.sourcePaths 改为 sourceUris: List<Uri>
        // 1. 收集所有需要备份的文件 Uri
        val allUris = request.sourceUris.flatMap { listFilesRecursively(it) }.distinct()
        if (allUris.isEmpty()) {
            Log.w(tag, "No files found to back up.")
            return true // 没有文件也算成功
        }
        Log.d(tag, "Starting backup for ${allUris.size} files.")

        // 2. 创建一个临时的、未加密的 zip 文件（在内部缓存中，所以仍使用 File）
        val tempZipFile = File.createTempFile("backup_unencrypted", ".zip", context.cacheDir)

        try {
            // 3. 压缩文件并生成元数据清单
            createZipArchiveWithManifest(allUris, tempZipFile) { fileName, current, total ->
                request.progressCallback(fileName, current, total)
            }

            // 4. 加密临时 zip 文件到最终目标位置 Uri
            encryptFile(Uri.fromFile(tempZipFile), request.destinationUri, request.password)
            Log.d(tag, "Backup successfully created at ${request.destinationUri}")

        } catch (e: Exception) {
            Log.e(tag, "Backup failed", e)
            return false
        } finally {
            tempZipFile.delete() // 清理临时文件
        }
        return true
    }

    /**
     * 【已修改】执行恢复操作。
     * @param sourceBackupUri 源备份文件的 Uri。
     * @param destinationDirectoryUri 目标恢复目录的 Tree Uri。
     */
    fun restore(sourceBackupUri: Uri, destinationDirectoryUri: Uri, password: CharArray, progressCallback: (String, Int, Int) -> Unit): RestoreResult {
        // 目标目录的检查将由 DocumentFile API 处理
        val tempDecryptedFile = File.createTempFile("backup_decrypted", ".zip", context.cacheDir)

        try {
            // 1. 解密备份文件到一个临时的 zip 文件
            decryptFile(sourceBackupUri, Uri.fromFile(tempDecryptedFile), password)

            // 2. 从临时 zip 文件中读取元数据清单 (仍可使用 File 操作，因其在内部缓存)
            val manifest = readManifestFromZip(tempDecryptedFile)
                ?: return RestoreResult(
                    isSuccess = false,
                    errorMessage = "Manifest file not found or corrupted."
                )

            // 3. 解压文件并进行校验到目标 Uri
            return unpackAndVerify(tempDecryptedFile, manifest, destinationDirectoryUri, progressCallback)

        } catch (e: Exception) {
            Log.e(tag, "Restore failed", e)
            return RestoreResult(isSuccess = false, errorMessage = e.message)
        } finally {
            tempDecryptedFile.delete()
        }
    }


    // =================================================================================
    // Private - 核心实现 (已重构)
    // =================================================================================

    /**
     * 【已修改】不再使用废弃的 DATA 列，而是构造 Uri。
     */
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

    /**
     * 【已修改】使用 DocumentFile 递归列出 Uri 指向的文件。
     */
    private fun listFilesRecursively(uri: Uri): List<Uri> {
        val docFile = DocumentFile.fromTreeUri(context, uri)
            ?: DocumentFile.fromSingleUri(context, uri)

        if (docFile == null) return emptyList()

        return if (docFile.isDirectory) {
            docFile.listFiles().flatMap { listFilesRecursively(it.uri) }
        } else {
            listOf(uri)
        }
    }

    /**
     * 【已修改】通过 Uri 的 InputStream 计算 SHA256。
     */
    private fun calculateSHA256(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri).use { fis ->
            if (fis == null) throw IOException("Unable to open InputStream for $uri")
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * 【辅助方法】从 Uri 获取元数据。
     */
    private fun getMetadataFromUri(uri: Uri): Triple<String, Long, Long> {
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

                val name = if (nameIndex > -1) cursor.getString(nameIndex) else getFileName(context, uri)
                val size = if (sizeIndex > -1) cursor.getLong(sizeIndex) else -1L

                return Triple(name, size, lastModified)
            }
        }
        // 回退方案
        val docFile = DocumentFile.fromSingleUri(context, uri)
        return Triple(docFile?.name ?: getFileName(context, uri), docFile?.length() ?: -1L, docFile?.lastModified() ?: -1L)
    }


    /**
     * 【已修改】从 Uri 列表创建 Zip 存档。
     */
    private fun createZipArchiveWithManifest(
        sourceUris: List<Uri>,
        zipFile: File, // 临时 zip 仍在缓存中，使用 File
        progressCallback: (String, Int, Int) -> Unit
    ): BackupManifest {
        val metadataList = mutableListOf<FileMetadata>()
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            sourceUris.forEachIndexed { index, uri ->
                val (name, size, lastModified) = getMetadataFromUri(uri)
                progressCallback(name, index + 1, sourceUris.size)

                // 路径使用显示名称，恢复时将是扁平结构
                val pathInArchive = name
                val checksum = calculateSHA256(uri)
                metadataList.add(
                    FileMetadata(
                        originalPath = uri.toString(), // 存储 Uri 字符串
                        pathInArchive = pathInArchive,
                        size = size,
                        lastModified = lastModified,
                        sha256Checksum = checksum
                    )
                )

                val entry = ZipEntry(pathInArchive)
                zos.putNextEntry(entry)
                context.contentResolver.openInputStream(uri).use { fis ->
                    fis?.copyTo(zos, BUFFER_SIZE)
                }
                zos.closeEntry()
            }

            // 添加 manifest 文件
            val manifest = BackupManifest("1.0", System.currentTimeMillis(), metadataList)
            zos.putNextEntry(ZipEntry(MANIFEST_FILENAME))
            zos.write(gson.toJson(manifest).toByteArray())
            zos.closeEntry()
            return manifest
        }
    }

    private fun readManifestFromZip(zipFile: File): BackupManifest? {
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == MANIFEST_FILENAME) {
                    return gson.fromJson(InputStreamReader(zis), BackupManifest::class.java)
                }
                entry = zis.nextEntry
            }
        }
        return null
    }

    /**
     * 【已修改】解包并验证到目标目录 Uri。
     */
    private fun unpackAndVerify(zipFile: File, manifest: BackupManifest, destDirUri: Uri, progressCallback: (String, Int, Int) -> Unit): RestoreResult {
        val destDirDocFile = DocumentFile.fromTreeUri(context, destDirUri)
            ?: return RestoreResult(isSuccess = false, errorMessage = "Invalid destination directory URI.")
        if (!destDirDocFile.canWrite()) {
            return RestoreResult(isSuccess = false, errorMessage = "No write permission for destination directory.")
        }

        val checksumMap = manifest.files.associateBy { it.pathInArchive }
        val corruptedFiles = mutableListOf<String>()
        var restoredCount = 0
        val totalFiles = manifest.files.size

        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            var currentIndex = 0
            while (entry != null) {
                val entryName = entry.name
                if (entryName == MANIFEST_FILENAME) {
                    entry = zis.nextEntry
                    continue
                }

                progressCallback(entryName, ++currentIndex, totalFiles)

                val metadata = checksumMap[entryName]
                if (metadata == null) {
                    Log.w(tag, "File $entryName found in zip but not in manifest. Skipping.")
                    entry = zis.nextEntry
                    continue
                }

                // 先解压到临时文件进行校验
                val tempOutputFile = File.createTempFile("restore_item", ".tmp", context.cacheDir)
                FileOutputStream(tempOutputFile).use { fos -> zis.copyTo(fos) }

                // 校验
                val calculatedChecksum = calculateSHA256(Uri.fromFile(tempOutputFile))
                if (calculatedChecksum == metadata.sha256Checksum) {
                    // 校验成功，写入到目标 DocumentFile
                    // 注意：这里简化处理，如果文件已存在则覆盖
                    val targetFile = destDirDocFile.findFile(entryName) ?: destDirDocFile.createFile("*/*", entryName)
                    if (targetFile == null) {
                        Log.e(tag, "Failed to create file in destination: $entryName")
                        corruptedFiles.add(entryName)
                    } else {
                        try {
                            context.contentResolver.openOutputStream(targetFile.uri).use { outStream ->
                                FileInputStream(tempOutputFile).use { inStream ->
                                    inStream.copyTo(outStream!!)
                                }
                            }
                            restoredCount++
                        } catch (e: IOException) {
                            Log.e(tag, "Failed to write to destination file: ${targetFile.uri}", e)
                            corruptedFiles.add(entryName)
                        }
                    }
                } else {
                    Log.e(tag, "Checksum mismatch for $entryName. Expected: ${metadata.sha256Checksum}, Got: $calculatedChecksum")
                    corruptedFiles.add(entryName)
                }
                tempOutputFile.delete()

                entry = zis.nextEntry
            }
        }

        return if (corruptedFiles.isEmpty() && restoredCount == totalFiles) {
            RestoreResult(isSuccess = true, restoredFilesCount = restoredCount, totalFilesCount = totalFiles)
        } else {
            RestoreResult(isSuccess = false, restoredFilesCount = restoredCount, totalFilesCount = totalFiles, errorMessage = "Some files failed verification.", corruptedFiles = corruptedFiles)
        }
    }


    /**
     * 【已修改】使用 Uri 进行文件加密。
     */
    private fun encryptFile(inputFileUri: Uri, outputFileUri: Uri, password: CharArray) {
        val salt = ByteArray(SALT_SIZE).apply { java.security.SecureRandom().nextBytes(this) }
        val keySpec = PBEKeySpec(password, salt, PBE_ITERATION_COUNT, AES_KEY_SIZE)
        val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val secretKey = SecretKeySpec(keyFactory.generateSecret(keySpec).encoded, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv

        context.contentResolver.openOutputStream(outputFileUri).use { fos ->
            if (fos == null) throw IOException("Unable to open OutputStream for $outputFileUri")
            fos.write(salt)
            fos.write(iv)
            context.contentResolver.openInputStream(inputFileUri).use { fis ->
                if (fis == null) throw IOException("Unable to open InputStream for $inputFileUri")
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    val encryptedBytes = cipher.update(buffer, 0, bytesRead)
                    if (encryptedBytes != null) {
                        fos.write(encryptedBytes)
                    }
                }
                val finalBytes = cipher.doFinal()
                if (finalBytes != null) {
                    fos.write(finalBytes)
                }
            }
        }
    }

    /**
     * 【已修改】使用 Uri 进行文件解密。
     */
    private fun decryptFile(inputFileUri: Uri, outputFileUri: Uri, password: CharArray) {
        context.contentResolver.openInputStream(inputFileUri).use { fis ->
            if (fis == null) throw IOException("Unable to open InputStream for $inputFileUri")
            val salt = ByteArray(SALT_SIZE)
            fis.read(salt)
            val iv = ByteArray(IV_SIZE)
            fis.read(iv)

            val keySpec = PBEKeySpec(password, salt, PBE_ITERATION_COUNT, AES_KEY_SIZE)
            val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val secretKey = SecretKeySpec(keyFactory.generateSecret(keySpec).encoded, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmParameterSpec = GCMParameterSpec(GCM_tag_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec)

            context.contentResolver.openOutputStream(outputFileUri).use { fos ->
                if (fos == null) throw IOException("Unable to open OutputStream for $outputFileUri")
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    val decryptedBytes = cipher.update(buffer, 0, bytesRead)
                    if (decryptedBytes != null) {
                        fos.write(decryptedBytes)
                    }
                }
                val finalBytes = cipher.doFinal()
                if (finalBytes != null) {
                    fos.write(finalBytes)
                }
            }
        }
    }
}