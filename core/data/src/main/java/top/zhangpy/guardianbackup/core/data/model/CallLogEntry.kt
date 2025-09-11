package top.zhangpy.guardianbackup.core.data.model

data class CallLogEntry(
    val id: Long,
    val number: String,  // 号码
    val date: Long,      // 日期时间戳
    val duration: Long,  // 通话时长（秒）
    val type: Int,        // 1: 来电, 2: 去电, 3: 未接
    val typeName: String // "来电", "去电" 或 "未接"
)