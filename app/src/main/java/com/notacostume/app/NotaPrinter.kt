package com.notacostume.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.MediaStore
import java.io.FileOutputStream

object NotaPrinter {

    const val PAGE_WIDTH = 419f
    const val PAGE_HEIGHT = 595f

    fun print(context: Context, nota: Nota, tokoNama: String) {
        val jobName = "Nota-${nota.nomor}"
        val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        pm.print(
            jobName,
            object : PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes?,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback?,
                    extras: Bundle?
                ) {
                    if (newAttributes == null) {
                        callback?.onLayoutFailed("Attributes kosong")
                        return
                    }
                    val info = PrintDocumentInfo.Builder(jobName)
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(1)
                        .build()
                    callback?.onLayoutFinished(info, true)
                }

                override fun onWrite(
                    pages: Array<out PageRange>?,
                    destination: ParcelFileDescriptor?,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback?
                ) {
                    val doc = PdfDocument()
                    val page = doc.startPage(
                        PdfDocument.PageInfo.Builder(PAGE_WIDTH.toInt(), PAGE_HEIGHT.toInt(), 1).create()
                    )
                    drawNota(page.canvas, nota, tokoNama)
                    doc.finishPage(page)
                    try {
                        destination?.let { FileOutputStream(it.fileDescriptor).use { f -> doc.writeTo(f) } }
                        callback?.onWriteFinished(arrayOf(PageRange(0, 0)))
                    } catch (e: Exception) {
                        callback?.onWriteFailed(e.message)
                    } finally {
                        doc.close()
                    }
                }
            },
            null
        )
    }

    fun savePdf(context: Context, nota: Nota, tokoNama: String): String? {
        val name = "Nota_${nota.nomor}.pdf"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null

        val doc = PdfDocument()
        val page = doc.startPage(
            PdfDocument.PageInfo.Builder(PAGE_WIDTH.toInt(), PAGE_HEIGHT.toInt(), 1).create()
        )
        drawNota(page.canvas, nota, tokoNama)
        doc.finishPage(page)
        try {
            resolver.openOutputStream(uri)?.use { doc.writeTo(it) }
        } catch (e: Exception) {
            return null
        } finally {
            doc.close()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null, null
            )
        }
        return name
    }

    fun preview(nota: Nota, tokoNama: String, targetWidth: Int): Bitmap {
        val scale = targetWidth.toFloat() / PAGE_WIDTH
        val bmp = Bitmap.createBitmap(
            (PAGE_WIDTH * scale).toInt(),
            (PAGE_HEIGHT * scale).toInt(),
            Bitmap.Config.ARGB_8888
        )
        drawNota(Canvas(bmp), nota, tokoNama, PAGE_WIDTH * scale)
        return bmp
    }

    private fun drawNota(
        canvas: Canvas,
        nota: Nota,
        tokoNama: String,
        width: Float = PAGE_WIDTH
    ) {
        val scale = width / PAGE_WIDTH
        canvas.save()
        canvas.scale(scale, scale)
        canvas.drawColor(Color.WHITE)

        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.BLACK

        var y = 34f

        // Kop: nama toko (dari nota) atau fallback tokoNama
        val kop = nota.toko.ifBlank { tokoNama }
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = 24f
        p.textAlign = Paint.Align.CENTER
        canvas.drawText(kop, PAGE_WIDTH / 2, y, p)

        y += 24f
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = 13f
        canvas.drawText("NOTA PEMBELIAN", PAGE_WIDTH / 2, y, p)

        y += 34f
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textAlign = Paint.Align.LEFT
        p.textSize = 13f
        canvas.drawText("No: ${nota.nomor}", 24f, y, p)
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        p.textAlign = Paint.Align.RIGHT
        canvas.drawText("Tanggal: ${nota.tanggal}", PAGE_WIDTH - 24f, y, p)

        y += 30f
        p.textAlign = Paint.Align.LEFT
        p.textSize = 12f

        y += 26f
        val rowH = 28f
        drawTableRow(canvas, p, y, rowH, arrayOf("No", "Nama Barang", "Jml", "Harga", "Total"), bold = true, fill = true)
        y += rowH
        val visibleItems = nota.items.take(15)
        for ((index, item) in visibleItems.withIndex()) {
            drawTableRow(
                canvas, p, y, rowH,
                arrayOf(
                    (index + 1).toString(),
                    item.nama,
                    item.jumlah.toString(),
                    Rupiah.format(item.harga),
                    Rupiah.format(item.total)
                ),
                bold = false, fill = index % 2 == 1
            )
            y += rowH
        }
        if (nota.items.size > visibleItems.size) {
            drawTableRow(canvas, p, y, rowH, arrayOf("…", "dan ${nota.items.size - visibleItems.size} barang lain", "", "", ""), bold = false, fill = false)
            y += rowH
        }
        y += 16f

        drawKv(canvas, p, "Total Harga", Rupiah.format(nota.total), y, emphasize = true)
        y += 34f

        if (nota.catatan.isNotBlank()) {
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            p.textSize = 11f
            val lines = wrapText(nota.catatan, p, PAGE_WIDTH - 48f)
            for (line in lines) {
                canvas.drawText(line, 24f, y, p)
                y += 18f
            }
            y += 12f
        }

        val bottom = PAGE_HEIGHT - 42f
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        p.textSize = 10f
        p.textAlign = Paint.Align.RIGHT
        canvas.drawText("Penjual", PAGE_WIDTH - 24f, bottom - 30f, p)
        p.strokeWidth = 1f
        canvas.drawLine(PAGE_WIDTH - 120f, bottom, PAGE_WIDTH - 24f, bottom, p)

        canvas.restore()
    }

    private fun drawTableRow(
        canvas: Canvas,
        p: Paint,
        top: Float,
        rowH: Float,
        cells: Array<String>,
        bold: Boolean,
        fill: Boolean
    ) {
        val left = 24f
        val right = PAGE_WIDTH - 24f
        val total = right - left
        val frac = floatArrayOf(0.09f, 0.36f, 0.12f, 0.22f, 0.21f)
        val x = FloatArray(frac.size + 1)
        x[0] = left
        for (i in frac.indices) x[i + 1] = x[i] + total * frac[i]

        p.typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        p.textSize = 11f

        if (fill) {
            p.color = Color.parseColor("#EEEEEE")
            canvas.drawRect(left, top, right, top + rowH, p)
            p.color = Color.BLACK
        }

        p.strokeWidth = 1f
        for (i in 0..x.size - 2) {
            canvas.drawLine(x[i], top, x[i], top + rowH, p)
        }
        canvas.drawLine(x[x.size - 1], top, x[x.size - 1], top + rowH, p)
        canvas.drawLine(left, top, right, top, p)
        canvas.drawLine(left, top + rowH, right, top + rowH, p)

        val baseline = top + (rowH + p.textSize) / 2f - 2f
        val aligns = arrayOf(
            Paint.Align.CENTER, Paint.Align.LEFT, Paint.Align.CENTER, Paint.Align.RIGHT, Paint.Align.RIGHT
        )
        for (i in cells.indices) {
            p.textAlign = aligns[i]
            val tx = when (aligns[i]) {
                Paint.Align.LEFT -> x[i] + 6f
                Paint.Align.RIGHT -> x[i + 1] - 6f
                else -> (x[i] + x[i + 1]) / 2f
            }
            canvas.drawText(cells[i], tx, baseline, p)
        }
        p.textAlign = Paint.Align.LEFT
    }

    private fun drawKv(
        canvas: Canvas,
        p: Paint,
        label: String,
        value: String,
        y: Float,
        emphasize: Boolean = false
    ) {
        p.textSize = if (emphasize) 15f else 12f
        p.typeface = Typeface.create(Typeface.DEFAULT, if (emphasize) Typeface.BOLD else Typeface.NORMAL)
        p.textAlign = Paint.Align.LEFT
        canvas.drawText(label, 24f, y, p)
        p.textAlign = Paint.Align.RIGHT
        canvas.drawText(value, PAGE_WIDTH - 24f, y, p)
        p.textAlign = Paint.Align.LEFT
    }

    private fun wrapText(text: String, p: Paint, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        val words = text.split(" ")
        val sb = StringBuilder()
        for (word in words) {
            val test = if (sb.isEmpty()) word else "$sb $word"
            if (p.measureText(test) > maxWidth && sb.isNotEmpty()) {
                result.add(sb.toString())
                sb.clear()
            }
            if (sb.isEmpty()) sb.append(word) else sb.append(" ").append(word)
        }
        if (sb.isNotEmpty()) result.add(sb.toString())
        return result
    }
}
