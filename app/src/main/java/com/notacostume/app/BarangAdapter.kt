package com.notacostume.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BarangAdapter(
    private var items: List<Barang> = emptyList(),
    val onEdit: (Barang) -> Unit,
    val onDelete: (Barang) -> Unit
) : RecyclerView.Adapter<BarangAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvNama: TextView = view.findViewById(R.id.tvNama)
        val tvHarga: TextView = view.findViewById(R.id.tvHarga)
        val tvBarcode: TextView = view.findViewById(R.id.tvBarcode)
        val tvStok: TextView = view.findViewById(R.id.tvStok)
        val btnEdit: ImageButton = view.findViewById(R.id.btnEdit)
        val btnHapus: ImageButton = view.findViewById(R.id.btnHapus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_barang_list, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvNama.text = item.nama
        holder.tvHarga.text = Rupiah.format(item.harga)
        holder.tvBarcode.text = if (item.barcode.isNotBlank()) "Barcode: ${item.barcode}" else "Tanpa barcode"
        holder.tvStok.text = "Stok: ${item.stok}"
        holder.btnEdit.setOnClickListener { onEdit(item) }
        holder.btnHapus.setOnClickListener { onDelete(item) }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<Barang>) {
        items = newItems
        notifyDataSetChanged()
    }
}
