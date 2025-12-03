package top.zhangpy.guardianbackup.core.data.system

import android.content.Context
import android.net.Uri
import android.util.Log
import top.zhangpy.guardianbackup.core.data.model.BackupManifest
import top.zhangpy.guardianbackup.core.data.model.BackupRequest
import top.zhangpy.guardianbackup.core.data.model.RestoreRequest
import top.zhangpy.guardianbackup.core.data.model.RestoreResult
import java.io.File


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

    private val tag = "FileSystemSource"

    private val fileRepository = FileRepository(context)
    private val integrityService = IntegrityService(fileRepository)
    private val encryptionService : IEncryptionService = EncryptionCBCStreamService(fileRepository)
    private val archiveService = ArchiveService(context, fileRepository, integrityService)

    /**
     * 【已修改】执行备份操作。
     * @param request 包含源 Uri 列表和目标 Uri 的备份请求。
     */
    fun backup(request: BackupRequest): Boolean {
        // 1. 收集所有需要备份的文件 Uri
        val allUris = request.sourceUrisAndPath.keys
        if (allUris.isEmpty()) {
            Log.w(tag, "No files found to back up.")
            return true // 没有文件也算成功
        }
        Log.d(tag, "Starting backup for ${allUris.size} files.")

        // 临时文件列表，用于在操作链中传递中间产物，并在最后清理
        val tempFilesToClean = mutableListOf<File>()

        var manifest : BackupManifest? = null
        val backupFileName = request.destinationUri.lastPathSegment?.removeSuffix(".dat")


        try {
            var currentSourceUris = allUris.toList()
            var urisAreInTempZip = false // 标记当前处理的是否是临时的 zip

            // 2. [条件性压缩]：如果请求配置了 zip
            if (request.isZipped) {
                Log.d(tag, "Step 1: Zipping files.")
                val tempZipFile = File.createTempFile(backupFileName?: "backup", ".zip", context.cacheDir)
                tempFilesToClean.add(tempZipFile)

                manifest = archiveService.createArchiveWithManifest(request.sourceUrisAndPath, tempZipFile, ) { fileName, current, total ->
                    request.progressCallback(fileName, current, total)
                }

                // 下一步操作的源变成了这个临时的 zip 文件
                currentSourceUris = listOf(Uri.fromFile(tempZipFile))
                urisAreInTempZip = true
            }

            // 3. [条件性加密]：如果请求配置了加密
            if (request.encryptionAlgorithm == "AES" && request.password != null) {
                Log.d(tag, "Step 2: Encrypting data.")
                // 加密总是单文件操作，所以取第一个
                val sourceToEncryptUri = currentSourceUris.first()

                // 如果没有压缩，并且有多个源文件，这是不支持的组合
                if (!urisAreInTempZip && currentSourceUris.size > 1) {
                    throw IllegalStateException("Encryption without zip is only supported for a single source file.")
                }

                val tempEncryptedFile = File.createTempFile("backup_encrypted", ".dat", context.cacheDir)
                tempFilesToClean.add(tempEncryptedFile)

                encryptionService.encryptFile(sourceToEncryptUri, Uri.fromFile(tempEncryptedFile), request.password)

                // 下一步操作的源变成了这个临时的加密文件
                currentSourceUris = listOf(Uri.fromFile(tempEncryptedFile))
            }

            // 4. [最终步骤]：将最终产物（可能是原始文件、zip或加密文件）复制到目标位置
            Log.d(tag, "Step 3: Copying final artifact to destination.")
            val finalSourceUri = currentSourceUris.first()
            fileRepository.copyUriContent(finalSourceUri, request.destinationUri)

            Log.d(tag, "Backup successfully created at ${request.destinationUri}")
            Log.i(tag, "Manifest: $manifest")
            return true

        } catch (e: Exception) {
            Log.e(tag, "Backup failed", e)
            return false
        } finally {
            // 5. 清理所有临时文件
            tempFilesToClean.forEach { it.delete() }
            Log.d(tag, "Cleaned up temporary files.")
        }
    }

    fun restore(request: RestoreRequest): RestoreResult {
        val tempDecryptedFile = File.createTempFile("backup_decrypted", ".zip", context.cacheDir)

        try {
            var zipUriToUnpack: Uri

            // 1. [条件性解密]：如果提供了密码，则假定文件是加密的
            if (request.password != null) {
                Log.d(tag, "Restore Step 1: Decrypting file.")
                encryptionService.decryptFile(request.sourceBackupUri, Uri.fromFile(tempDecryptedFile), request.password)
                zipUriToUnpack = Uri.fromFile(tempDecryptedFile)
            } else {
                // 如果没有密码，直接处理源文件
                Log.d(tag, "Restore Step 1: Skipping decryption (no password provided).")
                zipUriToUnpack = request.sourceBackupUri
            }

            // 2. 从 zip 文件中读取元数据清单
            // 注意：这里需要一个能从 Uri 读取的 readManifestFromZip 版本
            Log.d(tag, "Restore Step 2: Reading manifest.")
            val manifest = archiveService.readManifestFromArchive(zipUriToUnpack)
                ?: return RestoreResult(isSuccess = false, errorMessage = "Manifest file not found or corrupted.")

            // 3. 解压文件并进行校验到目标 Uri
            Log.d(tag, "Restore Step 3: Unpacking and verifying files.")
            return archiveService.unpackAndVerifyFromUri(zipUriToUnpack, manifest, request.destinationDirectoryUri, request.progressCallback)

        } catch (e: Exception) {
            Log.e(tag, "Restore failed", e)
            return RestoreResult(isSuccess = false, errorMessage = e.message)
        } finally {
            tempDecryptedFile.delete()
        }
    }

    /**
     * 【推荐的新方法】递归地列出给定根目录下所有文件，并返回一个包含每个文件 Uri 及其相对路径的 Map。
     *
     * @param baseUri 要开始遍历的根目录（或单个文件）的 Uri。
     * @return 一个 Map，其中键是文件的 Uri，值是该文件相对于 baseUri 的路径字符串。
     * 例如：{ "content://.../IMG_001.jpg": "DCIM/Camera/IMG_001.jpg" }
     */
    fun listFilesWithRelativePaths(baseUri: Uri): Map<Uri, String> {
        return fileRepository.listFilesWithRelativePaths(baseUri)
    }

    fun listFilesWithRelativePaths(baseUri: Uri, regex: Regex): Map<Uri, String> {
        return fileRepository.listFilesWithRelativePaths(baseUri, regex)
    }

    fun getNewFileUriInDownloads(fileName: String): Uri? {
        return fileRepository.getNewFileUriInDownloads(fileName)
    }

    fun createSubDirectory(parentDirUri: Uri, subDirName: String): Uri? {
        return fileRepository.createSubDirectory(parentDirUri, subDirName)
    }
}