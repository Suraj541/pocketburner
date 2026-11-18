package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vulnerabilities",
    indices = [
        Index(value = ["cveId"], unique = true),
        Index(value = ["severity"]),
        Index(value = ["publishedAt"]),
    ],
)
data class VulnerabilityEntity(
    @PrimaryKey val id: String,
    val cveId: String,
    val title: String,
    val description: String,
    val vendor: String,
    val product: String,
    val affectedVersions: String,
    val fixedVersions: String,
    val severity: String,
    val cvss: Double?,
    val epss: Double?,
    val cwe: String?,
    val isKev: Boolean,
    val publishedAt: String,
    val modifiedAt: String,
    val source: String,
    val references: String,
    val exploitStatus: String,
)

@Entity(
    tableName = "labs",
    indices = [Index(value = ["cveId"]), Index(value = ["status"])],
)
data class LabEntity(
    @PrimaryKey val id: String,
    val cveId: String,
    val name: String,
    val provider: String,
    val os: String,
    val architecture: String,
    val resources: String,
    val networkMode: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "lab_snapshots",
    indices = [Index(value = ["labId"])],
)
data class LabSnapshotEntity(
    @PrimaryKey val id: String,
    val labId: String,
    val label: String,
    val description: String,
    val createdAt: Long,
)

@Entity(
    tableName = "lab_events",
    indices = [Index(value = ["labId"]), Index(value = ["createdAt"])],
)
data class LabEventEntity(
    @PrimaryKey val id: String,
    val labId: String?,
    val category: String,
    val description: String,
    val previousValue: String?,
    val newValue: String?,
    val reversible: Boolean,
    val createdAt: Long,
)

@Entity(tableName = "vuln_data_sources")
data class VulnDataSourceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val baseUrl: String,
    val enabled: Boolean,
    val status: String,
    val lastSyncAt: Long?,
)
