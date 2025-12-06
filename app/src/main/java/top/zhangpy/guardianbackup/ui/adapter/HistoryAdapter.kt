package top.zhangpy.guardianbackup.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.zhangpy.guardianbackup.R
import top.zhangpy.guardianbackup.core.domain.model.BackupRecord

class HistoryAdapter(private var items: List<BackupRecord> = emptyList()) :
        RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val tvPath: TextView = view.findViewById(R.id.tvPath)
        val tvCount: TextView = view.findViewById(R.id.tvCount)
        val tvSize: TextView = view.findViewById(R.id.tvSize)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        holder.tvDate.text = sdf.format(Date(item.timestamp))
        holder.tvPath.text = item.filePath
        holder.tvCount.text = "Files: ${item.fileCount}"
        holder.tvSize.text = "Size: ${formatSize(item.sizeBytes)}"
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<BackupRecord>) {
        items = newItems
        notifyDataSetChanged()
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
