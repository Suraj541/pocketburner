package com.example.data.local

import com.example.vulnforge.createLabManifest
import kotlinx.coroutines.flow.Flow

class VulnForgeRepository(private val dao: VulnForgeDao) {
    val vulnerabilities: Flow<List<VulnerabilityEntity>> = dao.observeVulnerabilities()
    val labs: Flow<List<LabEntity>> = dao.observeLabs()
    val recentEvents: Flow<List<LabEventEntity>> = dao.observeRecentEvents()
    val dataSources: Flow<List<VulnDataSourceEntity>> = dao.observeDataSources()

    suspend fun seedIfEmpty() {
        if (dao.vulnerabilityCount() > 0) return
        val items = listOf(
            VulnerabilityEntity(
                id = "cve-2024-3094",
                cveId = "CVE-2024-3094",
                title = "XZ Utils backdoor",
                description = "Malicious code was discovered in XZ Utils releases 5.6.0 and 5.6.1. Use this record as an investigation reference, not as proof that a host is affected.",
                vendor = "XZ Utils",
                product = "liblzma",
                affectedVersions = "5.6.0, 5.6.1",
                fixedVersions = "5.6.2",
                severity = "Critical",
                cvss = 10.0,
                epss = 0.96,
                cwe = "CWE-506",
                isKev = true,
                publishedAt = "2024-03-29",
                modifiedAt = "2024-04-04",
                source = "NVD / CISA KEV",
                references = "https://nvd.nist.gov/vuln/detail/CVE-2024-3094",
                exploitStatus = "Public analysis",
            ),
            VulnerabilityEntity(
                id = "cve-2023-34362",
                cveId = "CVE-2023-34362",
                title = "MOVEit Transfer SQL injection",
                description = "SQL injection in Progress MOVEit Transfer. Validate vendor version ranges and deployment configuration before treating a match as confirmed.",
                vendor = "Progress",
                product = "MOVEit Transfer",
                affectedVersions = "< 2023.0.2",
                fixedVersions = "2023.0.2",
                severity = "High",
                cvss = 9.8,
                epss = 0.94,
                cwe = "CWE-89",
                isKev = true,
                publishedAt = "2023-06-02",
                modifiedAt = "2024-08-12",
                source = "NVD / CISA KEV",
                references = "https://nvd.nist.gov/vuln/detail/CVE-2023-34362",
                exploitStatus = "Known exploited",
            ),
            VulnerabilityEntity(
                id = "cve-2024-6387",
                cveId = "CVE-2024-6387",
                title = "OpenSSH regreSSHion",
                description = "Race condition in OpenSSH server signal handling. A practice lab should use an isolated guest and must not modify the host operating system.",
                vendor = "OpenSSH",
                product = "sshd",
                affectedVersions = "8.5p1 - 9.7p1",
                fixedVersions = "9.8p1",
                severity = "High",
                cvss = 8.1,
                epss = 0.71,
                cwe = "CWE-362",
                isKev = false,
                publishedAt = "2024-07-01",
                modifiedAt = "2024-07-08",
                source = "NVD",
                references = "https://nvd.nist.gov/vuln/detail/CVE-2024-6387",
                exploitStatus = "Research",
            ),
        )
        dao.upsertVulnerabilities(items)
        dao.upsertDataSources(
            listOf(
                VulnDataSourceEntity("nvd", "NVD", "National Vulnerability Database API 2.0", "https://nvd.nist.gov", true, "Ready", null),
                VulnDataSourceEntity("cisa-kev", "CISA KEV", "Known Exploited Vulnerabilities catalog", "https://www.cisa.gov", true, "Ready", null),
                VulnDataSourceEntity("osv", "OSV", "Open Source Vulnerabilities API", "https://api.osv.dev", false, "Needs sync", null),
                VulnDataSourceEntity("hexbrain", "HexBrain", "Optional remote intelligence connector", "https://hexbrainai.com/api/v1", false, "API key required", null),
            ),
        )
    }

    suspend fun createLab(cve: VulnerabilityEntity): LabEntity {
        val now = System.currentTimeMillis()
        val manifest = createLabManifest(cve)
        val lab = LabEntity(
            id = manifest.id,
            cveId = cve.cveId,
            name = "${cve.cveId} practice lab",
            provider = manifest.provider,
            os = manifest.os,
            architecture = manifest.architecture,
            resources = "${manifest.cpu} vCPU / ${manifest.memoryMb} MB",
            networkMode = "${manifest.networkMode}, localhost-only",
            status = "Planned",
            createdAt = now,
            updatedAt = now,
        )
        dao.upsertLab(lab)
        dao.insertEvent(
            LabEventEntity(
                id = "event-$now",
                labId = lab.id,
                category = "LAB_PLANNED",
                description = "Manifest created; explicit approval is required before provisioning.",
                previousValue = null,
                newValue = lab.networkMode,
                reversible = true,
                createdAt = now,
            ),
        )
        return lab
    }

    suspend fun updateLabStatus(lab: LabEntity, status: String) {
        dao.updateLabStatus(lab.id, status)
        dao.insertEvent(
            LabEventEntity(
                id = "event-${lab.id}-${status.lowercase()}-${System.currentTimeMillis()}",
                labId = lab.id,
                category = "LAB_STATUS",
                description = "Lab status changed to $status.",
                previousValue = lab.status,
                newValue = status,
                reversible = status != "Destroyed",
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun snapshotLab(lab: LabEntity, label: String) {
        val now = System.currentTimeMillis()
        dao.insertSnapshot(LabSnapshotEntity("snapshot-$now", lab.id, label, "User-created local baseline", now))
        dao.insertEvent(
            LabEventEntity("event-snapshot-$now", lab.id, "SNAPSHOT", "Snapshot '$label' captured.", null, null, true, now),
        )
    }
}
