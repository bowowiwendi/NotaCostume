package com.notacostume.app

data class Barang(
    val id: Long = 0L,
    val barcode: String = "",
    val nama: String = "",
    val harga: Long = 0L,
    val stok: Int = 0,
    val kategori: String = "",
    val ditambahkanPada: Long = System.currentTimeMillis()
)
