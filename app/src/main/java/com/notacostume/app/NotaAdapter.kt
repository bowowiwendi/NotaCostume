package com.notacostume.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.notacostume.app.databinding.ItemNotaBinding

class NotaAdapter(
    private val onClick: (Nota) -> Unit,
    private val onEdit: (Nota) -> Unit,
    private val onDelete: (Nota) -> Unit
) : RecyclerView.Adapter<NotaAdapter.VH>() {

    private val items = mutableListOf<Nota>()

    fun submit(list: List<Nota>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemNotaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class VH(private val b: ItemNotaBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(nota: Nota) {
            b.tvNomor.text = "${nota.nomor}  •  ${nota.tanggal}"
            b.tvPelanggan.text = nota.deskripsi.ifBlank { nota.toko }
            b.tvCostume.text = "${nota.jumlahBarang} item" + if (nota.toko.isNotBlank()) " dari ${nota.toko}" else ""
            b.tvInfo.text = Rupiah.format(nota.total)
            b.root.setOnClickListener { onClick(nota) }
            b.btnEdit.setOnClickListener { onEdit(nota) }
            b.btnHapus.setOnClickListener { onDelete(nota) }
        }
    }
}
