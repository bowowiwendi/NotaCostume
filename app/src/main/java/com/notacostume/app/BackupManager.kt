package com.notacostume.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Backup & restore LOKAL (tanpa Google Drive / SAF picker).
 * Backup langsung ke folder Download/NotaCostume via MediaStore (API 29+)
 * atau ke external files app (legacy). Tidak butuh permission tambahan.
 * Semua I/O di background thread agar tidak ANR/crash.
 */
object BackupManager {

    private const val DB_NAME = "nota.db"
    private const val FOLDER = "NotaCostume"
    private val sdf = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault())

    private fun toast(context: Context, msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    fun backupLocal(context: Context) {
        Thread {
            try {
                val ts = sdf.format(Date())
                val dbName = "NotaCostume-backup-$ts.db"
                val csvName = "NotaCostume-riwayat-$ts.csv"
                val src = File(context.getDatabasePath(DB_NAME).absolutePath)
                if (!src.exists()) {
                    toast(context, "Database belum ada")
                    return@Thread
                }

                // 1) Backup DB nota
                val dbUri = writeToStorage(context, dbName, "application/octet-stream") { out ->
                    src.inputStream().use { it.copyTo(out) }
                }
                if (dbUri == null) {
                    toast(context, "Gagal menyimpan backup DB")
                    return@Thread
                }

                // 1b) Backup data toko (SharedPreferences: toko_prefs.xml)
                try {
                    val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
                    val tokoFile = File(prefsDir, "toko_prefs.xml")
                    if (tokoFile.exists()) {
                        val tokoName = "NotaCostume-toko-$ts.xml"
                        writeToStorage(context, tokoName, "application/octet-stream") { out ->
                            tokoFile.inputStream().use { it.copyTo(out) }
                        }
                    }
                } catch (_: Exception) { /* toko optional */ }

                // 2) Backup CSV (optional)
                try {
                    val notas = NotaDbHelper(context).getAll()
                    if (notas.isNotEmpty()) {
                        val csv = CsvExporter.buildCsvString(notas)
                        writeToStorage(context, csvName, "text/csv") { out ->
                            out.write("﻿$csv".toByteArray(Charsets.UTF_8))
                        }
                    }
                } catch (_: Exception) { /* CSV optional */ }

                toast(context, "Backup lokal selesai:\nDownload/$FOLDER/$dbName")
            } catch (e: Exception) {
                logCrash(context, "backupLocal", e)
                toast(context, "Backup gagal: ${e.message}")
            }
        }.start()
    }

    /**
     * Tulis file ke Download/NotaCostume (Q+) atau external files app (legacy).
     * Mengembalikan Uri hasil, atau null kalau gagal.
     */
    private fun writeToStorage(
        context: Context,
        fileName: String,
        mime: String,
        writer: (java.io.OutputStream) -> Unit
    ): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/$FOLDER")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                context.contentResolver.openOutputStream(uri)?.use { writer(it) }
                uri
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    FOLDER
                )
                dir.mkdirs()
                val file = File(dir, fileName)
                file.outputStream().use { writer(it) }
                Uri.fromFile(file)
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Restore dari file yang dipilih user (picker). Berjalan di background. */
    fun restoreFromUri(context: Context, fileUri: Uri) {
        Thread {
            try {
                val srcStream = context.contentResolver.openInputStream(fileUri)
                    ?: throw Exception("Tidak bisa membaca file")
                val dest = File(context.getDatabasePath(DB_NAME).absolutePath)
                if (dest.exists()) dest.delete()
                srcStream.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }

                // Restore data toko dari file toko terbaru di folder backup (jika ada)
                try {
                    restoreTokoFromBackup(context)
                } catch (_: Exception) { /* toko optional */ }

                toast(context, "Restore berhasil. Tutup & buka ulang aplikasi.")
            } catch (e: Exception) {
                logCrash(context, "restoreFromUri", e)
                toast(context, "Restore gagal: ${e.message}")
            }
        }.start()
    }

    private fun restoreTokoFromBackup(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Q+: baca lewat MediaStore
            val uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val sel = "${MediaStore.Downloads.RELATIVE_PATH}=? AND ${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
            val args = arrayOf(Environment.DIRECTORY_DOWNLOADS + "/$FOLDER", "NotaCostume-toko-%")
            val c = context.contentResolver.query(uri, null, sel, args, "${MediaStore.Downloads.DISPLAY_NAME} DESC")
            c?.use { cur ->
                if (cur.moveToFirst()) {
                    val fileUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI.buildUpon()
                        .appendPath(cur.getString(cur.getColumnIndex(MediaStore.Downloads._ID))).build()
                    copyTokoFile(context, fileUri)
                }
            }
        } else {
            val folder = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                FOLDER
            )
            folder.listFiles { _, n -> n.startsWith("NotaCostume-toko-") }
                ?.maxByOrNull { it.name }?.let { copyTokoFile(context, Uri.fromFile(it)) }
        }
    }

    private fun copyTokoFile(context: Context, srcUri: Uri) {
        context.contentResolver.openInputStream(srcUri)?.use { input ->
            val dest = File(context.applicationInfo.dataDir, "shared_prefs/toko_prefs.xml")
            dest.parentFile?.mkdirs()
            dest.outputStream().use { out -> input.copyTo(out) }
        }
    }

    private fun logCrash(context: Context, where: String, e: Exception) {
        try {
            val log = File(context.getExternalFilesDir(null), "backup_error.log")
            log.appendText("\n[${sdf.format(Date())}] $where: ${e.stackTraceToString()}\n")
        } catch (_: Exception) {
        }
    }
}
