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

        // Background clean white — no outer gray
        canvas.drawColor(Color.WHITE)

        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val margin = 18f
        val contentWidth = PAGE_WIDTH - margin * 2

        // ── Card clean white tanpa garis abu / shadow berat ──
        val cardTop = 12f
        val zigzagH = 10f
        val cardBottom = PAGE_HEIGHT - 12f - zigzagH
        val cardRadius = 16f
        val cardRect = android.graphics.RectF(margin, cardTop, PAGE_WIDTH - margin, cardBottom)

        // Subtle soft shadow (hampir tidak terlihat, bukan garis abu tegas)
        p.color = Color.parseColor("#0A000000")
        canvas.drawRoundRect(
            android.graphics.RectF(cardRect.left + 1f, cardRect.top + 2f, cardRect.right - 1f, cardRect.bottom + 1f),
            cardRadius, cardRadius, p
        )

        // Card fill pure white, no stroke
        p.color = Color.WHITE
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, p)

        // ── Zigzag bottom — clean ──
        drawZigzag(canvas, margin, cardBottom, PAGE_WIDTH - margin, cardBottom + zigzagH, Color.WHITE)

        // ── Header: gradient purple elegan, sudut atas rounded ──
        val headerH = 74f
        val headerRect = android.graphics.RectF(margin, cardTop, PAGE_WIDTH - margin, cardTop + headerH)
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val shader = android.graphics.LinearGradient(
            margin, cardTop, PAGE_WIDTH - margin, cardTop + headerH,
            intArrayOf(Color.parseColor("#7C3AED"), Color.parseColor("#5B21B6")),
            floatArrayOf(0f, 1f),
            android.graphics.Shader.TileMode.CLAMP
        )
        headerPaint.shader = shader
        canvas.drawRoundRect(headerRect, cardRadius, cardRadius, headerPaint)
        // Tutup sudut bawah header agar rata
        headerPaint.shader = null
        headerPaint.color = Color.parseColor("#5B21B6")
        canvas.drawRect(margin, cardTop + headerH - cardRadius, PAGE_WIDTH - margin, cardTop + headerH, headerPaint)

        // Header accent line tipis putih transparan
        p.color = Color.parseColor("#33FFFFFF")
        p.strokeWidth = 1f
        canvas.drawLine(margin + 16f, cardTop + headerH - 1f, PAGE_WIDTH - margin - 16f, cardTop + headerH - 1f, p)

        // Toko name
        val kop = nota.toko.ifBlank { tokoNama }
        p.color = Color.WHITE
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = 17f
        p.textAlign = Paint.Align.CENTER
        // slight shadow for readability
        p.setShadowLayer(2f, 0f, 1f, Color.parseColor("#33000000"))
        canvas.drawText(kop.uppercase(), PAGE_WIDTH / 2, cardTop + 34f, p)
        p.clearShadowLayer()

        // Subtitle
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        p.textSize = 9.5f
        p.alpha = 210
        p.letterSpacing = 0.12f
        canvas.drawText("NOTA PEMBELIAN", PAGE_WIDTH / 2, cardTop + 53f, p)
        p.alpha = 255
        p.letterSpacing = 0f

        var y = cardTop + headerH + 20f

        // ── Info transaksi — tanpa garis abu tebal, hanya spacing + tipis sangat halus ──
        // Latar kotak info halus
        val infoTop = y - 8f
        val infoBottom = y + 34f
        val infoRect = android.graphics.RectF(margin + 10f, infoTop, PAGE_WIDTH - margin - 10f, infoBottom)
        p.color = Color.parseColor("#F8F7FF")
        canvas.drawRoundRect(infoRect, 10f, 10f, p)

        p.color = Color.parseColor("#64748B")
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        p.textSize = 9f
        p.textAlign = Paint.Align.LEFT
        p.letterSpacing = 0.04f
        canvas.drawText("No. Nota", margin + 22f, y + 2f, p)
        p.textAlign = Paint.Align.RIGHT
        p.color = Color.parseColor("#1E293B")
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = 10f
        canvas.drawText(nota.nomor, PAGE_WIDTH - margin - 22f, y + 2f, p)

        y += 18f
        p.color = Color.parseColor("#64748B")
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        p.textSize = 9f
        p.textAlign = Paint.Align.LEFT
        canvas.drawText("Tanggal", margin + 22f, y + 2f, p)
        p.textAlign = Paint.Align.RIGHT
        p.color = Color.parseColor("#1E293B")
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        p.textSize = 10f
        canvas.drawText(nota.tanggal, PAGE_WIDTH - margin - 22f, y + 2f, p)

        y = infoBottom + 16f

        // ── Judul section dengan aksen ungu ──
        p.color = Color.parseColor("#7C3AED")
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = 9f
        p.textAlign = Paint.Align.LEFT
        p.letterSpacing = 0.08f
        canvas.drawText("RINCIAN BARANG", margin + 14f, y, p)
        p.letterSpacing = 0f
        // garis aksen ungu kecil
        p.color = Color.parseColor("#7C3AED")
        p.strokeWidth = 2f
        p.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(margin + 14f, y + 5f, margin + 14f + 28f, y + 5f, p)
        y += 16f

        // ── Items — tanpa dotted/dashed abu, hanya spacing bersih ──
        val visibleItems = nota.items.take(14)
        for ((index, item) in visibleItems.withIndex()) {
            // Nama barang — bold, jelas
            p.color = Color.parseColor("#0F172A")
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            p.textSize = 11f
            p.textAlign = Paint.Align.LEFT
            // truncate if too long
            var nama = item.nama
            if (p.measureText(nama) > contentWidth - 110f) {
                while (nama.length > 3 && p.measureText("$nama…") > contentWidth - 110f) {
                    nama = nama.dropLast(1)
                }
                nama = "$nama…"
            }
            canvas.drawText(nama, margin + 14f, y, p)

            // Qty x Harga — muted
            p.color = Color.parseColor("#94A3B8")
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            p.textSize = 9f
            val qtyPrice = "${item.jumlah} × ${Rupiah.format(item.harga)}"
            canvas.drawText(qtyPrice, margin + 14f, y + 12f, p)

            // Total kanan — tegas
            p.color = Color.parseColor("#1E293B")
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            p.textSize = 11f
            p.textAlign = Paint.Align.RIGHT
            canvas.drawText(Rupiah.format(item.total), PAGE_WIDTH - margin - 14f, y + 4f, p)

            y += 26f

            // Separator ultra halus, hampir tak terlihat (bukan garis abu tegas)
            if (index < visibleItems.size - 1) {
                p.color = Color.parseColor("#F8FAFC")
                p.strokeWidth = 0.7f
                // only draw if enough space left
                if (y < PAGE_HEIGHT - 200f) {
                    canvas.drawLine(margin + 14f, y - 4f, PAGE_WIDTH - margin - 14f, y - 4f, p)
                }
            }
        }
        if (nota.items.size > visibleItems.size) {
            p.color = Color.parseColor("#94A3B8")
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            p.textSize = 9f
            p.textAlign = Paint.Align.LEFT
            canvas.drawText("… dan ${nota.items.size - visibleItems.size} barang lainnya", margin + 14f, y, p)
            y += 16f
        }

        // Garis pemisah halus sebelum total — bukan dashed abu
        y += 4f
        // Double thin line accent
        p.color = Color.parseColor("#EDE9FE")
        p.strokeWidth = 1f
        canvas.drawLine(margin + 14f, y, PAGE_WIDTH - margin - 14f, y, p)
        p.color = Color.parseColor("#F5F3FF")
        p.strokeWidth = 1f
        canvas.drawLine(margin + 14f, y + 3f, PAGE_WIDTH - margin - 14f, y + 3f, p)
        y += 14f

        // ── Total — desain elegan ungu muda, tanpa border hijau tebal ──
        val totalBgTop = y
        val totalBgBottom = y + 42f
        val totalBgRect = android.graphics.RectF(margin + 10f, totalBgTop, PAGE_WIDTH - margin - 10f, totalBgBottom)
        // Gradient soft purple
        val totalShader = android.graphics.LinearGradient(
            totalBgRect.left, totalBgTop, totalBgRect.right, totalBgBottom,
            intArrayOf(Color.parseColor("#F5F3FF"), Color.parseColor("#EDE9FE")),
            null, android.graphics.Shader.TileMode.CLAMP
        )
        val totalPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        totalPaint.shader = totalShader
        canvas.drawRoundRect(totalBgRect, 12f, 12f, totalPaint)
        // Left accent
        p.color = Color.parseColor("#7C3AED")
        p.strokeWidth = 3f
        canvas.drawLine(totalBgRect.left + 2f, totalBgTop + 8f, totalBgRect.left + 2f, totalBgBottom - 8f, p)

        p.color = Color.parseColor("#4C1D95")
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        p.textSize = 11f
        p.textAlign = Paint.Align.LEFT
        p.letterSpacing = 0.02f
        canvas.drawText("Total Pembayaran", margin + 26f, y + 18f, p)
        // small label jumlah item
        p.color = Color.parseColor("#7C3AED")
        p.textSize = 8.5f
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("${nota.items.size} item", margin + 26f, y + 30f, p)

        p.color = Color.parseColor("#4C1D95")
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = 17f
        p.textAlign = Paint.Align.RIGHT
        p.letterSpacing = 0f
        canvas.drawText(Rupiah.format(nota.total), PAGE_WIDTH - margin - 22f, y + 27f, p)

        y = totalBgBottom + 18f

        // ── Catatan — kotak elegan tanpa garis abu ──
        if (nota.catatan.isNotBlank()) {
            val noteLines = wrapText(nota.catatan, Paint().apply { textSize = 10f }, contentWidth - 48f)
            val noteH = 22f + noteLines.size * 13f + 12f
            if (y + noteH < PAGE_HEIGHT - 140f) {
                val noteTop = y
                val noteBottom = y + noteH
                val noteRect = android.graphics.RectF(margin + 10f, noteTop, PAGE_WIDTH - margin - 10f, noteBottom)
                p.color = Color.parseColor("#FFFBEB")
                canvas.drawRoundRect(noteRect, 10f, 10f, p)
                // left accent amber
                p.color = Color.parseColor("#F59E0B")
                canvas.drawLine(noteRect.left + 2f, noteTop + 8f, noteRect.left + 2f, noteBottom - 8f, p)

                p.color = Color.parseColor("#92400E")
                p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                p.textSize = 9f
                p.textAlign = Paint.Align.LEFT
                p.letterSpacing = 0.06f
                canvas.drawText("CATATAN", margin + 26f, y + 15f, p)
                p.letterSpacing = 0f
                y += 24f
                p.color = Color.parseColor("#78350F")
                p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                p.textSize = 10f
                for (line in noteLines) {
                    canvas.drawText(line, margin + 26f, y, p)
                    y += 13f
                }
                y = noteBottom + 10f
            }
        }

        // ── Foto bukti — tanpa border abu, rounded elegan + shadow halus ──
        if (nota.foto.isNotBlank() && y < PAGE_HEIGHT - 170f) {
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inSampleSize = 1
            }
            val photo = BitmapFactory.decodeFile(nota.foto, opts)
            if (photo != null) {
                y += 4f
                p.color = Color.parseColor("#64748B")
                p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                p.textSize = 9f
                p.textAlign = Paint.Align.LEFT
                p.letterSpacing = 0.06f
                canvas.drawText("BUKTI FOTO", margin + 14f, y, p)
                p.letterSpacing = 0f
                y += 8f
                val maxW = contentWidth - 20f
                val maxH = 110f
                val sc = minOf(maxW / photo.width.toFloat(), maxH / photo.height.toFloat())
                val dw = photo.width * sc
                val dh = photo.height * sc
                val photoRect = android.graphics.RectF(margin + 14f, y, margin + 14f + dw, y + dh)
                // shadow halus di bawah foto
                p.color = Color.parseColor("#0F000000")
                canvas.drawRoundRect(
                    android.graphics.RectF(photoRect.left + 1f, photoRect.top + 2f, photoRect.right + 1f, photoRect.bottom + 2f),
                    10f, 10f, p
                )
                // clip rounded
                canvas.save()
                val path = android.graphics.Path()
                path.addRoundRect(photoRect, 10f, 10f, android.graphics.Path.Direction.CW)
                canvas.clipPath(path)
                canvas.drawBitmap(photo, null, photoRect, null)
                canvas.restore()
                y += dh + 14f
            }
        }

        // ── Tanda tangan — tanpa garis abu tebal, hanya thin light ──
        val sigBottom = cardBottom - 18f
        if (nota.ttdPenjual.isNotBlank()) {
            val sig = BitmapFactory.decodeFile(nota.ttdPenjual)
            if (sig != null) {
                val sigLeft = PAGE_WIDTH - margin - 110f
                val sigRight = PAGE_WIDTH - margin - 14f
                val targetH = 34f
                val sc = minOf(targetH / sig.height.toFloat(), (sigRight - sigLeft) / sig.width.toFloat())
                val dw = sig.width * sc
                val dh = sig.height * sc
                val sigTop = sigBottom - dh - 16f
                // Draw signature
                canvas.drawBitmap(
                    sig, null,
                    android.graphics.RectF(sigRight - dw, sigTop, sigRight, sigBottom - 16f),
                    null
                )
                // Garis ttd halus — bukan abu tebal
                p.color = Color.parseColor("#E2E8F0")
                p.strokeWidth = 0.8f
                canvas.drawLine(sigLeft, sigBottom - 16f, sigRight, sigBottom - 16f, p)
                // Label
                p.color = Color.parseColor("#64748B")
                p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                p.textSize = 8.5f
                p.textAlign = Paint.Align.RIGHT
                p.letterSpacing = 0.04f
                canvas.drawText("Penjual", sigRight, sigBottom - dh - 20f, p)
                p.letterSpacing = 0f
                val namaP = nota.namaPenjual.ifBlank { "" }
                if (namaP.isNotBlank()) {
                    p.color = Color.parseColor("#1E293B")
                    p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    p.textSize = 9.5f
                    canvas.drawText(namaP, sigRight, sigBottom - 2f, p)
                    // underline nama? no
                } else {
                    p.color = Color.parseColor("#94A3B8")
                    p.textSize = 8f
                    p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    canvas.drawText("(tanda tangan)", sigRight, sigBottom - 2f, p)
                }
            }
        } else if (nota.namaPenjual.isNotBlank() && y < sigBottom - 40f) {
            // Jika tidak ada ttd tapi ada nama penjual, tampilkan placeholder garis
            val sigLeft = PAGE_WIDTH - margin - 110f
            val sigRight = PAGE_WIDTH - margin - 14f
            p.color = Color.parseColor("#E2E8F0")
            p.strokeWidth = 0.8f
            canvas.drawLine(sigLeft, sigBottom - 16f, sigRight, sigBottom - 16f, p)
            p.color = Color.parseColor("#64748B")
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            p.textSize = 8.5f
            p.textAlign = Paint.Align.RIGHT
            canvas.drawText("Penjual", sigRight, sigBottom - 30f, p)
            p.color = Color.parseColor("#1E293B")
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            p.textSize = 9.5f
            canvas.drawText(nota.namaPenjual, sigRight, sigBottom - 2f, p)
        }

        // ── Footer — minimal, tanpa garis abu ──
        // thin divider above footer — sangat halus
        p.color = Color.parseColor("#F1F5F9")
        p.strokeWidth = 0.7f
        canvas.drawLine(margin + 14f, cardBottom - 24f, PAGE_WIDTH - margin - 14f, cardBottom - 24f, p)

        p.color = Color.parseColor("#94A3B8")
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        p.textSize = 7.5f
        p.textAlign = Paint.Align.CENTER
        p.letterSpacing = 0.06f
        canvas.drawText("Terima kasih atas kepercayaan Anda ♡", PAGE_WIDTH / 2, cardBottom - 12f, p)
        p.letterSpacing = 0f

        canvas.restore()
    }

    private fun drawZigzag(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, color: Int) {
        val zigWidth = 16f
        val zigHeight = 8f
        val path = android.graphics.Path()
        path.moveTo(left, bottom)
        var x = left
        while (x < right) {
            val nextX = minOf(x + zigWidth, right)
            val midX = (x + nextX) / 2f
            path.lineTo(x, top)
            path.lineTo(midX, top)
            path.lineTo(midX, bottom)
            path.lineTo(nextX, bottom)
            x = nextX
        }
        path.lineTo(right, bottom)
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
