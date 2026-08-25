package com.notacostume.app

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.material.datepicker.MaterialDatePicker
import com.notacostume.app.databinding.FragmentFormBinding
import com.notacostume.app.databinding.ItemBarangBinding
import java.io.File
import java.io.FileOutputStream
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
    private var fotoPath: String = ""
    private var ttdPath: String = ""
    var onNotaSaved: (() -> Unit)? = null

    companion object {
        const val ARG_NOTA_ID = "notaId"
        fun forEdit(notaId: Long) = FormFragment().apply {
            arguments = Bundle().apply { putLong(ARG_NOTA_ID, notaId) }
        }
    }

    private val pickFoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val dest = File(requireContext().filesDir, "foto_${System.currentTimeMillis()}.jpg")
            try {
                requireContext().contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                }
                fotoPath = dest.absolutePath
                showFotoPreview()
            } catch (_: Exception) {
                Toast.makeText(requireContext(), R.string.export_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val scanBarcode = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val nama = data.getStringExtra("nama") ?: return@registerForActivityResult
            val harga = data.getLongExtra("harga", 0L)
            // Tambahkan item baru ke form
            addItemRow(nama = nama, jumlah = 1, harga = harga)
            updateSummary()
            Toast.makeText(requireContext(), getString(R.string.scan_found, nama), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentFormBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        TokoManager.ensureDefault(requireContext())
        b.etTanggal.setText(fmtDate.format(Date()))
        b.etTanggal.setOnClickListener { pickDate { d -> b.etTanggal.setText(d) } }
        b.btnTambahBarang.setOnClickListener { addItemRow(); updateSummary() }
        b.btnScanBarcode.setOnClickListener {
            val intent = Intent(requireContext(), ScannerActivity::class.java)
            scanBarcode.launch(intent)
        }
        addItemRow()
        updateSummary()
        b.btnSimpan.setOnClickListener { simpanNota() }
        b.btnTtd.setOnClickListener { showTtdDialog() }
        b.btnHapusTtd.setOnClickListener { hapusTtd() }
        b.btnTambahFoto.setOnClickListener {
            pickFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        b.btnHapusFoto.setOnClickListener { hapusFoto() }

        // Dropdown nama toko (prediksi + ketik manual)
        val tokoList = TokoManager.getAll(requireContext())
        val tokoNames = tokoList.map { it.nama }.distinct()
        val tokoAdapter = android.widget.ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            tokoNames
        )
        b.etToko.setAdapter(tokoAdapter)
        b.etToko.threshold = 1
        // Saat memilih toko dari dropdown, isi nama penjual otomatis dari data toko
        b.etToko.setOnItemClickListener { _, _, position, _ ->
            val chosen = tokoAdapter.getItem(position) ?: return@setOnItemClickListener
            val toko = tokoList.firstOrNull { it.nama == chosen }
            if (toko != null && toko.namaPenjual.isNotBlank()) {
                b.etNamaPenjual.setText(toko.namaPenjual)
            }
        }

        editId = arguments?.getLong(ARG_NOTA_ID) ?: 0L
        if (editId > 0) loadNota(editId)
    }

    private fun loadNota(id: Long) {
        val nota = db.getById(id) ?: return
        existingNomor = nota.nomor
        b.etToko.setText(nota.toko)
        b.etTanggal.setText(nota.tanggal)
        b.etCatatan.setText(nota.catatan)
        b.etNamaPenjual.setText(nota.namaPenjual)
        fotoPath = nota.foto
        ttdPath = nota.ttdPenjual
        b.llItems.removeAllViews()
        if (nota.items.isEmpty()) addItemRow()
        nota.items.forEach { addItemRow(it.nama, it.jumlah, it.harga) }
        showFotoPreview()
        showTtdPreview()
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
            val nama = row.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etBarang).text.toString().trim()
            if (nama.isEmpty()) return@mapNotNull null
            NotaItem(
                nama = nama,
                jumlah = Rupiah.parse(row.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etJumlah).text.toString()).toInt().coerceAtLeast(1),
                harga = Rupiah.parse(row.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etHarga).text.toString())
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

    private fun showFotoPreview() {
        val f = File(fotoPath)
        if (fotoPath.isNotBlank() && f.exists()) {
            b.ivFoto.setImageURI(Uri.fromFile(f))
            b.fotoContainer.visibility = View.VISIBLE
        } else {
            b.fotoContainer.visibility = View.GONE
        }
    }

    private fun showTtdPreview() {
        val f = File(ttdPath)
        if (ttdPath.isNotBlank() && f.exists()) {
            b.ivTtd.setImageURI(Uri.fromFile(f))
            b.ivTtd.visibility = View.VISIBLE
            b.btnHapusTtd.visibility = View.VISIBLE
        } else {
            b.ivTtd.visibility = View.GONE
            b.btnHapusTtd.visibility = View.GONE
        }
    }

    private fun hapusFoto() {
        File(fotoPath).takeIf { it.exists() }?.delete()
        fotoPath = ""
        showFotoPreview()
    }

    private fun hapusTtd() {
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.ttd_hapus_dialog)
            .setPositiveButton(R.string.btn_hapus) { _, _ ->
                File(ttdPath).takeIf { it.exists() }?.delete()
                ttdPath = ""
                showTtdPreview()
            }
            .setNegativeButton(R.string.batal, null)
            .show()
    }

    private fun showTtdDialog() {
        val dv = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_signature, null)
        val sig = dv.findViewById<SignatureView>(R.id.sigView)
        AlertDialog.Builder(requireContext())
            .setView(dv)
            .setPositiveButton(R.string.btn_simpan) { _, _ ->
                if (sig.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.ttd_hint, Toast.LENGTH_SHORT).show()
                } else {
                    saveTtd(sig)
                }
            }
            .setNegativeButton(R.string.batal, null)
            .setNeutralButton(R.string.bersihkan) { _, _ -> sig.clear() }
            .show()
    }

    private fun saveTtd(sig: SignatureView) {
        val bmp = sig.toBitmap()
        val file = File(requireContext().filesDir, "ttd_${System.currentTimeMillis()}.png")
        try {
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            ttdPath = file.absolutePath
            showTtdPreview()
        } catch (_: Exception) {
            Toast.makeText(requireContext(), R.string.export_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun simpanNota() {
        val items = readItems()
        if (items.isEmpty()) {
            Toast.makeText(requireContext(), R.string.isi_lengkap, Toast.LENGTH_SHORT).show()
            return
        }
        val namaPenjual = b.etNamaPenjual.text.toString().trim()
        val activeToko = TokoManager.getActive(requireContext())
        if (editId > 0) {
            val nota = Nota(
                id = editId,
                nomor = existingNomor,
                toko = activeToko.nama,
                tanggal = b.etTanggal.text.toString().ifBlank { fmtDate.format(Date()) },
                catatan = b.etCatatan.text.toString().trim(),
                dibuatPada = System.currentTimeMillis(),
                namaPenjual = namaPenjual,
                ttdPenjual = ttdPath,
                foto = fotoPath,
                items = items
            )
            db.update(nota)
            Toast.makeText(requireContext(), R.string.nota_diubah, Toast.LENGTH_SHORT).show()
            requireActivity().finish()
            return
        }
        val nota = Nota(
            nomor = db.nextNomor(),
            toko = activeToko.nama,
            tanggal = b.etTanggal.text.toString().ifBlank { fmtDate.format(Date()) },
            catatan = b.etCatatan.text.toString().trim(),
            dibuatPada = System.currentTimeMillis(),
            namaPenjual = namaPenjual,
            ttdPenjual = ttdPath,
            foto = fotoPath,
            items = items
        )
        val insertedId = db.insert(nota)
        showPrintAnimation(insertedId)
    }

    private fun clearForm() {
        for (v in intArrayOf(R.id.etToko, R.id.etTanggal, R.id.etCatatan, R.id.etNamaPenjual)) {
            b.root.findViewById<View>(v).let { if (it is EditText) it.text = null }
        }
        b.etTanggal.setText(fmtDate.format(Date()))
        // JANGAN hapus file ttd/foto fisik — itu sudah jadi milik nota yang baru disimpan.
        // Cukup reset variabel path agar form bersih untuk nota berikutnya.
        fotoPath = ""
        ttdPath = ""
        showFotoPreview()
        showTtdPreview()
        b.llItems.removeAllViews()
        addItemRow()
        updateSummary()
    }

    private fun showPrintAnimation(notaId: Long) {
        val activity = requireActivity()
        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)

        val overlay = LayoutInflater.from(requireContext())
            .inflate(R.layout.layout_print_overlay, rootView, false)
        rootView.addView(overlay)

        val tvStatus = overlay.findViewById<TextView>(R.id.tvPrintStatus)
        val tvSubStatus = overlay.findViewById<TextView>(R.id.tvPrintSubStatus)
        val progressBar = overlay.findViewById<ProgressBar>(R.id.progressPrint)
        val receiptPaper = overlay.findViewById<View>(R.id.receiptPaper)

        // Receipt starts hidden inside printer (clipped above), slides down
        receiptPaper.translationY = -60f
        receiptPaper.alpha = 0f
        receiptPaper.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(1500)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // Animate progress bar
        val progressHandler = Handler(Looper.getMainLooper())
        var progress = 0
        val progressRunnable = object : Runnable {
            override fun run() {
                progress += 2
                progressBar.progress = progress
                if (progress < 100) {
                    progressHandler.postDelayed(this, 50)
                }
            }
        }
        progressHandler.postDelayed(progressRunnable, 200)

        // After printing animation, show success
        Handler(Looper.getMainLooper()).postDelayed({
            tvStatus.text = getString(R.string.print_berhasil)
            tvSubStatus.text = getString(R.string.print_selesai)
            progressBar.visibility = View.GONE
        }, 2500)

        // Dismiss overlay and navigate to detail
        Handler(Looper.getMainLooper()).postDelayed({
            clearForm()
            onNotaSaved?.invoke()

            overlay.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction {
                    rootView.removeView(overlay)
                    val intent = Intent(requireContext(), NotaDetailActivity::class.java)
                    intent.putExtra("id", notaId)
                    startActivity(intent)
                }
                .start()
        }, 3500)
    }
}
