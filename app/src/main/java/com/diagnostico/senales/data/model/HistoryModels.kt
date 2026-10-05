package com.diagnostico.senales.data.model

import com.diagnostico.senales.core.util.Verdict
import org.json.JSONArray
import org.json.JSONObject

enum class TestType(val label: String, val shortLabel: String) {
    WIFI_SPEED("Prueba de velocidad Wi-Fi", "Velocidad Wi-Fi"),
    WIFI_STABILITY("Estabilidad Wi-Fi", "Estabilidad"),
    CELL_SPEED("Prueba de red móvil", "Velocidad móvil"),
    GPS_ACCURACY("Precisión GNSS", "Precisión GNSS"),
    GPS_ROUTE("Recorrido GNSS", "Recorrido");

    companion object {
        fun fromKey(key: String): TestType = entries.firstOrNull { it.name == key } ?: WIFI_SPEED
    }
}

/**
 * Un registro del historial. Solo contiene métricas de diagnóstico: nunca claves
 * de Wi-Fi, credenciales ni identificadores de la SIM.
 */
data class HistoryRecord(
    val id: Long,
    val timestampMillis: Long,
    val type: TestType,
    val transportLabel: String,
    val networkName: String?,
    val downloadMbps: Double?,
    val uploadMbps: Double?,
    val pingMs: Double?,
    val jitterMs: Double?,
    val lossPercent: Double?,
    val signalDbm: Int?,
    val gpsAccuracyMeters: Double?,
    val satellitesUsed: Int?,
    val verdict: Verdict?,
    val note: String?,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("timestamp", timestampMillis)
        put("type", type.name)
        put("transport", transportLabel)
        put("network", networkName ?: JSONObject.NULL)
        put("downloadMbps", downloadMbps ?: JSONObject.NULL)
        put("uploadMbps", uploadMbps ?: JSONObject.NULL)
        put("pingMs", pingMs ?: JSONObject.NULL)
        put("jitterMs", jitterMs ?: JSONObject.NULL)
        put("lossPercent", lossPercent ?: JSONObject.NULL)
        put("signalDbm", signalDbm ?: JSONObject.NULL)
        put("gpsAccuracyMeters", gpsAccuracyMeters ?: JSONObject.NULL)
        put("satellitesUsed", satellitesUsed ?: JSONObject.NULL)
        put("verdict", verdict?.name ?: JSONObject.NULL)
        put("note", note ?: JSONObject.NULL)
    }

    companion object {
        fun fromJson(json: JSONObject): HistoryRecord = HistoryRecord(
            id = json.optLong("id", 0L),
            timestampMillis = json.optLong("timestamp", 0L),
            type = TestType.fromKey(json.optString("type", TestType.WIFI_SPEED.name)),
            transportLabel = json.optString("transport", ""),
            networkName = json.optStringOrNull("network"),
            downloadMbps = json.optDoubleOrNull("downloadMbps"),
            uploadMbps = json.optDoubleOrNull("uploadMbps"),
            pingMs = json.optDoubleOrNull("pingMs"),
            jitterMs = json.optDoubleOrNull("jitterMs"),
            lossPercent = json.optDoubleOrNull("lossPercent"),
            signalDbm = if (json.isNull("signalDbm")) null else json.optInt("signalDbm"),
            gpsAccuracyMeters = json.optDoubleOrNull("gpsAccuracyMeters"),
            satellitesUsed = if (json.isNull("satellitesUsed")) null else json.optInt("satellitesUsed"),
            verdict = json.optStringOrNull("verdict")?.let { name ->
                Verdict.entries.firstOrNull { it.name == name }
            },
            note = json.optStringOrNull("note"),
        )
    }
}

internal fun JSONObject.optStringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val value = optString(key, "")
    return value.ifBlank { null }
}

internal fun JSONObject.optDoubleOrNull(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    val value = optDouble(key, Double.NaN)
    return if (value.isNaN()) null else value
}

internal fun JSONArray.toHistoryList(): List<HistoryRecord> =
    (0 until length()).mapNotNull { index -> optJSONObject(index)?.let(HistoryRecord::fromJson) }