package com.yy.perfectfloatwindow

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.yy.perfectfloatwindow.data.ThemeManager
import com.yy.perfectfloatwindow.ui.HomeFragment
import com.yy.perfectfloatwindow.ui.ProfileFragment

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNavigation: BottomNavigationView
    private var homeFragment: HomeFragment? = null
    private var profileFragment: ProfileFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNavigation = findViewById(R.id.bottomNavigation)

        // Set default fragment
        if (savedInstanceState == null) {
            homeFragment = HomeFragment()
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, homeFragment!!)
                .commit()
        }

        setupBottomNavigation()
        applyTheme()
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
    }

    private fun applyTheme() {
        val isChatGPT = ThemeManager.isChatGPTTheme(this)

        val fragmentContainer = findViewById<View>(R.id.fragmentContainer)

        if (isChatGPT) {
            // ChatGPT Theme
            window.statusBarColor = 0xFFFFFFFF.toInt()
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            bottomNavigation.setBackgroundColor(0xFFFFFFFF.toInt())
            fragmentContainer.setBackgroundColor(0xFFFFFFFF.toInt())
        } else {
            // Netflix Theme
            window.statusBarColor = 0xFF141414.toInt()
            window.decorView.systemUiVisibility = 0
            bottomNavigation.setBackgroundColor(0xFF1F1F1F.toInt())
            fragmentContainer.setBackgroundColor(0xFF141414.toInt())
        }
    }

    private fun setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    switchFragment(getHomeFragment())
                    true
                }
                R.id.nav_profile -> {
                    switchFragment(getProfileFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun getHomeFragment(): Fragment {
        if (homeFragment == null) {
            homeFragment = HomeFragment()
        }
        return homeFragment!!
    }

    private fun getProfileFragment(): Fragment {
        if (profileFragment == null) {
            profileFragment = ProfileFragment()
            profileFragment?.setOnThemeChangedListener {
                applyTheme()
                // Recreate home fragment to apply new theme
                homeFragment = null
            }
        }
        return profileFragment!!
    }

    private fun switchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
