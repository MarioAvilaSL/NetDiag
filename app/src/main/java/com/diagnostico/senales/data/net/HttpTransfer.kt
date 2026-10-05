package com.diagnostico.senales.data.net

import android.net.Network
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.math.min

/** URL HTTP/HTTPS mínima, sin dependencias externas. */
data class UrlSpec(
    val scheme: String,
    val host: String,
    val port: Int,
    val pathWithQuery: String,
) {
    val isSecure: Boolean get() = scheme == "https"
    val display: String get() = "$scheme://$host$pathWithQuery"

    companion object {
        fun parse(raw: String): UrlSpec? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            val uri = try {
                URI(trimmed)
            } catch (_: Exception) {
                return null
            }
            val scheme = uri.scheme?.lowercase() ?: return null
            if (scheme != "http" && scheme != "https") return null
            val host = uri.host ?: return null
            val port = when {
                uri.port > 0 -> uri.port
                scheme == "https" -> 443
                else -> 80
            }
            val path = uri.rawPath?.ifBlank { "/" } ?: "/"
            val query = uri.rawQuery?.let { "?$it" } ?: ""
            return UrlSpec(scheme = scheme, host = host, port = port, pathWithQuery = path + query)
        }
    }
}

data class TransferOutcome(
    val bytesTransferred: Long,
    val seconds: Double,
    val error: String?,
) {
    val megabitsPerSecond: Double?
        get() = if (error != null || seconds <= 0.0 || bytesTransferred <= 0L) {
            null
        } else {
            bytesTransferred * 8.0 / seconds / 1_000_000.0
        }
}

/**
 * Transferencias HTTP por socket para poder medir con precisión y, si hace falta,
 * forzar la conexión sobre una red concreta (por ejemplo, la red móvil aunque haya
 * Wi-Fi activo). Se escriben a mano porque HttpURLConnection no expone su socket.
 */
class HttpTransfer(
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 20_000,
) {

    fun download(
        url: UrlSpec,
        targetBytes: Long,
        network: Network?,
        warmupBytes: Long = 128L * 1024L,
        onProgress: (Long) -> Unit = {},
    ): TransferOutcome = try {
        val socket = openSocket(url, network)
        socket.use { active ->
            val output = active.getOutputStream()
            val request = buildString {
                append("GET ${url.pathWithQuery} HTTP/1.1\r\n")
                append("Host: ${url.host}\r\n")
                append("User-Agent: DiagnosticoSenales/1.0 (Android)\r\n")
                append("Accept: */*\r\n")
                append("Accept-Encoding: identity\r\n")
                append("Cache-Control: no-cache\r\n")
                append("Connection: close\r\n")
                append("\r\n")
            }
            output.write(request.toByteArray(Charsets.US_ASCII))
            output.flush()

            val input = active.getInputStream()
            val status = readResponseHeaders(input)
            if (status.statusCode !in 200..299) {
                TransferOutcome(0L, 0.0, "El servidor respondió HTTP ${status.statusCode}")
            } else {
                readBody(
                    input = input,
                    targetBytes = targetBytes,
                    chunked = status.isChunked,
                    warmupBytes = warmupBytes,
                    onProgress = onProgress,
                )
            }
        }
    } catch (e: Exception) {
        TransferOutcome(0L, 0.0, e.friendlyMessage())
    }

    fun upload(
        url: UrlSpec,
        targetBytes: Long,
        network: Network?,
        onProgress: (Long) -> Unit = {},
    ): TransferOutcome = try {
        val socket = openSocket(url, network)
        socket.use { active ->
            val output = active.getOutputStream()
            val header = buildString {
                append("POST ${url.pathWithQuery} HTTP/1.1\r\n")
                append("Host: ${url.host}\r\n")
                append("User-Agent: DiagnosticoSenales/1.0 (Android)\r\n")
                append("Content-Type: application/octet-stream\r\n")
                append("Content-Length: $targetBytes\r\n")
                append("Accept: */*\r\n")
                append("Connection: close\r\n")
                append("\r\n")
            }
            output.write(header.toByteArray(Charsets.US_ASCII))
            output.flush()

            val payload = ByteArray(BUFFER_SIZE)
            val start = System.nanoTime()
            var sent = 0L
            while (sent < targetBytes) {
                val chunk = min(payload.size.toLong(), targetBytes - sent).toInt()
                output.write(payload, 0, chunk)
                sent += chunk
                onProgress(sent)
            }
            output.flush()
            val seconds = (System.nanoTime() - start) / 1_000_000_000.0

            // Se drena la respuesta para que el servidor confirme la recepción.
            runCatching {
                val status = readResponseHeaders(active.getInputStream())
                if (status.statusCode !in 200..299) {
                    throw IOException("El servidor respondió HTTP ${status.statusCode}")
                }
            }
            TransferOutcome(sent, seconds, null)
        }
    } catch (e: Exception) {
        TransferOutcome(0L, 0.0, e.friendlyMessage())
    }

    /** Latencia de conexión TCP real, usada como referencia cuando ICMP no responde. */
    fun tcpLatencyMillis(url: UrlSpec, network: Network?, timeoutMillis: Int): Double? = try {
        val socket = openSocket(url, network, timeoutMillis)
        val start = System.nanoTime()
        socket.use { active ->
            if (active is SSLSocket) active.startHandshake()
        }
        (System.nanoTime() - start) / 1_000_000.0
    } catch (_: Exception) {
        null
    }

    private fun openSocket(url: UrlSpec, network: Network?, timeoutMillis: Int = connectTimeoutMillis): Socket {
        val raw = Socket()
        if (network != null) {
            network.bindSocket(raw)
        }
        raw.tcpNoDelay = true
        raw.connect(InetSocketAddress(url.host, url.port), timeoutMillis)
        if (!url.isSecure) {
            raw.soTimeout = readTimeoutMillis
            return raw
        }
        val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
        val secure = factory.createSocket(raw, url.host, url.port, true) as SSLSocket
        secure.soTimeout = readTimeoutMillis
        secure.startHandshake()
        return secure
    }

    private data class ResponseStatus(val statusCode: Int, val isChunked: Boolean)

    private fun readResponseHeaders(input: InputStream): ResponseStatus {
        val raw = StringBuilder()
        while (raw.length < MAX_HEADER_BYTES) {
            val byte = input.read()
            if (byte == -1) break
            raw.append(byte.toChar())
            if (raw.endsWith("\r\n\r\n")) break
        }
        val lines = raw.toString().split("\r\n")
        val statusLine = lines.firstOrNull().orEmpty()
        val statusCode = statusLine.split(' ').getOrNull(1)?.toIntOrNull() ?: 0
        val isChunked = lines.any { it.startsWith("Transfer-Encoding:", ignoreCase = true) && it.contains("chunked", true) }
        return ResponseStatus(statusCode, isChunked)
    }

    private fun readBody(
        input: InputStream,
        targetBytes: Long,
        chunked: Boolean,
        warmupBytes: Long,
        onProgress: (Long) -> Unit,
    ): TransferOutcome {
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        var measured = 0L
        var warmupLeft = warmupBytes
        var measureStartNanos = 0L

        while (measured < targetBytes) {
            val read = if (chunked) {
                readChunkedChunk(input, buffer)
            } else {
                val wanted = min(buffer.size.toLong(), targetBytes - measured).toInt()
                input.read(buffer, 0, wanted)
            }
            if (read <= 0) break
            total += read

            val warm = min(warmupLeft, read.toLong()).toInt()
            warmupLeft -= warm
            val effective = read - warm
            if (effective > 0) {
                if (measureStartNanos == 0L) measureStartNanos = System.nanoTime()
                measured += effective
                onProgress(measured)
            }
        }

        if (measureStartNanos == 0L) {
            // Descarga más corta que el calentamiento: no hay medición válida.
            return TransferOutcome(total, 0.0, "La respuesta fue demasiado pequeña para medir")
        }
        val seconds = (System.nanoTime() - measureStartNanos) / 1_000_000_000.0
        if (seconds <= 0.0) return TransferOutcome(total, 0.0, "Medición demasiado corta")
        return TransferOutcome(measured, seconds, null)
    }

    /** Lee un bloque con su tamaño y lo copia a [buffer]; devuelve los bytes leídos. */
    private fun readChunkedChunk(input: InputStream, buffer: ByteArray): Int {
        val sizeLine = StringBuilder()
        while (true) {
            val byte = input.read()
            if (byte == -1) return -1
            if (byte == '\n'.code) break
            if (byte != '\r'.code) sizeLine.append(byte.toChar())
        }
        val size = sizeLine.toString().substringBefore(';').trim().toIntOrNull(16) ?: return -1
        if (size == 0) return -1
        var read = 0
        while (read < size) {
            val n = input.read(buffer, read, min(buffer.size, size) - read)
            if (n <= 0) return if (read == 0) -1 else read
            read += n
        }
        input.read() // \r
        input.read() // \n
        return read
    }

    private fun Exception.friendlyMessage(): String = when (this) {
        is java.net.UnknownHostException -> "No se pudo resolver el nombre del servidor"
        is java.net.SocketTimeoutException -> "El servidor no respondió a tiempo"
        is javax.net.ssl.SSLException -> "Error de TLS con el servidor"
        is IOException -> message ?: "Error de red"
        else -> message ?: "Error inesperado"
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val MAX_HEADER_BYTES = 16 * 1024
    }
}