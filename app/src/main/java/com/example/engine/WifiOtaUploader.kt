package com.example.engine

import android.util.Base64
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink

enum class OtaUploadProtocol(
    val label: String,
    val defaultPath: String,
    val formFieldName: String,
    val description: String,
) {
    ESP_MULTIPART_UPDATE(
        label = "ESP /update",
        defaultPath = "/update",
        formFieldName = "update",
        description = "ESP32/ESP8266 WebServer Update, ArduinoOTA HTTP, M5Launcher /update",
    ),
    LAUNCHER_SD_UPLOAD(
        label = "Launcher /upload",
        defaultPath = "/upload",
        formFieldName = "file",
        description = "bmorcelli/Launcher WebUI SD partition upload (/upload)",
    ),
    ELEGANT_OTA_V3(
        label = "ElegantOTA",
        defaultPath = "/ota/upload",
        formFieldName = "file",
        description = "AsyncElegantOTA / ElegantOTA v3 with MD5 verification",
    ),
    ARDUINO_RAW_BINARY(
        label = "Raw HTTP Binary",
        defaultPath = "/ota",
        formFieldName = "",
        description = "Raw application/octet-stream POST for custom Arduino/ESP-IDF OTA",
    ),
}

data class WifiOtaTargetConfig(
    val useHttps: Boolean = false,
    val host: String = "192.168.4.1",
    val port: Int = 80,
    val endpointPath: String = "/update",
    val protocol: OtaUploadProtocol = OtaUploadProtocol.ESP_MULTIPART_UPDATE,
    val otaPassword: String = "",
    val otaUsername: String = "admin",
    val allowSelfSignedTls: Boolean = true,
    val expectedSha256: String? = null,
    val requireExpectedSha256: Boolean = false,
) {
    val scheme: String
        get() = if (useHttps) "https" else "http"

    fun buildTargetUrl(): String {
        val cleanHost = host.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore("/")
            .ifBlank { "192.168.4.1" }
        val cleanPath = if (endpointPath.startsWith("/")) endpointPath.trim() else "/${endpointPath.trim()}"
        val defaultPortForScheme = if (useHttps) 443 else 80
        val portSegment = if (port > 0 && port != defaultPortForScheme) ":$port" else ""
        return "$scheme://$cleanHost$portSegment$cleanPath"
    }
}

data class OtaUploadOutcome(
    val httpCode: Int,
    val targetUrl: String,
    val bytesSent: Long,
    val md5Hex: String,
    val sha256Hex: String,
    val serverResponse: String,
)

class WifiOtaUploader(
    private val customClientFactory: ((WifiOtaTargetConfig) -> OkHttpClient)? = null,
    private val integrityService: FirmwareIntegrityService = FirmwareIntegrityService(),
) {
    @Volatile
    private var activeCall: Call? = null

    fun cancelActiveTransfer() {
        activeCall?.cancel()
        activeCall = null
    }

    suspend fun probeEndpoint(config: WifiOtaTargetConfig): Result<String> = withContext(Dispatchers.IO) {
        val client = buildClient(config)
        val url = config.buildTargetUrl()
        val builder = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", "PocketBurner-OTA/1.0")
        applyAuthHeaders(builder, config)

        try {
            client.newCall(builder.build()).execute().use { resp ->
                val msg = "OTA Endpoint Ready: HTTP ${resp.code} (${url})"
                if (resp.code in 200..499) Result.success(msg)
                else Result.failure(IllegalStateException("Unexpected HTTP ${resp.code} from $url"))
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Unreachable ($url): ${e.message}", e))
        }
    }

    suspend fun pushFirmwareBinary(
        firmwareFile: File,
        config: WifiOtaTargetConfig,
        onProgress: (bytesSent: Long, totalBytes: Long, status: String) -> Unit,
    ): Result<OtaUploadOutcome> = withContext(Dispatchers.IO) {
        if (!firmwareFile.exists() || !firmwareFile.isFile) {
            return@withContext Result.failure(IllegalStateException("Firmware binary does not exist: ${firmwareFile.name}"))
        }
        val totalBytes = firmwareFile.length()
        if (totalBytes <= 0L) {
            return@withContext Result.failure(IllegalStateException("Firmware binary is empty (0 bytes)."))
        }

        val targetUrl = config.buildTargetUrl()
        val verifyRes = integrityService.verifyBeforeFlash(
            file = firmwareFile,
            expectedSha256 = config.expectedSha256,
            requireExpectedHash = config.requireExpectedSha256,
        ) { _, msg ->
            onProgress(0L, totalBytes, msg)
        }
        val report = verifyRes.getOrElse { err ->
            return@withContext Result.failure(err)
        }
        onProgress(0L, totalBytes, "Computing MD5 digest (SHA-256 ${report.status.label})…")
        val md5Hex = computeMd5Digest(firmwareFile)
        val sha256Hex = report.actualSha256 ?: computeDigests(firmwareFile).second

        val streamBody = StreamingBinaryRequestBody(
            file = firmwareFile,
            contentType = "application/octet-stream".toMediaType(),
            onBytesWritten = { sent ->
                val kbSent = sent / 1024L
                val kbTotal = (totalBytes / 1024L).coerceAtLeast(1L)
                val pct = ((sent * 100L) / totalBytes).toInt().coerceIn(0, 100)
                onProgress(sent, totalBytes, "Uploading ${firmwareFile.name} ($kbSent/$kbTotal KB • $pct%)")
            },
        )

        val requestBody: RequestBody = when (config.protocol) {
            OtaUploadProtocol.ESP_MULTIPART_UPDATE -> {
                MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("MD5", md5Hex)
                    .addFormDataPart(config.protocol.formFieldName, firmwareFile.name, streamBody)
                    .build()
            }
            OtaUploadProtocol.LAUNCHER_SD_UPLOAD -> {
                MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("path", "/${firmwareFile.name}")
                    .addFormDataPart(config.protocol.formFieldName, firmwareFile.name, streamBody)
                    .build()
            }
            OtaUploadProtocol.ELEGANT_OTA_V3 -> {
                MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("MD5", md5Hex)
                    .addFormDataPart("mode", "fr")
                    .addFormDataPart(config.protocol.formFieldName, firmwareFile.name, streamBody)
                    .build()
            }
            OtaUploadProtocol.ARDUINO_RAW_BINARY -> streamBody
        }

        val reqBuilder = Request.Builder()
            .url(targetUrl)
            .post(requestBody)
            .header("User-Agent", "PocketBurner-OTA/1.0")
            .header("X-Firmware-MD5", md5Hex)
            .header("X-Firmware-SHA256", sha256Hex)
            .header("X-Firmware-Name", firmwareFile.name)

        applyAuthHeaders(reqBuilder, config)

        val client = buildClient(config)
        val call = client.newCall(reqBuilder.build())
        activeCall = call
        return@withContext try {
            onProgress(0L, totalBytes, "Connecting ${config.scheme.uppercase()} OTA -> $targetUrl")
            call.execute().use { resp ->
                val respText = resp.body?.string()?.trim().orEmpty()
                if (!resp.isSuccessful) {
                    val detail = respText.take(160).ifBlank { resp.message }
                    return@withContext Result.failure(
                        IllegalStateException("OTA rejected (HTTP ${resp.code}): $detail")
                    )
                }
                onProgress(totalBytes, totalBytes, "OTA verified (${resp.code})! Device rebooting…")
                Result.success(
                    OtaUploadOutcome(
                        httpCode = resp.code,
                        targetUrl = targetUrl,
                        bytesSent = totalBytes,
                        md5Hex = md5Hex,
                        sha256Hex = sha256Hex,
                        serverResponse = respText.ifBlank { "OK (${resp.code})" },
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Wi-Fi OTA failed: ${e.message}", e))
        } finally {
            activeCall = null
        }
    }

    private fun applyAuthHeaders(builder: Request.Builder, config: WifiOtaTargetConfig) {
        if (config.otaPassword.isNotBlank()) {
            val rawCreds = "${config.otaUsername.ifBlank { "admin" }}:${config.otaPassword}"
            val encoded = Base64.encodeToString(rawCreds.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            builder.header("Authorization", "Basic $encoded")
            builder.header("X-OTA-Password", config.otaPassword)
        }
    }

    private fun buildClient(config: WifiOtaTargetConfig): OkHttpClient {
        customClientFactory?.let { return it(config) }
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(180, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)

        if (config.useHttps && config.allowSelfSignedTls) {
            val trustManager = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            val sslCtx = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf(trustManager), SecureRandom())
            }
            builder.sslSocketFactory(sslCtx.socketFactory, trustManager)
            builder.hostnameVerifier(HostnameVerifier { _, _ -> true })
        }
        return builder.build()
    }

    private class StreamingBinaryRequestBody(
        private val file: File,
        private val contentType: MediaType,
        private val onBytesWritten: (Long) -> Unit,
    ) : RequestBody() {
        override fun contentType(): MediaType = contentType
        override fun contentLength(): Long = file.length()

        override fun writeTo(sink: BufferedSink) {
            val total = file.length().coerceAtLeast(1L)
            val buffer = ByteArray(16 * 1024)
            var sent = 0L
            var lastReportedPct = -1
            FileInputStream(file).use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    sink.write(buffer, 0, read)
                    sent += read
                    val pct = ((sent * 100L) / total).toInt().coerceIn(0, 100)
                    if (pct != lastReportedPct || sent == total) {
                        lastReportedPct = pct
                        onBytesWritten(sent)
                    }
                }
            }
        }
    }

    companion object {
        fun computeMd5Digest(file: File): String {
            val md5 = MessageDigest.getInstance("MD5")
            val buf = ByteArray(16 * 1024)
            FileInputStream(file).use { input ->
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    md5.update(buf, 0, read)
                }
            }
            return md5.digest().joinToString("") { "%02x".format(it) }
        }

        fun computeDigests(file: File): Pair<String, String> {
            val md5 = MessageDigest.getInstance("MD5")
            val sha = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(16 * 1024)
            FileInputStream(file).use { input ->
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    md5.update(buf, 0, read)
                    sha.update(buf, 0, read)
                }
            }
            val md5Hex = md5.digest().joinToString("") { "%02x".format(it) }
            val shaHex = sha.digest().joinToString("") { "%02x".format(it) }
            return md5Hex to shaHex
        }
    }
}
