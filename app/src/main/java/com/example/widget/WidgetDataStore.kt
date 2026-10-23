package com.example.widget

import android.content.Context
import com.example.ui.screens.scan_sms.ScannedSmsItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object WidgetDataStore {
    private const val PREFS_NAME = "jeebban_widget_cache_prefs"
    private const val KEY_ITEMS = "cached_unregistered_items"
    private val gson = Gson()

    @Synchronized
    fun saveItems(context: Context, items: List<ScannedSmsItem>) {
        try {
            val json = gson.toJson(items)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_ITEMS, json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun getItems(context: Context): List<ScannedSmsItem> {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
            val type = object : TypeToken<List<ScannedSmsItem>>() {}.type
            gson.fromJson<List<ScannedSmsItem>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    @Synchronized
    fun removeItem(context: Context, smsHash: String): List<ScannedSmsItem> {
        if (smsHash.isBlank()) return getItems(context)
        val targetHashes = smsHash.split("|").map { it.trim() }.filter { it.isNotBlank() }.toSet()
        val current = getItems(context).toMutableList()
        current.removeAll { item ->
            item.smsHash == smsHash || targetHashes.contains(item.smsHash) || smsHash.contains(item.smsHash)
        }
        saveItems(context, current)
        return current
    }

    @Synchronized
    fun clearItems(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove(KEY_ITEMS).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
