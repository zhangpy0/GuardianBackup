package top.zhangpy.guardianbackup.core.data.system

import android.R.attr.tag
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import top.zhangpy.guardianbackup.core.data.model.BackupManifest
import top.zhangpy.guardianbackup.core.data.model.FileMetadata
import top.zhangpy.guardianbackup.core.data.model.RestoreResult
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 服务层：负责文件的压缩归档与解压验证。
 */
class ArchiveService(
    private val context: Context, // 依然需要 Context 来创建临时文件和 DocumentFile
    private val fileRepository: FileRepository,
    private val integrityService: IntegrityService
) {


    private val tag : String = "ArchiveService"
    companion object {
        private const val MANIFEST_FILENAME = "manifest.json"
        private const val BUFFER_SIZE = 8192
        private const val SALT_SIZE = 16
        private const val IV_SIZE = 12 // AES/GCM standard IV size is 12 bytes
        private const val PBE_ITERATION_COUNT = 65536
        private const val AES_KEY_SIZE = 256
        private const val GCM_tag_LENGTH = 128
    }
    private val gson = Gson()

    fun createArchiveWithManifest(
        filesMap: Map<Uri, String>,
        zipFile: File,
        progressCallback: (String, Int, Int) -> Unit
    ) : BackupManifest {
        val metadataList = mutableListOf<FileMetadata>()
        val filesToProcess = filesMap.entries.toList() // 转换为 List 以便使用索引

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            filesToProcess.forEachIndexed { index, (uri, pathInArchive) ->
                val (name, size, lastModified) = fileRepository.getMetadataFromUri(uri)
                progressCallback(name, index + 1, filesToProcess.size)

                // 直接使用 Map 中提供的相对路径
                val checksum = integrityService.calculateSHA256(uri)
                metadataList.add(
                    FileMetadata(
                        originalPath = uri.toString(),
                        pathInArchive = pathInArchive, // 直接使用，无需再计算
                        size = size,
                        lastModified = lastModified,
                        sha256Checksum = checksum
                    )
                )

                val entry = ZipEntry(pathInArchive)
                zos.putNextEntry(entry)
                fileRepository.getInputStream(uri).use { fis ->
                    fis.copyTo(zos, BUFFER_SIZE)
                }
                zos.closeEntry()
            }

            // 添加 manifest 文件
            val manifest = BackupManifest(zipFile.name.removeSuffix(".zip"),"1.0", System.currentTimeMillis(), metadataList)
            zos.putNextEntry(ZipEntry(MANIFEST_FILENAME))
            zos.write(gson.toJson(manifest).toByteArray())
            zos.closeEntry()
            return manifest
        }
    }

    fun readManifestFromArchive(zipUri: Uri): BackupManifest? {
        fileRepository.getInputStream(zipUri).use { fis ->
            ZipInputStream(fis).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name == MANIFEST_FILENAME) {
                        return gson.fromJson(InputStreamReader(zis), BackupManifest::class.java)
                    }
                    entry = zis.nextEntry
                }
            }
        }
        return null
    }

    fun unpackAndVerifyFromUri(
        zipUri: Uri,
        manifest: BackupManifest,
        destDirUri: Uri,
        progressCallback: (String, Int, Int) -> Unit
    ): RestoreResult {
        // 1. 准备目标目录
        val destDirDocFile = DocumentFile.fromTreeUri(context, destDirUri)
            ?: return RestoreResult(isSuccess = false, errorMessage = "Invalid destination directory URI.")
        if (!destDirDocFile.canWrite()) {
            return RestoreResult(isSuccess = false, errorMessage = "No write permission for destination directory.")
        }

        // 2. 初始化变量
        val checksumMap = manifest.files.associateBy { it.pathInArchive }
        val corruptedFiles = mutableListOf<String>()
        var restoredCount = 0
        val totalFiles = manifest.files.size

        // 3. 从 Uri 打开 Zip 输入流
        fileRepository.getInputStream(zipUri).use { fis ->

            ZipInputStream(fis).use { zis ->
                var entry = zis.nextEntry
                var currentIndex = 0
                while (entry != null) {
                    val entryName = entry.name // 这是相对路径，如 "DCIM/Camera/IMG.jpg"

                    // 跳过清单文件和目录条目
                    if (entryName == MANIFEST_FILENAME || entry.isDirectory) {
                        entry = zis.nextEntry
                        continue
                    }

                    progressCallback(entryName, ++currentIndex, totalFiles)

                    val metadata = checksumMap[entryName]
                    if (metadata == null) {
                        Log.w(tag, "File '$entryName' found in zip but not in manifest. Skipping.")
                        entry = zis.nextEntry
                        continue
                    }

                    // 4. 解压到临时文件以供校验
                    val tempOutputFile = File.createTempFile("restore_item", ".tmp", context.cacheDir)
                    try {
                        FileOutputStream(tempOutputFile).use { fos -> zis.copyTo(fos) }

                        // 5. 校验文件完整性
                        val calculatedChecksum = integrityService.calculateSHA256(Uri.fromFile(tempOutputFile))
                        if (calculatedChecksum == metadata.sha256Checksum) {

                            // 6. 校验成功，查找或创建目标目录
                            val targetParentDir = fileRepository.findOrCreateDirectoryPath(destDirDocFile, manifest.dirName+"/"+entryName)
                            if (targetParentDir == null) {
                                Log.e(tag, "Could not find or create parent directory for '$entryName'")
                                corruptedFiles.add(entryName)
                            } else {
                                val fileName = File(entryName).name
                                // 如果文件已存在则覆盖 (DocumentFile 的 createFile 会处理)
                                val targetFile = targetParentDir.findFile(fileName) ?: targetParentDir.createFile("*/*", fileName)

                                if (targetFile == null) {
                                    Log.e(tag, "Failed to create file in destination: '$entryName'")
                                    corruptedFiles.add(entryName)
                                } else {
                                    // 7. 写入最终文件
                                    fileRepository.getOutputStream(targetFile.uri, "w").use { outStream ->
                                        FileInputStream(tempOutputFile).use { inStream ->
                                            inStream.copyTo(outStream)
                                        }
                                    }
                                    restoredCount++
                                }
                            }
                        } else {
                            Log.e(tag, "Checksum mismatch for '$entryName'. Expected: ${metadata.sha256Checksum}, Got: $calculatedChecksum")
                            corruptedFiles.add(entryName)
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "Error processing entry '$entryName'", e)
                        corruptedFiles.add(entryName)
                    } finally {
                        tempOutputFile.delete() // 确保临时文件被删除
                    }

                    entry = zis.nextEntry
                }
            }
        }

        // 8. 返回最终结果
        return if (corruptedFiles.isEmpty() && restoredCount >= totalFiles) {
            RestoreResult(isSuccess = true, restoredFilesCount = restoredCount, totalFilesCount = totalFiles)
        } else {
            RestoreResult(
                isSuccess = false,
                restoredFilesCount = restoredCount,
                totalFilesCount = totalFiles,
                errorMessage = "Some files failed verification or could not be written.",
                corruptedFiles = corruptedFiles.distinct() // 去重
            )
        }
    }
}