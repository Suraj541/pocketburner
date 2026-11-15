package com.example.engine

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.local.DownloadState
import com.example.data.local.FirmwareDao
import com.example.data.local.FirmwareEntity
import com.example.data.local.RepoParserType
import com.example.data.local.RepoSourceDao
import com.example.data.local.RepoSourceEntity
import com.example.data.local.TaxonomyRepository
import com.example.hardware.DeviceFamily
import com.example.hardware.FirmwareCategory
import com.example.hardware.HardwareRegistry
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class FirmwareCatalogEngine(
    private val context: Context,
    private val firmwareDao: FirmwareDao,
    private val repoDao: RepoSourceDao,
    private val taxonomyRepository: TaxonomyRepository,
) {
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val firmwareDir: File
        get() = File(context.filesDir, "firmware").apply { mkdirs() }

    val defaultRepositories: List<RepoSourceEntity> = listOf(
        RepoSourceEntity(
            id = "repo_m5burner",
            name = "M5Burner Public Feed",
            url = "https://burner.m5stack.com/api/v1/firmware",
            parserType = RepoParserType.M5BURNER.name,
            enabled = true,
            lastSyncedAt = 0L,
            lastError = null,
            itemCount = 0,
        ),
        RepoSourceEntity(
            id = "repo_rpi_imager",
            name = "Raspberry Pi OS Official Imager Feed",
            url = "https://downloads.raspberrypi.org/os_list_imagingutility_v4.json",
            parserType = RepoParserType.RPI_IMAGER.name,
            enabled = true,
            lastSyncedAt = 0L,
            lastError = null,
            itemCount = 0,
        ),
        RepoSourceEntity(
            id = "repo_tasmota_esp",
            name = "Tasmota ESP8266/ESP32/S2/S3/C3/C6 Releases",
            url = "https://api.github.com/repos/arendst/Tasmota/releases/latest",
            parserType = RepoParserType.GITHUB_RELEASE.name,
            enabled = true,
            lastSyncedAt = 0L,
            lastError = null,
            itemCount = 0,
        ),
        RepoSourceEntity(
            id = "repo_micropython_esp_rp",
            name = "MicroPython (ESP32 / ESP8266 / RP2040 / Arduino)",
            url = "https://api.github.com/repos/micropython/micropython/releases/latest",
            parserType = RepoParserType.GITHUB_RELEASE.name,
            enabled = true,
            lastSyncedAt = 0L,
            lastError = null,
            itemCount = 0,
        ),
        RepoSourceEntity(
            id = "repo_arduino_index",
            name = "Arduino Official Package Index",
            url = "https://downloads.arduino.cc/packages/package_index.json",
            parserType = RepoParserType.ARDUINO_INDEX.name,
            enabled = true,
            lastSyncedAt = 0L,
            lastError = null,
            itemCount = 0,
        ),
        RepoSourceEntity(
            id = "repo_bmorcelli_catalog",
            name = "bmorcelli Full Firmware Catalog",
            url = "https://raw.githubusercontent.com/bmorcelli/M5Stack-json-fw/main/script/all_device_firmware.json",
            parserType = RepoParserType.M5BURNER.name,
            enabled = true,
            lastSyncedAt = 0L,
            lastError = null,
            itemCount = 0,
        ),
        RepoSourceEntity(
            id = "repo_bmorcelli_launcher",
            name = "M5Launcher (bmorcelli/Launcher)",
            url = "https://api.github.com/repos/bmorcelli/Launcher/releases/latest",
            parserType = RepoParserType.GITHUB_RELEASE.name,
            enabled = true,
            lastSyncedAt = 0L,
            lastError = null,
            itemCount = 0,
        ),
    )

    suspend fun ensureDefaultReposSeeded() = withContext(Dispatchers.IO) {
        val existingIds = repoDao.getEnabled().map { it.id }.toSet()
        val missing = defaultRepositories.filter { it.id !in existingIds }
        if (missing.isNotEmpty()) {
            repoDao.upsertAll(missing)
        }
    }

    suspend fun syncAllEnabledRepos(): List<String> = withContext(Dispatchers.IO) {
        ensureDefaultReposSeeded()
        val repos = repoDao.getEnabled()
        val errors = mutableListOf<String>()
        for (repo in repos) {
            val result = syncSingleRepo(repo)
            result.exceptionOrNull()?.let { err ->
                errors.add("${repo.name}: ${err.message ?: "Network error"}")
            }
        }
        errors
    }

    suspend fun syncSingleRepo(repo: RepoSourceEntity): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(repo.url)
                .header("Accept", "application/json")
                .header("User-Agent", "PocketBurner-Android/1.0")
                .build()

            val bodyText = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP ${response.code} ${response.message}")
                }
                response.body?.string() ?: throw IllegalStateException("Empty response body")
            }

            val parsed = when (RepoParserType.valueOf(repo.parserType)) {
                RepoParserType.M5BURNER -> parseM5BurnerFeed(bodyText, repo.id)
                RepoParserType.RPI_IMAGER -> parseRpiImagerFeed(bodyText, repo.id)
                RepoParserType.GITHUB_RELEASE -> parseGitHubReleaseFeed(bodyText, repo.id, repo.name)
                RepoParserType.ARDUINO_INDEX -> parseArduinoIndexFeed(bodyText, repo.id)
                RepoParserType.CUSTOM_JSON -> parseCustomFeed(bodyText, repo.id)
            }

            // Preserve already-downloaded state if item ID matches
            val merged = parsed.map { item ->
                val existing = firmwareDao.getById(item.id)
                if (existing != null && existing.downloadState == DownloadState.DOWNLOADED.name && existing.localFilePath != null) {
                    item.copy(
                        localFilePath = existing.localFilePath,
                        downloadState = DownloadState.DOWNLOADED.name,
                        downloadProgress = 100,
                    )
                } else {
                    item
                }
            }

            firmwareDao.clearRemoteByRepo(repo.id)
            firmwareDao.upsertAll(merged)
            taxonomyRepository.linkFirmwareBatch(merged)
            repoDao.upsert(
                repo.copy(
                    lastSyncedAt = System.currentTimeMillis(),
                    lastError = null,
                    itemCount = merged.size,
                )
            )
            Result.success(merged.size)
        } catch (e: Exception) {
            repoDao.upsert(
                repo.copy(
                    lastSyncedAt = System.currentTimeMillis(),
                    lastError = e.message ?: "Sync failed",
                )
            )
            Result.failure(e)
        }
    }

    suspend fun addRepoSource(
        name: String,
        url: String,
        parserType: RepoParserType,
    ): Result<Int> = withContext(Dispatchers.IO) {
        val repo = RepoSourceEntity(
            id = "repo_${UUID.randomUUID().toString().take(8)}",
            name = name.ifBlank { url.substringAfter("://").substringBefore("/") },
            url = url.trim(),
            parserType = parserType.name,
            enabled = true,
            lastSyncedAt = 0L,
            lastError = null,
            itemCount = 0,
        )
        repoDao.upsert(repo)
        syncSingleRepo(repo)
    }

    suspend fun addCustomUrlFirmware(
        title: String,
        author: String,
        category: FirmwareCategory,
        targetDeviceId: String,
        framework: String,
        version: String,
        downloadUrl: String,
        flashOffset: String,
        baudRate: Int,
        sha256: String?,
        description: String,
    ) = withContext(Dispatchers.IO) {
        val board = HardwareRegistry.findById(targetDeviceId)
        val ext = downloadUrl.substringBefore("?").substringAfterLast(".", "bin")
        val item = FirmwareEntity(
            id = "custom_url_${UUID.randomUUID().toString().take(8)}",
            title = title.trim(),
            author = author.ifBlank { "Custom Author" },
            category = category.label,
            deviceTagsCsv = targetDeviceId,
            family = (board?.family ?: DeviceFamily.ESP_IOT).name,
            version = version.ifBlank { "1.0.0" },
            availableVersionsCsv = version.ifBlank { "1.0.0" },
            framework = framework.ifBlank { "Custom Binary" },
            description = description.ifBlank { "User-added remote firmware package for $targetDeviceId." },
            coverImageUrl = null,
            downloadUrl = downloadUrl.trim(),
            localFilePath = null,
            fileExtension = ext.lowercase(),
            sizeBytes = 0L,
            sha256 = sha256?.takeIf { it.isNotBlank() },
            flashOffset = flashOffset.ifBlank { board?.appOffset ?: "0x10000" },
            defaultBaud = baudRate,
            flashMode = board?.flashMode ?: "DIO",
            repoSourceId = "user_custom",
            isCustomUserAdded = true,
            downloadState = DownloadState.NOT_DOWNLOADED.name,
            downloadProgress = 0,
            updatedAt = System.currentTimeMillis(),
        )
        firmwareDao.upsert(item)
        taxonomyRepository.linkFirmwareBatch(listOf(item))
    }

    suspend fun addCustomLocalFirmware(
        uri: Uri,
        title: String,
        category: FirmwareCategory,
        targetDeviceId: String,
        framework: String,
        version: String,
        flashOffset: String,
        baudRate: Int,
        description: String,
    ): Result<FirmwareEntity> = withContext(Dispatchers.IO) {
        try {
            val fileName = queryDisplayName(uri) ?: "custom_firmware.bin"
            val ext = fileName.substringAfterLast(".", "bin").lowercase()
            val id = "custom_local_${UUID.randomUUID().toString().take(8)}"
            val destFile = File(firmwareDir, "${id}_${fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")}")

            val digest = MessageDigest.getInstance("SHA-256")
            var totalBytes = 0L
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        totalBytes += read
                    }
                }
            } ?: return@withContext Result.failure(IllegalStateException("Unable to open selected file"))

            val shaHex = digest.digest().joinToString("") { "%02x".format(it) }
            val board = HardwareRegistry.findById(targetDeviceId)
            val entity = FirmwareEntity(
                id = id,
                title = title.ifBlank { fileName.substringBeforeLast(".") },
                author = "Local Import",
                category = category.label,
                deviceTagsCsv = targetDeviceId,
                family = (board?.family ?: DeviceFamily.ESP_IOT).name,
                version = version.ifBlank { "1.0.0" },
                availableVersionsCsv = version.ifBlank { "1.0.0" },
                framework = framework.ifBlank { ext.uppercase() },
                description = description.ifBlank { "Imported local .$ext image ($fileName) ready to flash." },
                coverImageUrl = null,
                downloadUrl = "local://$fileName",
                localFilePath = destFile.absolutePath,
                fileExtension = ext,
                sizeBytes = totalBytes,
                sha256 = shaHex,
                flashOffset = flashOffset.ifBlank { board?.appOffset ?: "0x10000" },
                defaultBaud = baudRate,
                flashMode = board?.flashMode ?: "DIO",
                repoSourceId = "user_local",
                isCustomUserAdded = true,
                downloadState = DownloadState.DOWNLOADED.name,
                downloadProgress = 100,
                updatedAt = System.currentTimeMillis(),
            )
            firmwareDao.upsert(entity)
            taxonomyRepository.linkFirmwareBatch(listOf(entity))
            Result.success(entity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addGeneratedFirmwarePackage(
        title: String,
        targetDeviceId: String,
        framework: String,
        fileName: String,
        codeContent: String,
        buildGuide: String,
    ): FirmwareEntity = withContext(Dispatchers.IO) {
        val id = "ai_fw_${UUID.randomUUID().toString().take(8)}"
        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val destFile = File(firmwareDir, "${id}_$safeName")
        val fullPayload = buildString {
            appendLine(codeContent)
            appendLine()
            appendLine("/* === POCKETBURNER MULTI-AGENT BUILD & FLASH MANIFEST ===")
            appendLine(buildGuide)
            appendLine("=== */")
        }
        val bytes = fullPayload.toByteArray(Charsets.UTF_8)
        destFile.writeBytes(bytes)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val shaHex = digest.joinToString("") { "%02x".format(it) }
        val ext = fileName.substringAfterLast(".", "ino").lowercase()
        val board = HardwareRegistry.findById(targetDeviceId)
        val entity = FirmwareEntity(
            id = id,
            title = title.trim().ifBlank { "$targetDeviceId AI Firmware" },
            author = "PocketBurner Multi-Agent",
            category = FirmwareCategory.AI_ASSISTANTS.label,
            deviceTagsCsv = targetDeviceId,
            family = (board?.family ?: DeviceFamily.ESP_IOT).name,
            version = "1.0.0-ai",
            availableVersionsCsv = "1.0.0-ai",
            framework = framework,
            description = "Multi-agent synthesized firmware source & build manifest for $targetDeviceId ($fileName).",
            coverImageUrl = null,
            downloadUrl = "local://$safeName",
            localFilePath = destFile.absolutePath,
            fileExtension = ext,
            sizeBytes = bytes.size.toLong(),
            sha256 = shaHex,
            flashOffset = board?.appOffset ?: "0x10000",
            defaultBaud = board?.defaultBaud ?: 115200,
            flashMode = board?.flashMode ?: "DIO",
            repoSourceId = "user_ai_forge",
            isCustomUserAdded = true,
            downloadState = DownloadState.DOWNLOADED.name,
            downloadProgress = 100,
            updatedAt = System.currentTimeMillis(),
        )
        firmwareDao.upsert(entity)
        taxonomyRepository.linkFirmwareBatch(listOf(entity))
        entity
    }

    suspend fun importAndDownloadWebBinary(
        downloadUrl: String,
        suggestedFileName: String?,
        targetDeviceId: String,
    ): Result<FirmwareEntity> = withContext(Dispatchers.IO) {
        val cleanName = (suggestedFileName?.takeIf { it.isNotBlank() }
            ?: downloadUrl.substringBefore("?").substringAfterLast("/").ifBlank { "launcher_firmware.bin" })
        val ext = cleanName.substringAfterLast(".", "bin").lowercase()
        val board = HardwareRegistry.findById(targetDeviceId)
            ?: HardwareRegistry.boards.first()
        val item = FirmwareEntity(
            id = "web_fw_${UUID.randomUUID().toString().take(8)}",
            title = cleanName.substringBeforeLast("."),
            author = "Launcher Web Portal",
            category = FirmwareCategory.UI_LAUNCHER.label,
            deviceTagsCsv = board.id,
            family = board.family.name,
            version = "web",
            availableVersionsCsv = "web",
            framework = "M5Launcher / Web Binary",
            description = "Downloaded via PocketLauncher Web Portal ($cleanName).",
            coverImageUrl = null,
            downloadUrl = downloadUrl,
            localFilePath = null,
            fileExtension = ext,
            sizeBytes = 0L,
            sha256 = null,
            flashOffset = board.appOffset,
            defaultBaud = board.defaultBaud,
            flashMode = board.flashMode,
            repoSourceId = "launcher_web",
            isCustomUserAdded = true,
            downloadState = DownloadState.NOT_DOWNLOADED.name,
            downloadProgress = 0,
            updatedAt = System.currentTimeMillis(),
        )
        firmwareDao.upsert(item)
        taxonomyRepository.linkFirmwareBatch(listOf(item))
        downloadFirmwareOnDemand(item)
    }

    suspend fun downloadFirmwareOnDemand(
        item: FirmwareEntity,
        onProgress: (Int, Long, Long) -> Unit = { _, _, _ -> },
    ): Result<FirmwareEntity> = withContext(Dispatchers.IO) {
        if (item.downloadUrl.startsWith("local://") && item.localFilePath != null) {
            return@withContext Result.success(item)
        }
        try {
            firmwareDao.updateDownloadStatus(item.id, DownloadState.DOWNLOADING.name, 0, null)
            val req = Request.Builder()
                .url(item.downloadUrl)
                .header("User-Agent", "PocketBurner-Android/1.0")
                .build()

            val safeName = "${item.id}_${item.version}".replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val destFile = File(firmwareDir, "$safeName.${item.fileExtension.ifBlank { "bin" }}")
            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Download HTTP ${resp.code}")
                }
                val body = resp.body ?: throw IllegalStateException("Empty download stream")
                val total = body.contentLength().takeIf { it > 0 } ?: item.sizeBytes
                body.byteStream().use { input ->
                    destFile.outputStream().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var lastPct = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            downloaded += read
                            val pct = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else 50
                            if (pct != lastPct) {
                                lastPct = pct
                                firmwareDao.updateDownloadStatus(item.id, DownloadState.DOWNLOADING.name, pct, null)
                                onProgress(pct, downloaded, total)
                            }
                        }
                    }
                }
            }

            val computedSha = digest.digest().joinToString("") { "%02x".format(it) }
            if (!item.sha256.isNullOrBlank() && !item.sha256.equals(computedSha, ignoreCase = true)) {
                destFile.delete()
                throw IllegalStateException("SHA-256 mismatch! Expected ${item.sha256.take(12)}…, got ${computedSha.take(12)}…")
            }

            val updated = item.copy(
                localFilePath = destFile.absolutePath,
                sizeBytes = downloaded,
                sha256 = item.sha256 ?: computedSha,
                downloadState = DownloadState.DOWNLOADED.name,
                downloadProgress = 100,
                updatedAt = System.currentTimeMillis(),
            )
            firmwareDao.upsert(updated)
            Result.success(updated)
        } catch (e: Exception) {
            firmwareDao.updateDownloadStatus(item.id, DownloadState.ERROR.name, 0, null)
            Result.failure(e)
        }
    }

    suspend fun deleteLocalBinary(item: FirmwareEntity) = withContext(Dispatchers.IO) {
        item.localFilePath?.let { File(it).delete() }
        if (item.isCustomUserAdded && item.downloadUrl.startsWith("local://")) {
            firmwareDao.deleteById(item.id)
        } else {
            firmwareDao.updateDownloadStatus(item.id, DownloadState.NOT_DOWNLOADED.name, 0, null)
        }
    }

    internal fun parseM5BurnerFeed(json: String, repoId: String): List<FirmwareEntity> {
        val array = when {
            json.trim().startsWith("[") -> JSONArray(json)
            else -> {
                val root = JSONObject(json)
                root.optJSONArray("data")
                    ?: root.optJSONArray("firmwares")
                    ?: root.optJSONArray("list")
                    ?: root.optJSONArray("items")
                    ?: flattenCategoryObjectArrays(root)
                    ?: JSONArray().apply { put(root) }
            }
        }
        val items = mutableListOf<FirmwareEntity>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val name = obj.optString("name").ifBlank { obj.optString("title", "Firmware #$i") }
            val author = obj.optString("author").ifBlank { obj.optString("published_by", "M5Stack Community") }
            val category = FirmwareCategory.fromString(
                obj.optString("category").ifBlank { obj.optString("firmware_category") }
            ).label

            val devicesList = mutableListOf<String>()
            listOf("device", "devices", "board", "boards", "target", "targets").forEach { key ->
                obj.optJSONArray(key)?.let { arr ->
                    for (j in 0 until arr.length()) {
                        normalizeBoardTag(arr.optString(j))?.let { devicesList.add(it) }
                    }
                }
                val singleVal = obj.optString(key)
                if (singleVal.isNotBlank() && !singleVal.startsWith("[") && !singleVal.startsWith("{")) {
                    singleVal.split(",", "/", "|").forEach { part ->
                        normalizeBoardTag(part)?.let { devicesList.add(it) }
                    }
                }
            }
            if (devicesList.isEmpty()) {
                devicesList.addAll(inferDevicesFromAssetName(name.lowercase()))
            }
            val deviceCsv = devicesList.distinct().joinToString(",")

            val versions = mutableListOf<String>()
            var versionDownloadUrl = ""
            var versionFileOffset = ""
            obj.optJSONArray("versions")?.let { arr ->
                for (j in 0 until arr.length()) {
                    val vObj = arr.optJSONObject(j)
                    if (vObj != null) {
                        val ver = vObj.optString("version").ifBlank { vObj.optString("ver") }
                        if (ver.isNotBlank()) versions.add(ver)
                        if (versionDownloadUrl.isBlank()) {
                            versionDownloadUrl = vObj.optString("download_url")
                                .ifBlank { vObj.optString("url") }
                                .ifBlank { vObj.optString("file") }
                        }
                        if (versionFileOffset.isBlank()) {
                            versionFileOffset = vObj.optString("offset").ifBlank { vObj.optString("address") }
                        }
                    } else {
                        val s = arr.optString(j)
                        if (s.isNotBlank()) versions.add(s)
                    }
                }
            }
            val primaryVersion = obj.optString("version").ifBlank { versions.firstOrNull() ?: "1.0.0" }
            val versionsCsv = versions.ifEmpty { listOf(primaryVersion) }.joinToString(",")

            var firstFileOffset = versionFileOffset
            var firstFilePath = ""
            obj.optJSONArray("files")?.let { filesArr ->
                for (f in 0 until filesArr.length()) {
                    val fObj = filesArr.optJSONObject(f) ?: continue
                    val path = fObj.optString("path").ifBlank { fObj.optString("filename") }
                    val addr = fObj.optString("address").ifBlank { fObj.optString("offset") }
                    if (firstFilePath.isBlank() && path.isNotBlank()) {
                        firstFilePath = path
                        firstFileOffset = addr.ifBlank { firstFileOffset }
                    }
                    if (addr.equals("0x10000", ignoreCase = true) || addr.equals("0x0", ignoreCase = true) || addr.equals("0x0000", ignoreCase = true)) {
                        firstFilePath = path.ifBlank { firstFilePath }
                        firstFileOffset = addr
                    }
                }
            }

            val rawDownloadUrl = obj.optString("download_url")
                .ifBlank { obj.optString("url") }
                .ifBlank { obj.optString("bin_url") }
                .ifBlank { obj.optString("file") }
                .ifBlank { versionDownloadUrl }
                .ifBlank { firstFilePath }
            if (rawDownloadUrl.isBlank()) continue
            val downloadUrl = if (rawDownloadUrl.startsWith("http://") || rawDownloadUrl.startsWith("https://")) {
                rawDownloadUrl
            } else {
                "https://m5burner-cdn.m5stack.com/firmware/${rawDownloadUrl.trimStart('/')}"
            }

            val rawCoverUrl = obj.optString("cover")
                .ifBlank { obj.optString("cover_image") }
                .ifBlank { obj.optString("image") }
                .takeIf { it.isNotBlank() }
            val coverUrl = rawCoverUrl?.let {
                if (it.startsWith("http://") || it.startsWith("https://")) it
                else "https://m5burner-cdn.m5stack.com/cover/${it.trimStart('/')}"
            }

            val firstBoard = HardwareRegistry.findById(deviceCsv.substringBefore(",").trim())
            items.add(
                FirmwareEntity(
                    id = "${repoId}_${name.hashCode()}_$i",
                    title = name,
                    author = author,
                    category = category,
                    deviceTagsCsv = deviceCsv,
                    family = (firstBoard?.family ?: DeviceFamily.M5STACK).name,
                    version = primaryVersion,
                    availableVersionsCsv = versionsCsv,
                    framework = obj.optString("framework").ifBlank { "ESP-IDF / Arduino" },
                    description = obj.optString("description").ifBlank { "M5Burner / bmorcelli community firmware package." },
                    coverImageUrl = coverUrl,
                    downloadUrl = downloadUrl,
                    localFilePath = null,
                    fileExtension = downloadUrl.substringBefore("?").substringAfterLast(".", "bin"),
                    sizeBytes = obj.optLong("size", 0L),
                    sha256 = obj.optString("sha256").takeIf { it.isNotBlank() },
                    flashOffset = obj.optString("offset").ifBlank { firstFileOffset }.ifBlank { firstBoard?.appOffset ?: "0x0000" },
                    defaultBaud = obj.optInt("default_baud", firstBoard?.defaultBaud ?: 921600),
                    flashMode = obj.optString("flash_mode").ifBlank { firstBoard?.flashMode ?: "DIO" },
                    repoSourceId = repoId,
                    isCustomUserAdded = false,
                    downloadState = DownloadState.NOT_DOWNLOADED.name,
                    downloadProgress = 0,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
        return items
    }

    private fun flattenCategoryObjectArrays(root: JSONObject): JSONArray? {
        val merged = JSONArray()
        val keys = root.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val arr = root.optJSONArray(k) ?: continue
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                if (!obj.has("category") && k.isNotBlank()) {
                    obj.put("category", k)
                }
                merged.put(obj)
            }
        }
        return merged.takeIf { it.length() > 0 }
    }

    private fun normalizeBoardTag(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isBlank() || trimmed == "null") return null
        HardwareRegistry.findById(trimmed)?.let { return it.id }
        val lower = trimmed.lowercase()
        return inferDevicesFromAssetName(lower).firstOrNull() ?: trimmed
    }

    private fun parseRpiImagerFeed(json: String, repoId: String): List<FirmwareEntity> {
        val root = JSONObject(json)
        val osList = root.optJSONArray("os_list") ?: return emptyList()
        val result = mutableListOf<FirmwareEntity>()

        fun walk(arr: JSONArray, parentCategory: String) {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val sub = obj.optJSONArray("subitems")
                val name = obj.optString("name")
                if (sub != null && sub.length() > 0) {
                    walk(sub, name.ifBlank { parentCategory })
                    continue
                }
                val url = obj.optString("url")
                if (url.isBlank()) continue
                val devicesArr = obj.optJSONArray("devices")
                val devTags = buildList {
                    if (devicesArr != null) {
                        for (d in 0 until devicesArr.length()) {
                            val raw = devicesArr.optString(d).lowercase()
                            when {
                                raw.contains("pi5") -> add("Raspberry Pi 5")
                                raw.contains("pi4") -> add("Raspberry Pi 4B")
                                raw.contains("pi3") -> add("Raspberry Pi Zero 2 W")
                            }
                        }
                    }
                    if (isEmpty()) {
                        add("Raspberry Pi 5")
                        add("Raspberry Pi 4B")
                        add("Raspberry Pi Zero 2 W")
                    }
                }.distinct().joinToString(",")

                val version = obj.optString("release_date").ifBlank { "Latest" }
                val ext = url.substringBefore("?").substringAfterLast(".", "img.xz")
                result.add(
                    FirmwareEntity(
                        id = "${repoId}_${name.hashCode()}_${result.size}",
                        title = name,
                        author = "Raspberry Pi Foundation",
                        category = FirmwareCategory.TOOLS.label,
                        deviceTagsCsv = devTags,
                        family = DeviceFamily.RASPBERRY_PI.name,
                        version = version,
                        availableVersionsCsv = version,
                        framework = "Raspberry Pi OS ($parentCategory)",
                        description = obj.optString("description").ifBlank { "Official Raspberry Pi OS disk image." },
                        coverImageUrl = obj.optString("icon").takeIf { it.isNotBlank() },
                        downloadUrl = url,
                        localFilePath = null,
                        fileExtension = ext,
                        sizeBytes = obj.optLong("image_download_size", 0L),
                        sha256 = obj.optString("extract_sha256").takeIf { it.isNotBlank() },
                        flashOffset = "0x0000",
                        defaultBaud = 115200,
                        flashMode = "Raw Block (.img/.xz)",
                        repoSourceId = repoId,
                        isCustomUserAdded = false,
                        downloadState = DownloadState.NOT_DOWNLOADED.name,
                        downloadProgress = 0,
                        updatedAt = System.currentTimeMillis(),
                    )
                )
                if (result.size >= 30) return
            }
        }
        walk(osList, "OS")
        return result
    }

    internal fun parseGitHubReleaseFeed(json: String, repoId: String, repoName: String): List<FirmwareEntity> {
        val root = JSONObject(json)
        val tag = root.optString("tag_name", "latest")
        val body = root.optString("body", "").lineSequence().take(3).joinToString(" ").take(220)
        val author = root.optJSONObject("author")?.optString("login") ?: "GitHub Release"
        val assets = root.optJSONArray("assets") ?: return emptyList()
        val result = mutableListOf<FirmwareEntity>()

        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name")
            val lower = name.lowercase()
            if (!lower.endsWith(".bin") && !lower.endsWith(".uf2") && !lower.endsWith(".hex") && !lower.endsWith(".zip")) {
                continue
            }
            val url = asset.optString("browser_download_url")
            if (url.isBlank()) continue

            val inferredDevices = inferDevicesFromAssetName(lower)
            val firstBoard = HardwareRegistry.findById(inferredDevices.firstOrNull() ?: "ESP32 DevKit")
            val category = when {
                lower.contains("launcher") || repoId.contains("bmorcelli") -> FirmwareCategory.UI_LAUNCHER.label
                lower.contains("tasmota") -> FirmwareCategory.IOT_AUTOMATION.label
                lower.contains("micropython") -> FirmwareCategory.DEVELOPMENT_LEARNING.label
                else -> FirmwareCategory.TOOLS.label
            }

            result.add(
                FirmwareEntity(
                    id = "${repoId}_${name.hashCode()}",
                    title = name.substringBeforeLast("."),
                    author = author,
                    category = category,
                    deviceTagsCsv = inferredDevices.joinToString(","),
                    family = (firstBoard?.family ?: DeviceFamily.ESP_IOT).name,
                    version = tag,
                    availableVersionsCsv = tag,
                    framework = repoName.substringBefore(" "),
                    description = body.ifBlank { "Release asset $name from $repoName ($tag)." },
                    coverImageUrl = null,
                    downloadUrl = url,
                    localFilePath = null,
                    fileExtension = lower.substringAfterLast("."),
                    sizeBytes = asset.optLong("size", 0L),
                    sha256 = null,
                    flashOffset = firstBoard?.appOffset ?: "0x0000",
                    defaultBaud = firstBoard?.defaultBaud ?: 921600,
                    flashMode = firstBoard?.flashMode ?: "DIO",
                    repoSourceId = repoId,
                    isCustomUserAdded = false,
                    downloadState = DownloadState.NOT_DOWNLOADED.name,
                    downloadProgress = 0,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
        return result
    }

    private fun parseArduinoIndexFeed(json: String, repoId: String): List<FirmwareEntity> {
        val root = JSONObject(json)
        val packages = root.optJSONArray("packages") ?: return emptyList()
        val result = mutableListOf<FirmwareEntity>()

        for (p in 0 until packages.length()) {
            val pkg = packages.optJSONObject(p) ?: continue
            val maintainer = pkg.optString("maintainer", "Arduino")
            val platforms = pkg.optJSONArray("platforms") ?: continue
            val seenArch = mutableSetOf<String>()
            // Walk newest platforms first
            for (i in platforms.length() - 1 downTo 0) {
                val plat = platforms.optJSONObject(i) ?: continue
                val arch = plat.optString("architecture")
                if (!seenArch.add(arch)) continue
                val name = plat.optString("name", "Arduino $arch")
                val version = plat.optString("version", "1.0.0")
                val url = plat.optString("url")
                if (url.isBlank()) continue
                val boardsArr = plat.optJSONArray("boards")
                val boardNames = buildList {
                    if (boardsArr != null) {
                        for (b in 0 until boardsArr.length().coerceAtMost(5)) {
                            add(boardsArr.optJSONObject(b)?.optString("name") ?: "")
                        }
                    }
                }.filter { it.isNotBlank() }

                val mappedDevice = when {
                    arch.contains("renesas", true) -> "Arduino Uno R4 WiFi"
                    arch.contains("mega", true) || arch.contains("avr", true) -> "Arduino Uno R3,Arduino Mega 2560,Arduino Nano"
                    arch.contains("esp32", true) -> "Arduino Nano ESP32"
                    arch.contains("rp2040", true) -> "Raspberry Pi Pico"
                    arch.contains("sam", true) -> "Arduino Due"
                    else -> "Arduino Uno R3"
                }

                result.add(
                    FirmwareEntity(
                        id = "${repoId}_${arch}_$version",
                        title = "$name Core / Bootloader",
                        author = maintainer,
                        category = FirmwareCategory.DEVELOPMENT_LEARNING.label,
                        deviceTagsCsv = mappedDevice,
                        family = DeviceFamily.ARDUINO.name,
                        version = version,
                        availableVersionsCsv = version,
                        framework = "Arduino Core ($arch)",
                        description = "Official Arduino core archive supporting: ${boardNames.joinToString(", ").ifBlank { arch }}.",
                        coverImageUrl = null,
                        downloadUrl = url,
                        localFilePath = null,
                        fileExtension = url.substringAfterLast(".", "tar.bz2"),
                        sizeBytes = plat.optString("size").toLongOrNull() ?: 0L,
                        sha256 = plat.optString("checksum").removePrefix("SHA-256:").takeIf { it.isNotBlank() },
                        flashOffset = "0x0000",
                        defaultBaud = 115200,
                        flashMode = "STK500 / BOSSA",
                        repoSourceId = repoId,
                        isCustomUserAdded = false,
                        downloadState = DownloadState.NOT_DOWNLOADED.name,
                        downloadProgress = 0,
                        updatedAt = System.currentTimeMillis(),
                    )
                )
            }
        }
        return result
    }

    private fun parseCustomFeed(json: String, repoId: String): List<FirmwareEntity> {
        val trimmed = json.trim()
        return when {
            trimmed.contains("\"os_list\"") -> parseRpiImagerFeed(trimmed, repoId)
            trimmed.contains("\"assets\"") && trimmed.contains("\"tag_name\"") -> parseGitHubReleaseFeed(trimmed, repoId, "Custom GitHub")
            trimmed.contains("\"packages\"") && trimmed.contains("\"platforms\"") -> parseArduinoIndexFeed(trimmed, repoId)
            else -> parseM5BurnerFeed(trimmed, repoId)
        }
    }

    internal fun inferDevicesFromAssetName(lower: String): List<String> = when {
        lower.contains("cardputer-adv") || lower.contains("cardputer_adv") || lower.contains("cardputer adv") -> listOf("Cardputer ADV", "Cardputer")
        lower.contains("cardputer") -> listOf("Cardputer", "Cardputer ADV")
        lower.contains("stickc-plus2") || lower.contains("stickcplus2") || lower.contains("stickc_plus2") || lower.contains("plus2") -> listOf("StickC-Plus2")
        lower.contains("stickc-plus") || lower.contains("stickcplus") || lower.contains("stickc_plus") -> listOf("StickC-Plus")
        lower.contains("sticks3") -> listOf("StickS3")
        lower.contains("stickt2") -> listOf("StickT2")
        lower.contains("stickc") -> listOf("StickC", "StickC-Plus", "StickC-Plus2")
        lower.contains("cores3") -> listOf("CoreS3")
        lower.contains("core2") -> listOf("Core2")
        lower.contains("coreink") -> listOf("CoreInk")
        lower.contains("corep4") -> listOf("CoreP4X")
        lower.contains("corematrix") -> listOf("CoreMatrix")
        lower.contains("m5go") -> listOf("M5GO", "Core Series")
        lower.contains("fire") -> listOf("Fire", "Core Series")
        lower.contains("toughc5") -> listOf("ToughC5")
        lower.contains("tough") -> listOf("Tough", "Core2")
        lower.contains("dial") -> listOf("Dial")
        lower.contains("dinmeter") -> listOf("DinMeter")
        lower.contains("papers3") -> listOf("PaperS3", "Paper")
        lower.contains("papercolor") -> listOf("PaperColor", "Paper")
        lower.contains("papermono") -> listOf("PaperMono", "Paper")
        lower.contains("paperdiy") -> listOf("PaperDIY", "Paper")
        lower.contains("paper") -> listOf("Paper", "PaperS3")
        lower.contains("atoms3r") -> listOf("AtomS3R", "AtomS3")
        lower.contains("atoms3-lite") || lower.contains("atoms3lite") -> listOf("AtomS3-Lite", "AtomS3")
        lower.contains("atoms3") -> listOf("AtomS3", "Atom Series")
        lower.contains("atom-matrix") || lower.contains("atommatrix") -> listOf("Atom-Matrix", "Atom Series")
        lower.contains("atom-lite") || lower.contains("atomlite") -> listOf("Atom-Lite", "Atom Series")
        lower.contains("nanoc6") -> listOf("NanoC6", "ESP32-C6")
        lower.contains("nanoh2") -> listOf("NanoH2", "ESP32-H2")
        lower.contains("stamp-s3") || lower.contains("stamps3") -> listOf("Stamp-S3", "Stamp Series")
        lower.contains("stamp-c3") || lower.contains("stampc3") -> listOf("Stamp-C3", "Stamp Series")
        lower.contains("stamp-c5") || lower.contains("stampc5") -> listOf("Stamp-C5", "Stamp Series")
        lower.contains("stamp-c6") || lower.contains("stampc6") -> listOf("Stamp-C6LoRa", "Stamp Series")
        lower.contains("stamp-p4") || lower.contains("stampp4") -> listOf("Stamp-P4", "Stamp Series")
        lower.contains("stamp-pico") || lower.contains("stamppico") -> listOf("Stamp-Pico", "Stamp Series")
        lower.contains("capsule") -> listOf("Capsule")
        lower.contains("stackchan") -> listOf("StackChan")
        lower.contains("timercam") -> listOf("TimerCam")
        lower.contains("tab5") -> listOf("Tab5")
        lower.contains("station") -> listOf("Station")
        lower.contains("powerhub") -> listOf("PowerHub")
        lower.contains("vameter") -> listOf("VAMeter")
        lower.contains("xiaozhi") -> listOf("Xiaozhi Card Kit")
        lower.contains("nesso") -> listOf("Arduino Nesso N1")
        lower.contains("esp32s3") || lower.contains("esp32-s3") -> listOf("ESP32-S3", "CoreS3", "Cardputer", "AtomS3")
        lower.contains("esp32s2") || lower.contains("esp32-s2") -> listOf("ESP32-S2")
        lower.contains("esp32c6") || lower.contains("esp32-c6") -> listOf("ESP32-C6", "NanoC6", "Unit C6L")
        lower.contains("esp32c5") || lower.contains("esp32-c5") -> listOf("ESP32-C5", "ToughC5", "Stamp-C5")
        lower.contains("esp32c3") || lower.contains("esp32-c3") -> listOf("ESP32-C3", "Stamp-C3")
        lower.contains("esp32h2") || lower.contains("esp32-h2") -> listOf("ESP32-H2", "NanoH2")
        lower.contains("esp32p4") || lower.contains("esp32-p4") -> listOf("ESP32-P4", "Tab5", "CoreP4X")
        lower.contains("esp8266") || lower.contains("tasmota.bin") -> listOf("ESP8266")
        lower.contains("rp2040") || lower.contains("pico_w") || lower.contains("pico-") -> listOf("Raspberry Pi Pico")
        lower.contains("rp2350") || lower.contains("pico2") -> listOf("Raspberry Pi Pico 2")
        lower.contains("m5stack") || lower.contains("m5core") || lower.contains("core-s") -> listOf("Core Series", "Core2", "CoreS3")
        else -> listOf("ESP32 DevKit", "Core2", "StickC-Plus2")
    }

    private fun queryDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return uri.lastPathSegment
    }
}
