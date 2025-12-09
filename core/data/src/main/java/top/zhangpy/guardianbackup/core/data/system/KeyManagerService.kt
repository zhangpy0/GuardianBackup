package top.zhangpy.guardianbackup.core.data.system

import android.content.Context
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader
import java.security.SecureRandom

/**
 * 密钥管理服务：负责密钥文件的创建、读取和验证。
 *
 * 密钥文件格式 (.gkey):
 * - 第一行: 版本标识 "GKEY_V1"
 * - 第二行: 密钥内容（Base64编码的随机字节或用户自定义内容）
 * - 第三行: 备注信息（可选）
 */
class KeyManagerService(private val context: Context) {

    companion object {
        const val KEY_FILE_EXTENSION = ".gkey"
        const val KEY_FILE_VERSION = "GKEY_V1"
        const val DEFAULT_KEY_LENGTH = 32 // 256-bit key

        // 用于生成安全密钥的字符集
        private const val KEY_CHARSET =
                "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#\$%^&*"
    }

    private val fileRepository = FileRepository(context)

    /**
     * 生成一个安全的随机密钥字符串
     *
     * @param length 密钥长度，默认32个字符（256位）
     * @return 随机生成的密钥字符串
     */
    fun generateSecureKey(length: Int = DEFAULT_KEY_LENGTH): String {
        val secureRandom = SecureRandom()
        return (1..length)
                .map { KEY_CHARSET[secureRandom.nextInt(KEY_CHARSET.length)] }
                .joinToString("")
    }

    /**
     * 创建密钥文件并保存到指定 Uri
     *
     * @param outputUri 输出文件的 Uri（必须是可写的）
     * @param keyContent 密钥内容，如果为 null 则自动生成随机密钥
     * @param note 可选的备注信息
     * @return 写入的密钥内容，失败返回 null
     */
    fun createKeyFile(outputUri: Uri, keyContent: String? = null, note: String? = null): String? {
        return try {
            val key = keyContent ?: generateSecureKey()
            val fileContent = buildString {
                appendLine(KEY_FILE_VERSION)
                appendLine(key)
                if (!note.isNullOrBlank()) {
                    appendLine(note)
                }
            }

            fileRepository.getOutputStream(outputUri).use { outputStream ->
                outputStream.write(fileContent.toByteArray(Charsets.UTF_8))
            }
            key
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 验证密钥文件的格式是否正确
     *
     * @param keyUri 密钥文件的 Uri
     * @return 验证结果
     */
    fun validateKeyFile(keyUri: Uri): KeyValidationResult {
        return try {
            context.contentResolver.openInputStream(keyUri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    val lines = reader.readLines()

                    when {
                        lines.isEmpty() -> {
                            KeyValidationResult(false, "密钥文件为空")
                        }
                        lines.size == 1 -> {
                            // 兼容旧格式：只有一行密钥内容（无版本标识）
                            val key = lines[0].trim()
                            if (key.isEmpty()) {
                                KeyValidationResult(false, "密钥内容为空")
                            } else {
                                KeyValidationResult(true, "有效的密钥文件（旧格式）", isLegacyFormat = true)
                            }
                        }
                        lines[0].trim() == KEY_FILE_VERSION -> {
                            // 新格式
                            val key = lines.getOrNull(1)?.trim()
                            if (key.isNullOrEmpty()) {
                                KeyValidationResult(false, "密钥内容为空")
                            } else {
                                KeyValidationResult(true, "有效的密钥文件")
                            }
                        }
                        else -> {
                            // 兼容模式：第一行不是版本标识，当作旧格式处理
                            val key = lines[0].trim()
                            if (key.isEmpty()) {
                                KeyValidationResult(false, "密钥内容为空")
                            } else {
                                KeyValidationResult(true, "有效的密钥文件（旧格式）", isLegacyFormat = true)
                            }
                        }
                    }
                }
            }
                    ?: KeyValidationResult(false, "无法读取密钥文件")
        } catch (e: Exception) {
            KeyValidationResult(false, "读取密钥文件失败: ${e.message}")
        }
    }

    /**
     * 从密钥文件读取密钥内容
     *
     * @param keyUri 密钥文件的 Uri
     * @return 密钥内容的字符数组，失败返回 null
     */
    fun readKeyFromFile(keyUri: Uri): CharArray? {
        return try {
            context.contentResolver.openInputStream(keyUri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    val lines = reader.readLines()

                    when {
                        lines.isEmpty() -> null
                        lines.size == 1 -> {
                            // 旧格式：只有密钥内容
                            lines[0].trim().toCharArray()
                        }
                        lines[0].trim() == KEY_FILE_VERSION -> {
                            // 新格式：第二行是密钥
                            lines.getOrNull(1)?.trim()?.toCharArray()
                        }
                        else -> {
                            // 兼容旧格式
                            lines[0].trim().toCharArray()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

/** 密钥验证结果 */
data class KeyValidationResult(
        val isValid: Boolean,
        val message: String,
        val isLegacyFormat: Boolean = false
)
