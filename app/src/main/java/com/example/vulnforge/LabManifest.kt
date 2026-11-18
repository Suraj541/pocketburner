package com.example.vulnforge

import com.example.data.local.VulnerabilityEntity

data class LabManifest(
    val id: String,
    val cve: String,
    val provider: String,
    val os: String,
    val architecture: String,
    val cpu: Int,
    val memoryMb: Int,
    val networkMode: String,
    val requiresApproval: Boolean = true,
) {
    val isHostIsolated: Boolean
        get() = networkMode == "isolated"
}

fun createLabManifest(cve: VulnerabilityEntity): LabManifest = LabManifest(
    id = "lab-${cve.cveId.lowercase()}",
    cve = cve.cveId,
    provider = "docker",
    os = "linux",
    architecture = "amd64",
    cpu = 1,
    memoryMb = 1024,
    networkMode = "isolated",
)
