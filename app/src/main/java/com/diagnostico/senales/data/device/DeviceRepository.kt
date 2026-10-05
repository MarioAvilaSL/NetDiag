package com.diagnostico.senales.data.device

import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.diagnostico.senales.core.util.NO_DISPONIBLE
import com.diagnostico.senales.data.model.DeviceSnapshot
import com.diagnostico.senales.data.model.SensorInfo
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface

/**
 * Lectura de las propiedades del dispositivo. Cada campo que el sistema no
 * publica se devuelve como `null` y la interfaz lo muestra como no disponible,
 * nunca como un valor inventado.
 */
class DeviceRepository(context: Context) {

    private val appContext = context.applicationContext
    private val activityManager =
        appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val sensorManager =
        appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val batteryManager =
        appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val wifiManager =
        appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun snapshot(): DeviceSnapshot {
        val memoryInfo = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        val dataDirStat = StatFs(Environment.getDataDirectory().path)
        val batteryIntent = appContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
        val interfaces = networkInterfaces()
        val macInfo = macAddress(interfaces)

        return DeviceSnapshot(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            brand = Build.BRAND.orEmpty(),
            device = Build.DEVICE.orEmpty(),
            hardware = Build.HARDWARE.orEmpty(),
            board = Build.BOARD.orEmpty(),
            androidRelease = Build.VERSION.RELEASE.orEmpty(),
            sdkInt = Build.VERSION.SDK_INT,
            securityPatch = Build.VERSION.SECURITY_PATCH?.takeIf { it.isNotBlank() } ?: NO_DISPONIBLE,
            buildId = Build.ID.orEmpty(),
            fingerprintShort = Build.FINGERPRINT.orEmpty().take(48),
            primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: NO_DISPONIBLE,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            totalRamBytes = memoryInfo.totalMem,
            availableRamBytes = memoryInfo.availMem,
            lowMemory = memoryInfo.lowMemory,
            totalStorageBytes = dataDirStat.totalBytes,
            availableStorageBytes = dataDirStat.availableBytes,
            batteryLevelPercent = batteryLevel(batteryIntent),
            batteryStatus = batteryStatusLabel(batteryIntent),
            batteryHealth = batteryHealthLabel(batteryIntent),
            batteryTemperatureCelsius = batteryTemperature(batteryIntent),
            batteryVoltageMilliVolts = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
                ?.takeIf { it > 0 },
            batteryCurrentMicroAmps = runCatching {
                batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            }.getOrNull()?.takeIf { it != Int.MIN_VALUE },
            batteryChargeCounterMicroAmpHours = runCatching {
                batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            }.getOrNull()?.takeIf { it > 0 },
            batteryTechnology = batteryIntent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY),
            chargingSource = chargingSourceLabel(batteryIntent),
            bluetoothSupported = appContext.packageManager.hasSystemFeature(
                android.content.pm.PackageManager.FEATURE_BLUETOOTH,
            ),
            bluetoothState = bluetoothState(),
            bluetoothName = bluetoothName(),
            nfcSupported = appContext.packageManager.hasSystemFeature(
                android.content.pm.PackageManager.FEATURE_NFC,
            ),
            nfcState = nfcState(),
            sensors = readSensors(),
            wifiInterfaceIpv4 = interfaces.firstOrNull { it.kind == InterfaceKind.WIFI }?.ipv4,
            wifiInterfaceIpv6 = interfaces.firstOrNull { it.kind == InterfaceKind.WIFI }?.ipv6,
            mobileInterfaceIpv4 = interfaces.firstOrNull { it.kind == InterfaceKind.MOBILE }?.ipv4,
            mobileInterfaceIpv6 = interfaces.firstOrNull { it.kind == InterfaceKind.MOBILE }?.ipv6,
            anyInterfaceIpv4 = interfaces.firstOrNull { it.ipv4 != null }?.ipv4,
            macAddress = macInfo.first,
            macNote = macInfo.second,
            capturedAt = System.currentTimeMillis(),
        )
    }

    fun isWifiEnabled(): Boolean = runCatching { wifiManager.isWifiEnabled }.getOrDefault(false)

    // -- Batería ------------------------------------------------------------

    private fun batteryLevel(intent: Intent?): Int? {
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level < 0 || scale <= 0) return null
        return (level * 100f / scale).toInt().coerceIn(0, 100)
    }

    private fun batteryTemperature(intent: Intent?): Float? {
        val tenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?: Int.MIN_VALUE
        if (tenths == Int.MIN_VALUE || tenths == 0) return null
        return tenths / 10f
    }

    private fun batteryStatusLabel(intent: Intent?): String = when (
        intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    ) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "Cargando"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Descargando"
        BatteryManager.BATTERY_STATUS_FULL -> "Cargada"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "No cargando"
        else -> NO_DISPONIBLE
    }

    private fun batteryHealthLabel(intent: Intent?): String = when (
        intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
    ) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Buena"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Sobrecalentada"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Agotada"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Sobrecargada"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Fallo no especificado"
        BatteryManager.BATTERY_HEALTH_COLD -> "Fría"
        else -> NO_DISPONIBLE
    }

    private fun chargingSourceLabel(intent: Intent?): String = when (
        intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
    ) {
        BatteryManager.BATTERY_PLUGGED_AC -> "Corriente alterna"
        BatteryManager.BATTERY_PLUGGED_USB -> "USB"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Carga inalámbrica"
        0 -> "Sin cargador"
        else -> NO_DISPONIBLE
    }

    // -- Bluetooth y NFC ----------------------------------------------------

    private fun bluetoothAdapter(): BluetoothAdapter? {
        val manager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        @Suppress("DEPRECATION")
        return runCatching { manager?.adapter }.getOrNull()
    }

    private fun bluetoothState(): String {
        val adapter = bluetoothAdapter()
            ?: return if (appContext.packageManager.hasSystemFeature(
                    android.content.pm.PackageManager.FEATURE_BLUETOOTH,
                )
            ) {
                "Adaptador no disponible"
            } else {
                "El dispositivo no tiene Bluetooth"
            }
        val state = try {
            @Suppress("DEPRECATION")
            adapter.state
        } catch (_: SecurityException) {
            return "Permiso de Bluetooth no concedido"
        }
        return when (state) {
            BluetoothAdapter.STATE_ON -> "Activado"
            BluetoothAdapter.STATE_OFF -> "Desactivado"
            BluetoothAdapter.STATE_TURNING_ON -> "Activándose"
            BluetoothAdapter.STATE_TURNING_OFF -> "Desactivándose"
            else -> NO_DISPONIBLE
        }
    }

    private fun bluetoothName(): String? = try {
        @Suppress("DEPRECATION")
        bluetoothAdapter()?.name?.takeIf { it.isNotBlank() }
    } catch (_: SecurityException) {
        null
    }

    private fun nfcState(): String {
        if (!appContext.packageManager.hasSystemFeature(
                android.content.pm.PackageManager.FEATURE_NFC,
            )
        ) {
            return "El dispositivo no tiene NFC"
        }
        val adapter = runCatching { NfcAdapter.getDefaultAdapter(appContext) }.getOrNull()
            ?: return "Adaptador NFC no disponible"
        val enabled = try {
            adapter.isEnabled
        } catch (_: Exception) {
            return NO_DISPONIBLE
        }
        return if (enabled) "Activado" else "Desactivado"
    }

    // -- Sensores ------------------------------------------------------------

    private fun readSensors(): List<SensorInfo> = runCatching {
        sensorManager.getSensorList(Sensor.TYPE_ALL).map { sensor ->
            SensorInfo(
                name = sensor.name ?: NO_DISPONIBLE,
                typeLabel = sensorTypeLabel(sensor.type),
                vendor = sensor.vendor ?: NO_DISPONIBLE,
                version = sensor.version,
                maxRange = formatSensorRange(sensor.maximumRange),
                power = formatSensorPower(sensor.power),
                resolution = formatSensorResolution(sensor.resolution),
                // `Sensor.getAccuracy()` no está en el SDK público, así que no se
                // inventa una precisión de sensor.
                accuracyLabel = NO_DISPONIBLE,
                isDynamic = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    sensor.isDynamicSensor
                } else {
                    null
                },
                isWakeUpSensor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    sensor.isWakeUpSensor
                } else {
                    null
                },
            )
        }
    }.getOrDefault(emptyList())

    // -- Red ----------------------------------------------------------------

    private enum class InterfaceKind { WIFI, MOBILE, ETHERNET, VPN, OTHER }

    private data class InterfaceInfo(
        val name: String,
        val kind: InterfaceKind,
        val ipv4: String?,
        val ipv6: String?,
        val hardwareAddress: String?,
    )

    private fun networkInterfaces(): List<InterfaceInfo> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp }
            .map { candidate ->
                val addresses = candidate.inetAddresses.toList()
                InterfaceInfo(
                    name = candidate.name,
                    kind = classifyInterface(candidate.name),
                    ipv4 = addresses.filterIsInstance<Inet4Address>()
                        .firstOrNull { !it.isLoopbackAddress }?.hostAddress,
                    ipv6 = addresses.filterIsInstance<Inet6Address>()
                        .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                        ?.hostAddress
                        ?: addresses.filterIsInstance<Inet6Address>()
                            .firstOrNull { !it.isLoopbackAddress }?.hostAddress,
                    hardwareAddress = runCatching {
                        candidate.hardwareAddress
                            ?.takeIf { it.isNotEmpty() }
                            ?.joinToString(":") { byte -> "%02X".format(byte) }
                    }.getOrNull(),
                )
            }
    }.getOrDefault(emptyList())

    private fun classifyInterface(name: String): InterfaceKind = when {
        name.startsWith("wlan") || name.startsWith("wifi") -> InterfaceKind.WIFI
        name.startsWith("rmnet") || name.startsWith("ccm") || name.startsWith("ppp") ||
            name.startsWith("wap") || name.startsWith("cell") -> InterfaceKind.MOBILE
        name.startsWith("eth") -> InterfaceKind.ETHERNET
        name.startsWith("tun") || name.startsWith("vpn") -> InterfaceKind.VPN
        else -> InterfaceKind.OTHER
    }

    /**
     * Desde Android 10 las MAC de Wi-Fi y las de los hotspots salen mascaradas
     * como 02:00:00:00:00:00 salvo para apps con privilegios de sistema.
     */
    private fun macAddress(interfaces: List<InterfaceInfo>): Pair<String?, String?> {
        val masked = "02:00:00:00:00:00"
        val candidate = interfaces.firstOrNull { it.kind == InterfaceKind.WIFI }
            ?.hardwareAddress
            ?: interfaces.firstOrNull { it.kind == InterfaceKind.ETHERNET }?.hardwareAddress
        return when {
            candidate.isNullOrBlank() ->
                null to "Android no expone la dirección MAC a las apps desde Android 10."

            candidate == masked ->
                null to "El sistema devuelve una MAC enmascarada (02:00:00:00:00:00): " +
                    "solo las apps con privilegios de sistema pueden leerla."

            else -> candidate.uppercase() to null
        }
    }

    // -- Formato de sensores --------------------------------------------------

    private fun formatSensorRange(range: Float): String = when {
        range <= 0f -> NO_DISPONIBLE
        range > 1000f -> String.format(com.diagnostico.senales.core.util.FMT, "%.0f", range)
        else -> String.format(com.diagnostico.senales.core.util.FMT, "%.3f", range)
    }

    private fun formatSensorPower(mah: Float): String =
        if (mah <= 0f) NO_DISPONIBLE
        else String.format(com.diagnostico.senales.core.util.FMT, "%.3f mA", mah)

    private fun formatSensorResolution(resolution: Float): String =
        if (resolution <= 0f) NO_DISPONIBLE
        else String.format(com.diagnostico.senales.core.util.FMT, "%.6f", resolution)

    private fun sensorTypeLabel(type: Int): String = when (type) {
        Sensor.TYPE_ACCELEROMETER -> "Acelerómetro"
        Sensor.TYPE_GYROSCOPE -> "Giroscopio"
        Sensor.TYPE_MAGNETIC_FIELD -> "Magnetómetro"
        Sensor.TYPE_LIGHT -> "Luz"
        Sensor.TYPE_PROXIMITY -> "Proximidad"
        Sensor.TYPE_PRESSURE -> "Barómetro"
        Sensor.TYPE_RELATIVE_HUMIDITY -> "Humedad"
        Sensor.TYPE_AMBIENT_TEMPERATURE -> "Temperatura ambiente"
        Sensor.TYPE_STEP_COUNTER -> "Contador de pasos"
        Sensor.TYPE_STEP_DETECTOR -> "Detector de pasos"
        Sensor.TYPE_HEART_RATE -> "Frecuencia cardiaca"
        Sensor.TYPE_LINEAR_ACCELERATION -> "Aceleración lineal"
        Sensor.TYPE_GRAVITY -> "Gravedad"
        Sensor.TYPE_ROTATION_VECTOR -> "Vector de rotación"
        Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> "Rotación geomagnética"
        Sensor.TYPE_SIGNIFICANT_MOTION -> "Movimiento significativo"
        Sensor.TYPE_ACCELEROMETER_UNCALIBRATED -> "Acelerómetro sin calibrar"
        Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> "Giroscopio sin calibrar"
        else -> "Tipo #$type"
    }
}