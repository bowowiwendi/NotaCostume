package com.notacostume.app

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Backup & restore database SQLite ke folder yang dipilih user via SAF
 * (bisa folder Google Drive). Tidak butuh API key / rclone.
 */
object BackupManager {

    private const val DB_NAME = "nota.db"

    fun backupDb(context: Context, treeUri: Uri) {
        try {
            val tree = DocumentFile.fromTreeUri(context, treeUri) ?: run {
                Toast.makeText(context, "Folder tidak valid", Toast.LENGTH_SHORT).show()
                return
            }
            val ts = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())
            val target = tree.createFile("application/octet-stream", "NotaCostume-backup-$ts.db")
                ?: run {
                    Toast.makeText(context, "Gagal membuat file di folder tujuan", Toast.LENGTH_SHORT).show()
                    return
                }
            val src = File(context.getDatabasePath(DB_NAME).absolutePath)
            if (!src.exists()) {
                Toast.makeText(context, "Database belum ada", Toast.LENGTH_SHORT).show()
                return
            }
            context.contentResolver.openOutputStream(target.uri)?.use { out ->
                src.inputStream().use { it.copyTo(out) }
            }
            Toast.makeText(context, "Backup berhasil: ${target.name}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Backup gagal: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun backupAll(context: Context, treeUri: Uri) {
        backupDb(context, treeUri)
        try {
            val notas = NotaDbHelper(context).getAll()
            if (notas.isNotEmpty()) {
                backupCsv(context, treeUri, notas)
            }
        } catch (_: Exception) {
            // CSV optional, jangan gagalkan backup DB
        }
    }

    fun restoreDb(context: Context, fileUri: Uri) {
        try {
            val srcStream = context.contentResolver.openInputStream(fileUri) ?: run {
                Toast.makeText(context, "Tidak bisa membaca file", Toast.LENGTH_SHORT).show()
                return
            }
            val dest = File(context.getDatabasePath(DB_NAME).absolutePath)
            // Hapus dulu jika ada agar copy bersih
            if (dest.exists()) dest.delete()
            srcStream.use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Toast.makeText(context, "Restore berhasil. Restart aplikasi.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Restore gagal: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /** Export CSV juga ke folder Drive (bonus, selain ke Downloads). */
    fun backupCsv(context: Context, treeUri: Uri, notas: List<Nota>) {
        if (notas.isEmpty()) {
            Toast.makeText(context, R.string.export_empty, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return
            val ts = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())
            val target = tree.createFile("text/csv", "NotaCostume-riwayat-$ts.csv") ?: return
            val csv = CsvExporter.buildCsvString(notas)
            context.contentResolver.openOutputStream(target.uri)?.use { out ->
                out.write("\uFEFF$csv".toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(context, "CSV tersimpan: ${target.name}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
