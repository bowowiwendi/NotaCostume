package com.notacostume.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

class ScannerActivity : AppCompatActivity() {

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var scanned = false

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera()
        else {
            Toast.makeText(this, R.string.scan_camera_denied, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scanner)

        findViewById<android.widget.ImageButton>(R.id.btnBack)?.setOnClickListener { finish() }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnManualInput)
            .setOnClickListener { showManualInputDialog() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val previewView = findViewById<androidx.camera.view.PreviewView>(R.id.previewView)
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { imageAnalysis ->
                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null && !scanned) {
                            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                            val scanner = BarcodeScanning.getClient()
                            scanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    for (barcode in barcodes) {
                                        val raw = barcode.rawValue ?: continue
                                        if (raw.isBlank()) continue
                                        if (!scanned) {
                                            scanned = true
                                            runOnUiThread { onBarcodeScanned(raw) }
                                        }
                                    }
                                }
                                .addOnFailureListener { e ->
                                    // Binary/decode error — biarkan user scan ulang
                                    runOnUiThread {
                                        Toast.makeText(this, "Scan gagal: ${e.localizedMessage ?: "coba lagi"}", Toast.LENGTH_SHORT).show()
                                    }
                                    scanned = false
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        } else {
                            imageProxy.close()
                        }
                    }
                }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onBarcodeScanned(barcode: String) {
        val tvResult = findViewById<TextView>(R.id.tvBarcodeResult)
        tvResult.text = barcode
        tvResult.visibility = android.view.View.VISIBLE

        val db = NotaDbHelper(this)
        val barang = db.getBarangByBarcode(barcode)

        if (barang != null) {
            // Produk ditemukan di database
            val intent = Intent().apply {
                putExtra("barcode", barang.barcode)
                putExtra("nama", barang.nama)
                putExtra("harga", barang.harga)
                putExtra("stok", barang.stok)
                putExtra("found", true)
            }
            setResult(RESULT_OK, intent)
            Toast.makeText(this, getString(R.string.scan_found, barang.nama), Toast.LENGTH_SHORT).show()
            finish()
        } else {
            // Produk belum ada — tanya mau input baru?
            showAddBarangDialog(barcode)
        }
    }

    private fun showAddBarangDialog(barcode: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_barang, null)
        val etNama = dialogView.findViewById<EditText>(R.id.etNamaBarang)
        val etHarga = dialogView.findViewById<EditText>(R.id.etHargaBarang)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.scan_new_title))
            .setMessage(getString(R.string.scan_new_message, barcode))
            .setView(dialogView)
            .setPositiveButton(R.string.btn_simpan) { _, _ ->
                val nama = etNama.text.toString().trim()
                val harga = etHarga.text.toString().trim().toLongOrNull() ?: 0L
                if (nama.isBlank()) {
                    Toast.makeText(this, R.string.isi_lengkap, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val db = NotaDbHelper(this)
                val id = db.insertBarang(Barang(barcode = barcode, nama = nama, harga = harga))
                val intent = Intent().apply {
                    putExtra("barcode", barcode)
                    putExtra("nama", nama)
                    putExtra("harga", harga)
                    putExtra("stok", 0)
                    putExtra("found", true)
                }
                setResult(RESULT_OK, intent)
                Toast.makeText(this, getString(R.string.scan_saved, nama), Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton(R.string.batal) { _, _ ->
                // Batal — kembali scan
                scanned = false
            }
            .setCancelable(false)
            .show()
    }

    private fun showManualInputDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.scan_manual_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.scan_manual)
            .setView(input)
            .setPositiveButton(R.string.btn_simpan) { _, _ ->
                val code = input.text.toString().trim()
                if (code.isNotBlank()) {
                    scanned = true
                    onBarcodeScanned(code)
                }
            }
            .setNegativeButton(R.string.batal, null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
