package top.zhangpy.guardianbackup.core.data.service

import android.content.Context
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.zhangpy.guardianbackup.core.data.model.RestoreRequestBuilder
import top.zhangpy.guardianbackup.core.data.model.execute
import top.zhangpy.guardianbackup.core.domain.service.RestoreResultDomain
import top.zhangpy.guardianbackup.core.domain.service.RestoreService

class RestoreServiceImpl(private val context: Context) : RestoreService {

    override suspend fun restore(
            sourceUri: Uri,
            destinationUri: Uri,
            key: String?,
            isFileKey: Boolean,
            onProgress: (String, Int, Int) -> Unit
    ): RestoreResultDomain {
        return withContext(Dispatchers.IO) {
            val passwordChars =
                    if (key != null) {
                        if (isFileKey) {
                            readKeyFile(Uri.parse(key))
                        } else {
                            key.toCharArray()
                        }
                    } else {
                        null
                    }

            val builder =
                    RestoreRequestBuilder(context)
                            .source(sourceUri)
                            .destination(destinationUri)
                            .onProgress(onProgress)

            if (passwordChars != null) {
                builder.withPassword(passwordChars)
            }

            try {
                val request = builder.build()
                val result = request.execute()
                RestoreResultDomain(
                        isSuccess = result.isSuccess,
                        restoredFilesCount = result.restoredFilesCount,
                        totalFilesCount = result.totalFilesCount,
                        corruptedFiles = result.corruptedFiles
                )
            } catch (e: Exception) {
                e.printStackTrace()
                RestoreResultDomain(false, 0, 0, emptyList())
            }
        }
    }

    private fun readKeyFile(uri: Uri): CharArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText().trim().toCharArray()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
