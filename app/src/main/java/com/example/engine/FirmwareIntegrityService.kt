package com.example.engine

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

enum class IntegrityStatus(val label: String) {
    VERIFIED("SHA-256 Verified"),
    COMPUTED_NO_EXPECTED("SHA-256 Computed (No Upstream Hash)"),
    MISMATCH("SHA-256 Integrity Mismatch"),
    MISSING_FILE("Binary Missing or Empty"),
    ERROR("Verification Error"),
}

data class IntegrityVerificationReport(
    val filePath: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val actualSha256: String?,
    val expectedSha256: String?,
    val status: IntegrityStatus,
    val verifiedAtEpochMs: Long = System.currentTimeMillis(),
    val summary: String,
) {
    val isSafeToFlash: Boolean
        get() = status == IntegrityStatus.VERIFIED || status == IntegrityStatus.COMPUTED_NO_EXPECTED
}

class FirmwareIntegrityException(
    val report: IntegrityVerificationReport,
    cause: Throwable? = null,
) : SecurityException(report.summary, cause)

/**
 * Computes and verifies SHA-256 cryptographic digests for downloaded or imported
 * firmware binaries prior to dispatching them to serial (USB OTG), UF2, raw IMG,
 * or Wi-Fi OTA flashing engines.
 */
class FirmwareIntegrityService {

    private val _lastVerificationReport = MutableStateFlow<IntegrityVerificationReport?>(null)
    val lastVerificationReport: StateFlow<IntegrityVerificationReport?> = _lastVerificationReport.asStateFlow()

    private val _reportsByPath = MutableStateFlow<Map<String, IntegrityVerificationReport>>(emptyMap())
    val reportsByPath: StateFlow<Map<String, IntegrityVerificationReport>> = _reportsByPath.asStateFlow()

    /**
     * Streams [file] on [Dispatchers.IO] in fixed-size chunks to compute its lowercase
     * hex-encoded SHA-256 digest without loading large images into heap memory.
     */
    suspend fun computeSha256(
        file: File,
        onProgress: (Float) -> Unit = {},
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.isFile) {
            return@withContext Result.failure(IllegalStateException("Firmware file not found: ${file.name}"))
        }
        val totalBytes = file.length()
        if (totalBytes <= 0L) {
            return@withContext Result.failure(IllegalStateException("Firmware file is empty (0 bytes): ${file.name}"))
        }

        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(STREAM_BUFFER_BYTES)
            var processed = 0L
            var lastReportedPct = -1

            FileInputStream(file).use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                    processed += read
                    val pct = ((processed * 100L) / totalBytes).toInt().coerceIn(0, 100)
                    if (pct != lastReportedPct || processed == totalBytes) {
                        lastReportedPct = pct
                        onProgress(pct / 100f)
                    }
                }
            }
            Result.success(digest.digest().toHexLower())
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Failed computing SHA-256 for ${file.name}: ${e.message}", e))
        }
    }

    /**
     * Verifies [file] integrity before flashing.
     * - Returns `Result.failure(FirmwareIntegrityException)` if the file is missing/empty,
     *   if hashing fails, if an expected SHA-256 hash mismatches the binary on disk, or if
     *   [requireExpectedHash] is true and no upstream SHA-256 digest was provided.
     * - Returns `Result.success(IntegrityVerificationReport)` when safe to pass to the flasher.
     */
    suspend fun verifyBeforeFlash(
        file: File,
        expectedSha256: String?,
        requireExpectedHash: Boolean = false,
        onProgress: (Float, String) -> Unit = { _, _ -> },
    ): Result<IntegrityVerificationReport> = withContext(Dispatchers.IO) {
        val normalizedExpected = normalizeSha256(expectedSha256)

        if (!file.exists() || !file.isFile || file.length() <= 0L) {
            val report = IntegrityVerificationReport(
                filePath = file.absolutePath,
                fileName = file.name,
                fileSizeBytes = if (file.exists()) file.length() else 0L,
                actualSha256 = null,
                expectedSha256 = normalizedExpected,
                status = IntegrityStatus.MISSING_FILE,
                summary = "Integrity check aborted: binary '${file.name}' is missing or empty.",
            )
            recordReport(report)
            return@withContext Result.failure(FirmwareIntegrityException(report))
        }

        val sizeBytes = file.length()
        onProgress(0f, "Computing SHA-256 checksum for ${file.name}…")

        val hashRes = computeSha256(file) { frac ->
            val pct = (frac * 100f).toInt().coerceIn(0, 100)
            onProgress(frac, "Verifying SHA-256 integrity ($pct%)…")
        }

        val actualSha = hashRes.getOrElse { err ->
            val report = IntegrityVerificationReport(
                filePath = file.absolutePath,
                fileName = file.name,
                fileSizeBytes = sizeBytes,
                actualSha256 = null,
                expectedSha256 = normalizedExpected,
                status = IntegrityStatus.ERROR,
                summary = err.message ?: "SHA-256 calculation error for ${file.name}.",
            )
            recordReport(report)
            return@withContext Result.failure(FirmwareIntegrityException(report, err))
        }

        val status = evaluateDigestMatch(actualSha, normalizedExpected)
        if (status == IntegrityStatus.MISMATCH) {
            val report = IntegrityVerificationReport(
                filePath = file.absolutePath,
                fileName = file.name,
                fileSizeBytes = sizeBytes,
                actualSha256 = actualSha,
                expectedSha256 = normalizedExpected,
                status = IntegrityStatus.MISMATCH,
                summary = "SHA-256 mismatch for ${file.name}! Expected ${normalizedExpected?.take(12)}…, got ${actualSha.take(12)}…. Flashing blocked to prevent bricking.",
            )
            recordReport(report)
            return@withContext Result.failure(FirmwareIntegrityException(report))
        }

        if (status == IntegrityStatus.COMPUTED_NO_EXPECTED && requireExpectedHash) {
            val report = IntegrityVerificationReport(
                filePath = file.absolutePath,
                fileName = file.name,
                fileSizeBytes = sizeBytes,
                actualSha256 = actualSha,
                expectedSha256 = null,
                status = IntegrityStatus.COMPUTED_NO_EXPECTED,
                summary = "Strict integrity policy blocked '${file.name}': upstream source did not provide a reference SHA-256 (computed ${actualSha.take(12)}…).",
            )
            recordReport(report)
            return@withContext Result.failure(FirmwareIntegrityException(report))
        }

        val summary = if (status == IntegrityStatus.VERIFIED) {
            "SHA-256 verified (${actualSha.take(12)}…) matches catalog checksum."
        } else {
            "SHA-256 computed (${actualSha.take(12)}…); no upstream reference hash listed."
        }
        onProgress(1f, summary)
        val report = IntegrityVerificationReport(
            filePath = file.absolutePath,
            fileName = file.name,
            fileSizeBytes = sizeBytes,
            actualSha256 = actualSha,
            expectedSha256 = normalizedExpected,
            status = status,
            summary = summary,
        )
        recordReport(report)
        Result.success(report)
    }

    private fun recordReport(report: IntegrityVerificationReport) {
        _lastVerificationReport.value = report
        _reportsByPath.value = _reportsByPath.value + (report.filePath to report)
    }

    companion object {
        private const val STREAM_BUFFER_BYTES = 64 * 1024

        fun normalizeSha256(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            var cleaned = raw.trim().lowercase()
            if (cleaned.startsWith("sha-256:")) {
                cleaned = cleaned.removePrefix("sha-256:")
            } else if (cleaned.startsWith("sha256:")) {
                cleaned = cleaned.removePrefix("sha256:")
            }
            return cleaned.trim().takeIf { it.isNotBlank() }
        }

        fun evaluateDigestMatch(actualSha256: String, expectedSha256: String?): IntegrityStatus {
            val normalizedExpected = normalizeSha256(expectedSha256)
                ?: return IntegrityStatus.COMPUTED_NO_EXPECTED
            return if (actualSha256.trim().equals(normalizedExpected, ignoreCase = true)) {
                IntegrityStatus.VERIFIED
            } else {
                IntegrityStatus.MISMATCH
            }
        }

        fun computeSha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).toHexLower()

        private fun ByteArray.toHexLower(): String =
            joinToString("") { "%02x".format(it) }
    }
}
