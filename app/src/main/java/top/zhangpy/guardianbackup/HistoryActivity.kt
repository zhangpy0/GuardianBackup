package top.zhangpy.guardianbackup

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import top.zhangpy.guardianbackup.ui.adapter.HistoryAdapter
import top.zhangpy.guardianbackup.ui.dialog.BackupDetailsDialogFragment

class HistoryActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        window.statusBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        window.decorView.fitsSystemWindows = true
        // 让系统装饰区不再被内容覆盖（内容不延伸到状态栏/导航栏）
        WindowCompat.setDecorFitsSystemWindows(window, true)

        // 如果之前通过 FLAG_FULLSCREEN 隐藏过系统栏，清除该标志并显示系统栏
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
        WindowInsetsControllerCompat(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())

        val factory = MainViewModelFactory(application)
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        val tvEmpty = findViewById<View>(R.id.tvEmpty)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter =
                HistoryAdapter(
                        onItemClick = { record ->
                            BackupDetailsDialogFragment.newInstance(record)
                                    .show(supportFragmentManager, "details")
                        },
                        onOpenClick = { record ->
                            try {
                                val intent = Intent(Intent.ACTION_VIEW)
                                intent.setDataAndType(Uri.parse(record.filePath), "application/zip")
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(
                                                this,
                                                "No app found to open this file",
                                                Toast.LENGTH_SHORT
                                        )
                                        .show()
                            }
                        }
                )
        recyclerView.adapter = adapter

        viewModel.backupHistory.observe(this) { history ->
            if (history.isEmpty()) {
                tvEmpty.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                tvEmpty.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                adapter.submitList(history)
            }
        }
    }
}
