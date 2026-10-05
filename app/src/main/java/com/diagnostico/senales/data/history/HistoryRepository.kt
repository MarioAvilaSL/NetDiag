package com.diagnostico.senales.data.history

import android.content.Context
import com.diagnostico.senales.data.model.HistoryRecord
import com.diagnostico.senales.data.model.toHistoryList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

/**
 * Historial de pruebas en un único JSON dentro de `filesDir`.
 *
 * Se evita Room a propósito: el esquema es una lista plana de métricas, no hay
 * relaciones, y así no hace falta KSP ni generación de código. Los datos nunca
 * salen del dispositivo.
 */
class HistoryRepository(context: Context) {

    private val file = File(context.applicationContext.filesDir, FILE_NAME)
    private val writeMutex = Mutex()
    private val _records = MutableStateFlow<List<HistoryRecord>>(emptyList())
    val records: StateFlow<List<HistoryRecord>> = _records.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        val loaded = readFromDisk()
        _records.value = loaded
    }

    suspend fun add(record: HistoryRecord) {
        writeMutex.withLock {
            val updated = (listOf(record) + _records.value)
                .sortedByDescending { it.timestampMillis }
                .take(MAX_RECORDS)
            _records.value = updated
            persist(updated)
        }
    }

    suspend fun delete(id: Long) {
        writeMutex.withLock {
            val updated = _records.value.filterNot { it.id == id }
            _records.value = updated
            persist(updated)
        }
    }

    suspend fun clear() {
        writeMutex.withLock {
            _records.value = emptyList()
            persist(emptyList())
        }
    }

    fun nextId(): Long = (System.currentTimeMillis() * 1000L) +
        (0 until 1000).random()

    private suspend fun persist(records: List<HistoryRecord>) = withContext(Dispatchers.IO) {
        runCatching {
            val array = JSONArray()
            records.forEach { array.put(it.toJson()) }
            val temporary = File(file.parentFile, "$FILE_NAME.tmp")
            temporary.writeText(array.toString())
            if (file.exists()) file.delete()
            temporary.renameTo(file)
        }
    }

    private fun readFromDisk(): List<HistoryRecord> = runCatching {
        if (!file.exists()) return@runCatching emptyList()
        val text = file.readText()
        if (text.isBlank()) return@runCatching emptyList()
        JSONArray(text).toHistoryList().sortedByDescending { it.timestampMillis }
    }.getOrDefault(emptyList())

    private companion object {
        const val FILE_NAME = "history.json"
        const val MAX_RECORDS = 300
    }
}