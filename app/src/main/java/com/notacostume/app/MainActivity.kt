package com.notacostume.app

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.notacostume.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    private val formFragment by lazy { FormFragment() }
    private val riwayatFragment by lazy { RiwayatFragment() }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemePreference()
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.menu_theme) {
                showThemeDialog()
                true
            } else false
        }

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
                R.id.menu_settings -> {
                    startActivity(android.content.Intent(this, SettingsActivity::class.java))
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

    private fun applyThemePreference() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val mode = when (prefs.getString("theme_preference", "system")) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun showThemeDialog() {
        val modes = intArrayOf(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES
        )
        val labels = arrayOf(
            getString(R.string.theme_sistem),
            getString(R.string.theme_terang),
            getString(R.string.theme_gelap)
        )
        var selected = modes.indexOf(AppCompatDelegate.getDefaultNightMode()).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.theme_title)
            .setSingleChoiceItems(labels, selected) { _, which -> selected = which }
            .setPositiveButton("OK") { _, _ ->
                AppCompatDelegate.setDefaultNightMode(modes[selected])
                val pref = when (selected) {
                    1 -> "light"
                    2 -> "dark"
                    else -> "system"
                }
                PreferenceManager.getDefaultSharedPreferences(this)
                    .edit()
                    .putString("theme_preference", pref)
                    .apply()
            }
            .setNegativeButton(R.string.batal, null)
            .show()
    }
}
