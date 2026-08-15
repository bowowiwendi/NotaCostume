package com.notacostume.app

import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup

class SettingsActivity : AppCompatActivity() {

    private val pickRestoreFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) BackupManager.restoreFromUri(this, uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_settings)

            val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
            toolbar.setNavigationOnClickListener { finish() }

            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            val currentMode = prefs.getString("theme_preference", "system") ?: "system"

            val toggle = findViewById<MaterialButtonToggleGroup>(R.id.themeToggle)
            // Set pilihan awal tanpa memicu listener (singleSelection menjamin 1 saja)
            val initialId = when (currentMode) {
                "light" -> R.id.btnThemeLight
                "dark" -> R.id.btnThemeDark
                else -> R.id.btnThemeSystem
            }
            toggle.check(initialId)

            toggle.addOnButtonCheckedListener { _, id, isChecked ->
                // Hanya proses saat tombol DIPENCET (isChecked=true) dan itu selection baru
                if (!isChecked) return@addOnButtonCheckedListener
                val (pref, mode) = when (id) {
                    R.id.btnThemeLight -> "light" to AppCompatDelegate.MODE_NIGHT_NO
                    R.id.btnThemeDark -> "dark" to AppCompatDelegate.MODE_NIGHT_YES
                    else -> "system" to AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                prefs.edit { putString("theme_preference", pref) }
                // configChanges="uiMode" cegah recreate -> apply in-place tanpa kedip
                AppCompatDelegate.setDefaultNightMode(mode)
            }

            findViewById<MaterialButton>(R.id.btnBackup)
                .setOnClickListener { BackupManager.backupLocal(this) }

            findViewById<MaterialButton>(R.id.btnRestore)
                .setOnClickListener {
                    pickRestoreFile.launch(arrayOf("application/octet-stream", "*/*"))
                }
        } catch (e: Exception) {
            Toast.makeText(this, "Settings error: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        delegate.applyDayNight()
    }
}
