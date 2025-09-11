package top.zhangpy.guardianbackup.core.data.model

data class SmsMessage(
    val id: Long,
    val address: String, // 发送/接收方号码
    val body: String,    // 短信内容
    val date: Long,      // 日期时间戳
    val type: Int,        // 1: 接收, 2: 发送
    val typeName: String // "接收" 或 "发送"
)
