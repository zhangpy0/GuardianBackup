package top.zhangpy.guardianbackup

import PermissionManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.zhangpy.guardianbackup.core.data.model.BackupRequestBuilder
import top.zhangpy.guardianbackup.core.data.model.RestoreRequestBuilder
import top.zhangpy.guardianbackup.core.data.model.execute
import top.zhangpy.guardianbackup.core.data.system.ContentResolverSource
import top.zhangpy.guardianbackup.core.data.system.FileSystemPickerUtils
import top.zhangpy.guardianbackup.core.data.system.FileSystemSource
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {

//    private val requiredPermissions = arrayOf(
//        Manifest.permission.READ_SMS,
//        Manifest.permission.READ_CONTACTS,
//        Manifest.permission.WRITE_CONTACTS,
//        Manifest.permission.READ_CALL_LOG
//    )
//
//    private fun requestAllPermissionsAtOnce() {
//        val permissionsToRequest = requiredPermissions.filter { permission ->
//            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
//        }
//
//        if (permissionsToRequest.isEmpty()) {
//            // 所有权限都已授予
//            Log.d("Permission", "所有权限都已授予")
//            backupAll()
//        } else {
//            // 请求未授予的权限
//            requestMultiplePermissionsLauncher.launch(permissionsToRequest.toTypedArray())
//        }
//    }
//    private val requestMultiplePermissionsLauncher = registerForActivityResult(
//        ActivityResultContracts.RequestMultiplePermissions()
//    ) { permissions ->
//        val grantedPermissions = mutableListOf<String>()
//        val deniedPermissions = mutableListOf<String>()
//
//        permissions.entries.forEach { entry ->
//            if (entry.value) {
//                grantedPermissions.add(entry.key)
//                Log.d("Permission", "${entry.key} 已授权")
//            } else {
//                deniedPermissions.add(entry.key)
//                Log.d("Permission", "${entry.key} 被拒绝")
//            }
//
//        }
//        backupAll()
//    }
    private lateinit var tvSelectedDirectory: TextView

    private lateinit var tvSelectedFile: TextView
    private var selectedDirectoryUri: Uri? = null
    private var selectedFileUri: Uri? = null
    lateinit var fileSystemSource: FileSystemSource

    // 加密测试
    private val directoryPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // 将结果交给工具类处理
        FileSystemPickerUtils.handleDirectoryPickerResult(this, result.resultCode, result.data) { uri ->
            // 这是选择成功后的回调
            Log.i("MainActivity", "目录选择成功: $uri")
            Log.i("MainActivity", "目录路径: ${uri.path}")
            Log.i("MainActivity", "URI Scheme: ${uri.scheme}")
            val file = DocumentFile.fromTreeUri(this, uri)
            if (file == null || !file.canRead() || !file.canWrite()) {
                Toast.makeText(this, "无法读取或写入所选目录，请选择其他目录", Toast.LENGTH_LONG).show()
                return@handleDirectoryPickerResult
            }
            selectedDirectoryUri = uri
            tvSelectedDirectory.text = "已选目录:\n${uri.path}"

        }
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        FileSystemPickerUtils.handleFilePickerResult(this, result.resultCode, result.data) { uri ->
            // 这是选择成功后的回调
            Log.i("MainActivity", "文件选择成功: $uri")
            Log.i("MainActivity", "文件路径: ${uri.path}")
            Log.i("MainActivity", "URI Scheme: ${uri.scheme}")
            val file = DocumentFile.fromSingleUri(this, uri)
            if (file == null || !file.canRead()) {
                Toast.makeText(this, "无法读取所选文件，请选择其他文件", Toast.LENGTH_LONG).show()
                return@handleFilePickerResult
            }
            // todo 恢复
            selectedFileUri = uri
            tvSelectedFile.text = "已选文件:\n${uri.path}"
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mainView = layoutInflater.inflate(R.layout.activity_main, null)
        setContentView(mainView)
        val btnSelectDirectory: Button = findViewById(R.id.btnSelectDirectory)
        val btnSelectFile : Button = findViewById(R.id.btnSelectFile)
        val btnBackup: Button = findViewById(R.id.btnBackup)
        val btnRestore: Button = findViewById(R.id.btnRestore)
        val btnListFiles: Button = findViewById(R.id.btnListFiles)
        tvSelectedDirectory = findViewById(R.id.tvSelectedDirectory)
        tvSelectedFile = findViewById(R.id.tvSelectedFile)
        val externalCacheDir = externalCacheDir
        val contentResolverSource = ContentResolverSource(this)
        fileSystemSource = FileSystemSource(this)

        if (externalCacheDir == null) {
            Log.e("test", "外部缓存目录不可用")
        }
        val jsonF = File(externalCacheDir, "test.json")
        val vcfF = File(externalCacheDir,"test.vcf")
        //tv.text = stringFromJNI()
//        requestAllPermissionsAtOnce()
        // 初始化权限管理器，传入application context
        PermissionManager.initialize(this, application)

        // 请求权限
        requestPermissions()
//        contentResolverSource.saveContactsAsJson(jsonF)
//        contentResolverSource.saveContactsAsVcf(vcfF)

        // 加密测试
        btnSelectDirectory.setOnClickListener {
            // 从工具类获取 Intent 并启动
            val intent = FileSystemPickerUtils.createDirectoryPickerIntent()
            directoryPickerLauncher.launch(intent)
        }

        // 解密测试
        btnSelectFile.setOnClickListener {
            // 从工具类获取 Intent 并启动
            val intent = FileSystemPickerUtils.createFilePickerIntent(arrayOf("application/octet-stream"))
            filePickerLauncher.launch(intent)
        }

        btnBackup.setOnClickListener {
            var uri = selectedDirectoryUri
            if (uri == null) {
                Toast.makeText(this, "请先选择一个目录", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val file = DocumentFile.fromTreeUri(this, uri)
            if (file == null || !file.canRead() || !file.canWrite()) {
                Toast.makeText(this, "无法读取或写入所选目录，请选择其他目录", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val filesMap = fileSystemSource.listFilesWithRelativePaths(uri)
            val desFile = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)

            val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
            val fileName = "${file.name}_backup_${timeStamp}.dat"

            // 直接使用 java.io.File 来创建文件对象
            // desFile 是 getExternalFilesDir 返回的父目录 File 对象
            val destinationFile = File(desFile, fileName)

            try {
                // createNewFile() 会真正在文件系统上创建这个文件
                // 如果文件已存在，它会返回 false
                if (destinationFile.createNewFile()) {
                    // 文件创建成功，将 File 对象转换为 Uri
                    // 注意：对于较新的 Android 版本，你可能需要通过 FileProvider 来获取 content:// Uri
                    // 但对于传递给应用内部逻辑，File Uri 通常也可用。为了更好的兼容性，推荐 FileProvider。
                    // 这里为了简单，我们先用 Uri.fromFile
                    uri = Uri.fromFile(destinationFile)
                    Log.i("MainActivity", "备份文件已成功创建: $uri")
                } else {
                    Toast.makeText(this, "创建失败，文件可能已存在", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
            } catch (e: IOException) {
                // 捕获可能发生的 I/O 异常
                Log.e("MainActivity", "创建备份文件时发生 IO 异常", e)
                Toast.makeText(this, "创建备份文件失败: ${e.message}", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            Log.i("MainActivity", "备份文件将保存到: $uri")

            val backupRequestBuilder : BackupRequestBuilder = BackupRequestBuilder(this)
                .sourceUrisAndPath(filesMap)
                .destinationUri(uri)
                .zip(true)
                .encrypt("AES","123".toCharArray())
                .onProgress { fileName, current, total ->
                    Log.d("MainActivity","正在处理文件 $fileName 进度: $current/$total")
                }
            val backupRequest = backupRequestBuilder.build()
            lifecycleScope.launch {
                val success = withContext(Dispatchers.IO) {
                    backupRequest.execute()
                }
                if (success) {
                    Log.i("MainActivity", "备份完成")
                } else {
                    Log.e("MainActivity", "备份失败")
                }
            }
        }

        btnRestore.setOnClickListener {
            if (selectedFileUri == null) {
                Toast.makeText(this, "请先选择一个备份文件", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val file = DocumentFile.fromSingleUri(this, selectedFileUri!!)
            if (file == null || !file.canRead()) {
                Toast.makeText(this, "无法读取所选文件，请选择其他文件", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

//            var desDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
//            desDir = File(desDir, "restore")
//            if (!desDir.exists()) {
//                val created = desDir.mkdirs()
//                if (!created) {
//                    Toast.makeText(this, "无法创建恢复目录，请检查存储权限", Toast.LENGTH_LONG).show()
//                    return@setOnClickListener
//                }
//            }
//            desDir = File(desDir, "restore_${file.name}")
//            if (!desDir.exists()) {
//                val created = desDir.mkdirs()
//                if (!created) {
//                    Toast.makeText(this, "无法创建恢复目录，请检查存储权限", Toast.LENGTH_LONG).show()
//                    return@setOnClickListener
//                }
//            }
//
//            val desUrl = Uri.fromFile(desDir)

            if (selectedDirectoryUri == null) {
                Toast.makeText(this, "请先选择一个目录作为恢复目标", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val subDirName = "restore_" + file.name?.removeSuffix(".dat")
            var desUrl = selectedDirectoryUri
//            desUrl = fileSystemSource.createSubDirectory(desUrl!!, subDirName)
            if (desUrl == null) {
                Toast.makeText(this, "无法创建恢复目录，请检查存储权限", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            Log.i("MainActivity", "恢复目标目录: $desUrl")
            val restoreRequestBuilder = RestoreRequestBuilder(this)
                .source(selectedFileUri!!)
                .destination(desUrl)
                .onProgress {
                            fileName, current, total ->
                    Log.d("MainActivity","正在处理文件 $fileName 进度: $current/$total")
                }
                .withPassword("123".toCharArray())
                .build()

            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    val restoreRes = restoreRequestBuilder.execute()
                    return@withContext restoreRes
                }
                if (result.isSuccess) {
                    Log.i("MainActivity", "恢复完成，已恢复 ${result.totalFilesCount} 个文件")
                    if (result.corruptedFiles.isNotEmpty()) {
                        Log.w("MainActivity", "以下文件恢复时校验失败: ${result.corruptedFiles}")
                    }
                } else {
                    Log.e("MainActivity", "恢复失败")
                }
            }
        }

        btnListFiles.setOnClickListener {
            if (selectedDirectoryUri != null) {
                // 使用工具类列出文件
                val files = FileSystemPickerUtils.listFilesInDirectory(this, selectedDirectoryUri!!)

                if (files.isEmpty()) {
                    Toast.makeText(this, "目录为空或无法读取", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val fileNames = files.joinToString(separator = "\n") { file ->
                    val type = if (file.isDirectory) "目录" else "文件"
                    "$type: ${file.name}"
                }
                Log.d("MainActivity", "目录内容:\n$fileNames")
                Toast.makeText(this, "文件列表已打印到 Logcat", Toast.LENGTH_LONG).show()

            } else {
                Toast.makeText(this, "请先选择一个目录", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestPermissions() {
        // 不再需要传入context参数
        PermissionManager.requestAllPermissions { grantedPermissions, deniedPermissions ->
            if (deniedPermissions.isEmpty()) {
                // 所有权限都已授予，执行备份操作
//                backupAll()
            } else {
                // 处理被拒绝的权限
                Log.d("Permission", "被拒绝的权限: $deniedPermissions")
            }
        }
    }

    private fun backupAll() {
        val contentResolverSource = ContentResolverSource(this)
        Log.d("test",contentResolverSource.getSmsAsModels().toString())
        Log.d("test",contentResolverSource.getContactsAsModels().toString())
        Log.d("test",contentResolverSource.getCallLogsAsModels().toString())
    }


    /**
     * A native method that is implemented by the 'guardianbackup' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String

    companion object {
        // Used to load the 'guardianbackup' library on application startup.
        init {
//            System.loadLibrary("guardianbackup")
        }
    }
}