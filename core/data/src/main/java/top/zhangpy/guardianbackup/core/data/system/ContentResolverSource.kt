// in file: top/zhangpy/guardianbackup/core/data/system/ContentResolverSource.kt
package top.zhangpy.guardianbackup.core.data.system

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Log
import com.google.gson.Gson
import top.zhangpy.guardianbackup.core.data.model.CallLogEntry
import top.zhangpy.guardianbackup.core.data.model.Contact
import top.zhangpy.guardianbackup.core.data.model.SmsMessage

import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter

/**
 * 一个数据源类，负责通过 ContentResolver 从 Android 系统获取数据，
 * 并提供将其保存为不同格式（Model List, JSON, VCF）的方法。
 *
 * **注意**: 调用此类中的任何方法前，必须确保已获得相应的运行时权限:
 * - `android.permission.READ_CONTACTS`
 * - `android.permission.READ_SMS`
 * - `android.permission.READ_CALL_LOG`
 *
 * @param context 上下文对象，用于获取 ContentResolver。
 */
class ContentResolverSource(private val context: Context) {

    private val contentResolver: ContentResolver = context.contentResolver
    private val gson = Gson()
    private val tag = "ContentResolverSource"

    // =================================================================================
    // Contacts (联系人)
    // =================================================================================

    /**
     * 获取所有联系人，并以 List<Contact> 的形式返回。
     * @return 包含所有联系人信息的列表。如果查询失败或没有权限，则返回空列表。
     */
    @SuppressLint("Range")
    fun getContactsAsModels(): List<Contact> {
        val contacts = mutableListOf<Contact>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone._ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        try {
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    do {
                        contacts.add(
                            Contact(
                                id = cursor.getLong(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone._ID)),
                                name = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)),
                                phoneNumber = cursor.getString(
                                    cursor.getColumnIndex(
                                        ContactsContract.CommonDataKinds.Phone.NUMBER
                                    )
                                )
                            )
                        )
                    } while (cursor.moveToNext())
                }
            }
        } catch (e: SecurityException) {
            Log.e(tag, "Failed to get contacts. Permission READ_CONTACTS may be missing.", e)
            return emptyList()
        }
        Log.d(tag, "Fetched ${contacts.size} contacts.")
        return contacts
    }

    /**
     * 将所有联系人数据保存为 JSON 文件。
     * @param file 目标文件对象。
     * @return 如果保存成功返回 true，否则返回 false。
     */
    fun saveContactsAsJson(file: File): Boolean {
        return saveDataToJsonFile(getContactsAsModels(), file)
    }

    /**
     * 将所有联系人导出为一个 VCF (vCard) 文件。
     * 该文件可以包含多个联系人条目，并且可以被大多数通讯录应用导入。
     * @param file 目标 .vcf 文件对象。
     * @return 如果导出成功返回 true，否则返回 false。
     */
    fun saveContactsAsVcf(file: File): Boolean {
        try {
            val projection = arrayOf(ContactsContract.Contacts.LOOKUP_KEY)
            contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                FileOutputStream(file).use { fos ->
                    if (cursor.moveToFirst()) {
                        do {
                            val lookupKey = cursor.getString(0)
                            val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, lookupKey)
                            contentResolver.openInputStream(uri)?.use { vCardInputStream ->
                                // 将 vCard 数据流式写入文件
                                vCardInputStream.copyTo(fos)
                            }
                        } while (cursor.moveToNext())
                    }
                    Log.d(tag, "Successfully exported contacts to VCF file: ${file.absolutePath}")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error exporting contacts to VCF file.", e)
        }
        return false
    }


    // =================================================================================
    // SMS (短信)
    // =================================================================================

    /**
     * 获取所有短信，并以 List<SmsMessage> 的形式返回。
     * @return 包含所有短信信息的列表。
     */
    @SuppressLint("Range")
    fun getSmsAsModels(): List<SmsMessage> {
        val smsMessages = mutableListOf<SmsMessage>()
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )
        try {
            contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                null,
                null,
                Telephony.Sms.DATE + " DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    do {
                        val type = cursor.getInt(cursor.getColumnIndex(Telephony.Sms.TYPE))
                        smsMessages.add(
                            SmsMessage(
                                id = cursor.getLong(cursor.getColumnIndex(Telephony.Sms._ID)),
                                address = cursor.getString(cursor.getColumnIndex(Telephony.Sms.ADDRESS)),
                                body = cursor.getString(cursor.getColumnIndex(Telephony.Sms.BODY)),
                                date = cursor.getLong(cursor.getColumnIndex(Telephony.Sms.DATE)),
                                type = type,
                                typeName = if (type == Telephony.Sms.MESSAGE_TYPE_INBOX) "接收" else "发送"
                            )
                        )
                    } while (cursor.moveToNext())
                }
            }
        } catch (e: SecurityException) {
            Log.e(tag, "Failed to get SMS. Permission READ_SMS may be missing.", e)
            return emptyList()
        }
        Log.d(tag, "Fetched ${smsMessages.size} SMS messages.")
        return smsMessages
    }

    /**
     * 将所有短信数据保存为 JSON 文件。
     * @param file 目标文件对象。
     * @return 如果保存成功返回 true，否则返回 false。
     */
    fun saveSmsAsJson(file: File): Boolean {
        return saveDataToJsonFile(getSmsAsModels(), file)
    }

    // =================================================================================
    // Call Logs (通话记录)
    // =================================================================================

    /**
     * 获取所有通话记录，并以 List<CallLogEntry> 的形式返回。
     * @return 包含所有通话记录信息的列表。
     */
    @SuppressLint("Range")
    fun getCallLogsAsModels(): List<CallLogEntry> {
        val callLogs = mutableListOf<CallLogEntry>()
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.TYPE
        )
        try {
            contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                CallLog.Calls.DATE + " DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    do {
                        val type = cursor.getInt(cursor.getColumnIndex(CallLog.Calls.TYPE))
                        callLogs.add(
                            CallLogEntry(
                                id = cursor.getLong(cursor.getColumnIndex(CallLog.Calls._ID)),
                                number = cursor.getString(cursor.getColumnIndex(CallLog.Calls.NUMBER)),
                                date = cursor.getLong(cursor.getColumnIndex(CallLog.Calls.DATE)),
                                duration = cursor.getLong(cursor.getColumnIndex(CallLog.Calls.DURATION)),
                                type = type,
                                typeName = when (type) {
                                    CallLog.Calls.INCOMING_TYPE -> "来电"
                                    CallLog.Calls.OUTGOING_TYPE -> "去电"
                                    CallLog.Calls.MISSED_TYPE -> "未接"
                                    CallLog.Calls.VOICEMAIL_TYPE -> "语音信箱"
                                    CallLog.Calls.REJECTED_TYPE -> "已拒接"
                                    CallLog.Calls.BLOCKED_TYPE -> "已阻止"
                                    else -> "未知"
                                }
                            )
                        )
                    } while (cursor.moveToNext())
                }
            }
        } catch (e: SecurityException) {
            Log.e(tag, "Failed to get call logs. Permission READ_CALL_LOG may be missing.", e)
            return emptyList()
        }
        Log.d(tag, "Fetched ${callLogs.size} call logs.")
        return callLogs
    }

    /**
     * 将所有通话记录数据保存为 JSON 文件。
     * @param file 目标文件对象。
     * @return 如果保存成功返回 true，否则返回 false。
     */
    fun saveCallLogsAsJson(file: File): Boolean {
        return saveDataToJsonFile(getCallLogsAsModels(), file)
    }


    // =================================================================================
    // Private Helper (私有辅助方法)
    // =================================================================================

    /**
     * 将任意数据列表转换为 JSON 字符串并写入文件。
     */
    private fun <T> saveDataToJsonFile(data: List<T>, file: File): Boolean {
        if (data.isEmpty()) {
            Log.w(tag, "Data is empty for ${file.name}, skipping file write.")
            // 可以根据业务逻辑决定空数据是否算成功，这里返回 true 表示“没有数据需要写入”这个操作是成功的。
            return true
        }
        return try {
            val jsonString = gson.toJson(data)
            FileWriter(file).use { writer ->
                writer.write(jsonString)
            }
            Log.d(tag, "Successfully saved data to ${file.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(tag, "Error saving data to file ${file.name}", e)
            false
        }
    }
}