# PocketBurner / VulnForge

PocketBurner is a local Android firmware workbench with a VulnForge workspace for CVE research and isolated practice-lab planning.

## VulnForge vertical slice

The VulnForge tab is backed by Room and includes:

- Attributed local CVE records with CVSS, EPSS, CWE, KEV, version and provenance fields.
- Search and severity filtering.
- CVE detail inspection with an explicit practice-lab approval action.
- Safe lab manifests (isolated networking, localhost-only exposure, bounded resources).
- Local lab status, snapshots, and an auditable change timeline.
- Data-source readiness states for NVD, CISA KEV, OSV, and optional HexBrain.

See [SETUP.md](SETUP.md) for launch instructions and [STATUS.md](STATUS.md) for the implementation boundary.

## Development

Open the project in Android Studio and run the `app` debug configuration. The repository currently does not include a Gradle wrapper script; use the IDE's configured Gradle toolchain.
