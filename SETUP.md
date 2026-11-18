# VulnForge setup

VulnForge is currently delivered as a native Android local-first workspace inside PocketBurner. It stores CVE records, lab manifests, snapshots, data-source status, and change events in the existing Room database; no credentials or external service is required for the first run.

## Run

1. Open the repository in Android Studio with JDK 11+.
2. Sync Gradle and run the `app` debug configuration.
3. Open the **VulnForge** tab. The first launch seeds a small, attributed CVE catalog locally.

The existing firmware workflows remain available in Burner, Forge, Connect, Terminal, and Nano.

## Optional integrations

The NVD, CISA KEV, OSV, HexBrain, Telegram, Docker, Hyper-V, and VirtualBox integrations are intentionally represented as explicit data-source and lab-provider states until their credentials/provider permissions are configured. The app never changes host networking, installs drivers, executes downloaded PoCs, or provisions a vulnerable target automatically.

## Validation

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```
