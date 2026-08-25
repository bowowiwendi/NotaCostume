package com.notacostume.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Build
import androidx.preference.PreferenceManager
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

object ThermalPrinter {

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    // ESC/POS commands
    private val ESC_INIT = byteArrayOf(0x1B, 0x40)           // Initialize printer
    private val ESC_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 1) // Center align
    private val ESC_ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0)   // Left align
    private val ESC_ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 2)  // Right align
    private val ESC_BOLD_ON = byteArrayOf(0x1B, 0x45, 1)      // Bold on
    private val ESC_BOLD_OFF = byteArrayOf(0x1B, 0x45, 0)     // Bold off
    private val ESC_SIZE_NORMAL = byteArrayOf(0x1D, 0x21, 0)  // Normal size
    private val ESC_SIZE_DOUBLE = byteArrayOf(0x1D, 0x21, 17) // Double size (width + height)
    private val ESC_CUT = byteArrayOf(0x1D, 0x56, 0)          // Full cut
    private val ESC_CUT_PARTIAL = byteArrayOf(0x1D, 0x56, 1)  // Partial cut
    private val LF = byteArrayOf(0x0A)                         // Line feed

    fun isAvailable(context: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val mac = prefs.getString("printer_mac", null)
        return mac != null
    }

    fun getSavedPrinterName(context: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getString("printer_name", "") ?: ""
    }

    @SuppressLint("MissingPermission")
    fun getPairedDevices(context: Context): List<BluetoothDevice> {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()
        return adapter.bondedDevices?.toList() ?: emptyList()
    }

    @SuppressLint("MissingPermission")
    fun connect(context: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val mac = prefs.getString("printer_mac", null) ?: return false

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter ?: return false
        if (!adapter.isEnabled) return false

        val device = adapter.getRemoteDevice(mac) ?: return false

        try {
            // Standard SPP UUID
            val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
            socket = device.createRfcommSocketToServiceRecord(uuid)
            socket?.connect()
            outputStream = socket?.outputStream
            // Init printer
            outputStream?.write(ESC_INIT)
            outputStream?.flush()
            return true
        } catch (e: IOException) {
            disconnect()
            return false
        }
    }

    fun disconnect() {
        try {
            outputStream?.close()
            socket?.close()
        } catch (_: Exception) {}
        outputStream = null
        socket = null
    }

    fun isConnected(): Boolean {
        return socket?.isConnected == true && outputStream != null
    }

    fun printNota(context: Context, nota: Nota, tokoNama: String): Boolean {
        if (!isConnected()) {
            if (!connect(context)) return false
        }
        try {
            val os = outputStream ?: return false
            val lineSep = "─".repeat(32)

            // Init
            os.write(ESC_INIT)
            os.write(ESC_SIZE_NORMAL)

            // Header
            os.write(ESC_ALIGN_CENTER)
            os.write(ESC_BOLD_ON)
            os.write(ESC_SIZE_DOUBLE)
            val kop = nota.toko.ifBlank { tokoNama }
            os.write("$kop\n".toByteArray())
            os.write(ESC_SIZE_NORMAL)
            os.write(ESC_BOLD_OFF)
            os.write("NOTA PEMBELIAN\n".toByteArray())
            os.write("$lineSep\n".toByteArray())

            // Info
            os.write(ESC_ALIGN_LEFT)
            os.write(ESC_BOLD_ON)
            os.write("No. Nota  : ".toByteArray())
            os.write(ESC_BOLD_OFF)
            os.write("${nota.nomor}\n".toByteArray())

            os.write(ESC_BOLD_ON)
            os.write("Tanggal   : ".toByteArray())
            os.write(ESC_BOLD_OFF)
            os.write("${nota.tanggal}\n".toByteArray())
            os.write("$lineSep\n".toByteArray())

            // Items
            os.write(ESC_BOLD_ON)
            os.write("DAFTAR BARANG\n".toByteArray())
            os.write(ESC_BOLD_OFF)
            os.write("$lineSep\n".toByteArray())

            for ((i, item) in nota.items.withIndex()) {
                os.write("${i + 1}. ${item.nama}\n".toByteArray())
                os.write("   ${item.jumlah} x ${Rupiah.format(item.harga)}".toByteArray())
                os.write(ESC_ALIGN_RIGHT)
                os.write(" ${Rupiah.format(item.total)}\n".toByteArray())
                os.write(ESC_ALIGN_LEFT)
            }
            os.write("$lineSep\n".toByteArray())

            // Total
            os.write(ESC_BOLD_ON)
            os.write(ESC_SIZE_DOUBLE)
            os.write("Total     : ${Rupiah.format(nota.total)}\n".toByteArray())
            os.write(ESC_SIZE_NORMAL)
            os.write(ESC_BOLD_OFF)
            os.write("$lineSep\n".toByteArray())

            // Catatan
            if (nota.catatan.isNotBlank()) {
                os.write("Catatan: ${nota.catatan}\n".toByteArray())
                os.write("$lineSep\n".toByteArray())
            }

            // Footer
            os.write(ESC_ALIGN_CENTER)
            os.write("Terima kasih atas kunjungan Anda\n".toByteArray())
            os.write("\n\n".toByteArray())

            // Feed & cut
            os.write(ESC_CUT_PARTIAL)
            os.flush()
            return true
        } catch (e: IOException) {
            disconnect()
            return false
        }
    }
}
