package top.zhangpy.guardianbackup.core.data.system

import android.net.Uri
import top.zhangpy.guardianbackup.core.domain.model.FileFilter

/** 文件筛选服务：根据筛选条件过滤文件列表 */
class FileFilterService(private val fileRepository: FileRepository) {

    /**
     * 根据筛选条件过滤文件列表
     *
     * @param files 原始文件映射 (Uri -> 相对路径)
     * @param filter 筛选条件
     * @return 筛选后的文件映射
     */
    fun filterFiles(files: Map<Uri, String>, filter: FileFilter): Map<Uri, String> {
        // 如果没有设置任何筛选条件，直接返回原始列表
        if (!filter.hasAnyFilter()) {
            return files
        }

        return files.filter { (uri, relativePath) -> matchesFilter(uri, relativePath, filter) }
    }

    /**
     * 检查单个文件是否匹配筛选条件
     *
     * @param uri 文件 Uri
     * @param relativePath 文件相对路径
     * @param filter 筛选条件
     * @return 是否匹配
     */
    fun matchesFilter(uri: Uri, relativePath: String, filter: FileFilter): Boolean {
        val fileName = relativePath.substringAfterLast('/')

        // 1. 扩展名筛选
        if (filter.extensions.isNotEmpty()) {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            if (ext !in filter.extensions.map { it.lowercase() }) {
                return false
            }
        }

        // 2. 文件名正则匹配
        if (filter.namePattern != null) {
            if (!filter.namePattern!!.matches(fileName)) {
                return false
            }
        }

        // 3. 路径包含筛选
        if (filter.pathIncludes.isNotEmpty()) {
            if (!filter.pathIncludes.any { pattern ->
                        relativePath.contains(pattern, ignoreCase = true)
                    }
            ) {
                return false
            }
        }

        // 4. 路径排除筛选
        if (filter.pathExcludes.isNotEmpty()) {
            if (filter.pathExcludes.any { pattern ->
                        relativePath.contains(pattern, ignoreCase = true)
                    }
            ) {
                return false
            }
        }

        // 5. 如果需要检查时间和大小，获取文件元数据
        if (filter.modifiedAfter != null ||
                        filter.modifiedBefore != null ||
                        filter.minSize != null ||
                        filter.maxSize != null
        ) {

            try {
                val (_, size, lastModified) = fileRepository.getMetadataFromUri(uri)

                // 时间范围检查
                if (!FileFilter.checkTimeRange(
                                lastModified,
                                filter.modifiedAfter,
                                filter.modifiedBefore
                        )
                ) {
                    return false
                }

                // 大小范围检查
                if (!FileFilter.checkSizeRange(size, filter.minSize, filter.maxSize)) {
                    return false
                }
            } catch (e: Exception) {
                // 无法获取元数据时，默认包含该文件
                e.printStackTrace()
            }
        }

        return true
    }
}
