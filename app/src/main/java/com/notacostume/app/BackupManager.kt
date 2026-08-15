package com.notacostume.app

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Backup & restore database SQLite ke folder yang dipilih user via SAF
 * (bisa folder Google Drive). Tidak butuh API key / rclone.
 * Semua I/O dijalankan di background thread agar tidak ANR/crash di UI thread.
 */
object BackupManager {

    private const val DB_NAME = "nota.db"
    private val sdf = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault())

    private fun toast(context: Context, msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    fun backupAll(context: Context, treeUri: Uri) {
        Thread {
            try {
                val tree = DocumentFile.fromTreeUri(context, treeUri)
                if (tree == null || !tree.isDirectory) {
                    toast(context, "Folder tidak valid")
                    return@Thread
                }
                backupDbInternal(context, tree)
                try {
                    val notas = NotaDbHelper(context).getAll()
                    if (notas.isNotEmpty()) backupCsvInternal(context, tree, notas)
                } catch (_: Exception) {
                    // CSV optional
                }
                toast(context, "Backup selesai")
            } catch (e: Exception) {
                logCrash(context, "backupAll", e)
                toast(context, "Backup gagal: ${e.message}")
            }
        }.start()
    }

    private fun backupDbInternal(context: Context, tree: DocumentFile) {
        val ts = sdf.format(Date())
        val target = tree.createFile("application/octet-stream", "NotaCostume-backup-$ts.db")
            ?: throw Exception("Gagal membuat file di folder tujuan")
        val src = File(context.getDatabasePath(DB_NAME).absolutePath)
        if (!src.exists()) throw Exception("Database belum ada")
        context.contentResolver.openOutputStream(target.uri)?.use { out ->
            src.inputStream().use { it.copyTo(out) }
        }
    }

    private fun backupCsvInternal(context: Context, tree: DocumentFile, notas: List<Nota>) {
        if (notas.isEmpty()) return
        val ts = sdf.format(Date())
        val target = tree.createFile("text/csv", "NotaCostume-riwayat-$ts.csv") ?: return
        val csv = CsvExporter.buildCsvString(notas)
        context.contentResolver.openOutputStream(target.uri)?.use { out ->
            out.write("\uFEFF$csv".toByteArray(Charsets.UTF_8))
        }
    }

    fun restoreDb(context: Context, fileUri: Uri) {
        Thread {
            try {
                val srcStream = context.contentResolver.openInputStream(fileUri)
                    ?: throw Exception("Tidak bisa membaca file")
                val dest = File(context.getDatabasePath(DB_NAME).absolutePath)
                if (dest.exists()) dest.delete()
                srcStream.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                toast(context, "Restore berhasil. Tutup & buka ulang aplikasi.")
            } catch (e: Exception) {
                logCrash(context, "restoreDb", e)
                toast(context, "Restore gagal: ${e.message}")
            }
        }.start()
    }

    private fun logCrash(context: Context, where: String, e: Exception) {
        try {
            val log = File(context.getExternalFilesDir(null), "backup_error.log")
            FileWriter(log, true).use { fw ->
                fw.append("\n[${sdf.format(Date())}] $where: ${e.stackTraceToString()}\n")
            }
        } catch (_: Exception) {
        }
    }
}
