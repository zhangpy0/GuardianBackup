package top.zhangpy.guardianbackup

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import top.zhangpy.guardianbackup.core.data.system.ContentResolverSource
import top.zhangpy.guardianbackup.core.data.system.DirectoryPickerUtils
import top.zhangpy.guardianbackup.core.data.system.FileSystemSource
import java.io.File
import kotlin.contracts.contract

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
    private var selectedDirectoryUri: Uri? = null
    lateinit var fileSystemSource: FileSystemSource

    // Activity 仍然需要持有 ActivityResultLauncher
    private val directoryPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // 将结果交给工具类处理
        DirectoryPickerUtils.handleDirectoryPickerResult(this, result.resultCode, result.data) { uri ->
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
//            val backupRequest = fileSystemSource.createBackupRequest()
//            fileSystemSource.backup()
            // 2. 将 URI 保存起来供下次使用
            DirectoryPickerUtils.saveDirectoryUri(this, uri)

            Toast.makeText(this, "已选择目录", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mainView = layoutInflater.inflate(R.layout.activity_main, null)
        setContentView(mainView)
        val btnSelectDirectory: Button = findViewById(R.id.btnSelectDirectory)
        val btnListFiles: Button = findViewById(R.id.btnListFiles)
        tvSelectedDirectory = findViewById(R.id.tvSelectedDirectory)
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
        btnSelectDirectory.setOnClickListener {
            // 从工具类获取 Intent 并启动
            val intent = DirectoryPickerUtils.createDirectoryPickerIntent()
            directoryPickerLauncher.launch(intent)
        }

        btnListFiles.setOnClickListener {
            if (selectedDirectoryUri != null) {
                // 使用工具类列出文件
                val files = DirectoryPickerUtils.listFilesInDirectory(this, selectedDirectoryUri!!)

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