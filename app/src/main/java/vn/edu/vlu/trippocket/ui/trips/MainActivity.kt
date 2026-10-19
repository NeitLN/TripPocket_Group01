package vn.edu.vlu.trippocket.ui.trips

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.databinding.ActivityMainBinding
import vn.edu.vlu.trippocket.ui.login.LoginActivity
import vn.edu.vlu.trippocket.util.applySystemBarPadding

/** Hosts Screen 2 (TripListFragment, declared in activity_main.xml). */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding()

        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_toggle_empty -> {
                    tripList()?.toggleEmptyPreview()
                    true
                }
                R.id.action_sign_out -> {
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    private fun tripList() =
        supportFragmentManager.findFragmentById(R.id.fragmentContainer) as? TripListFragment
}
