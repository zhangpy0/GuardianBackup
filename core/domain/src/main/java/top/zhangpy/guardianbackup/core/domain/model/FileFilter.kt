package top.zhangpy.guardianbackup.core.domain.model

/** 文件筛选条件数据模型 用于在备份过程中筛选需要备份的文件 */
data class FileFilter(
        /** 文件扩展名筛选（如 "jpg", "mp4", "pdf"） 空列表表示不筛选扩展名 */
        val extensions: List<String> = emptyList(),

        /** 文件名正则表达式匹配 null 表示不筛选文件名 */
        val namePattern: Regex? = null,

        /** 修改时间筛选 - 起始时间（毫秒时间戳） null 表示不限制起始时间 */
        val modifiedAfter: Long? = null,

        /** 修改时间筛选 - 结束时间（毫秒时间戳） null 表示不限制结束时间 */
        val modifiedBefore: Long? = null,

        /** 最小文件大小（字节） null 表示不限制最小大小 */
        val minSize: Long? = null,

        /** 最大文件大小（字节） null 表示不限制最大大小 */
        val maxSize: Long? = null,

        /** 路径包含模式列表 文件路径必须包含至少一个模式才会被包含 空列表表示不筛选路径 */
        val pathIncludes: List<String> = emptyList(),

        /** 路径排除模式列表 文件路径包含任一模式将被排除 空列表表示不排除任何路径 */
        val pathExcludes: List<String> = emptyList()
) {
    companion object {
        /** 预设筛选器：仅图片文件 */
        val IMAGES_ONLY =
                FileFilter(
                        extensions =
                                listOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif")
                )

        /** 预设筛选器：仅视频文件 */
        val VIDEOS_ONLY =
                FileFilter(
                        extensions = listOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp")
                )

        /** 预设筛选器：仅文档文件 */
        val DOCUMENTS_ONLY =
                FileFilter(
                        extensions =
                                listOf(
                                        "pdf",
                                        "doc",
                                        "docx",
                                        "xls",
                                        "xlsx",
                                        "ppt",
                                        "pptx",
                                        "txt",
                                        "md"
                                )
                )

        /** 预设筛选器：仅音频文件 */
        val AUDIO_ONLY =
                FileFilter(extensions = listOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "wma"))

        /** 预设筛选器：媒体文件（图片、视频、音频） */
        val MEDIA_ONLY =
                FileFilter(
                        extensions =
                                IMAGES_ONLY.extensions +
                                        VIDEOS_ONLY.extensions +
                                        AUDIO_ONLY.extensions
                )

        /** 无筛选：备份所有文件 */
        val NO_FILTER = FileFilter()

        /**
         * 检查文件大小是否在指定范围内
         * @param size 文件大小
         * @param minSize 最小大小限制
         * @param maxSize 最大大小限制
         * @return 是否在范围内
         */
        fun checkSizeRange(size: Long, minSize: Long?, maxSize: Long?): Boolean {
            if (minSize != null && size < minSize) return false
            if (maxSize != null && size > maxSize) return false
            return true
        }

        /**
         * 检查时间是否在指定范围内
         * @param time 时间戳
         * @param after 起始时间限制
         * @param before 结束时间限制
         * @return 是否在范围内
         */
        fun checkTimeRange(time: Long, after: Long?, before: Long?): Boolean {
            if (after != null && time < after) return false
            if (before != null && time > before) return false
            return true
        }
    }

    /** 检查是否有任何筛选条件被设置 */
    fun hasAnyFilter(): Boolean {
        return extensions.isNotEmpty() ||
                namePattern != null ||
                modifiedAfter != null ||
                modifiedBefore != null ||
                minSize != null ||
                maxSize != null ||
                pathIncludes.isNotEmpty() ||
                pathExcludes.isNotEmpty()
    }
}
