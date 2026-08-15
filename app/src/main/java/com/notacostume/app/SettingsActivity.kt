package com.notacostume.app

import android.os.Bundle
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class SettingsActivity : AppCompatActivity() {

    private val pickRestoreFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) BackupManager.restoreFromUri(this, uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        supportFragmentManager.beginTransaction()
            .replace(R.id.container, SettingsFragment())
            .commit()
    }

    inner class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(com.notacostume.app.R.xml.preferences, rootKey)

            findPreference<ListPreference>("theme_preference")?.setOnPreferenceChangeListener { _, newValue ->
                val mode = when (newValue) {
                    "light" -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                    "dark" -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                    else -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode)
                true
            }

            findPreference<Preference>("backup_local")?.setOnPreferenceClickListener {
                BackupManager.backupLocal(this@SettingsActivity)
                true
            }

            findPreference<Preference>("restore_drive")?.setOnPreferenceClickListener {
                pickRestoreFile.launch(arrayOf("application/octet-stream", "*/*"))
                true
            }
        }
    }
}
