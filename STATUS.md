# VulnForge status

## Completed

- Room-backed vulnerability records with CVE, CVSS, EPSS, CWE, KEV, version, provenance, and exploit-status fields.
- Local seed catalog with attributed records for CVE-2024-3094, CVE-2023-34362, and CVE-2024-6387.
- Search and severity filtering in a polished dark-compatible Compose workspace.
- Explicit CVE detail dialog and practice-lab approval flow.
- Safe lab manifest defaults: Docker, Linux, amd64, 1 vCPU, 1 GB RAM, isolated network, explicit approval.
- Lab lifecycle controls for planned/running/stopped states, local snapshots, and an auditable event timeline.
- Data-source status panel for NVD, CISA KEV, OSV, and optional HexBrain.
- Focused unit test covering lab isolation and approval defaults.

## Partial / not yet wired

- Remote NVD/CISA/OSV/HexBrain synchronization, Telegram connectors, MCP server, guest monitoring, VM adapters, encrypted settings, and production authentication still require backend/provider work.
- Docker and VM actions are intentionally status-only in this Android slice; no host command execution or vulnerable environment creation is performed.

## Known limitations

- Seed data is a small local catalog, not a bulk vulnerability mirror.
- The existing application uses Room destructive migration for development upgrades.
