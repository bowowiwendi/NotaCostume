package com.notacostume.app

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.notacostume.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    private val formFragment: FormFragment
        get() = supportFragmentManager.findFragmentByTag(TAG_FORM) as FormFragment

    private val riwayatFragment: RiwayatFragment
        get() = supportFragmentManager.findFragmentByTag(TAG_RIWAYAT) as RiwayatFragment

    private val kalkulatorFragment: KalkulatorFragment
        get() = supportFragmentManager.findFragmentByTag(TAG_KALKULATOR) as KalkulatorFragment

    private val tokoFragment: TokoFragment
        get() = supportFragmentManager.findFragmentByTag(TAG_TOKO) as TokoFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemePreference()
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }

        if (savedInstanceState == null) {
            val form = FormFragment()
            val riwayat = RiwayatFragment()
            val kalkulator = KalkulatorFragment()
            val toko = TokoFragment()
            supportFragmentManager.beginTransaction()
                .add(R.id.fragmentContainer, form, TAG_FORM)
                .add(R.id.fragmentContainer, riwayat, TAG_RIWAYAT)
                .add(R.id.fragmentContainer, kalkulator, TAG_KALKULATOR)
                .add(R.id.fragmentContainer, toko, TAG_TOKO)
                .hide(riwayat)
                .hide(kalkulator)
                .hide(toko)
                .commit()
        }

        b.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_form -> { showFragment(formFragment); true }
                R.id.menu_riwayat -> { riwayatFragment.refresh(); showFragment(riwayatFragment); true }
                R.id.menu_kalkulator -> { showFragment(kalkulatorFragment); true }
                R.id.menu_toko -> { tokoFragment.refresh(); showFragment(tokoFragment); true }
                else -> false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        riwayatFragment.refresh()
        tokoFragment.refresh()
    }

    // Dengan configChanges="uiMode", sistem tidak recreate activity saat tema berubah.
    // AppCompat menerapkan tema in-place -> tanpa kedip.
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        delegate.applyDayNight()
    }

    private fun showFragment(target: Fragment) {
        val fragments = listOf(formFragment, riwayatFragment, kalkulatorFragment, tokoFragment)
        val others = fragments.filter { it != target }
        supportFragmentManager.beginTransaction()
            .show(target)
            .hide(others[0])
            .hide(others[1])
            .hide(others[2])
            .commit()
    }

    private fun applyThemePreference() {
        val mode = PreferenceManager.getDefaultSharedPreferences(this)
            .getString("theme_preference", "system")
        val nightMode = when (mode) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    companion object {
        const val TAG_FORM = "form"
        const val TAG_RIWAYAT = "riwayat"
        const val TAG_KALKULATOR = "kalkulator"
        const val TAG_TOKO = "toko"
    }
}