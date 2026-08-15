package com.notacostume.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButtonToggleGroup

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

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val currentMode = prefs.getString("theme_preference", "system") ?: "system"

        val toggle = findViewById<MaterialButtonToggleGroup>(R.id.themeToggle)
        val checkedId = when (currentMode) {
            "light" -> R.id.btnThemeLight
            "dark" -> R.id.btnThemeDark
            else -> R.id.btnThemeSystem
        }
        toggle.check(checkedId)

        toggle.addOnButtonCheckedListener { _, id, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val (pref, mode) = when (id) {
                R.id.btnThemeLight -> "light" to AppCompatDelegate.MODE_NIGHT_NO
                R.id.btnThemeDark -> "dark" to AppCompatDelegate.MODE_NIGHT_YES
                else -> "system" to AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            prefs.edit { putString("theme_preference", pref) }
            AppCompatDelegate.setDefaultNightMode(mode)
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnBackup)
            .setOnClickListener { BackupManager.backupLocal(this) }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnRestore)
            .setOnClickListener {
                pickRestoreFile.launch(arrayOf("application/octet-stream", "*/*"))
            }
    }
}
