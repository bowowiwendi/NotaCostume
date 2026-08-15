package com.notacostume.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.notacostume.app.databinding.FragmentTokoBinding

class TokoFragment : Fragment() {

    private var _b: FragmentTokoBinding? = null
    private val b get() = _b!!

    private val adapter = TokoAdapter(
        onAktifkan = { toko -> aktifkanToko(toko) },
        onEdit = { toko -> showTokoDialog(toko) },
        onDelete = { toko -> confirmDelete(toko) }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentTokoBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b.rvToko.layoutManager = LinearLayoutManager(requireContext())
        b.rvToko.adapter = adapter

        b.fabAdd.setOnClickListener { showTokoDialog(null) }

        TokoManager.ensureDefault(requireContext())
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    fun refresh() {
        adapter.submit(TokoManager.getAll(requireContext()))
    }

    private fun showTokoDialog(toko: Toko?) {
        val isEdit = toko != null
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_toko, null)
        val etNama = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etNamaToko)
        val etAlamat = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etAlamatToko)
        val etTelepon = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etTeleponToko)

        if (isEdit) {
            etNama.setText(toko!!.nama)
            etAlamat.setText(toko!!.alamat)
            etTelepon.setText(toko!!.telepon)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (isEdit) R.string.dialog_toko_edit else R.string.dialog_toko_add)
            .setView(view)
            .setPositiveButton(if (isEdit) R.string.btn_simpan else R.string.dialog_toko_add) { _, _ ->
                val nama = etNama.text.toString().trim()
                if (nama.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.label_nama_toko, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val newToko = Toko(
                    id = if (isEdit) toko!!.id else 0L,
                    nama = nama,
                    alamat = etAlamat.text.toString().trim(),
                    telepon = etTelepon.text.toString().trim(),
                    isActive = if (isEdit) toko!!.isActive else false
                )
                if (isEdit) TokoManager.update(requireContext(), newToko)
                else TokoManager.add(requireContext(), newToko)
                refresh()
                Toast.makeText(requireContext(), if (isEdit) R.string.nota_diubah else R.string.nota_disimpan, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.batal, null)
            .show()
    }

    private fun aktifkanToko(toko: Toko) {
        TokoManager.setActive(requireContext(), toko.id)
        refresh()
        Toast.makeText(requireContext(), getString(R.string.toko_aktif), Toast.LENGTH_SHORT).show()
    }

    private fun confirmDelete(toko: Toko) {
        if (TokoManager.getAll(requireContext()).size <= 1) {
            Toast.makeText(requireContext(), R.string.minimal_satu_toko, Toast.LENGTH_SHORT).show()
            return
        }
        if (toko.isActive) {
            Toast.makeText(requireContext(), "Toko aktif tidak bisa dihapus. Aktifkan toko lain dulu.", Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(toko.nama)
            .setMessage(R.string.hapus_toko_konfirmasi)
            .setPositiveButton(R.string.ya) { _, _ ->
                TokoManager.delete(requireContext(), toko.id)
                refresh()
                Toast.makeText(requireContext(), "Toko dihapus", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.batal, null)
            .show()
    }
}