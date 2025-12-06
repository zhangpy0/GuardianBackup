package top.zhangpy.guardianbackup.core.data.service

import android.content.Context
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.zhangpy.guardianbackup.core.data.model.BackupRequestBuilder
import top.zhangpy.guardianbackup.core.data.model.execute
import top.zhangpy.guardianbackup.core.domain.service.BackupService

class BackupServiceImpl(private val context: Context) : BackupService {

    override suspend fun backup(
            sourceUris: Map<Uri, String>,
            destinationUri: Uri,
            key: String?,
            isFileKey: Boolean,
            onProgress: (String, Int, Int) -> Unit
    ): Boolean {
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

            // Using "AES" as default if key is present
            val algorithm = if (passwordChars != null) "AES" else null

            val builder =
                    BackupRequestBuilder(context)
                            .sourceUrisAndPath(sourceUris)
                            .destinationUri(destinationUri)
                            .zip(true)
                            .onProgress(onProgress)

            if (algorithm != null && passwordChars != null) {
                builder.encrypt(algorithm, passwordChars)
            }

            try {
                val request = builder.build()
                request.execute()
            } catch (e: Exception) {
                e.printStackTrace()
                false
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
