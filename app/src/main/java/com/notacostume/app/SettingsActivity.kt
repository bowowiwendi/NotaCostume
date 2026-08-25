package com.notacostume.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton

class SettingsActivity : AppCompatActivity() {

    private val pickRestoreFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) BackupManager.restoreFromUri(this, uri)
    }

    private val requestBtPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) showPrinterSelectionDialog()
        else Toast.makeText(this, R.string.printer_not_found, Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_settings)

            val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
            toolbar.setNavigationOnClickListener { finish() }

            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            val currentMode = prefs.getString("theme_preference", "system") ?: "system"

            val toggle = findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.themeToggle)
            val initialId = when (currentMode) {
                "light" -> R.id.btnThemeLight
                "dark" -> R.id.btnThemeDark
                else -> R.id.btnThemeSystem
            }
            toggle.check(initialId)

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

            findViewById<MaterialButton>(R.id.btnBackup)
                .setOnClickListener { BackupManager.backupLocal(this) }

            findViewById<MaterialButton>(R.id.btnRestore)
                .setOnClickListener {
                    pickRestoreFile.launch(arrayOf("application/octet-stream", "*/*"))
                }

            // ── Printer Termal ──
            updatePrinterStatus()

            findViewById<MaterialButton>(R.id.btnSelectPrinter)
                .setOnClickListener { checkBtAndShowDialog() }

            findViewById<MaterialButton>(R.id.btnTestPrint)
                .setOnClickListener {
                    val name = ThermalPrinter.getSavedPrinterName(this)
                    if (name.isBlank()) {
                        Toast.makeText(this, R.string.printer_none, Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    Toast.makeText(this, R.string.printer_connecting, Toast.LENGTH_SHORT).show()
                    Thread {
                        val ok = ThermalPrinter.connect(this)
                        runOnUiThread {
                            if (ok) Toast.makeText(this, R.string.printer_success, Toast.LENGTH_SHORT).show()
                            else Toast.makeText(this, R.string.printer_failed, Toast.LENGTH_LONG).show()
                        }
                    }.start()
                }
        } catch (e: Exception) {
            Toast.makeText(this, "Settings error: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun checkBtAndShowDialog() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.BLUETOOTH_CONNECT)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (perms.isNotEmpty()) requestBtPermission.launch(perms.toTypedArray())
        else showPrinterSelectionDialog()
    }

    @SuppressLint("MissingPermission")
    private fun showPrinterSelectionDialog() {
        val devices = ThermalPrinter.getPairedDevices(this)
        if (devices.isEmpty()) {
            Toast.makeText(this, R.string.printer_not_found, Toast.LENGTH_LONG).show()
            return
        }
        val names = devices.map { it.name ?: it.address }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.printer_select)
            .setItems(names) { _, which ->
                val device = devices[which]
                val prefs = PreferenceManager.getDefaultSharedPreferences(this)
                prefs.edit {
                    putString("printer_mac", device.address)
                    putString("printer_name", device.name ?: device.address)
                }
                updatePrinterStatus()
                Toast.makeText(this, getString(R.string.printer_connected, device.name), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.batal, null)
            .show()
    }

    private fun updatePrinterStatus() {
        val tv = findViewById<TextView>(R.id.tvPrinterStatus)
        val btnTest = findViewById<MaterialButton>(R.id.btnTestPrint)
        val name = ThermalPrinter.getSavedPrinterName(this)
        if (name.isNotBlank()) {
            tv.text = getString(R.string.printer_connected, name)
            btnTest.visibility = android.view.View.VISIBLE
        } else {
            tv.text = getString(R.string.printer_none)
            btnTest.visibility = android.view.View.GONE
        }
    }
}
