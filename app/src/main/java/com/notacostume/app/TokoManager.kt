package com.notacostume.app

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object TokoManager {

    private const val PREFS_NAME = "toko_prefs"
    private const val KEY_TOKO_LIST = "toko_list"
    private const val KEY_ACTIVE_ID = "active_toko_id"

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val gson = Gson()
    private val typeToken = object : TypeToken<List<Toko>>() {}.type

    fun getAll(context: Context): List<Toko> {
        val json = getPrefs(context).getString(KEY_TOKO_LIST, null)
        return if (json != null) gson.fromJson(json, typeToken) else emptyList()
    }

    fun saveAll(context: Context, list: List<Toko>) {
        getPrefs(context).edit().putString(KEY_TOKO_LIST, gson.toJson(list)).apply()
    }

    fun add(context: Context, toko: Toko): Toko {
        val list = getAll(context).toMutableList()
        val newId = if (list.isEmpty()) 1L else list.maxByOrNull { it.id }!!.id + 1
        val newToko = toko.copy(id = newId, isActive = list.isEmpty())
        list.add(newToko)
        saveAll(context, list)
        if (newToko.isActive) setActive(context, newId)
        return newToko
    }

    fun update(context: Context, toko: Toko) {
        val list = getAll(context).toMutableList()
        val idx = list.indexOfFirst { it.id == toko.id }
        if (idx >= 0) {
            list[idx] = toko
            saveAll(context, list)
        }
    }

    fun delete(context: Context, id: Long) {
        val list = getAll(context).filter { it.id != id }.toMutableList()
        val activeId = getActiveId(context)
        if (activeId == id && list.isNotEmpty()) {
            list[0] = list[0].copy(isActive = true)
            getPrefs(context).edit().putLong(KEY_ACTIVE_ID, list[0].id).apply()
        }
        saveAll(context, list)
    }

    fun getActive(context: Context): Toko {
        val activeId = getActiveId(context)
        return getAll(context).firstOrNull { it.id == activeId } ?: Toko.DEFAULT
    }

    fun getActiveId(context: Context): Long {
        return getPrefs(context).getLong(KEY_ACTIVE_ID, 0L)
    }

    fun setActive(context: Context, id: Long) {
        val list = getAll(context).toMutableList()
        val wasActive = list.find { it.isActive }
        wasActive?.let { i -> list[list.indexOf(i)] = i.copy(isActive = false) }
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) list[idx] = list[idx].copy(isActive = true)
        saveAll(context, list)
        getPrefs(context).edit().putLong(KEY_ACTIVE_ID, id).apply()
    }

    fun ensureDefault(context: Context) {
        if (getAll(context).isEmpty()) {
            add(context, Toko.DEFAULT.copy(isActive = true))
        }
    }
}