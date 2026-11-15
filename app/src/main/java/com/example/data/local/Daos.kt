package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TaxonomyDao {
    @Query("SELECT * FROM categories ORDER BY displayOrder ASC")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun categoryCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategories(categories: List<CategoryEntity>)

    @Query("SELECT * FROM device_series ORDER BY displayOrder ASC")
    fun observeSeries(): Flow<List<DeviceSeriesEntity>>

    @Transaction
    @Query("SELECT * FROM device_series ORDER BY displayOrder ASC")
    fun observeSeriesWithDevices(): Flow<List<DeviceSeriesWithDevices>>

    @Query("SELECT * FROM devices ORDER BY displayOrder ASC")
    fun observeAllDevices(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE seriesId = :seriesId ORDER BY displayOrder ASC")
    fun observeDevicesBySeries(seriesId: String): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE parentDeviceId = :parentId ORDER BY displayOrder ASC")
    fun observeChildDevices(parentId: String): Flow<List<DeviceEntity>>

    @Query("SELECT COUNT(*) FROM devices")
    suspend fun deviceCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSeries(series: List<DeviceSeriesEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDevices(devices: List<DeviceEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFirmwareCategoryRefs(refs: List<FirmwareCategoryCrossRef>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFirmwareDeviceRefs(refs: List<FirmwareDeviceCrossRef>)
}

@Dao
interface FirmwareDao {
    @Query("SELECT * FROM firmware_items ORDER BY isCustomUserAdded DESC, updatedAt DESC")
    fun observeAll(): Flow<List<FirmwareEntity>>

    @Transaction
    @Query("SELECT * FROM firmware_items ORDER BY isCustomUserAdded DESC, updatedAt DESC")
    fun observeAllWithTaxonomy(): Flow<List<FirmwareWithTaxonomy>>

    @Transaction
    @Query(
        """
        SELECT DISTINCT f.* FROM firmware_items f
        LEFT JOIN firmware_category_xref fc ON f.id = fc.firmwareId
        LEFT JOIN firmware_device_xref fd ON f.id = fd.firmwareId
        LEFT JOIN devices d ON fd.deviceId = d.id
        WHERE (:categoryId = 'ALL' OR fc.categoryId = :categoryId OR LOWER(f.category) = LOWER(:categoryLabel))
          AND (:deviceId = 'All devices' OR fd.deviceId = :deviceId OR d.seriesId = :deviceId OR d.parentDeviceId = :deviceId OR LOWER(f.deviceTagsCsv) LIKE '%' || LOWER(:deviceId) || '%')
        ORDER BY f.isCustomUserAdded DESC, f.updatedAt DESC
        """
    )
    fun observeFilteredOffline(
        categoryId: String,
        categoryLabel: String,
        deviceId: String,
    ): Flow<List<FirmwareEntity>>

    @Query("SELECT * FROM firmware_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FirmwareEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<FirmwareEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: FirmwareEntity)

    @Query("UPDATE firmware_items SET downloadState = :state, downloadProgress = :progress, localFilePath = :localPath WHERE id = :id")
    suspend fun updateDownloadStatus(id: String, state: String, progress: Int, localPath: String?)

    @Query("UPDATE firmware_items SET userNote = :note, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateUserNote(id: String, note: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE firmware_items SET downloadCount = downloadCount + 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun incrementDownloadCount(id: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM firmware_items WHERE repoSourceId = :repoId AND isCustomUserAdded = 0 AND downloadState != 'DOWNLOADED'")
    suspend fun clearRemoteByRepo(repoId: String)

    @Query("DELETE FROM firmware_items WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface RepoSourceDao {
    @Query("SELECT * FROM repo_sources ORDER BY name ASC")
    fun observeAll(): Flow<List<RepoSourceEntity>>

    @Query("SELECT * FROM repo_sources")
    suspend fun getAll(): List<RepoSourceEntity>

    @Query("SELECT * FROM repo_sources WHERE enabled = 1")
    suspend fun getEnabled(): List<RepoSourceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(repo: RepoSourceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(repos: List<RepoSourceEntity>)

    @Query("DELETE FROM repo_sources WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ScriptDao {
    @Query("SELECT * FROM scripts ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ScriptEntity>>

    @Query("SELECT * FROM scripts WHERE fileName = :fileName LIMIT 1")
    suspend fun findByName(fileName: String): ScriptEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(script: ScriptEntity)

    @Query("DELETE FROM scripts WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ConnectionProfileDao {
    @Query("SELECT * FROM connection_profiles ORDER BY lastConnectedAt DESC")
    fun observeAll(): Flow<List<ConnectionProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ConnectionProfileEntity)

    @Query("DELETE FROM connection_profiles WHERE id = :id")
    suspend fun deleteById(id: String)
}
