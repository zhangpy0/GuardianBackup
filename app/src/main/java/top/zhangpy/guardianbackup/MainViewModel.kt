package top.zhangpy.guardianbackup

import android.app.Application
import android.net.Uri
            isFileKey: Boolean
    ) {
        _isLoading.value = true
        _statusMessage.value = "Starting Backup..."
        viewModelScope.launch {
            try {
                val success =
                        backupUseCase(sourceUris, destinationUri, key, isFileKey) {
                                fileName,
                                current,
                                total ->
                            _progress.postValue("Backing up: $fileName ($current/$total)")
                        }
                if (success) {
                    _statusMessage.value = "Backup Completed Successfully"
                } else {
                    _statusMessage.value = "Backup Failed"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
                e.printStackTrace()
            } finally {
                _isLoading.value = false
                _progress.value = ""
            }
        }
    }

    fun restore(sourceUri: Uri, destinationUri: Uri, key: String?, isFileKey: Boolean) {
        _isLoading.value = true
        _statusMessage.value = "Starting Restore..."
        viewModelScope.launch {
            try {
                val result =
                        restoreUseCase(sourceUri, destinationUri, key, isFileKey) {
                                fileName,
                                current,
                                total ->
                            _progress.postValue("Restoring: $fileName ($current/$total)")
                        }

                if (result.isSuccess) {
                    val msg = "Restore Completed. ${result.restoredFilesCount} files restored."
                    if (result.corruptedFiles.isNotEmpty()) {
                        _statusMessage.value =
                                "$msg Warning: ${result.corruptedFiles.size} corrupted files."
                    } else {
                        _statusMessage.value = msg
                    }
                } else {
                    _statusMessage.value = "Restore Failed"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
                e.printStackTrace()
            } finally {
                _isLoading.value = false
                _progress.value = ""
            }
        }
    }
}

class MainViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return MainViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
