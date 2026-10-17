package com.tiskz.app.data

import android.content.Context
import com.tiskz.app.model.CallRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray

class HistoryRepository(context: Context) {

    private val prefs = context.getSharedPreferences("tiskz_history", Context.MODE_PRIVATE)
    private val _records = MutableStateFlow(load())
    val records: StateFlow<List<CallRecord>> = _records.asStateFlow()

    fun add(record: CallRecord) {
        _records.update { (listOf(record) + it).take(MAX_RECORDS) }
        persist()
    }

    fun find(id: String): CallRecord? = _records.value.firstOrNull { it.id == id }

    fun clear() {
        _records.value = emptyList()
        persist()
    }

    private fun load(): List<CallRecord> = runCatching {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        List(array.length()) { CallRecord.fromJson(array.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun persist() {
        val array = JSONArray()
        _records.value.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "records"
        const val MAX_RECORDS = 100
    }
}
