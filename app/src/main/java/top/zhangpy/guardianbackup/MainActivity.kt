package top.zhangpy.guardianbackup

import PermissionManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModelProvider
import top.zhangpy.guardianbackup.core.data.system.FileFilterService
import top.zhangpy.guardianbackup.core.data.system.FileRepository
import top.zhangpy.guardianbackup.core.data.system.FileSystemPickerUtils
import top.zhangpy.guardianbackup.core.data.system.FileSystemSource
import top.zhangpy.guardianbackup.core.data.system.KeyManagerService
import top.zhangpy.guardianbackup.core.domain.model.FileFilter
import top.zhangpy.guardianbackup.ui.FilterDialogFragment

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    private var selectedDirectoryUri: Uri? = null
    private var selectedBackupFileUri: Uri? = null
    private var selectedKeyFileUri: Uri? = null

    // New Feature: File Filter
    private var currentFileFilter: FileFilter = FileFilter.NO_FILTER
    private lateinit var fileFilterService: FileFilterService
    // New Feature: Key Manager
    private lateinit var keyManagerService: KeyManagerService

    // UI Components
    private lateinit var tvSelectedDirectory: TextView
    private lateinit var tvSelectedBackupFile: TextView
    private lateinit var tvSelectedKeyFile: TextView
    private lateinit var etCustomKey: EditText
    private lateinit var layoutCustomKey: LinearLayout
    private lateinit var rgKeyType: RadioGroup
    private lateinit var tvStatus: TextView
    private lateinit var tvProgress: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvFilterStatus: TextView // New UI

    private val directoryPickerLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                FileSystemPickerUtils.handleDirectoryPickerResult(
                        this,
                        result.resultCode,
                        result.data
                ) { uri ->
                    val file = DocumentFile.fromTreeUri(this, uri)
                    if (file == null || !file.canRead() || !file.canWrite()) {
                        Toast.makeText(
                                        this,
                                        "Cannot read/write selected directory.",
                                        Toast.LENGTH_LONG
                                )
                                .show()
                        return@handleDirectoryPickerResult
                    }
                    selectedDirectoryUri = uri
                    tvSelectedDirectory.text = uri.path
                }
            }

    private val backupFilePickerLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                FileSystemPickerUtils.handleFilePickerResult(
                        this,
                        result.resultCode,
                        result.data
                ) { uri ->
                    selectedBackupFileUri = uri
                    tvSelectedBackupFile.text = uri.path
                }
            }

    private val keyFilePickerLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                FileSystemPickerUtils.handleFilePickerResult(
                        this,
                        result.resultCode,
                        result.data
                ) { uri ->
                    // Validate key file
                    val validation = keyManagerService.validateKeyFile(uri)
                    if (validation.isValid) {
                        selectedKeyFileUri = uri
                        tvSelectedKeyFile.text = uri.path
                        if (validation.isLegacyFormat) {
                            Toast.makeText(
                                            this,
                                            "Legacy key file selected (valid).",
                                            Toast.LENGTH_SHORT
                                    )
                                    .show()
                        }
                    } else {
                        Toast.makeText(
                                        this,
                                        "Invalid key file: ${validation.message}",
                                        Toast.LENGTH_LONG
                                )
                                .show()
                        selectedKeyFileUri = null
                        tvSelectedKeyFile.text = "Invalid file selected"
                    }
                }
            }

    private val createKeyFileLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK && result.data != null) {
                    result.data?.data?.let { uri ->
                        val key = keyManagerService.createKeyFile(uri)
                        if (key != null) {
                            Toast.makeText(
                                            this,
                                            "Key file created successfully!",
                                            Toast.LENGTH_SHORT
                                    )
                                    .show()
                            selectedKeyFileUri = uri
                            tvSelectedKeyFile.text = uri.path
                            // Set text field as well for visual confirmation (optional)
                            etCustomKey.setText(
                                    key
                            ) // Not putting into etCustomKey to avoid confusion, file is enough
                        } else {
                            Toast.makeText(this, "Failed to write key file.", Toast.LENGTH_SHORT)
                                    .show()
                        }
                    }
                }
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.statusBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        window.decorView.fitsSystemWindows = true
        // 让系统装饰区不再被内容覆盖（内容不延伸到状态栏/导航栏）
        WindowCompat.setDecorFitsSystemWindows(window, true)

        // 如果之前通过 FLAG_FULLSCREEN 隐藏过系统栏，清除该标志并显示系统栏
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
        WindowInsetsControllerCompat(window, window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())

        // Initialize Permission Manager
        PermissionManager.initialize(this, application)
        PermissionManager.requestAllPermissions { _, _ -> }

        // Initialize ViewModel
        val factory = MainViewModelFactory(application)
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        // Initialize Services
        keyManagerService = KeyManagerService(this)
        fileFilterService = FileFilterService(FileRepository(this))

        // Bind UI
        bindViews()
        setupListeners()
        observeViewModel()
    }

    private fun bindViews() {
        tvSelectedDirectory = findViewById(R.id.tvSelectedDirectory)
        tvSelectedBackupFile = findViewById(R.id.tvSelectedBackupFile)
        tvSelectedKeyFile = findViewById(R.id.tvSelectedKeyFile)
        etCustomKey = findViewById(R.id.etCustomKey)
        layoutCustomKey = findViewById(R.id.layoutCustomKey)
        rgKeyType = findViewById(R.id.rgKeyType)
        tvStatus = findViewById(R.id.tvStatus)
        tvProgress = findViewById(R.id.tvProgress)
        progressBar = findViewById(R.id.progressBar)
        tvFilterStatus = findViewById(R.id.tvFilterStatus)
    }

    private fun setupListeners() {
        findViewById<Button>(R.id.btnSelectDirectory).setOnClickListener {
            val intent = FileSystemPickerUtils.createDirectoryPickerIntent()
            directoryPickerLauncher.launch(intent)
        }

        // Filter Button
        findViewById<Button>(R.id.btnFilter).setOnClickListener {
            val dialog =
                    FilterDialogFragment(currentFileFilter) { newFilter ->
                        currentFileFilter = newFilter
                        updateFilterStatus()
                    }
            dialog.show(supportFragmentManager, "FilterDialog")
        }

        findViewById<Button>(R.id.btnSelectBackupFile).setOnClickListener {
            val intent =
                    FileSystemPickerUtils.createFilePickerIntent(
                            arrayOf("*/*")
                    ) // Allow all types for backup file
            backupFilePickerLauncher.launch(intent)
        }

        findViewById<Button>(R.id.btnSelectKeyFile).setOnClickListener {
            val intent = FileSystemPickerUtils.createFilePickerIntent(arrayOf("*/*"))
            keyFilePickerLauncher.launch(intent)
        }

        findViewById<Button>(R.id.btnCreateKeyFile).setOnClickListener {
            val intent =
                    Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "text/plain" // Or application/octet-stream but text is safer for
                        // preview
                        putExtra(Intent.EXTRA_TITLE, "my_backup_key.gkey")
                    }
            createKeyFileLauncher.launch(intent)
        }

        findViewById<Button>(R.id.btnViewHistory).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        //        // status bar height adjustment
        //        val statusBarHeight =
        // WindowInsetsCompat.toWindowInsetsCompat(window.decorView.rootWindowInsets)
        //            .getInsets(WindowInsetsCompat.Type.statusBars()).top
        //        findViewById<Button>(R.id.btnViewHistory).setPadding(0, statusBarHeight / 2, 0, 0)

        rgKeyType.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbCustomKey) {
                layoutCustomKey.visibility = View.VISIBLE
            } else {
                layoutCustomKey.visibility = View.GONE
            }
        }

        findViewById<Button>(R.id.btnBackup).setOnClickListener { performBackup() }

        findViewById<Button>(R.id.btnRestore).setOnClickListener { performRestore() }
    }

    private fun updateFilterStatus() {
        if (currentFileFilter.hasAnyFilter()) {
            tvFilterStatus.text = "Filter Active: ${currentFileFilter.extensions.joinToString(",")}"
            tvFilterStatus.setTextColor(
                    resources.getColor(android.R.color.holo_blue_dark)
            ) // simple color
        } else {
            tvFilterStatus.text = "No filter applied - backup all files"
            tvFilterStatus.setTextColor(Color.parseColor("#666666"))
        }
    }

    private fun observeViewModel() {
        viewModel.statusMessage.observe(this) { msg ->
            tvStatus.text = msg
            if (msg.contains("Success") || msg.contains("Failed") || msg.contains("Error")) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }
        viewModel.isLoading.observe(this) { loading ->
            progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            findViewById<Button>(R.id.btnBackup).isEnabled = !loading
            findViewById<Button>(R.id.btnRestore).isEnabled = !loading
        }
        viewModel.progress.observe(this) { progress -> tvProgress.text = progress }
    }

    private fun getKeyConfig(): Pair<String?, Boolean> {
        return if (rgKeyType.checkedRadioButtonId == R.id.rbDefaultKey) {
            Pair("123", false)
        } else {
            if (selectedKeyFileUri != null) {
                Pair(selectedKeyFileUri.toString(), true)
            } else {
                val inputKey = etCustomKey.text.toString()
                if (inputKey.isNotEmpty()) {
                    Pair(inputKey, false)
                } else {
                    Pair(null, false)
                }
            }
        }
    }

    private fun performBackup() {
        if (selectedDirectoryUri == null) {
            Toast.makeText(this, "Please select a source directory", Toast.LENGTH_SHORT).show()
            return
        }

        val (key, isFileKey) = getKeyConfig()
        if (key == null) {
            Toast.makeText(this, "Please enter a key or select a key file", Toast.LENGTH_SHORT)
                    .show()
            return
        }

        // Prepare source URIs
        val fileSystemSource = FileSystemSource(this)
        val filesMap = fileSystemSource.listFilesWithRelativePaths(selectedDirectoryUri!!)
        if (filesMap.isEmpty()) {
            Toast.makeText(this, "Selected directory is empty", Toast.LENGTH_SHORT).show()
            return
        }

        // Apply Filter
        val filteredMap = fileFilterService.filterFiles(filesMap, currentFileFilter)
        if (filteredMap.isEmpty()) {
            Toast.makeText(this, "No files matched the filter criteria.", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(this, "Backing up ${filteredMap.size} files...", Toast.LENGTH_SHORT).show()

        // Prepare destination file (Public Downloads)
        // val desFile = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val dirName = DocumentFile.fromTreeUri(this, selectedDirectoryUri!!)?.name ?: "unknown"
        val timeStamp =
                java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                        .format(java.util.Date())
        val fileName = "${dirName}_backup_${timeStamp}.dat"
        // val destinationFile = File(desFile, fileName) // Old: Private app storage

        try {
            // New: Public Downloads storage
            val destinationUri = fileSystemSource.getNewFileUriInDownloads(fileName)

            if (destinationUri != null) {
                viewModel.backup(filteredMap, destinationUri, key, isFileKey)
            } else {
                Toast.makeText(
                                this,
                                "Failed to create destination file in Downloads",
                                Toast.LENGTH_SHORT
                        )
                        .show()
            }

            // Old Implementation
            /*
            if (destinationFile.createNewFile()) {
                val destinationUri = Uri.fromFile(destinationFile)
                viewModel.backup(filesMap, destinationUri, key, isFileKey)
            } else {
                Toast.makeText(this, "Failed to create destination file", Toast.LENGTH_SHORT).show()
            }
            */
        } catch (e: Exception) {
            Toast.makeText(this, "Error creating file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performRestore() {
        if (selectedBackupFileUri == null) {
            Toast.makeText(this, "Please select a backup file", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedDirectoryUri == null) {
            Toast.makeText(this, "Please select a destination directory", Toast.LENGTH_SHORT).show()
            return
        }

        val (key, isFileKey) = getKeyConfig()
        if (key == null) {
            Toast.makeText(this, "Please enter a key or select a key file", Toast.LENGTH_SHORT)
                    .show()
            return
        }

        viewModel.restore(selectedBackupFileUri!!, selectedDirectoryUri!!, key, isFileKey)
    }
}
