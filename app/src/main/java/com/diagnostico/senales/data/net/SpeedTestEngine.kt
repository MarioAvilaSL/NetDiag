package com.diagnostico.senales.data.net

import android.net.Network
import com.diagnostico.senales.data.model.SpeedTestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Descarga y subida reales de Internet. Son deliberadamente distintas de la
 * "velocidad de enlace" que reporta el adaptador Wi-Fi: una es la negociación
 * entre la antena y el router, la otra lo que llega realmente desde Internet.
 */
class SpeedTestEngine(private val transfer: HttpTransfer = HttpTransfer()) {

    suspend fun run(
        downloadUrl: String,
        uploadUrl: String,
        downloadBytes: Long = DEFAULT_DOWNLOAD_BYTES,
        uploadBytes: Long = DEFAULT_UPLOAD_BYTES,
        network: Network? = null,
        onDownloadProgress: (Long, Long) -> Unit = { _, _ -> },
        onUploadProgress: (Long, Long) -> Unit = { _, _ -> },
    ): SpeedTestResult = withContext(Dispatchers.IO) {
        val downSpec = UrlSpec.parse(downloadUrl)
        val upSpec = UrlSpec.parse(uploadUrl)

        val down = if (downSpec == null) {
            TransferOutcome(0L, 0.0, "URL de descarga no válida")
        } else {
            transfer.download(downSpec, downloadBytes, network) { read ->
                onDownloadProgress(read, downloadBytes)
            }
        }

        val up = if (upSpec == null) {
            TransferOutcome(0L, 0.0, "URL de subida no válida")
        } else {
            transfer.upload(upSpec, uploadBytes, network) { sent ->
                onUploadProgress(sent, uploadBytes)
            }
        }

        SpeedTestResult(
            downloadMbps = down.megabitsPerSecond,
            uploadMbps = up.megabitsPerSecond,
            downloadedBytes = down.bytesTransferred,
            uploadedBytes = up.bytesTransferred,
            downloadSeconds = down.seconds,
            uploadSeconds = up.seconds,
            downloadEndpoint = downSpec?.display,
            uploadEndpoint = upSpec?.display,
            downloadError = down.error,
            uploadError = up.error,
        )
    }

    companion object {
        /**
         * Cloudflare tiene un endpoint público sin clave ni registro. Si en una red
         * concreta está bloqueado, la pantalla deja editar la URL.
         */
        const val DEFAULT_DOWNLOAD_URL = "https://speed.cloudflare.com/__down?bytes=25000000"
        const val DEFAULT_UPLOAD_URL = "https://speed.cloudflare.com/__up"
        const val FALLBACK_DOWNLOAD_URL = "https://proof.ovh.net/files/10Mb.dat"
        const val DEFAULT_PING_TARGET = "1.1.1.1"
        const val DEFAULT_DOWNLOAD_BYTES = 25L * 1024L * 1024L
        const val DEFAULT_UPLOAD_BYTES = 10L * 1024L * 1024L
    }
}