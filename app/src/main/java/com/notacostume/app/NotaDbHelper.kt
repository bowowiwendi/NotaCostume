package com.notacostume.app

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class NotaDbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        createTables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Pastikan kolom selalu ada walau DB lama (tanpa guard versi)
        for (col in arrayOf("nama_penjual TEXT", "ttd_penjual TEXT", "foto TEXT")) {
            try {
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN $col")
            } catch (_: Exception) {
                // kolom sudah ada -> abaikan
            }
        }
    }

    private fun createTables(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE $TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                nomor TEXT,
                toko TEXT,
                tanggal TEXT,
                catatan TEXT,
                dibuat_pada INTEGER,
                nama_penjual TEXT,
                ttd_penjual TEXT,
                foto TEXT
            )"""
        )
        db.execSQL(
            """CREATE TABLE $TABLE_ITEM (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                nota_id INTEGER,
                nama TEXT,
                jumlah INTEGER,
                harga INTEGER
            )"""
        )
    }

    fun insert(nota: Nota): Long {
        val db = writableDatabase
        db.beginTransaction()
        val nid: Long
        try {
            nid = db.insert(TABLE, null, nota.toValues())
            nota.items.forEach { item ->
                db.insert(TABLE_ITEM, null, item.toValues(nid))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return nid
    }

    fun update(nota: Nota) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.update(TABLE, nota.toValues(), "id = ?", arrayOf(nota.id.toString()))
            db.delete(TABLE_ITEM, "nota_id = ?", arrayOf(nota.id.toString()))
            nota.items.forEach { item ->
                db.insert(TABLE_ITEM, null, item.toValues(nota.id))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun delete(id: Long): Int {
        val db = writableDatabase
        var rows: Int
        db.beginTransaction()
        try {
            rows = db.delete(TABLE_ITEM, "nota_id = ?", arrayOf(id.toString()))
            rows += db.delete(TABLE, "id = ?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return rows
    }

    fun getById(id: Long): Nota? {
        val db = readableDatabase
        db.query(TABLE, null, "id = ?", arrayOf(id.toString()), null, null, null, "1").use { c ->
            if (!c.moveToFirst()) return null
            val nota = c.toNota().copy(items = loadItems(db, id))
            return nota
        }
    }

    fun getAll(): List<Nota> {
        val list = mutableListOf<Nota>()
        readableDatabase.query(TABLE, null, null, null, null, null, "dibuat_pada DESC").use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(c.getColumnIndexOrThrow("id"))
                list.add(c.toNota().copy(items = loadItems(readableDatabase, id)))
            }
        }
        return list
    }

    private fun loadItems(db: SQLiteDatabase, notaId: Long): List<NotaItem> {
        val items = mutableListOf<NotaItem>()
        db.query(TABLE_ITEM, null, "nota_id = ?", arrayOf(notaId.toString()), null, null, "id").use { c ->
            while (c.moveToNext()) {
                items.add(
                    NotaItem(
                        nama = c.getString(c.getColumnIndexOrThrow("nama")) ?: "",
                        jumlah = c.getInt(c.getColumnIndexOrThrow("jumlah")),
                        harga = c.getLong(c.getColumnIndexOrThrow("harga"))
                    )
                )
            }
        }
        return items
    }

    fun nextNomor(): String {
        var count = 0L
        readableDatabase.query(TABLE, arrayOf("COUNT(*)"), null, null, null, null, null).use { c ->
            if (c.moveToFirst()) count = c.getLong(0)
        }
        return "NP-${count + 1}"
    }

    private fun Nota.toValues() = ContentValues().apply {
        put("nomor", nomor)
        put("toko", toko)
        put("tanggal", tanggal)
        put("catatan", catatan)
        put("dibuat_pada", dibuatPada)
        put("nama_penjual", namaPenjual)
        put("ttd_penjual", ttdPenjual)
        put("foto", foto)
    }

    private fun NotaItem.toValues(notaId: Long) = ContentValues().apply {
        put("nota_id", notaId)
        put("nama", nama)
        put("jumlah", jumlah)
        put("harga", harga)
    }

    private fun Cursor.toNota() = Nota(
        id = getLong(getColumnIndexOrThrow("id")),
        nomor = getString(getColumnIndexOrThrow("nomor")) ?: "",
        toko = getString(getColumnIndexOrThrow("toko")) ?: "",
        tanggal = getString(getColumnIndexOrThrow("tanggal")) ?: "",
        catatan = getString(getColumnIndexOrThrow("catatan")) ?: "",
        dibuatPada = getLong(getColumnIndexOrThrow("dibuat_pada")),
        namaPenjual = getString(getColumnIndexOrThrow("nama_penjual")) ?: "",
        ttdPenjual = getString(getColumnIndexOrThrow("ttd_penjual")) ?: "",
        foto = getString(getColumnIndexOrThrow("foto")) ?: ""
    )

    companion object {
        private const val DB_NAME = "nota.db"
        private const val DB_VERSION = 5
        private const val TABLE = "nota"
        private const val TABLE_ITEM = "nota_item"
    }
}
