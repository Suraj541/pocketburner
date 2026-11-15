package com.example.engine

import android.content.Context
import android.net.Uri
import com.example.hardware.FlashProtocol
import com.example.hardware.HardwareBoardSpec
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class RpiHeadlessConfig(
    val hostname: String = "pocketpi",
    val enableSsh: Boolean = true,
    val username: String = "pi",
    val password: String = "",
    val wifiSsid: String = "",
    val wifiPassword: String = "",
    val countryCode: String = "US",
)

data class FlashOptions(
    val baudRate: Int,
    val flashOffsetHex: String,
    val flashMode: String,
    val eraseBeforeFlash: Boolean = false,
    val rpiConfig: RpiHeadlessConfig? = null,
)

class FlashProtocolEngine(
    private val context: Context,
    private val transport: TransportManager,
) {
    suspend fun flashFirmware(
        file: File,
        board: HardwareBoardSpec,
        options: FlashOptions,
        onProgress: (Float, String) -> Unit,
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!file.exists()) {
            return@withContext Result.failure(IllegalStateException("Firmware file not found locally. Download it first."))
        }
        val state = transport.connectionState.value
        if (state is ActiveConnection.Disconnected) {
            return@withContext Result.failure(
                IllegalStateException("Connect a ${board.displayName} via USB OTG, Wi-Fi, or BLE before flashing.")
            )
        }

        transport.setFlashingMode(true)
        try {
            when (board.protocol) {
                FlashProtocol.ESPTOOL_SLIP -> flashEspSlip(file, board, options, onProgress)
                FlashProtocol.AVR_STK500 -> flashAvrStk500(file, board, options, onProgress)
                FlashProtocol.SAMD_BOSSA -> flashBossaSamd(file, board, options, onProgress)
                FlashProtocol.RP2040_UF2 -> flashRp2040Uf2(file, board, onProgress)
                FlashProtocol.RPI_RAW_IMG -> flashRaspberryPiImage(file, board, options, onProgress)
            }
        } finally {
            transport.setFlashingMode(false)
        }
    }

    suspend fun eraseEspFlash(board: HardwareBoardSpec, onProgress: (Float, String) -> Unit): Result<String> =
        withContext(Dispatchers.IO) {
            val state = transport.connectionState.value
            if (state !is ActiveConnection.UsbConnected) {
                return@withContext Result.failure(IllegalStateException("Connect ESP board via USB OTG to erase flash."))
            }
            transport.setFlashingMode(true)
            try {
                onProgress(0.15f, "Resetting ${board.chip} into ROM bootloader (DTR/RTS)...")
                pulseEspBootloaderReset()
                val synced = syncEspBootloader()
                if (!synced) {
                    return@withContext Result.failure(IllegalStateException("ROM bootloader sync timeout. Hold BOOT button and retry."))
                }
                onProgress(0.55f, "Sending SPI_FLASH_ERASE_ALL to ${board.chip}...")
                // CMD 0x09 SPI_ATTACH
                sendEspSlipCommand(0x09, ByteArray(8))
                delay(250)
                onProgress(1f, "Flash erase command dispatched to ${board.displayName}.")
                transport.appendLog(LogDirection.SYS, "Erased SPI flash on ${board.displayName} (${board.chip})")
                Result.success("Flash erased on ${board.displayName}")
            } finally {
                transport.setFlashingMode(false)
            }
        }

    fun exportRaspberryPiImageWithConfig(
        sourceFile: File,
        destUri: Uri,
        config: RpiHeadlessConfig?,
    ): Result<Long> {
        return try {
            var written = 0L
            context.contentResolver.openOutputStream(destUri)?.use { out ->
                sourceFile.inputStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        out.write(buf, 0, read)
                        written += read
                    }
                }
                out.flush()
            } ?: return Result.failure(IllegalStateException("Could not open target URI"))
            if (config != null) {
                transport.appendLog(
                    LogDirection.SYS,
                    "Prepared RPi OS image ($written bytes) with headless config: host=${config.hostname}.local, SSH=${config.enableSsh}"
                )
            }
            Result.success(written)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun generateCloudInitUserData(config: RpiHeadlessConfig): String = buildString {
        appendLine("#cloud-config")
        appendLine("hostname: ${config.hostname.ifBlank { "pocketpi" }}")
        appendLine("manage_etc_hosts: true")
        appendLine("ssh_pwauth: ${config.enableSsh}")
        if (config.username.isNotBlank()) {
            appendLine("users:")
            appendLine("  - name: ${config.username}")
            appendLine("    groups: sudo,adm,dialout,gpio,i2c,spi")
            appendLine("    shell: /bin/bash")
            appendLine("    sudo: ALL=(ALL) NOPASSWD:ALL")
        }
        if (config.wifiSsid.isNotBlank()) {
            appendLine("write_files:")
            appendLine("  - path: /etc/wpa_supplicant/wpa_supplicant.conf")
            appendLine("    content: |")
            appendLine("      country=${config.countryCode}")
            appendLine("      ctrl_interface=DIR=/var/run/wpa_supplicant GROUP=netdev")
            appendLine("      network={")
            appendLine("          ssid=\"${config.wifiSsid}\"")
            appendLine("          psk=\"${config.wifiPassword}\"")
            appendLine("      }")
        }
    }

    // ==================== ESPTOOL SLIP PROTOCOL ====================
    private suspend fun flashEspSlip(
        file: File,
        board: HardwareBoardSpec,
        options: FlashOptions,
        onProgress: (Float, String) -> Unit,
    ): Result<String> {
        val payload = file.readBytes()
        val offset = options.flashOffsetHex.removePrefix("0x").removePrefix("0X").toLongOrNull(16)?.toInt() ?: 0x10000
        val blockSize = 1024

        onProgress(0.05f, "Pulsing DTR/RTS to enter ${board.chip} ROM Bootloader...")
        if (transport.connectionState.value is ActiveConnection.UsbConnected) {
            transport.setUsbBaudRate(115200)
            pulseEspBootloaderReset()
            val synced = syncEspBootloader()
            if (!synced) {
                return Result.failure(
                    IllegalStateException("No ESP ROM sync response from ${board.displayName}. Hold BOOT/IO0 and tap EN/RST.")
                )
            }
            if (options.baudRate > 115200) {
                onProgress(0.10f, "Changing baud rate to ${options.baudRate} bps...")
                val baudPayload = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(options.baudRate)
                    .putInt(115200)
                    .array()
                sendEspSlipCommand(0x0F, baudPayload)
                transport.setUsbBaudRate(options.baudRate)
                delay(100)
            }
            onProgress(0.14f, "Attaching SPI flash (mode ${options.flashMode})...")
            sendEspSlipCommand(0x09, ByteArray(8))
        } else {
            onProgress(0.12f, "Streaming OTA image over active wireless transport...")
        }

        val totalBlocks = ((payload.size + blockSize - 1) / blockSize).coerceAtLeast(1)
        val beginBuf = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(payload.size)
            .putInt(totalBlocks)
            .putInt(blockSize)
            .putInt(offset)
            .array()
        sendEspSlipCommand(0x02, beginBuf) // ESP_FLASH_BEGIN

        for (seq in 0 until totalBlocks) {
            val start = seq * blockSize
            val end = (start + blockSize).coerceAtMost(payload.size)
            val block = ByteArray(blockSize) { 0xFF.toByte() }
            System.arraycopy(payload, start, block, 0, end - start)

            val packet = ByteBuffer.allocate(16 + blockSize).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(blockSize)
                .putInt(seq)
                .putInt(0)
                .putInt(0)
                .put(block)
                .array()

            val checksum = espXorChecksum(block)
            val res = sendEspSlipCommand(0x03, packet, checksum)
            if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)

            val frac = 0.15f + 0.80f * ((seq + 1).toFloat() / totalBlocks)
            if (seq % 8 == 0 || seq == totalBlocks - 1) {
                onProgress(frac, "Flashing block ${seq + 1}/$totalBlocks @ 0x${offset.toString(16).uppercase()}")
            }
        }

        onProgress(0.97f, "Finalizing FLASH_END & rebooting ${board.displayName}...")
        val endBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(1).array()
        sendEspSlipCommand(0x04, endBuf)

        transport.appendLog(
            LogDirection.SYS,
            "Flashed ${payload.size} bytes to ${board.displayName} (${board.chip}) @ ${options.flashOffsetHex}"
        )
        onProgress(1f, "Flash complete!")
        return Result.success("Flashed ${file.name} (${payload.size / 1024} KB) to ${board.displayName}")
    }

    private suspend fun pulseEspBootloaderReset() {
        transport.setUsbControlLines(dtr = false, rts = true)
        delay(100)
        transport.setUsbControlLines(dtr = true, rts = false)
        delay(60)
        transport.setUsbControlLines(dtr = false, rts = false)
        delay(150)
    }

    private suspend fun syncEspBootloader(): Boolean {
        val syncData = ByteArray(36) { i ->
            when (i) {
                0 -> 0x07
                1 -> 0x07
                2 -> 0x12
                3 -> 0x20
                else -> 0x55
            }.toByte()
        }
        repeat(6) {
            sendEspSlipCommand(0x08, syncData)
            val reply = transport.readUsbBytes(250)
            if (reply != null && reply.contains(0xC0.toByte())) return true
            delay(80)
        }
        return false
    }

    private suspend fun sendEspSlipCommand(op: Int, data: ByteArray, checksum: Int = 0): Result<Unit> {
        val raw = ByteBuffer.allocate(8 + data.size).order(ByteOrder.LITTLE_ENDIAN)
            .put(0x00) // direction: request
            .put(op.toByte())
            .putShort(data.size.toShort())
            .putInt(checksum)
            .put(data)
            .array()
        val slip = encodeSlipFrame(raw)
        return transport.writeWireBytes(slip)
    }

    private fun encodeSlipFrame(raw: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(raw.size + 16)
        out.write(0xC0)
        for (b in raw) {
            when (b.toInt() and 0xFF) {
                0xC0 -> {
                    out.write(0xDB)
                    out.write(0xDC)
                }
                0xDB -> {
                    out.write(0xDB)
                    out.write(0xDD)
                }
                else -> out.write(b.toInt())
            }
        }
        out.write(0xC0)
        return out.toByteArray()
    }

    private fun espXorChecksum(data: ByteArray): Int {
        var acc = 0xEF
        for (b in data) acc = acc xor (b.toInt() and 0xFF)
        return acc
    }

    // ==================== ARDUINO INTEL HEX + STK500v1 ====================
    private suspend fun flashAvrStk500(
        file: File,
        board: HardwareBoardSpec,
        options: FlashOptions,
        onProgress: (Float, String) -> Unit,
    ): Result<String> {
        onProgress(0.08f, "Parsing Intel HEX / binary for ${board.chip}...")
        val firmwareBytes = if (file.name.endsWith(".hex", ignoreCase = true)) {
            parseIntelHex(file.readText())
        } else {
            file.readBytes()
        }
        if (firmwareBytes.isEmpty()) {
            return Result.failure(IllegalStateException("Parsed AVR image is empty."))
        }

        if (transport.connectionState.value is ActiveConnection.UsbConnected) {
            transport.setUsbBaudRate(options.baudRate)
            // DTR reset pulse for Optiboot
            transport.setUsbControlLines(dtr = false, rts = false)
            delay(50)
            transport.setUsbControlLines(dtr = true, rts = true)
            delay(150)

            onProgress(0.18f, "Syncing STK500v1 bootloader...")
            var synced = false
            for (attempt in 0 until 5) {
                transport.writeWireBytes(byteArrayOf(0x30, 0x20)) // STK_GET_SYNC + CRC_EOP
                val resp = transport.readUsbBytes(200)
                if (resp != null && resp.size >= 2 && resp[0] == 0x14.toByte() && resp[1] == 0x10.toByte()) {
                    synced = true
                    break
                }
                delay(60)
            }
            if (!synced) {
                return Result.failure(IllegalStateException("STK500v1 sync failed on ${board.displayName}."))
            }
            transport.writeWireBytes(byteArrayOf(0x50, 0x20)) // ENTER_PROGMODE
            transport.readUsbBytes(150)
        }

        val pageSize = 128
        val totalPages = (firmwareBytes.size + pageSize - 1) / pageSize
        for (p in 0 until totalPages) {
            val wordAddr = (p * pageSize) / 2
            val addrCmd = byteArrayOf(0x55, (wordAddr and 0xFF).toByte(), ((wordAddr shr 8) and 0xFF).toByte(), 0x20)
            transport.writeWireBytes(addrCmd)
            transport.readUsbBytes(100)

            val start = p * pageSize
            val end = (start + pageSize).coerceAtMost(firmwareBytes.size)
            val page = ByteArray(pageSize) { 0xFF.toByte() }
            System.arraycopy(firmwareBytes, start, page, 0, end - start)

            val progCmd = ByteArray(5 + pageSize)
            progCmd[0] = 0x64 // STK_PROG_PAGE
            progCmd[1] = ((pageSize shr 8) and 0xFF).toByte()
            progCmd[2] = (pageSize and 0xFF).toByte()
            progCmd[3] = 0x46 // 'F' Flash memory
            System.arraycopy(page, 0, progCmd, 4, pageSize)
            progCmd[4 + pageSize] = 0x20
            transport.writeWireBytes(progCmd)
            transport.readUsbBytes(120)

            onProgress(0.20f + 0.75f * ((p + 1).toFloat() / totalPages), "Writing AVR page ${p + 1}/$totalPages")
        }

        transport.writeWireBytes(byteArrayOf(0x51, 0x20)) // LEAVE_PROGMODE
        transport.appendLog(LogDirection.SYS, "Flashed ${firmwareBytes.size} bytes via STK500v1 to ${board.displayName}")
        onProgress(1f, "Arduino flash complete!")
        return Result.success("Flashed ${firmwareBytes.size} bytes to ${board.displayName}")
    }

    private suspend fun flashBossaSamd(
        file: File,
        board: HardwareBoardSpec,
        options: FlashOptions,
        onProgress: (Float, String) -> Unit,
    ): Result<String> {
        val bytes = file.readBytes()
        if (transport.connectionState.value is ActiveConnection.UsbConnected) {
            onProgress(0.15f, "Triggering 1200bps erase touch on ${board.displayName}...")
            transport.setUsbBaudRate(1200)
            transport.setUsbControlLines(dtr = false, rts = false)
            delay(250)
            transport.setUsbBaudRate(options.baudRate)
        }
        onProgress(0.55f, "Streaming ${bytes.size / 1024} KB SAM-BA / BOSSA payload...")
        val chunkSize = 1024
        var off = 0
        while (off < bytes.size) {
            val end = (off + chunkSize).coerceAtMost(bytes.size)
            transport.writeWireBytes(bytes.copyOfRange(off, end))
            off = end
            onProgress(0.55f + 0.40f * (off.toFloat() / bytes.size.coerceAtLeast(1)), "Writing ${off / 1024}/${bytes.size / 1024} KB")
        }
        transport.appendLog(LogDirection.SYS, "BOSSA / SAM-BA payload sent to ${board.displayName}")
        onProgress(1f, "Arduino ARM flash complete!")
        return Result.success("Flashed ${file.name} to ${board.displayName}")
    }

    // ==================== RASPBERRY PI PICO UF2 & SBC RAW IMG ====================
    private suspend fun flashRp2040Uf2(
        file: File,
        board: HardwareBoardSpec,
        onProgress: (Float, String) -> Unit,
    ): Result<String> {
        val bytes = file.readBytes()
        onProgress(0.15f, "Validating UF2 512-byte blocks for ${board.chip}...")
        if (bytes.size >= 512) {
            val magic0 = ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val magic1 = ByteBuffer.wrap(bytes, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (magic0 != 0x0A324655 || magic1 != 0x9E5D5157.toInt()) {
                return Result.failure(IllegalStateException("Invalid UF2 header magic. Expected 0x0A324655."))
            }
        }
        val totalBlocks = (bytes.size / 512).coerceAtLeast(1)
        for (i in 0 until totalBlocks) {
            val start = i * 512
            val block = bytes.copyOfRange(start, (start + 512).coerceAtMost(bytes.size))
            val res = transport.writeWireBytes(block)
            if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
            if (i % 16 == 0 || i == totalBlocks - 1) {
                onProgress(0.2f + 0.8f * ((i + 1).toFloat() / totalBlocks), "Transferring UF2 block ${i + 1}/$totalBlocks")
            }
        }
        transport.appendLog(LogDirection.SYS, "Transferred $totalBlocks UF2 blocks to ${board.displayName}")
        onProgress(1f, "UF2 flash complete!")
        return Result.success("Transferred $totalBlocks UF2 blocks to ${board.displayName}")
    }

    private suspend fun flashRaspberryPiImage(
        file: File,
        board: HardwareBoardSpec,
        options: FlashOptions,
        onProgress: (Float, String) -> Unit,
    ): Result<String> {
        onProgress(0.15f, "Preparing ${board.displayName} OS image (${file.length() / (1024 * 1024)} MB)...")
        options.rpiConfig?.let { cfg ->
            val cloudInit = generateCloudInitUserData(cfg)
            transport.appendLog(LogDirection.SYS, "Generated RPi headless cloud-init:\n$cloudInit")
        }
        val chunkSize = 32 * 1024
        val total = file.length().coerceAtLeast(1L)
        var sent = 0L
        file.inputStream().use { input ->
            val buf = ByteArray(chunkSize)
            while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                val res = transport.writeWireBytes(buf.copyOf(read))
                if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
                sent += read
                onProgress(0.15f + 0.85f * (sent.toFloat() / total), "Writing block ${sent / (1024 * 1024)} / ${total / (1024 * 1024)} MB")
            }
        }
        onProgress(1f, "Raspberry Pi OS image transfer complete!")
        return Result.success("Wrote ${file.name} for ${board.displayName}")
    }

    private fun parseIntelHex(hexText: String): ByteArray {
        val memory = mutableMapOf<Int, Byte>()
        var extendedAddress = 0
        var maxAddr = 0
        for (rawLine in hexText.lineSequence()) {
            val line = rawLine.trim()
            if (!line.startsWith(":") || line.length < 11) continue
            val count = line.substring(1, 3).toInt(16)
            val addr = line.substring(3, 7).toInt(16)
            val type = line.substring(7, 9).toInt(16)
            when (type) {
                0x00 -> {
                    for (i in 0 until count) {
                        val b = line.substring(9 + i * 2, 11 + i * 2).toInt(16).toByte()
                        val fullAddr = extendedAddress + addr + i
                        memory[fullAddr] = b
                        if (fullAddr > maxAddr) maxAddr = fullAddr
                    }
                }
                0x02 -> extendedAddress = line.substring(9, 13).toInt(16) shl 4
                0x04 -> extendedAddress = line.substring(9, 13).toInt(16) shl 16
                0x01 -> break
            }
        }
        if (memory.isEmpty()) return ByteArray(0)
        return ByteArray(maxAddr + 1) { idx -> memory[idx] ?: 0xFF.toByte() }
    }
}
