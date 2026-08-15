package com.notacostume.app

data class Toko(
    val id: Long = 0L,
    val nama: String = "",
    val alamat: String = "",
    val telepon: String = "",
    val isActive: Boolean = false
) {
    companion object {
        val DEFAULT = Toko(nama = "TOKO COSTUME", alamat = "Jl. Contoh Raya No. 123, Kota")
    }
}