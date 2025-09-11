package top.zhangpy.guardianbackup

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import top.zhangpy.guardianbackup.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

//    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mainView = layoutInflater.inflate(R.layout.activity_main, null)
        setContentView(mainView)
        val tv: TextView = findViewById(R.id.sample_text)
        tv.text = stringFromJNI()
    }

    /**
     * A native method that is implemented by the 'guardianbackup' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String

    companion object {
        // Used to load the 'guardianbackup' library on application startup.
        init {
            System.loadLibrary("guardianbackup")
        }
    }
}