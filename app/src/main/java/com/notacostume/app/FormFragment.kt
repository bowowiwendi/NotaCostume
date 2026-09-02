package com.notacostume.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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
    private var pendingCameraFile: File? = null
    private var pendingCameraUri: Uri? = null
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
                // Hapus foto lama jika ada agar tidak menumpuk file sampah
                File(fotoPath).takeIf { it.exists() && fotoPath.isNotBlank() }?.delete()
                requireContext().contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                }
                fotoPath = dest.absolutePath
                showFotoPreview()
            } catch (_: Exception) {
                dest.delete()
                Toast.makeText(requireContext(), R.string.export_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val takeFoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && pendingCameraFile != null && pendingCameraFile!!.exists()) {
            // Hapus foto lama
            val old = File(fotoPath)
            if (fotoPath.isNotBlank() && old.exists() && old.absolutePath != pendingCameraFile!!.absolutePath) {
                old.delete()
            }
            fotoPath = pendingCameraFile!!.absolutePath
            showFotoPreview()
        } else {
            // Gagal / dibatalkan -> hapus file temp kosong
            pendingCameraFile?.takeIf { it.exists() }?.delete()
        }
        pendingCameraFile = null
        pendingCameraUri = null
    }

    private val requestCameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            launchCameraInternal()
        } else {
            Toast.makeText(requireContext(), getString(R.string.scan_camera_denied), Toast.LENGTH_LONG).show()
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
        b.btnTambahFoto.setOnClickListener { showFotoOptions() }
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
        val binding = _b ?: return
        val nota = db.getById(id) ?: return
        existingNomor = nota.nomor
        binding.etToko.setText(nota.toko)
        binding.etTanggal.setText(nota.tanggal)
        binding.etCatatan.setText(nota.catatan)
        binding.etNamaPenjual.setText(nota.namaPenjual)
        fotoPath = nota.foto
        ttdPath = nota.ttdPenjual
        binding.llItems.removeAllViews()
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
        val binding2 = _b ?: return
        val binding = ItemBarangBinding.inflate(layoutInflater, binding2.llItems, false)
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
            binding2.llItems.removeView(binding.root)
            if (binding2.llItems.childCount == 0) addItemRow()
            updateSummary()
        }
        binding2.llItems.addView(binding.root)
    }

    private fun readItems(): List<NotaItem> {
        val binding = _b ?: return emptyList()
        return (0 until binding.llItems.childCount).mapNotNull { i ->
            val row = binding.llItems.getChildAt(i) as View
            val nama = row.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etBarang).text.toString().trim()
            if (nama.isEmpty()) return@mapNotNull null
            NotaItem(
                nama = nama,
                jumlah = Rupiah.parse(row.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etJumlah).text.toString()).toInt().coerceAtLeast(1),
                harga = Rupiah.parse(row.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etHarga).text.toString())
            )
        }
    }

    private fun updateSummary() {
        val binding = _b ?: return
        val total = readItems().sumOf { it.total }
        binding.tvTotal.text = Rupiah.format(total)
    }

    private fun pickDate(onPick: (String) -> Unit) {
        val picker = MaterialDatePicker.Builder.datePicker().build()
        picker.show(parentFragmentManager, "date")
        picker.addOnPositiveButtonClickListener { millis ->
            onPick(fmtDate.format(Date(millis)))
        }
    }

    private fun showFotoPreview() {
        val binding = _b ?: return
        val f = File(fotoPath)
        if (fotoPath.isNotBlank() && f.exists()) {
            binding.ivFoto.setImageURI(Uri.fromFile(f))
            binding.fotoContainer.visibility = View.VISIBLE
        } else {
            binding.fotoContainer.visibility = View.GONE
        }
    }

    private fun showTtdPreview() {
        val binding = _b ?: return
        val f = File(ttdPath)
        if (ttdPath.isNotBlank() && f.exists()) {
            binding.ivTtd.setImageURI(Uri.fromFile(f))
            binding.ivTtd.visibility = View.VISIBLE
            binding.btnHapusTtd.visibility = View.VISIBLE
        } else {
            binding.ivTtd.visibility = View.GONE
            binding.btnHapusTtd.visibility = View.GONE
        }
    }

    private fun hapusFoto() {
        File(fotoPath).takeIf { it.exists() }?.delete()
        fotoPath = ""
        pendingCameraFile?.takeIf { it.exists() }?.delete()
        pendingCameraFile = null
        pendingCameraUri = null
        showFotoPreview()
    }

    private fun showFotoOptions() {
        val options = arrayOf(
            getString(R.string.foto_pilih_kamera),
            getString(R.string.foto_pilih_galeri)
        )
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.foto_pilih_judul)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCameraPermissionAndLaunch()
                    1 -> pickFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
            .setNegativeButton(R.string.batal, null)
            .show()
    }

    private fun checkCameraPermissionAndLaunch() {
        val ctx = requireContext()
        when {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                launchCameraInternal()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                AlertDialog.Builder(ctx)
                    .setTitle(getString(R.string.foto_pilih_kamera))
                    .setMessage("Aplikasi membutuhkan izin kamera untuk mengambil foto bukti langsung. Izinkan akses kamera?")
                    .setPositiveButton("Izinkan") { _, _ ->
                        requestCameraPermission.launch(Manifest.permission.CAMERA)
                    }
                    .setNegativeButton(R.string.batal, null)
                    .show()
            }
            else -> {
                requestCameraPermission.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun launchCameraInternal() {
        try {
            val ctx = requireContext()
            val file = File(ctx.filesDir, "foto_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            pendingCameraFile = file
            pendingCameraUri = uri
            takeFoto.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Gagal membuka kamera: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    // Wrapper untuk kompatibilitas jika dipanggil dari luar
    private fun launchCamera() = checkCameraPermissionAndLaunch()

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
        val binding = _b ?: return
        try {
            val items = readItems()
            if (items.isEmpty()) {
                Toast.makeText(requireContext(), R.string.isi_lengkap, Toast.LENGTH_SHORT).show()
                return
            }
            val namaPenjual = binding.etNamaPenjual.text.toString().trim()
            val tokoName = binding.etToko.text.toString().trim().ifBlank { getString(R.string.toko_nama) }
            if (editId > 0) {
                val nota = Nota(
                    id = editId,
                    nomor = existingNomor,
                    toko = tokoName,
                    tanggal = binding.etTanggal.text.toString().ifBlank { fmtDate.format(Date()) },
                    catatan = binding.etCatatan.text.toString().trim(),
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
                toko = tokoName,
                tanggal = binding.etTanggal.text.toString().ifBlank { fmtDate.format(Date()) },
                catatan = binding.etCatatan.text.toString().trim(),
                dibuatPada = System.currentTimeMillis(),
                namaPenjual = namaPenjual,
                ttdPenjual = ttdPath,
                foto = fotoPath,
                items = items
            )
            val insertedId = db.insert(nota)
            showPrintAnimation(insertedId)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Gagal simpan nota: ${e.localizedMessage ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    private fun clearForm() {
        val binding = _b ?: return
        for (v in intArrayOf(R.id.etToko, R.id.etTanggal, R.id.etCatatan, R.id.etNamaPenjual)) {
            binding.root.findViewById<View>(v).let { if (it is EditText) it.text = null }
        }
        binding.etTanggal.setText(fmtDate.format(Date()))
        fotoPath = ""
        ttdPath = ""
        showFotoPreview()
        showTtdPreview()
        binding.llItems.removeAllViews()
        addItemRow()
        updateSummary()
    }

    private fun showPrintAnimation(notaId: Long) {
        val ctx = context ?: return
        val activity = activity ?: return
        if (activity.isFinishing || activity.isDestroyed) return

        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)

        val overlay = LayoutInflater.from(ctx)
            .inflate(R.layout.layout_print_overlay, rootView, false)
        rootView.addView(overlay)

        val tvStatus = overlay.findViewById<TextView>(R.id.tvPrintStatus)
        val tvSubStatus = overlay.findViewById<TextView>(R.id.tvPrintSubStatus)
        val progressBar = overlay.findViewById<ProgressBar>(R.id.progressPrint)
        val receiptPaper = overlay.findViewById<View>(R.id.receiptPaper)

        // Receipt slides down from printer
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

        // Success message
        Handler(Looper.getMainLooper()).postDelayed({
            if (activity.isFinishing || activity.isDestroyed) return@postDelayed
            tvStatus.text = getString(R.string.print_berhasil)
            tvSubStatus.text = getString(R.id.tvPrintSubStatus) // keep original sub
            tvSubStatus.text = getString(R.string.print_selesai)
            progressBar.visibility = View.GONE
        }, 2500)

        // Navigate to detail
        Handler(Looper.getMainLooper()).postDelayed({
            if (activity.isFinishing || activity.isDestroyed) return@postDelayed
            try { clearForm() } catch (_: Exception) {}
            onNotaSaved?.invoke()
            try { rootView.removeView(overlay) } catch (_: Exception) {}
            try {
                val intent = Intent(ctx, NotaDetailActivity::class.java)
                intent.putExtra("id", notaId)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(intent)
            } catch (_: Exception) {}
        }, 3500)
    }
}
