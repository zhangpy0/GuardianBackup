package top.zhangpy.guardianbackup

import android.Manifest
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import top.zhangpy.guardianbackup.core.data.system.ContentResolverSource
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
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mainView = layoutInflater.inflate(R.layout.activity_main, null)
        setContentView(mainView)
        val tv: TextView = findViewById(R.id.sample_text)
        //tv.text = stringFromJNI()
//        requestAllPermissionsAtOnce()
        // 初始化权限管理器，传入application context
        PermissionManager.initialize(this, application)

        // 请求权限
        requestPermissions()
    }

    private fun requestPermissions() {
        // 不再需要传入context参数
        PermissionManager.requestAllPermissions { grantedPermissions, deniedPermissions ->
            if (deniedPermissions.isEmpty()) {
                // 所有权限都已授予，执行备份操作
                backupAll()
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