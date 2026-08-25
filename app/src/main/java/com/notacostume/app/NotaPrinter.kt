package com.notacostume.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
        val safeWidth = targetWidth.coerceIn(200, 1200)
        val scale = safeWidth.toFloat() / PAGE_WIDTH
        val bmp = Bitmap.createBitmap(
            (PAGE_WIDTH * scale).toInt().coerceAtLeast(1),
            (PAGE_HEIGHT * scale).toInt().coerceAtLeast(1),
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

        // Background
        canvas.drawColor(Color.WHITE)

        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val margin = 20f
        val contentWidth = PAGE_WIDTH - margin * 2

        // ── Card shadow & background ──
        val cardTop = 16f
        val zigzagH = 12f
        val cardBottom = PAGE_HEIGHT - 16f - zigzagH
        val cardRadius = 12f
        val cardRect = android.graphics.RectF(margin - 4f, cardTop, PAGE_WIDTH - margin + 4f, cardBottom)

        // Shadow (wrapped in try-catch for devices that don't support BlurMaskFilter)
        try {
            p.color = Color.parseColor("#22000000")
            p.maskFilter = android.graphics.BlurMaskFilter(6f, android.graphics.BlurMaskFilter.Blur.NORMAL)
            canvas.drawRoundRect(cardRect, cardRadius, cardRadius, p)
            p.maskFilter = null
        } catch (_: Exception) {
            p.color = Color.parseColor("#11000000")
            canvas.drawRoundRect(cardRect, cardRadius, cardRadius, p)
        }

        // Card fill
        p.color = Color.WHITE
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, p)

        // ── Zigzag / Gerigi at bottom ──
        drawZigzag(canvas, margin - 4f, cardBottom, PAGE_WIDTH - margin + 4f, cardBottom + zigzagH, Color.WHITE)

        // ── Header (gradient purple) ──
        val headerH = 80f
        val headerRect = android.graphics.RectF(margin - 4f, cardTop, PAGE_WIDTH - margin + 4f, cardTop + headerH)
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val shader = android.graphics.LinearGradient(
            margin, cardTop, PAGE_WIDTH - margin, cardTop + headerH,
            intArrayOf(Color.parseColor("#7C3AED"), Color.parseColor("#4F46E5")),
            null,
            android.graphics.Shader.TileMode.CLAMP
        )
        headerPaint.shader = shader
        canvas.drawRoundRect(headerRect, cardRadius, cardRadius, headerPaint)
        // Fix bottom corners of header (cover with rect)
        headerPaint.color = Color.parseColor("#4F46E5")
        canvas.drawRect(margin - 4f, cardTop + headerH - cardRadius, PAGE_WIDTH - margin + 4f, cardTop + headerH, headerPaint)

        // Toko name in header
        val kop = nota.toko.ifBlank { tokoNama }
        p.color = Color.WHITE
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = 18f
        p.textAlign = Paint.Align.CENTER
        canvas.drawText(kop, PAGE_WIDTH / 2, cardTop + 36f, p)

        // Subtitle
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        p.textSize = 11f
        p.alpha = 200
        canvas.drawText("NOTA PEMBELIAN", PAGE_WIDTH / 2, cardTop + 56f, p)
        p.alpha = 255

        var y = cardTop + headerH + 24f

        // ── Transaction info ──
        p.color = Color.parseColor("#6B7280")
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        p.textSize = 10f
        p.textAlign = Paint.Align.LEFT
        canvas.drawText("No. Nota", margin + 12f, y, p)
        p.textAlign = Paint.Align.RIGHT
        p.color = Color.parseColor("#1F2937")
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(nota.nomor, PAGE_WIDTH - margin - 12f, y, p)

        y += 18f
        p.color = Color.parseColor("#6B7280")
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        p.textSize = 10f
        p.textAlign = Paint.Align.LEFT
        canvas.drawText("Tanggal", margin + 12f, y, p)
        p.textAlign = Paint.Align.RIGHT
        p.color = Color.parseColor("#1F2937")
        canvas.drawText(nota.tanggal, PAGE_WIDTH - margin - 12f, y, p)

        y += 14f
        // Dashed separator
        drawDashedLine(canvas, margin + 12f, y, PAGE_WIDTH - margin - 12f, y, Color.parseColor("#D1D5DB"), 1f)
        y += 18f

        // ── Items section title ──
        p.color = Color.parseColor("#6B7280")
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = 10f
        p.textAlign = Paint.Align.LEFT
        canvas.drawText("DAFTAR BARANG", margin + 12f, y, p)
        y += 16f

        // ── Items ──
        val visibleItems = nota.items.take(12)
        for ((index, item) in visibleItems.withIndex()) {
            // Item name
            p.color = Color.parseColor("#1F2937")
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            p.textSize = 11f
            p.textAlign = Paint.Align.LEFT
            canvas.drawText(item.nama, margin + 12f, y, p)

            // Qty x Harga
            p.color = Color.parseColor("#9CA3AF")
            p.textSize = 9f
            val qtyPrice = "${item.jumlah} x ${Rupiah.format(item.harga)}"
            canvas.drawText(qtyPrice, margin + 12f, y + 13f, p)

            // Total price (right)
            p.color = Color.parseColor("#1F2937")
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            p.textSize = 11f
            p.textAlign = Paint.Align.RIGHT
            canvas.drawText(Rupiah.format(item.total), PAGE_WIDTH - margin - 12f, y, p)

            y += 28f

            // Dotted separator between items
            if (index < visibleItems.size - 1) {
                drawDottedLine(canvas, margin + 12f, y - 6f, PAGE_WIDTH - margin - 12f, y - 6f, Color.parseColor("#E5E7EB"), 1f)
            }
        }
        if (nota.items.size > visibleItems.size) {
            p.color = Color.parseColor("#9CA3AF")
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            p.textSize = 10f
            p.textAlign = Paint.Align.LEFT
            canvas.drawText("… dan ${nota.items.size - visibleItems.size} barang lainnya", margin + 12f, y, p)
            y += 18f
        }

        y += 8f
        drawDashedLine(canvas, margin + 12f, y, PAGE_WIDTH - margin - 12f, y, Color.parseColor("#D1D5DB"), 1f)
        y += 20f

        // ── Total section (highlighted) ──
        val totalBgTop = y - 6f
        val totalBgBottom = y + 32f
        val totalBgRect = android.graphics.RectF(margin + 8f, totalBgTop, PAGE_WIDTH - margin - 8f, totalBgBottom)
        p.color = Color.parseColor("#F0FDF4")
        canvas.drawRoundRect(totalBgRect, 8f, 8f, p)
        // Green border
        p.color = Color.parseColor("#22C55E")
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1f
        canvas.drawRoundRect(totalBgRect, 8f, 8f, p)
        p.style = Paint.Style.FILL

        p.color = Color.parseColor("#166534")
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        p.textSize = 12f
        p.textAlign = Paint.Align.LEFT
        canvas.drawText("Total Pembayaran", margin + 20f, y + 18f, p)

        p.color = Color.parseColor("#166534")
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = 16f
        p.textAlign = Paint.Align.RIGHT
        canvas.drawText(Rupiah.format(nota.total), PAGE_WIDTH - margin - 20f, y + 18f, p)

        y = totalBgBottom + 18f

        // ── Catatan ──
        if (nota.catatan.isNotBlank()) {
            drawDashedLine(canvas, margin + 12f, y, PAGE_WIDTH - margin - 12f, y, Color.parseColor("#D1D5DB"), 1f)
            y += 14f
            p.color = Color.parseColor("#6B7280")
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            p.textSize = 10f
            p.textAlign = Paint.Align.LEFT
            canvas.drawText("Catatan", margin + 12f, y, p)
            y += 14f
            p.color = Color.parseColor("#374151")
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            p.textSize = 10f
            val lines = wrapText(nota.catatan, p, contentWidth - 24f)
            for (line in lines) {
                canvas.drawText(line, margin + 12f, y, p)
                y += 14f
            }
            y += 8f
        }

        // ── Foto (larger & clearer) ──
        if (nota.foto.isNotBlank() && y < PAGE_HEIGHT - 180f) {
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inSampleSize = 1
            }
            val photo = BitmapFactory.decodeFile(nota.foto, opts)
            if (photo != null) {
                drawDashedLine(canvas, margin + 12f, y, PAGE_WIDTH - margin - 12f, y, Color.parseColor("#D1D5DB"), 1f)
                y += 14f
                p.color = Color.parseColor("#6B7280")
                p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                p.textSize = 10f
                p.textAlign = Paint.Align.LEFT
                canvas.drawText("Bukti Foto", margin + 12f, y, p)
                y += 8f
                val maxW = contentWidth - 24f
                val maxH = 120f
                val sc = minOf(maxW / photo.width.toFloat(), maxH / photo.height.toFloat())
                val dw = photo.width * sc
                val dh = photo.height * sc
                val photoRect = android.graphics.RectF(margin + 12f, y, margin + 12f + dw, y + dh)
                // Photo border
                p.color = Color.parseColor("#E5E7EB")
                p.style = Paint.Style.STROKE
                p.strokeWidth = 1f
                canvas.drawRoundRect(photoRect, 6f, 6f, p)
                p.style = Paint.Style.FILL
                // Photo with rounded corners
                canvas.save()
                val path = android.graphics.Path()
                path.addRoundRect(photoRect, 6f, 6f, android.graphics.Path.Direction.CW)
                canvas.clipPath(path)
                canvas.drawBitmap(photo, null, photoRect, null)
                canvas.restore()
                y += dh + 12f
            }
        }

        // ── Signature area ──
        val sigAreaTop = y
        val sigBottom = cardBottom - 20f
        if (nota.ttdPenjual.isNotBlank()) {
            val sig = BitmapFactory.decodeFile(nota.ttdPenjual)
            if (sig != null) {
                drawDashedLine(canvas, margin + 12f, sigAreaTop, PAGE_WIDTH - margin - 12f, sigAreaTop, Color.parseColor("#D1D5DB"), 1f)
                val sigLeft = PAGE_WIDTH - margin - 100f
                val sigRight = PAGE_WIDTH - margin - 12f
                val targetH = 32f
                val sc = minOf(targetH / sig.height.toFloat(), (sigRight - sigLeft) / sig.width.toFloat())
                val dw = sig.width * sc
                val dh = sig.height * sc
                val sigTop = sigBottom - dh - 14f
                canvas.drawBitmap(
                    sig, null,
                    android.graphics.RectF(sigRight - dw, sigTop, sigRight, sigBottom - 14f),
                    null
                )
                // Garis ttd
                p.color = Color.parseColor("#9CA3AF")
                p.strokeWidth = 0.8f
                canvas.drawLine(sigLeft, sigBottom - 14f, sigRight, sigBottom - 14f, p)
                // Label
                p.color = Color.parseColor("#6B7280")
                p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                p.textSize = 9f
                p.textAlign = Paint.Align.RIGHT
                canvas.drawText("Penjual", sigRight, sigBottom - dh - 18f, p)
                val namaP = nota.namaPenjual.ifBlank { "" }
                if (namaP.isNotBlank()) {
                    p.textSize = 9f
                    canvas.drawText(namaP, sigRight, sigBottom, p)
                }
            }
        }

        // ── Footer ──
        p.color = Color.parseColor("#9CA3AF")
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        p.textSize = 8f
        p.textAlign = Paint.Align.CENTER
        canvas.drawText("Terima kasih atas kunjungan Anda", PAGE_WIDTH / 2, cardBottom - 10f, p)

        canvas.restore()
    }

    private fun drawZigzag(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, color: Int) {
        val zigWidth = 12f
        val zigHeight = 6f
        val path = android.graphics.Path()
        path.moveTo(left, top)
        var x = left
        while (x < right) {
            val nextX = minOf(x + zigWidth, right)
            // Square zigzag: up-flat-down pattern
            path.lineTo(x, top)
            path.lineTo(x, top - zigHeight)
            path.lineTo(nextX, top - zigHeight)
            path.lineTo(nextX, top)
            x = nextX
        }
        path.lineTo(right, bottom)
        path.lineTo(left, bottom)
        path.close()

        val zigPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        zigPaint.color = color
        canvas.drawPath(path, zigPaint)
    }

    private fun drawDashedLine(canvas: Canvas, startX: Float, startY: Float, endX: Float, endY: Float, color: Int, strokeWidth: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = color
        p.strokeWidth = strokeWidth
        p.pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f, 6f), 0f)
        canvas.drawLine(startX, startY, endX, endY, p)
    }

    private fun drawDottedLine(canvas: Canvas, startX: Float, startY: Float, endX: Float, endY: Float, color: Int, strokeWidth: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = color
        p.strokeWidth = strokeWidth
        p.pathEffect = android.graphics.DashPathEffect(floatArrayOf(2f, 4f), 0f)
        canvas.drawLine(startX, startY, endX, endY, p)
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
