package com.notacostume.app

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.notacostume.app.databinding.ActivityBarangListBinding

class BarangListActivity : AppCompatActivity() {

    private lateinit var b: ActivityBarangListBinding
    private val db by lazy { NotaDbHelper(this) }
    private val adapter by lazy {
        BarangAdapter(
            onEdit = { barang -> showEditDialog(barang) },
            onDelete = { barang -> confirmDelete(barang) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityBarangListBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.toolbar.setNavigationOnClickListener { finish() }
        b.rvBarang.layoutManager = LinearLayoutManager(this)
        b.rvBarang.adapter = adapter
        loadData()
    }

    private fun loadData() {
        val items = db.getBarangAll()
        adapter.updateData(items)
        b.tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        b.rvBarang.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showEditDialog(barang: Barang) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_barang, null)
        val etNama = dialogView.findViewById<EditText>(R.id.etNamaBarang)
        val etHarga = dialogView.findViewById<EditText>(R.id.etHargaBarang)
        etNama.setText(barang.nama)
        etHarga.setText(barang.harga.toString())

        AlertDialog.Builder(this)
            .setTitle("Edit Barang")
            .setView(dialogView)
            .setPositiveButton("Simpan") { _, _ ->
                val nama = etNama.text.toString().trim()
                val harga = etHarga.text.toString().trim().toLongOrNull() ?: 0L
                if (nama.isBlank()) {
                    Toast.makeText(this, R.string.isi_lengkap, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                db.updateBarang(barang.copy(nama = nama, harga = harga))
                loadData()
                Toast.makeText(this, "Barang diperbarui", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.batal, null)
            .show()
    }

    private fun confirmDelete(barang: Barang) {
        AlertDialog.Builder(this)
            .setTitle("Hapus Barang")
            .setMessage("Hapus \"${barang.nama}\" dari database?")
            .setPositiveButton(R.string.btn_hapus) { _, _ ->
                db.deleteBarang(barang.id)
                loadData()
                Toast.makeText(this, "${barang.nama} dihapus", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.batal, null)
            .show()
    }
}
