package com.notacostume.app

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.datepicker.MaterialDatePicker
import com.notacostume.app.databinding.FragmentFormBinding
import com.notacostume.app.databinding.ItemBarangBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FormFragment : Fragment() {

    private var _b: FragmentFormBinding? = null
    private val b get() = _b!!

    private val db by lazy { NotaDbHelper(requireContext()) }
    private val fmtDate = SimpleDateFormat("dd-MM-yyyy", Locale("id", "ID"))

    private var editId: Long = 0L
    private var existingNomor: String = ""
    var onNotaSaved: (() -> Unit)? = null

    companion object {
        const val ARG_NOTA_ID = "notaId"
        fun forEdit(notaId: Long) = FormFragment().apply {
            arguments = Bundle().apply { putLong(ARG_NOTA_ID, notaId) }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentFormBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b.etTanggal.setOnClickListener { pickDate { d -> b.etTanggal.setText(d) } }
        b.btnTambahBarang.setOnClickListener { addItemRow(); updateSummary() }
        addItemRow()
        updateSummary()
        b.btnSimpan.setOnClickListener { simpanNota() }

        editId = arguments?.getLong(ARG_NOTA_ID) ?: 0L
        if (editId > 0) loadNota(editId)
    }

    private fun loadNota(id: Long) {
        val nota = db.getById(id) ?: return
        existingNomor = nota.nomor
        b.etToko.setText(nota.toko)
        b.etTanggal.setText(nota.tanggal)
        b.etCatatan.setText(nota.catatan)
        b.llItems.removeAllViews()
        if (nota.items.isEmpty()) addItemRow()
        nota.items.forEach { addItemRow(it.nama, it.jumlah, it.harga) }
        updateSummary()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    private fun addItemRow(nama: String = "", jumlah: Int = 1, harga: Long = 0L) {
        val binding = ItemBarangBinding.inflate(layoutInflater, b.llItems, false)
        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = updateSummary()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        }
        binding.etBarang.addTextChangedListener(watcher)
        binding.etJumlah.addTextChangedListener(watcher)
        binding.etHarga.addTextChangedListener(watcher)

        if (nama.isNotEmpty()) binding.etBarang.setText(nama)
        binding.etJumlah.setText(jumlah.toString())
        if (harga > 0) binding.etHarga.setText(harga.toString())

        binding.btnHapusItem.setOnClickListener {
            b.llItems.removeView(binding.root)
            if (b.llItems.childCount == 0) addItemRow()
            updateSummary()
        }
        b.llItems.addView(binding.root)
    }

    private fun readItems(): List<NotaItem> =
        (0 until b.llItems.childCount).mapNotNull { i ->
            val row = b.llItems.getChildAt(i) as View
            val nama = row.findViewById<EditText>(R.id.etBarang).text.toString().trim()
            if (nama.isEmpty()) return@mapNotNull null
            NotaItem(
                nama = nama,
                jumlah = Rupiah.parse(row.findViewById<EditText>(R.id.etJumlah).text.toString()).toInt().coerceAtLeast(1),
                harga = Rupiah.parse(row.findViewById<EditText>(R.id.etHarga).text.toString())
            )
        }

    private fun updateSummary() {
        val total = readItems().sumOf { it.total }
        b.tvTotal.text = Rupiah.format(total)
    }

    private fun pickDate(onPick: (String) -> Unit) {
        val picker = MaterialDatePicker.Builder.datePicker().build()
        picker.show(parentFragmentManager, "date")
        picker.addOnPositiveButtonClickListener { millis ->
            onPick(fmtDate.format(Date(millis)))
        }
    }

    private fun simpanNota() {
        val items = readItems()
        if (items.isEmpty()) {
            Toast.makeText(requireContext(), R.string.isi_lengkap, Toast.LENGTH_SHORT).show()
            return
        }
        if (editId > 0) {
            val nota = Nota(
                id = editId,
                nomor = existingNomor,
                toko = b.etToko.text.toString().trim(),
                tanggal = b.etTanggal.text.toString().ifBlank { fmtDate.format(Date()) },
                catatan = b.etCatatan.text.toString().trim(),
                dibuatPada = System.currentTimeMillis(),
                items = items
            )
            db.update(nota)
            Toast.makeText(requireContext(), R.string.nota_diubah, Toast.LENGTH_SHORT).show()
            requireActivity().finish()
            return
        }
        val nota = Nota(
            nomor = db.nextNomor(),
            toko = b.etToko.text.toString().trim(),
            tanggal = b.etTanggal.text.toString().ifBlank { fmtDate.format(Date()) },
            catatan = b.etCatatan.text.toString().trim(),
            dibuatPada = System.currentTimeMillis(),
            items = items
        )
        db.insert(nota)
        Toast.makeText(requireContext(), R.string.nota_disimpan, Toast.LENGTH_SHORT).show()
        clearForm()
        onNotaSaved?.invoke()
    }

    private fun clearForm() {
        for (v in intArrayOf(R.id.etToko, R.id.etTanggal, R.id.etCatatan)) {
            b.root.findViewById<View>(v).let { if (it is EditText) it.text = null }
        }
        b.llItems.removeAllViews()
        addItemRow()
        updateSummary()
    }
}
