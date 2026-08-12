package com.notacostume.app

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.notacostume.app.databinding.FragmentRiwayatBinding

class RiwayatFragment : Fragment() {

    private var _b: FragmentRiwayatBinding? = null
    private val b get() = _b!!

    private val db by lazy { NotaDbHelper(requireContext()) }
    private val adapter = NotaAdapter(
        onClick = { nota ->
            startActivity(Intent(requireContext(), NotaDetailActivity::class.java).putExtra("id", nota.id))
        },
        onEdit = { nota ->
            startActivity(Intent(requireContext(), EditNotaActivity::class.java).putExtra("id", nota.id))
        },
        onDelete = { nota -> confirmDelete(nota) }
    )

    private var allNotas: List<Nota> = emptyList()
    private var query: String = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentRiwayatBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b.rvRiwayat.layoutManager = LinearLayoutManager(requireContext())
        b.rvRiwayat.adapter = adapter

        b.btnExport.setOnClickListener { exportCsv() }

        b.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString()?.trim().orEmpty()
                applyFilter()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
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
        allNotas = db.getAll()
        applyFilter()
    }

    private fun applyFilter() {
        val q = query.lowercase()
        val list = if (q.isEmpty()) {
            allNotas
        } else {
            allNotas.filter { n ->
                n.nomor.lowercase().contains(q) ||
                    n.toko.lowercase().contains(q) ||
                    n.tanggal.lowercase().contains(q) ||
                    n.deskripsi.lowercase().contains(q)
            }
        }
        b.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        adapter.submit(list)
    }

    private fun confirmDelete(nota: Nota) {
        AlertDialog.Builder(requireContext())
            .setTitle(nota.nomor)
            .setMessage(R.string.hapus_konfirmasi)
            .setPositiveButton(R.string.ya) { _, _ ->
                db.delete(nota.id)
                Toast.makeText(requireContext(), R.string.nota_terhapus, Toast.LENGTH_SHORT).show()
                refresh()
            }
            .setNegativeButton(R.string.batal, null)
            .show()
    }

    private fun exportCsv() {
        val name = CsvExporter.export(requireContext(), db.getAll())
        if (name != null) {
            Toast.makeText(requireContext(), getString(R.string.export_done, name), Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(requireContext(), R.string.export_empty, Toast.LENGTH_SHORT).show()
        }
    }
}
