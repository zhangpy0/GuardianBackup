package top.zhangpy.guardianbackup

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import top.zhangpy.guardianbackup.core.data.repository.BackupRepositoryImpl
import top.zhangpy.guardianbackup.core.data.service.BackupServiceImpl
import top.zhangpy.guardianbackup.core.data.service.RestoreServiceImpl
import top.zhangpy.guardianbackup.core.domain.usecase.BackupUseCase
import top.zhangpy.guardianbackup.core.domain.usecase.GetBackupHistoryUseCase
import top.zhangpy.guardianbackup.core.domain.usecase.RestoreUseCase

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val backupUseCase: BackupUseCase
    private val restoreUseCase: RestoreUseCase
    private val getBackupHistoryUseCase: GetBackupHistoryUseCase

    init {
        // Manual Dependency Injection
        val context = application.applicationContext
        val backupRepository = BackupRepositoryImpl(context)
        val backupService = BackupServiceImpl(context)
        val restoreService = RestoreServiceImpl(context)

        backupUseCase = BackupUseCase(backupService, backupRepository)
        restoreUseCase = RestoreUseCase(restoreService)
        getBackupHistoryUseCase = GetBackupHistoryUseCase(backupRepository)
    }

    private val _statusMessage = MutableLiveData<String>()
    val statusMessage: LiveData<String> = _statusMessage

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    // Backup Progress
    private val _progress = MutableLiveData<String>()
    val progress: LiveData<String> = _progress

    // History
    val backupHistory = getBackupHistoryUseCase().asLiveData()

    fun backup(
            sourceUris: Map<Uri, String>,
            destinationUri: Uri,
            key: String?,
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
