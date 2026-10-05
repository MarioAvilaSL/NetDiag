package com.diagnostico.senales.data.cellular

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.CellIdentityCdma
import android.telephony.CellIdentityGsm
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellIdentityWcdma
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrength
import android.telephony.CellSignalStrengthCdma
import android.telephony.CellSignalStrengthGsm
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.CellSignalStrengthWcdma
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.diagnostico.senales.core.permission.AppPermissions
import com.diagnostico.senales.core.permission.hasAll
import com.diagnostico.senales.core.util.NO_DISPONIBLE
import com.diagnostico.senales.core.util.dbmToPercent
import com.diagnostico.senales.data.model.CellularSnapshot
import com.diagnostico.senales.data.model.SimSlotInfo

/**
 * Operador, SIM y celda en la que está el dispositivo.
 *
 * Solo se leen campos públicos: en ningún caso el IMSI, el ICCID, el número de
 * línea ni las claves de la SIM, que Android no expone a las apps normales.
 */
class CellularRepository(context: Context) {

    private val appContext = context.applicationContext
    private val telephonyManager =
        appContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val subscriptionManager =
        appContext.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private data class CellDetails(
        val typeLabel: String,
        val cellId: String?,
        val tac: Int?,
        val pci: Int?,
        val frequencyMhz: Int?,
        val frequencyNote: String?,
        val rsrp: Int?,
        val rsrq: Int?,
        val sinr: Double?,
        val rssi: Int?,
        val dbm: Int?,
        val level: Int?,
        val isRegistered: Boolean,
    )

    fun telephonySupported(): Boolean =
        appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)

    fun canReadTelephony(): Boolean = appContext.hasAll(AppPermissions.telephony)

    fun hasActiveSubscription(): Boolean = activeSubscriptions().isNotEmpty()

    fun snapshot(): CellularSnapshot {
        val supported = telephonySupported()
        val notes = mutableListOf<String>()
        val subscriptions = activeSubscriptions()
        val dataSubId = activeDataSubscriptionId(subscriptions)
        val manager = dataSubId?.let { createForSubscription(it) } ?: telephonyManager

        val slots = subscriptions.map { info ->
            val state = simStateLabel(info.simSlotIndex)
            SimSlotInfo(
                slotIndex = info.simSlotIndex,
                slotLabel = "SIM ${info.simSlotIndex + 1}",
                state = state,
                // `SubscriptionInfo.getSimState()` no es público y `SIM_STATE_ACTIVE`
                // tampoco, así que la actividad se deduce del estado publicado.
                isActive = state == "Activa y preparada",
                isDataSubscription = info.subscriptionId == dataSubId,
                carrierName = info.carrierName?.toString()?.takeIf { it.isNotBlank() },
                countryIso = info.countryIso?.takeIf { it.isNotBlank() },
                mcc = info.mccString?.takeIf { it.isNotBlank() },
                mnc = info.mncString?.takeIf { it.isNotBlank() },
                isEmbedded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    runCatching { info.isEmbedded }.getOrNull()
                } else {
                    null
                },
            )
        }

        if (!canReadTelephony()) {
            notes += "Falta el permiso de estado del teléfono: sin él Android no revela " +
                "operador, SIM ni tipo de red."
        }
        if (!appContext.hasAll(AppPermissions.location)) {
            notes += "Falta el permiso de ubicación: los datos de la celda (Cell ID, TAC, " +
                "RSRP, RSRQ y SINR) solo se publican con ubicación."
        }
        if (!supported) notes += "Este dispositivo no tiene módem de telefonía."

        val networkType = networkTypeOf(manager)
        val cell = readCellDetails(notes)
        val dataSlotIndex = subscriptions.firstOrNull { it.subscriptionId == dataSubId }?.simSlotIndex
            ?: subscriptions.firstOrNull()?.simSlotIndex
            ?: 0
        val dataSubscription = subscriptions.firstOrNull { it.subscriptionId == dataSubId }
            ?: subscriptions.firstOrNull()

        return CellularSnapshot(
            telephonySupported = supported,
            hasActiveSubscription = subscriptions.isNotEmpty(),
            dataSubscriptionLabel = if (dataSubId != null) {
                "SIM ${dataSlotIndex + 1}"
            } else {
                NO_DISPONIBLE
            },
            simState = simStateLabel(dataSlotIndex),
            slots = slots,
            operatorName = readOperatorName(subscriptions, dataSubId),
            countryIso = dataSubscription?.countryIso,
            mcc = dataSubscription?.mccString,
            mnc = dataSubscription?.mncString,
            networkTypeLabel = networkTypeLabel(networkType),
            networkGeneration = networkGeneration(networkType),
            signalDbm = cell?.dbm,
            signalQualityPercent = cell?.dbm?.let { dbmToPercent(it) },
            signalLevelLabel = cell?.level?.let { signalLevelLabel(it) },
            rsrp = cell?.rsrp,
            rsrq = cell?.rsrq,
            sinr = cell?.sinr,
            rssi = cell?.rssi,
            cellId = cell?.cellId,
            tac = cell?.tac,
            pci = cell?.pci,
            frequencyMhz = cell?.frequencyMhz,
            cellTypeLabel = cell?.typeLabel,
            isRoaming = readRoaming(manager),
            dataEnabled = runCatching { manager.isDataEnabled }.getOrNull(),
            dataState = dataStateLabel(manager),
            dataActivityLabel = dataActivityLabel(),
            capturedAt = System.currentTimeMillis(),
            unavailableNotes = notes,
        )
    }

    /** Datos de la celda en uso: requieren permiso de ubicación. */
    private fun readCellDetails(notes: MutableList<String>): CellDetails? {
        val allCellInfo = try {
            telephonyManager.allCellInfo
        } catch (_: SecurityException) {
            notes += "El sistema ha bloqueado la lectura de la celda por falta de ubicación."
            null
        } catch (_: Exception) {
            null
        } ?: return null
        if (allCellInfo.isEmpty()) {
            notes += "El módem no ha registrado ninguna celda todavía."
            return null
        }

        val chosen = allCellInfo.firstOrNull { it.registered() } ?: allCellInfo.first()
        val details = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && chosen is CellInfoNr ->
                cellDetailsFromNr(chosen)

            chosen is CellInfoLte -> cellDetailsFromLte(chosen)
            chosen is CellInfoWcdma -> cellDetailsFromWcdma(chosen)
            chosen is CellInfoGsm -> cellDetailsFromGsm(chosen)
            chosen is CellInfoCdma -> cellDetailsFromCdma(chosen)
            else -> null
        }
        if (details == null) {
            notes += "El módem no ha publicado medidas de señal utilizables para esta celda."
        } else {
            details.frequencyNote?.let { notes += it }
        }
        return details
    }

    private fun cellDetailsFromNr(info: CellInfoNr): CellDetails? {
        // Las medidas de señal NR (SS-RSRP, SS-RSRQ y SS-SINR) son de Android 10 en adelante.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val identity = info.cellIdentity as? CellIdentityNr ?: return null
        val signal = info.cellSignalStrength as? CellSignalStrengthNr ?: return null
        val nrarfcn = validInt(identity.nrarfcn)
        val frequency = nrarfcn?.let { CellFrequency.nrDownlinkMhz(it) }
        return CellDetails(
            typeLabel = "5G NR",
            cellId = validLong(identity.nci)?.toString(),
            tac = validInt(identity.tac),
            pci = validInt(identity.pci),
            frequencyMhz = frequency?.toInt(),
            frequencyNote = if (frequency != null) {
                "Frecuencia aproximada a partir del NR-ARFCN."
            } else {
                null
            },
            rsrp = validInt(signal.ssRsrp),
            rsrq = validInt(signal.ssRsrq),
            // El sistema publica el SINR NR en centésimas de dB: 410 significa 41 dB.
            sinr = validInt(signal.ssSinr)?.let { it / 100.0 },
            rssi = null,
            dbm = validInt(signal.dbm),
            level = validInt(signal.level),
            isRegistered = info.registered(),
        )
    }

    private fun cellDetailsFromLte(info: CellInfoLte): CellDetails? {
        val identity = (info.cellIdentity as? CellIdentityLte) ?: return null
        val signal = (info.cellSignalStrength as? CellSignalStrengthLte) ?: return null
        val earfcn = validInt(identity.earfcn)
        val frequency = earfcn?.let { CellFrequency.lteDownlinkMhz(it) }
        return CellDetails(
            typeLabel = "LTE / 4G",
            cellId = validInt(identity.ci)?.toString(),
            tac = validInt(identity.tac),
            pci = validInt(identity.pci),
            frequencyMhz = frequency?.toInt(),
            frequencyNote = if (frequency != null) {
                "Frecuencia aproximada a partir del EARFCN."
            } else {
                null
            },
            rsrp = validInt(signal.rsrp),
            rsrq = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                validInt(signal.rsrq)
            } else {
                null
            },
            sinr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // RSRQ-SNR llega en décimas de dB: 75 significa 7,5 dB.
                validInt(signal.rssnr)?.let { it / 10.0 }
            } else {
                null
            },
            rssi = validInt(signal.rssi),
            dbm = validInt(signal.dbm),
            level = validInt(signal.level),
            isRegistered = info.registered(),
        )
    }

    private fun cellDetailsFromWcdma(info: CellInfoWcdma): CellDetails? {
        val identity = (info.cellIdentity as? CellIdentityWcdma) ?: return null
        val signal = (info.cellSignalStrength as? CellSignalStrengthWcdma) ?: return null
        return CellDetails(
            typeLabel = "UMTS / 3G",
            cellId = validInt(identity.cid)?.toString(),
            tac = validInt(identity.lac),
            pci = validInt(identity.psc),
            frequencyMhz = null,
            frequencyNote = "Android no publica un valor fiable de UARFCN en MHz para 3G.",
            rsrp = null,
            rsrq = null,
            sinr = validInt(signal.ecNo)?.takeIf { it > 0 }?.let { it / 10.0 },
            rssi = null,
            dbm = validInt(signal.dbm),
            level = validInt(signal.level),
            isRegistered = info.registered(),
        )
    }

    private fun cellDetailsFromGsm(info: CellInfoGsm): CellDetails? {
        val identity = (info.cellIdentity as? CellIdentityGsm) ?: return null
        val signal = (info.cellSignalStrength as? CellSignalStrengthGsm) ?: return null
        val arfcn = validInt(identity.arfcn)
        return CellDetails(
            typeLabel = "GSM / 2G",
            cellId = validInt(identity.cid)?.toString(),
            tac = validInt(identity.lac),
            pci = validInt(identity.psc),
            frequencyMhz = arfcn?.let { gsmDownlinkMhz(it) },
            frequencyNote = if (arfcn != null) {
                "Frecuencia estimada a partir del ARFCN; Android no publica la banda GSM."
            } else {
                null
            },
            rsrp = null,
            rsrq = null,
            sinr = null,
            rssi = validInt(signal.rssi),
            dbm = validInt(signal.dbm),
            level = validInt(signal.level),
            isRegistered = info.registered(),
        )
    }

    private fun cellDetailsFromCdma(info: CellInfoCdma): CellDetails? {
        val identity = info.cellIdentity as? CellIdentityCdma ?: return null
        val signal = info.cellSignalStrength as? CellSignalStrengthCdma
        return CellDetails(
            typeLabel = "CDMA / 2G",
            cellId = validInt(identity.basestationId)?.let { "0x${it.toString(16)}" },
            tac = validInt(identity.systemId),
            pci = validInt(identity.networkId),
            frequencyMhz = null,
            frequencyNote = "CDMA no publica la frecuencia de la portadora.",
            rsrp = null,
            rsrq = null,
            sinr = validInt(signal?.evdoSnr)?.let { it / 10.0 },
            rssi = null,
            dbm = validInt(signal?.cdmaDbm) ?: validInt(signal?.evdoDbm),
            level = validInt(signal?.level),
            isRegistered = info.registered(),
        )
    }

    // -- Utilidades de rango válido ------------------------------------------

    /** Los valores no disponibles llegan como enteros absurdos; se descartan. */
    private fun validInt(value: Int): Int? =
        value.takeIf { it != CellInfo.UNAVAILABLE && it != Int.MAX_VALUE && it != Int.MIN_VALUE }

    private fun validInt(value: Int?): Int? = value?.let { validInt(it) }

    private fun validLong(value: Long): Long? =
        value.takeIf { it != CellInfo.UNAVAILABLE_LONG && it != Long.MAX_VALUE }

    private fun CellInfo.registered(): Boolean = runCatching { isRegistered() }.getOrDefault(false)

    /** Estimación de la portadora GSM a partir del ARFCN (900 y 1800 MHz). */
    private fun gsmDownlinkMhz(arfcn: Int): Int? = when {
        arfcn in 0..124 -> 890 + 2 * arfcn
        arfcn in 512..885 -> (1710.2 + 0.2 * (arfcn - 512)).toInt()
        else -> null
    }

    // -- Suscripciones -------------------------------------------------------

    private fun activeSubscriptions(): List<SubscriptionInfo> = try {
        subscriptionManager.activeSubscriptionInfoList?.toList().orEmpty()
    } catch (_: SecurityException) {
        emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    private fun activeDataSubscriptionId(subscriptions: List<SubscriptionInfo>): Int? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val id = runCatching { SubscriptionManager.getActiveDataSubscriptionId() }.getOrNull()
            if (id != null && id != SubscriptionManager.INVALID_SUBSCRIPTION_ID) return id
        }
        return subscriptions.firstOrNull()?.subscriptionId
    }

    private fun createForSubscription(subscriptionId: Int): TelephonyManager = try {
        telephonyManager.createForSubscriptionId(subscriptionId)
    } catch (_: Exception) {
        telephonyManager
    }

    private fun readOperatorName(
        subscriptions: List<SubscriptionInfo>,
        dataSubId: Int?,
    ): String? {
        subscriptions.firstOrNull { it.subscriptionId == dataSubId }
            ?.carrierName
            ?.toString()
            ?.takeIf { it.isNotBlank() && it != UNKNOWN_OPERATOR }
            ?.let { return it }
        val fromManager = runCatching {
            val manager = dataSubId?.let { createForSubscription(it) } ?: telephonyManager
            manager.networkOperatorName
        }.getOrNull()
        return fromManager?.takeIf { it.isNotBlank() && it != UNKNOWN_OPERATOR }
    }

    private fun networkTypeOf(manager: TelephonyManager): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { manager.dataNetworkType }.getOrNull()
                ?.takeIf { it != TelephonyManager.NETWORK_TYPE_UNKNOWN }
                ?: TelephonyManager.NETWORK_TYPE_UNKNOWN
        } else {
            runCatching { manager.networkType }.getOrNull()
                ?: TelephonyManager.NETWORK_TYPE_UNKNOWN
        }

    private fun simStateLabel(slotIndex: Int): String {
        val state = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { telephonyManager.getSimState(slotIndex) }.getOrNull()
        } else {
            runCatching { telephonyManager.simState }.getOrNull()
        }
        return when (state) {
            // `SIM_STATE_ACTIVE` no es público; `SIM_STATE_READY` comparte valor y uso.
            TelephonyManager.SIM_STATE_READY -> "Activa y preparada"
            TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "Bloqueada por red"
            TelephonyManager.SIM_STATE_PIN_REQUIRED -> "Requiere PIN"
            TelephonyManager.SIM_STATE_PUK_REQUIRED -> "Requiere PUK"
            TelephonyManager.SIM_STATE_ABSENT -> "Ausente"
            TelephonyManager.SIM_STATE_PERM_DISABLED -> "Deshabilitada por el fabricante"
            TelephonyManager.SIM_STATE_CARD_IO_ERROR -> "Error de lectura de la SIM"
            TelephonyManager.SIM_STATE_CARD_RESTRICTED -> "Restringida"
            TelephonyManager.SIM_STATE_NOT_READY -> "No preparada"
            else -> NO_DISPONIBLE
        }
    }

    private fun readRoaming(manager: TelephonyManager): Boolean? =
        runCatching { manager.isNetworkRoaming }.getOrNull()

    private fun dataStateLabel(manager: TelephonyManager): String =
        when (runCatching { manager.dataState }.getOrNull()) {
            TelephonyManager.DATA_DISCONNECTED -> "Desconectada"
            TelephonyManager.DATA_CONNECTING -> "Conectando"
            TelephonyManager.DATA_CONNECTED -> "Conectada"
            TelephonyManager.DATA_SUSPENDED -> "Suspendida"
            else -> NO_DISPONIBLE
        }

    private fun dataActivityLabel(): String? {
        val capabilities = runCatching { connectivityManager.activeNetwork }.getOrNull()
            ?.let { runCatching { connectivityManager.getNetworkCapabilities(it) }.getOrNull() }
            ?: return null
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return null
        return if (runCatching { telephonyManager.dataActivity }.getOrNull() != 0) {
            "Datos móviles en uso"
        } else {
            "Red móvil disponible, sin tráfico ahora mismo"
        }
    }

    // -- Etiquetas -----------------------------------------------------------

    private fun signalLevelLabel(level: Int): String = when (level) {
        4 -> "Muy buena"
        3 -> "Buena"
        2 -> "Regular"
        1 -> "Débil"
        0 -> "Sin cobertura o desconocida"
        else -> NO_DISPONIBLE
    }

    private fun networkGeneration(networkType: Int): String = when (networkType) {
        TelephonyManager.NETWORK_TYPE_NR -> "5G"
        TelephonyManager.NETWORK_TYPE_LTE -> "4G"
        TelephonyManager.NETWORK_TYPE_HSPAP,
        TelephonyManager.NETWORK_TYPE_HSPA,
        TelephonyManager.NETWORK_TYPE_HSDPA,
        TelephonyManager.NETWORK_TYPE_HSUPA,
        TelephonyManager.NETWORK_TYPE_UMTS,
        -> "3G"

        TelephonyManager.NETWORK_TYPE_GSM,
        TelephonyManager.NETWORK_TYPE_EDGE,
        TelephonyManager.NETWORK_TYPE_GPRS,
        TelephonyManager.NETWORK_TYPE_CDMA,
        TelephonyManager.NETWORK_TYPE_1xRTT,
        TelephonyManager.NETWORK_TYPE_IDEN,
        -> "2G"

        else -> NO_DISPONIBLE
    }

    private fun networkTypeLabel(networkType: Int): String = when (networkType) {
        TelephonyManager.NETWORK_TYPE_NR -> "NR (5G)"
        TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
        TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPA+"
        TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA"
        TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA"
        TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA"
        TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS"
        TelephonyManager.NETWORK_TYPE_GSM -> "GSM"
        TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
        TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
        TelephonyManager.NETWORK_TYPE_CDMA -> "CDMA"
        TelephonyManager.NETWORK_TYPE_1xRTT -> "1xRTT"
        else -> NO_DISPONIBLE
    }

    private companion object {
        const val UNKNOWN_OPERATOR = ""
    }
}