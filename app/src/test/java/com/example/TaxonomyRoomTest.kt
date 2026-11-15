package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.DownloadState
import com.example.data.local.FirmwareEntity
import com.example.data.local.TaxonomyRepository
import com.example.hardware.DeviceFamily
import com.example.hardware.FirmwareCategory
import com.example.hardware.HardwareRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TaxonomyRoomTest {

    private lateinit var db: AppDatabase
    private lateinit var taxonomyRepo: TaxonomyRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        taxonomyRepo = TaxonomyRepository(db.taxonomyDao())
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun taxonomySeeding_storesAllCategoriesAndHierarchicalDeviceTree() = runTest {
        taxonomyRepo.ensureTaxonomySeeded()

        val categories = db.taxonomyDao().observeCategories().first()
        assertEquals(18, categories.size)
        assertEquals("All categories", categories.first().name)
        assertEquals("Utilities", categories.last().name)

        val seriesWithDevices = db.taxonomyDao().observeSeriesWithDevices().first()
        assertTrue(seriesWithDevices.any { it.series.id == "ROOT_M5STACK" })
        assertTrue(seriesWithDevices.any { it.series.id == "Core Series" && it.series.parentSeriesId == "ROOT_M5STACK" })

        val totalDevices = db.taxonomyDao().observeAllDevices().first()
        assertEquals(HardwareRegistry.boards.size, totalDevices.size)

        val coreChildren = db.taxonomyDao().observeChildDevices("Core Series").first()
        assertTrue(coreChildren.any { it.id == "CoreS3" })
        assertTrue(coreChildren.any { it.id == "Core2" })
    }

    @Test
    fun offlineFirmwareQuery_filtersByCategoryAndHierarchicalParentOrDevice() = runTest {
        taxonomyRepo.ensureTaxonomySeeded()

        val fw1 = FirmwareEntity(
            id = "fw_cardputer_test",
            title = "Marauder WiFi Tool",
            author = "Community",
            category = FirmwareCategory.SECURITY_RESEARCH.label,
            deviceTagsCsv = "Cardputer,Cardputer ADV,CoreS3",
            family = DeviceFamily.M5STACK.name,
            version = "1.2.0",
            availableVersionsCsv = "1.2.0",
            framework = "ESP-IDF",
            description = "Offline test entry",
            coverImageUrl = null,
            downloadUrl = "https://example.com/marauder.bin",
            localFilePath = null,
            fileExtension = "bin",
            sizeBytes = 102400L,
            sha256 = null,
            flashOffset = "0x0000",
            defaultBaud = 921600,
            flashMode = "QIO",
            repoSourceId = "repo_m5burner",
            isCustomUserAdded = false,
            downloadState = DownloadState.NOT_DOWNLOADED.name,
            downloadProgress = 0,
            updatedAt = 1000L,
        )
        val fw2 = fw1.copy(
            id = "fw_rpi_test",
            title = "Raspberry Pi OS Lite",
            category = FirmwareCategory.TOOLS.label,
            deviceTagsCsv = "Raspberry Pi 5,Raspberry Pi 4B",
            family = DeviceFamily.RASPBERRY_PI.name,
        )
        db.firmwareDao().upsertAll(listOf(fw1, fw2))
        taxonomyRepo.linkFirmwareBatch(listOf(fw1, fw2))

        // Filter by category
        val securityItems = db.firmwareDao().observeFilteredOffline(
            categoryId = FirmwareCategory.SECURITY_RESEARCH.name,
            categoryLabel = FirmwareCategory.SECURITY_RESEARCH.label,
            deviceId = "All devices",
        ).first()
        assertEquals(1, securityItems.size)
        assertEquals("fw_cardputer_test", securityItems.first().id)

        // Filter by hierarchical series ("Core Series" matches CoreS3)
        val coreSeriesItems = db.firmwareDao().observeFilteredOffline(
            categoryId = "ALL",
            categoryLabel = "All categories",
            deviceId = "Core Series",
        ).first()
        assertEquals(1, coreSeriesItems.size)
        assertEquals("fw_cardputer_test", coreSeriesItems.first().id)

        // Filter by specific device ("Raspberry Pi 5")
        val rpiItems = db.firmwareDao().observeFilteredOffline(
            categoryId = "ALL",
            categoryLabel = "All categories",
            deviceId = "Raspberry Pi 5",
        ).first()
        assertEquals(1, rpiItems.size)
        assertEquals("fw_rpi_test", rpiItems.first().id)
    }
}
