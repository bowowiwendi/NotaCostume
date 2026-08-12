package com.notacostume.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.notacostume.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    private val formFragment by lazy { FormFragment() }
    private val riwayatFragment by lazy { RiwayatFragment() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.fragmentContainer, formFragment, "form")
                .add(R.id.fragmentContainer, riwayatFragment, "riwayat")
                .hide(riwayatFragment)
                .commit()
        }

        b.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_form -> {
                    showFragment(formFragment)
                    true
                }
                R.id.menu_riwayat -> {
                    riwayatFragment.refresh()
                    showFragment(riwayatFragment)
                    true
                }
                else -> false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        riwayatFragment.refresh()
    }

    private fun showFragment(target: Fragment) {
        val other = if (target === formFragment) riwayatFragment else formFragment
        supportFragmentManager.beginTransaction()
            .show(target)
            .hide(other)
            .commit()
    }
}
