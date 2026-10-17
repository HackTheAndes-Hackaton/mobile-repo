package com.tiskz.app.data

import android.content.Context
import com.tiskz.app.util.PhoneNumbers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Números bloqueados localmente. Los usa el filtro de llamadas antes de que suene el teléfono. */
class BlocklistRepository(context: Context) {

    private val prefs = context.getSharedPreferences("tiskz_blocklist", Context.MODE_PRIVATE)
    private val _blocked = MutableStateFlow(prefs.getStringSet(KEY, emptySet()).orEmpty().toSet())
    val blocked: StateFlow<Set<String>> = _blocked.asStateFlow()

    fun isBlocked(number: String): Boolean {
        val key = PhoneNumbers.matchKey(number)
        return key.isNotEmpty() && key in _blocked.value
    }

    fun block(number: String) {
        val key = PhoneNumbers.matchKey(number)
        if (key.isEmpty()) return
        save(_blocked.value + key)
    }

    fun clear() = save(emptySet())

    private fun save(numbers: Set<String>) {
        _blocked.value = numbers
        prefs.edit().putStringSet(KEY, numbers).apply()
    }

    private companion object {
        const val KEY = "numbers"
    }
}
