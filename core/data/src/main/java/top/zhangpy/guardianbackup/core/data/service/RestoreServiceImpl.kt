package top.zhangpy.guardianbackup.core.data.service

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.zhangpy.guardianbackup.core.data.model.RestoreRequestBuilder
import top.zhangpy.guardianbackup.core.data.model.execute
import top.zhangpy.guardianbackup.core.data.system.KeyManagerService
import top.zhangpy.guardianbackup.core.domain.service.RestoreResultDomain
import top.zhangpy.guardianbackup.core.domain.service.RestoreService

class RestoreServiceImpl(private val context: Context) : RestoreService {

    private val keyManagerService = KeyManagerService(context)

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
                            keyManagerService.readKeyFromFile(Uri.parse(key))
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
}
