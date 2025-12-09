package top.zhangpy.guardianbackup.ui.dialog

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.gson.Gson
import top.zhangpy.guardianbackup.core.domain.model.BackupManifest
import top.zhangpy.guardianbackup.core.domain.model.BackupRecord

class BackupDetailsDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_MANIFEST_JSON = "manifest_json"

        fun newInstance(record: BackupRecord): BackupDetailsDialogFragment {
            val fragment = BackupDetailsDialogFragment()
            val args = Bundle()
            args.putString(ARG_MANIFEST_JSON, record.manifestJson)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val manifestJson = arguments?.getString(ARG_MANIFEST_JSON)

        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Backup Details")

        if (manifestJson.isNullOrEmpty()) {
            builder.setMessage("No detailed information available for this backup.")
        } else {
            try {
                val manifest = Gson().fromJson(manifestJson, BackupManifest::class.java)
                val details = buildString {
                    val displayName =
                            if (manifest.sourcePath.isNotEmpty()) manifest.sourcePath
                            else manifest.dirName
                    append("Source: $displayName\n")
                    append("Created: ${java.util.Date(manifest.creationDate)}\n")
                    append("App Version: ${manifest.appVersion}\n\n")
                    append("Files (${manifest.files.size}):\n")

                    // Show file tree (simplified as list for now)
                    manifest.files.take(20).forEach { file ->
                        append("• ${file.pathInArchive} (${formatSize(file.size)})\n")
                    }
                    if (manifest.files.size > 20) {
                        append("... and ${manifest.files.size - 20} more files.\n")
                    }
                }
                builder.setMessage(details)
            } catch (e: Exception) {
                builder.setMessage("Error parsing backup details.")
            }
        }

        builder.setPositiveButton("Close", null)

        return builder.create()
    }

    private fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(
                "%.1f %s",
                size / Math.pow(1024.0, digitGroups.toDouble()),
                units[digitGroups]
        )
    }
}
