package com.example

import com.example.data.local.VulnerabilityEntity
import com.example.vulnforge.createLabManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VulnForgeManifestTest {
    @Test
    fun manifestUsesSafeDefaultsAndRequiresApproval() {
        val cve = VulnerabilityEntity(
            id = "cve-test",
            cveId = "CVE-2099-0001",
            title = "Test",
            description = "Test",
            vendor = "Vendor",
            product = "Product",
            affectedVersions = "1.0",
            fixedVersions = "1.1",
            severity = "High",
            cvss = 8.0,
            epss = null,
            cwe = null,
            isKev = false,
            publishedAt = "2099-01-01",
            modifiedAt = "2099-01-01",
            source = "Test",
            references = "",
            exploitStatus = "Unknown",
        )

        val manifest = createLabManifest(cve)

        assertEquals("isolated", manifest.networkMode)
        assertEquals(1, manifest.cpu)
        assertEquals(1024, manifest.memoryMb)
        assertTrue(manifest.requiresApproval)
        assertTrue(manifest.isHostIsolated)
    }
}
