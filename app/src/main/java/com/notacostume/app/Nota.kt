package com.notacostume.app

data class NotaItem(
    val nama: String = "",
    val jumlah: Int = 1,
    val harga: Long = 0L
) {
    val total: Long get() = harga * jumlah
}

data class Nota(
    val id: Long = 0L,
    val nomor: String = "",
    val toko: String = "",
    val tanggal: String = "",
    val catatan: String = "",
    val dibuatPada: Long = System.currentTimeMillis(),
    val items: List<NotaItem> = emptyList()
) {
    val total: Long get() = items.sumOf { it.total }
    val jumlahBarang: Int get() = items.sumOf { it.jumlah }
    val deskripsi: String get() = items.joinToString(", ") { it.nama }
}
