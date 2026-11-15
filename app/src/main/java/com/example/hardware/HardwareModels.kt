package com.example.hardware

enum class FirmwareCategory(val label: String) {
    ALL("All categories"),
    TOOLS("Tools"),
    GAMES("Games"),
    SECURITY_RESEARCH("Security Research"),
    NETWORK_COMMUNICATION("Network & Communication"),
    IOT_AUTOMATION("IoT & Automation"),
    SENSORS_MONITORING("Sensors & Monitoring"),
    NAVIGATION_TRACKING("Navigation & Tracking"),
    AUDIO_MEDIA("Audio & Media"),
    CAMERA_VISION("Camera & Vision"),
    DISPLAY_ART("Display & Art"),
    PRODUCTIVITY("Productivity"),
    ROBOTICS_CONTROL("Robotics & Control"),
    AI_ASSISTANTS("AI & Assistants"),
    DEVELOPMENT_LEARNING("Development & Learning"),
    UI_LAUNCHER("UI & Launcher"),
    EBOOKS_READING("E-books & Reading"),
    UTILITIES("Utilities");

    companion object {
        fun fromString(value: String?): FirmwareCategory {
            if (value.isNullOrBlank()) return TOOLS
            return entries.firstOrNull {
                it.label.equals(value.trim(), ignoreCase = true) ||
                    it.name.equals(value.trim().replace(" ", "_").replace("&", "").replace("__", "_"), ignoreCase = true)
            } ?: TOOLS
        }
    }
}

enum class DeviceFamily(val label: String) {
    M5STACK("M5Stack"),
    ESP_IOT("ESP IoT"),
    ARDUINO("Arduino"),
    RASPBERRY_PI("Raspberry Pi"),
}

enum class FlashProtocol(val label: String) {
    ESPTOOL_SLIP("Espressif esptool SLIP"),
    AVR_STK500("Arduino STK500v1"),
    SAMD_BOSSA("Arduino BOSSA / CDC"),
    RP2040_UF2("RP2040/RP2350 UF2"),
    RPI_RAW_IMG("Raspberry Pi OS Raw Image"),
}

data class UsbSignature(
    val vendorId: Int,
    val productId: Int,
    val bridgeName: String,
)

data class HardwareBoardSpec(
    val id: String,
    val displayName: String,
    val seriesGroup: String,
    val family: DeviceFamily,
    val chip: String,
    val architecture: String,
    val cpuClockMhz: Int,
    val flashSizeLabel: String,
    val ramLabel: String,
    val defaultBaud: Int,
    val bootloaderOffset: String,
    val appOffset: String,
    val flashMode: String,
    val chipDetectMagic: String,
    val protocol: FlashProtocol,
    val logicVoltage: String,
    val usbSignatures: List<UsbSignature>,
    val keyInterfaces: List<String>,
    val gpioPinoutSummary: String,
    val supportedLanguages: List<String>,
    val notes: String,
)
