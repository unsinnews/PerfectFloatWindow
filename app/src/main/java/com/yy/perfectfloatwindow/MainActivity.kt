package com.yy.perfectfloatwindow

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
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
        }
        return profileFragment!!
    }

    private fun switchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
