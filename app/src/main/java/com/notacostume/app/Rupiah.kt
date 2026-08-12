package com.notacostume.app

import java.text.NumberFormat
import java.util.Locale

object Rupiah {
    private val fmt: NumberFormat = NumberFormat.getNumberInstance(Locale("id", "ID"))

    fun format(value: Long): String = "Rp ${fmt.format(value)}"

    fun parse(text: String?): Long =
        text?.replace(Regex("[^\\d]"), "")?.toLongOrNull() ?: 0L

    fun formatTanggal(tgl: String): String = tgl
}
