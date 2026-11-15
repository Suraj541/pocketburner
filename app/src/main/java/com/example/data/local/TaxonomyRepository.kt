package com.example.data.local

import com.example.hardware.DeviceFamily
import com.example.hardware.FirmwareCategory
import com.example.hardware.HardwareBoardSpec
import com.example.hardware.HardwareRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class TaxonomyRepository(
    private val taxonomyDao: TaxonomyDao,
) {
    val categories: Flow<List<CategoryEntity>> = taxonomyDao.observeCategories()
    val seriesWithDevices: Flow<List<DeviceSeriesWithDevices>> = taxonomyDao.observeSeriesWithDevices()
    val allDevices: Flow<List<DeviceEntity>> = taxonomyDao.observeAllDevices()

    suspend fun ensureTaxonomySeeded() = withContext(Dispatchers.IO) {
        if (taxonomyDao.categoryCount() == 0) {
            val categoryEntities = FirmwareCategory.entries.mapIndexed { idx, cat ->
                CategoryEntity(
                    id = cat.name,
                    name = cat.label,
                    displayOrder = idx,
                )
            }
            taxonomyDao.upsertCategories(categoryEntities)
        }

        if (taxonomyDao.deviceCount() == 0) {
            val seriesOrder = mutableListOf<DeviceSeriesEntity>()
            val seenSeries = mutableSetOf<String>()

            // Top-level family root nodes for hierarchical navigation
            val familyRoots = listOf(
                DeviceSeriesEntity("ROOT_M5STACK", "M5Stack Ecosystem", DeviceFamily.M5STACK.name, null, 0),
                DeviceSeriesEntity("ROOT_ESP_IOT", "Espressif ESP IoT", DeviceFamily.ESP_IOT.name, null, 1),
                DeviceSeriesEntity("ROOT_ARDUINO", "Arduino", DeviceFamily.ARDUINO.name, null, 2),
                DeviceSeriesEntity("ROOT_RASPBERRY_PI", "Raspberry Pi", DeviceFamily.RASPBERRY_PI.name, null, 3),
            )
            familyRoots.forEach {
                seriesOrder.add(it)
                seenSeries.add(it.id)
            }

            HardwareRegistry.boards.forEach { board ->
                if (seenSeries.add(board.seriesGroup)) {
                    val parentRoot = when (board.family) {
                        DeviceFamily.M5STACK -> "ROOT_M5STACK"
                        DeviceFamily.ESP_IOT -> "ROOT_ESP_IOT"
                        DeviceFamily.ARDUINO -> "ROOT_ARDUINO"
                        DeviceFamily.RASPBERRY_PI -> "ROOT_RASPBERRY_PI"
                    }
                    seriesOrder.add(
                        DeviceSeriesEntity(
                            id = board.seriesGroup,
                            name = board.seriesGroup,
                            family = board.family.name,
                            parentSeriesId = parentRoot,
                            displayOrder = seriesOrder.size,
                        )
                    )
                }
            }
            taxonomyDao.upsertSeries(seriesOrder)

            val deviceEntities = HardwareRegistry.boards.mapIndexed { idx, spec ->
                spec.toEntity(order = idx, parentDeviceId = inferParentDeviceId(spec))
            }
            taxonomyDao.upsertDevices(deviceEntities)
        }
    }

    suspend fun linkFirmwareBatch(items: List<FirmwareEntity>) = withContext(Dispatchers.IO) {
        ensureTaxonomySeeded()
        val catRefs = mutableListOf<FirmwareCategoryCrossRef>()
        val devRefs = mutableListOf<FirmwareDeviceCrossRef>()
        val knownIds = HardwareRegistry.boards.map { it.id }.toSet()

        items.forEach { fw ->
            val cat = FirmwareCategory.fromString(fw.category)
            catRefs.add(FirmwareCategoryCrossRef(fw.id, cat.name))

            fw.deviceTagsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach { tag ->
                val matched = knownIds.firstOrNull { it.equals(tag, ignoreCase = true) }
                if (matched != null) {
                    devRefs.add(FirmwareDeviceCrossRef(fw.id, matched))
                }
            }
        }
        if (catRefs.isNotEmpty()) taxonomyDao.insertFirmwareCategoryRefs(catRefs)
        if (devRefs.isNotEmpty()) taxonomyDao.insertFirmwareDeviceRefs(devRefs)
    }

    private fun inferParentDeviceId(spec: HardwareBoardSpec): String? = when (spec.id) {
        "Core2", "CoreS3", "CoreInk", "CoreP4X", "CoreMatrix", "Fire", "M5GO", "Tough", "ToughC5", "Tab5", "Station" -> "Core Series"
        "Atom-Lite", "Atom-Matrix", "AtomS3", "AtomS3-Lite", "AtomS3R" -> "Atom Series"
        "Stamp-S3", "Stamp-C3", "Stamp-Pico", "Stamp-P4", "Stamp-C5", "Stamp-C6LoRa" -> "Stamp Series"
        "StickC-Plus", "StickC-Plus2", "StickS3", "StickT2" -> "StickC"
        "Cardputer ADV" -> "Cardputer"
        "PaperS3", "PaperDIY", "PaperColor", "PaperMono" -> "Paper"
        else -> null
    }

    private fun HardwareBoardSpec.toEntity(order: Int, parentDeviceId: String?) = DeviceEntity(
        id = id,
        seriesId = seriesGroup,
        parentDeviceId = parentDeviceId,
        displayName = displayName,
        family = family.name,
        chip = chip,
        architecture = architecture,
        cpuClockMhz = cpuClockMhz,
        flashSizeLabel = flashSizeLabel,
        ramLabel = ramLabel,
        defaultBaud = defaultBaud,
        bootloaderOffset = bootloaderOffset,
        appOffset = appOffset,
        flashMode = flashMode,
        chipDetectMagic = chipDetectMagic,
        protocol = protocol.name,
        logicVoltage = logicVoltage,
        gpioPinoutSummary = gpioPinoutSummary,
        notes = notes,
        displayOrder = order,
    )
}
