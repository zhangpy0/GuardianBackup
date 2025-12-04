import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

object PermissionManager {

    private val requiredPermissions = arrayOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.WRITE_CONTACTS,
        Manifest.permission.READ_CALL_LOG
    )

    private var applicationContext: Application? = null
    private var permissionCallback: ((grantedPermissions: List<String>, deniedPermissions: List<String>) -> Unit)? = null
    private var requestMultiplePermissionsLauncher: ActivityResultLauncher<Array<String>>? = null

    /**
     * 初始化权限管理器
     * @param activity 需要请求权限的Activity
     * @param application 应用程序上下文
     */
    fun initialize(activity: AppCompatActivity, application: Application) {
        applicationContext = application

        requestMultiplePermissionsLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val grantedPermissions = mutableListOf<String>()
            val deniedPermissions = mutableListOf<String>()

            permissions.entries.forEach { entry ->
                if (entry.value) {
                    grantedPermissions.add(entry.key)
                    Log.d("Permission", "${entry.key} 已授权")
                } else {
                    deniedPermissions.add(entry.key)
                    Log.d("Permission", "${entry.key} 被拒绝")
                }
            }

            // 执行回调
            permissionCallback?.invoke(grantedPermissions, deniedPermissions)
        }
    }

    /**
     * 请求所有必需的权限
     * @param callback 权限请求结果回调
     */
    fun requestAllPermissions(callback: (grantedPermissions: List<String>, deniedPermissions: List<String>) -> Unit) {
        val context = applicationContext
            ?: throw IllegalStateException("PermissionManager未初始化，请先调用initialize()")

        permissionCallback = callback

        val permissionsToRequest = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isEmpty()) {
            // 所有权限都已授予
            Log.d("Permission", "所有权限都已授予")
            callback(requiredPermissions.toList(), emptyList())
        } else {
            // 请求未授予的权限
            requestMultiplePermissionsLauncher?.launch(permissionsToRequest.toTypedArray())
                ?: throw IllegalStateException("PermissionManager未初始化，请先调用initialize()")
        }
    }

    /**
     * 检查所有必需权限是否已授予
     * @return 是否所有权限都已授予
     */
    fun areAllPermissionsGranted(): Boolean {
        val context = applicationContext ?: return false
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 检查特定权限是否已授予
     * @param permission 要检查的权限
     * @return 权限是否已授予
     */
    fun isPermissionGranted(permission: String): Boolean {
        val context = applicationContext ?: return false
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 获取已授予的权限列表
     * @return 已授予的权限列表
     */
    fun getGrantedPermissions(): List<String> {
        val context = applicationContext ?: return emptyList()
        return requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 获取被拒绝的权限列表
     * @return 被拒绝的权限列表
     */
    fun getDeniedPermissions(): List<String> {
        val context = applicationContext ?: return emptyList()
        return requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 获取所有必需权限
     * @return 所有必需权限数组
     */
    fun getRequiredPermissions(): Array<String> {
        return requiredPermissions.copyOf()
    }
}