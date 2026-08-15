package com.notacostume.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.notacostume.app.databinding.ItemTokoBinding

class TokoAdapter(
    private val onAktifkan: (Toko) -> Unit,
    private val onEdit: (Toko) -> Unit,
    private val onDelete: (Toko) -> Unit
) : RecyclerView.Adapter<TokoAdapter.VH>() {

    private val items = mutableListOf<Toko>()

    fun submit(list: List<Toko>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemTokoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class VH(private val b: ItemTokoBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(toko: Toko) {
            b.tvNamaToko.text = toko.nama
            b.tvAlamatToko.text = if (toko.alamat.isNotBlank()) toko.alamat else "-"
            b.tvTeleponToko.text = if (toko.telepon.isNotBlank()) toko.telepon else "-"
            
            if (toko.isActive) {
                b.tvStatusToko.text = R.string.toko_aktif
                b.tvStatusToko.setTextColor(b.root.context.getColor(com.google.android.material.R.color.design_default_color_primary))
                b.tvStatusToko.background = b.root.context.getDrawable(R.drawable.bg_chip)
            } else {
                b.tvStatusToko.text = R.string.toko_tidak_aktif
                b.tvStatusToko.setTextColor(b.root.context.getColor(com.google.android.material.R.color.design_default_color_on_surface_variant))
                b.tvStatusToko.background = b.root.context.getDrawable(R.drawable.bg_chip)
            }

            b.btnAktifkan.setOnClickListener { onAktifkan(toko) }
            b.btnEdit.setOnClickListener { onEdit(toko) }
            b.btnHapus.setOnClickListener { onDelete(toko) }
        }
    }
}