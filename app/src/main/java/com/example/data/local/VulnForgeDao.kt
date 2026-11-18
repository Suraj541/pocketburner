package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VulnForgeDao {
    @Query("SELECT COUNT(*) FROM vulnerabilities")
    suspend fun vulnerabilityCount(): Int

    @Query("SELECT * FROM vulnerabilities ORDER BY isKev DESC, publishedAt DESC")
    fun observeVulnerabilities(): Flow<List<VulnerabilityEntity>>

    @Query("SELECT * FROM vulnerabilities WHERE id = :id LIMIT 1")
    suspend fun getVulnerability(id: String): VulnerabilityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVulnerabilities(items: List<VulnerabilityEntity>)

    @Query("SELECT * FROM labs ORDER BY updatedAt DESC")
    fun observeLabs(): Flow<List<LabEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLab(lab: LabEntity)

    @Query("UPDATE labs SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateLabStatus(id: String, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM lab_snapshots WHERE labId = :labId ORDER BY createdAt DESC")
    fun observeSnapshots(labId: String): Flow<List<LabSnapshotEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: LabSnapshotEntity)

    @Query("SELECT * FROM lab_events ORDER BY createdAt DESC LIMIT 50")
    fun observeRecentEvents(): Flow<List<LabEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: LabEventEntity)

    @Query("SELECT * FROM vuln_data_sources ORDER BY name ASC")
    fun observeDataSources(): Flow<List<VulnDataSourceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDataSources(items: List<VulnDataSourceEntity>)
}
