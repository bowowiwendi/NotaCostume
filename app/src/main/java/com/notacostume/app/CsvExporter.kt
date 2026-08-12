package com.notacostume.app

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {

    /** Simpan riwayat nota sebagai CSV. Mengembalikan nama file, atau null jika gagal. */
    fun export(context: Context, notas: List<Nota>): String? {
        if (notas.isEmpty()) return null

        val csv = buildCsv(notas)
        val bytes = "\uFEFF$csv".toByteArray(Charsets.UTF_8) // BOM agar terbaca Excel
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())
        val filename = "NotaCostume-riwayat-$timestamp.csv"

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return null
            } else {
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: return null
                val file = File(dir, filename)
                FileOutputStream(file).use { it.write(bytes) }
            }
            filename
        } catch (e: Exception) {
            null
        }
    }

    private fun buildCsv(notas: List<Nota>): String {
        val sb = StringBuilder()
        sb.append(
            csvLine(
                listOf(
                    "Nomor", "Tanggal", "Toko", "Barang", "Jumlah", "Harga Satuan",
                    "Subtotal", "Total Nota", "Catatan"
                )
            )
        )
        for (nota in notas) {
            for (item in nota.items) {
                sb.append(
                    csvLine(
                        listOf(
                            nota.nomor,
                            nota.tanggal,
                            nota.toko,
                            item.nama,
                            item.jumlah.toString(),
                            item.harga.toString(),
                            item.total.toString(),
                            nota.total.toString(),
                            nota.catatan
                        )
                    )
                )
            }
            // Baris tambahan agar nota tanpa item tetap tercatat
            if (nota.items.isEmpty()) {
                sb.append(
                    csvLine(
                        listOf(nota.nomor, nota.tanggal, nota.toko, "", "", "", "", nota.total.toString(), nota.catatan)
                    )
                )
            }
        }
        return sb.toString()
    }

    private fun csvLine(fields: List<String>): String {
        val escaped = fields.map { f ->
            if (f.contains(",") || f.contains("\"") || f.contains("\n")) {
                "\"" + f.replace("\"", "\"\"") + "\""
            } else {
                f
            }
        }
        return escaped.joinToString(",") + "\n"
    }
}
