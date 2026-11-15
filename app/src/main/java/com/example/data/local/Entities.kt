package com.example.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation

enum class DownloadState {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
    ERROR,
}

enum class RepoParserType {
    M5BURNER,
    RPI_IMAGER,
    GITHUB_RELEASE,
    ARDUINO_INDEX,
    CUSTOM_JSON,
}

enum class TransportType {
    USB_OTG,
    BLE,
    WIFI_TCP_TLS,
}

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val displayOrder: Int,
)

@Entity(
    tableName = "device_series",
    indices = [Index(value = ["parentSeriesId"]), Index(value = ["family"])],
)
data class DeviceSeriesEntity(
    @PrimaryKey val id: String,
    val name: String,
    val family: String,
    val parentSeriesId: String?,
    val displayOrder: Int,
)

@Entity(
    tableName = "devices",
    foreignKeys = [
        ForeignKey(
            entity = DeviceSeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["seriesId"]),
        Index(value = ["parentDeviceId"]),
        Index(value = ["family"]),
    ],
)
data class DeviceEntity(
    @PrimaryKey val id: String,
    val seriesId: String,
    val parentDeviceId: String?,
    val displayName: String,
    val family: String,
    val chip: String,
    val architecture: String,
    val cpuClockMhz: Int,
    val flashSizeLabel: String,
    val ramLabel: String,
    val defaultBaud: Int,
    val bootloaderOffset: String,
    val appOffset: String,
    val flashMode: String,
    val chipDetectMagic: String,
    val protocol: String,
    val logicVoltage: String,
    val gpioPinoutSummary: String,
    val notes: String,
    val displayOrder: Int,
)

@Entity(
    tableName = "firmware_items",
    indices = [Index(value = ["repoSourceId"]), Index(value = ["category"]), Index(value = ["family"])],
)
data class FirmwareEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val category: String,
    val deviceTagsCsv: String,
    val family: String,
    val version: String,
    val availableVersionsCsv: String,
    val framework: String,
    val description: String,
    val coverImageUrl: String?,
    val downloadUrl: String,
    val localFilePath: String?,
    val fileExtension: String,
    val sizeBytes: Long,
    val sha256: String?,
    val flashOffset: String,
    val defaultBaud: Int,
    val flashMode: String,
    val repoSourceId: String,
    val isCustomUserAdded: Boolean,
    val downloadState: String,
    val downloadProgress: Int,
    val updatedAt: Long,
)

@Entity(
    tableName = "firmware_category_xref",
    primaryKeys = ["firmwareId", "categoryId"],
    foreignKeys = [
        ForeignKey(
            entity = FirmwareEntity::class,
            parentColumns = ["id"],
            childColumns = ["firmwareId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["categoryId"]), Index(value = ["firmwareId"])],
)
data class FirmwareCategoryCrossRef(
    val firmwareId: String,
    val categoryId: String,
)

@Entity(
    tableName = "firmware_device_xref",
    primaryKeys = ["firmwareId", "deviceId"],
    foreignKeys = [
        ForeignKey(
            entity = FirmwareEntity::class,
            parentColumns = ["id"],
            childColumns = ["firmwareId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = DeviceEntity::class,
            parentColumns = ["id"],
            childColumns = ["deviceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["deviceId"]), Index(value = ["firmwareId"])],
)
data class FirmwareDeviceCrossRef(
    val firmwareId: String,
    val deviceId: String,
)

data class DeviceSeriesWithDevices(
    @Embedded val series: DeviceSeriesEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "seriesId",
    )
    val devices: List<DeviceEntity>,
)

data class FirmwareWithTaxonomy(
    @Embedded val firmware: FirmwareEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = FirmwareCategoryCrossRef::class,
            parentColumn = "firmwareId",
            entityColumn = "categoryId",
        ),
    )
    val categories: List<CategoryEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = FirmwareDeviceCrossRef::class,
            parentColumn = "firmwareId",
            entityColumn = "deviceId",
        ),
    )
    val devices: List<DeviceEntity>,
)

@Entity(tableName = "repo_sources")
data class RepoSourceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    val parserType: String,
    val enabled: Boolean,
    val lastSyncedAt: Long,
    val lastError: String?,
    val itemCount: Int,
)

@Entity(tableName = "scripts")
data class ScriptEntity(
    @PrimaryKey val id: String,
    val fileName: String,
    val language: String,
    val filePath: String,
    val targetDeviceId: String,
    val sizeBytes: Long,
    val sha256: String,
    val updatedAt: Long,
)

@Entity(tableName = "connection_profiles")
data class ConnectionProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val transportType: String,
    val address: String,
    val port: Int,
    val useTls: Boolean,
    val baudRate: Int,
    val targetBoardId: String,
    val lastConnectedAt: Long,
)
