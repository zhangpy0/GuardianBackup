package top.zhangpy.guardianbackup.core.data.system

import top.zhangpy.guardianbackup.core.data.model.BackupManifest
import top.zhangpy.guardianbackup.core.data.model.BackupRequest
import top.zhangpy.guardianbackup.core.data.model.FileMetadata
import top.zhangpy.guardianbackup.core.data.model.RestoreResult

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.google.gson.Gson
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.security.MessageDigest
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
 * **权限要求**:
 * - `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` 或 Android 13+ 的 `READ_MEDIA_VISUAL_USER_SELECTED`
 * - 对于旧版 Android，可能需要 `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE`
 * - 对于 SD 卡写入，强烈建议使用 Storage Access Framework (SAF)。
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
        private const val GCM_TAG_LENGTH = 128
    }

    // =================================================================================
    // Public API - 文件发现
    // =================================================================================

    /**
     * 获取设备上所有照片，按文件夹（Bucket）分组。
     * @return Map<文件夹名称, 文件列表>
     */
    fun getPhotoFolders(): Map<String, List<File>> {
        return getMediaFolders(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
    }

    /**
     * 获取设备上所有视频，按文件夹（Bucket）分组。
     * @return Map<文件夹名称, 文件列表>
     */
    fun getVideoFolders(): Map<String, List<File>> {
        return getMediaFolders(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
    }

    // =================================================================================
    // Public API - 备份与恢复
    // =================================================================================

    /**
     * 执行备份操作。
     */
    fun backup(request: BackupRequest): Boolean {
        // 1. 收集所有需要备份的文件
        val allFiles = request.sourcePaths.flatMap { listFilesRecursively(it) }.distinct()
        if (allFiles.isEmpty()) {
            Log.w(tag, "No files found to back up.")
            return true // 没有文件也算成功
        }
        val totalFiles = allFiles.size
        Log.d(tag, "Starting backup for ${allFiles.size} files.")

        // 2. 创建一个临时的、未加密的 zip 文件
        val tempZipFile = File.createTempFile("backup_unencrypted", ".zip", context.cacheDir)
        val manifest: BackupManifest

        try {
            // 3. 压缩文件并生成元数据清单
            manifest = createZipArchiveWithManifest(allFiles, tempZipFile) { fileName, current, total ->
                request.progressCallback(fileName, current, total)
            }

            // 4. 加密临时 zip 文件到最终目标位置
            encryptFile(tempZipFile, request.destinationFile, request.password)
            Log.d(tag, "Backup successfully created at ${request.destinationFile.absolutePath}")

        } catch (e: Exception) {
            Log.e(tag, "Backup failed", e)
            return false
        } finally {
            tempZipFile.delete() // 清理临时文件
        }
        return true
    }

    /**
     * 执行恢复操作。
     */
    fun restore(sourceBackupFile: File, destinationDirectory: File, password: CharArray, progressCallback: (String, Int, Int) -> Unit): RestoreResult {
        if (!destinationDirectory.exists()) {
            destinationDirectory.mkdirs()
        }

        val tempDecryptedFile = File.createTempFile("backup_decrypted", ".zip", context.cacheDir)

        try {
            // 1. 解密备份文件到一个临时的 zip 文件
            decryptFile(sourceBackupFile, tempDecryptedFile, password)

            // 2. 从临时 zip 文件中读取元数据清单
            val manifest = readManifestFromZip(tempDecryptedFile)
                ?: return RestoreResult(
                    isSuccess = false,
                    errorMessage = "Manifest file not found or corrupted."
                )

            // 3. 解压文件并进行校验
            return unpackAndVerify(tempDecryptedFile, manifest, destinationDirectory, progressCallback)

        } catch (e: Exception) {
            Log.e(tag, "Restore failed", e)
            return RestoreResult(isSuccess = false, errorMessage = e.message)
        } finally {
            tempDecryptedFile.delete()
        }
    }


    // =================================================================================
    // Private - 核心实现
    // =================================================================================

    private fun getMediaFolders(uri: android.net.Uri): Map<String, List<File>> {
        val folders = mutableMapOf<String, MutableList<File>>()
        val projection = arrayOf(
            MediaStore.MediaColumns.DATA, // 文件路径
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME // 文件夹名
        )

        context.contentResolver.query(uri, projection, null, null, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")
            ?.use { cursor ->
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                val bucketColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataColumn)
                    val bucket = cursor.getString(bucketColumn)
                    val file = File(path)
                    if (file.exists()) {
                        folders.getOrPut(bucket) { mutableListOf() }.add(file)
                    }
                }
            }
        return folders
    }

    private fun listFilesRecursively(file: File): List<File> {
        return if (file.isDirectory) {
            file.listFiles()?.flatMap { listFilesRecursively(it) } ?: emptyList()
        } else {
            listOf(file)
        }
    }

    private fun calculateSHA256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun createZipArchiveWithManifest(
        files: List<File>,
        zipFile: File,
        progressCallback: (String, Int, Int) -> Unit
    ): BackupManifest {
        val metadataList = mutableListOf<FileMetadata>()
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            files.forEachIndexed { index, file ->
                progressCallback(file.name, index + 1, files.size)

                val pathInArchive = file.absolutePath.substringAfter(files.first().parentFile?.parent ?: "")
                val checksum = calculateSHA256(file)
                metadataList.add(
                    FileMetadata(
                        file.absolutePath,
                        pathInArchive,
                        file.length(),
                        file.lastModified(),
                        checksum
                    )
                )

                val entry = ZipEntry(pathInArchive)
                zos.putNextEntry(entry)
                FileInputStream(file).use { fis -> fis.copyTo(zos, BUFFER_SIZE) }
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

    private fun unpackAndVerify(zipFile: File, manifest: BackupManifest, destDir: File, progressCallback: (String, Int, Int) -> Unit): RestoreResult {
        val checksumMap = manifest.files.associateBy { it.pathInArchive }
        val corruptedFiles = mutableListOf<String>()
        var restoredCount = 0
        val totalFiles = manifest.files.size

        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            var currentIndex = 0
            while (entry != null) {
                if (entry.name == MANIFEST_FILENAME) {
                    entry = zis.nextEntry
                    continue
                }

                progressCallback(entry.name, ++currentIndex, totalFiles)

                val metadata = checksumMap[entry.name]
                if (metadata == null) {
                    Log.w(tag, "File ${entry.name} found in zip but not in manifest. Skipping.")
                    entry = zis.nextEntry
                    continue
                }

                val outputFile = File(destDir, entry.name)
                outputFile.parentFile?.mkdirs()

                // 先解压到临时文件
                val tempOutputFile = File.createTempFile("restore_item", ".tmp", context.cacheDir)
                FileOutputStream(tempOutputFile).use { fos -> zis.copyTo(fos) }

                // 校验
                val calculatedChecksum = calculateSHA256(tempOutputFile)
                if (calculatedChecksum == metadata.sha256Checksum) {
                    // 校验成功，移动到最终位置
                    if (tempOutputFile.renameTo(outputFile)) {
                        restoredCount++
                    } else {
                        Log.e(tag, "Failed to move verified file to final destination: ${outputFile.path}")
                        corruptedFiles.add(entry.name)
                    }
                } else {
                    Log.e(tag, "Checksum mismatch for ${entry.name}. Expected: ${metadata.sha256Checksum}, Got: $calculatedChecksum")
                    corruptedFiles.add(entry.name)
                }
                tempOutputFile.delete() // 清理临时文件

                entry = zis.nextEntry
            }
        }

        return if (corruptedFiles.isEmpty() && restoredCount == totalFiles) {
            RestoreResult(
                isSuccess = true,
                restoredFilesCount = restoredCount,
                totalFilesCount = totalFiles
            )
        } else {
            RestoreResult(
                isSuccess = false,
                restoredFilesCount = restoredCount,
                totalFilesCount = totalFiles,
                errorMessage = "Some files failed verification.",
                corruptedFiles = corruptedFiles
            )
        }
    }

    private fun encryptFile(inputFile: File, outputFile: File, password: CharArray) {
        // 1. 生成随机盐
        val salt = ByteArray(SALT_SIZE).apply { java.security.SecureRandom().nextBytes(this) }

        // 2. 从密码和盐派生密钥
        val keySpec = PBEKeySpec(password, salt, PBE_ITERATION_COUNT, AES_KEY_SIZE)
        val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val secretKey = SecretKeySpec(keyFactory.generateSecret(keySpec).encoded, "AES")

        // 3. 初始化 Cipher
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv // 获取随机生成的 IV

        // 4. 将 盐 和 IV 写入文件头部，以便解密时使用
        FileOutputStream(outputFile).use { fos ->
            fos.write(salt)
            fos.write(iv)
            // 5. 加密文件内容
            FileInputStream(inputFile).use { fis ->
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

    private fun decryptFile(inputFile: File, outputFile: File, password: CharArray) {
        FileInputStream(inputFile).use { fis ->
            // 1. 从文件头部读取 盐 和 IV
            val salt = ByteArray(SALT_SIZE)
            fis.read(salt)
            val iv = ByteArray(IV_SIZE)
            fis.read(iv)

            // 2. 从密码和盐派生出相同的密钥
            val keySpec = PBEKeySpec(password, salt, PBE_ITERATION_COUNT, AES_KEY_SIZE)
            val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val secretKey = SecretKeySpec(keyFactory.generateSecret(keySpec).encoded, "AES")

            // 3. 初始化 Cipher
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmParameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec)

            // 4. 解密文件内容
            FileOutputStream(outputFile).use { fos ->
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