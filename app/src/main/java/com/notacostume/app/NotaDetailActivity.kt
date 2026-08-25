package com.notacostume.app

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.notacostume.app.databinding.ActivityDetailBinding
import java.io.File
import java.io.FileOutputStream

class NotaDetailActivity : AppCompatActivity() {

    private lateinit var b: ActivityDetailBinding
    private val db by lazy { NotaDbHelper(this) }
    private var nota: Nota? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.toolbar.setNavigationOnClickListener { finish() }
        b.btnPrint.setOnClickListener { doPrint() }
        b.btnPdf.setOnClickListener { doSavePdf() }
        b.btnShare.setOnClickListener { doShare() }

        val id = intent.getLongExtra("id", 0L)
        nota = db.getById(id)
        nota?.let { render(it) } ?: finish()
    }

    override fun onResume() {
        super.onResume()
        nota?.let { n ->
            val updated = db.getById(n.id)
            if (updated != null) {
                nota = updated
                render(updated)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.menu_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_edit -> {
                nota?.let { n ->
                    startActivity(Intent(this, EditNotaActivity::class.java).putExtra("id", n.id))
                }
                true
            }
            R.id.menu_hapus -> {
                confirmDelete()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun render(n: Nota) {
        b.toolbar.subtitle = n.nomor
        val width = resources.displayMetrics.widthPixels - (48 * resources.displayMetrics.density).toInt()
        val bmp = NotaPrinter.preview(n, getString(R.string.toko_nama), width)
        b.ivPreview.setImageBitmap(bmp)

        // Animate receipt sliding out of printer
        b.ivPreview.translationY = -300f
        b.ivPreview.alpha = 0f
        b.ivPreview.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(800)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun doPrint() {
        nota?.let { NotaPrinter.print(this, it, getString(R.string.toko_nama)) }
    }

    private fun doSavePdf() {
        nota?.let { n ->
            val name = NotaPrinter.savePdf(this, n, getString(R.string.toko_nama))
            if (name != null) {
                Toast.makeText(this, getString(R.string.pdf_tersimpan, name), Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, R.string.gagal_pdf, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun doShare() {
        nota?.let { n ->
            val bmp = NotaPrinter.preview(n, getString(R.string.toko_nama), 1240)
            val dir = File(cacheDir, "nota").apply { mkdirs() }
            val file = File(dir, "${n.nomor}.png")
            try {
                FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } catch (e: Exception) {
                Toast.makeText(this, R.string.gagal_pdf, Toast.LENGTH_SHORT).show()
                return
            }
            val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, getString(R.string.share_text, n.nomor))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share_title)))
        }
    }

    private fun confirmDelete() {
        nota?.let { n ->
            AlertDialog.Builder(this)
                .setTitle(n.nomor)
                .setMessage(R.string.hapus_konfirmasi)
                .setPositiveButton(R.string.ya) { _, _ ->
                    db.delete(n.id)
                    Toast.makeText(this, R.string.nota_terhapus, Toast.LENGTH_SHORT).show()
                    finish()
                }
                .setNegativeButton(R.string.batal, null)
                .show()
        }
    }
}
