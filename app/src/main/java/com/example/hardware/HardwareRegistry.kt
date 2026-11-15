package com.example.hardware

object HardwareRegistry {

    private val sigCp2104 = UsbSignature(0x10C4, 0xEA60, "Silicon Labs CP210x")
    private val sigCh340 = UsbSignature(0x1A86, 0x7523, "WCH CH340/CH341")
    private val sigCh9102 = UsbSignature(0x1A86, 0x55D4, "WCH CH9102")
    private val sigEspJtag = UsbSignature(0x303A, 0x1001, "Espressif USB-Serial/JTAG")
    private val sigEspOtg = UsbSignature(0x303A, 0x0002, "Espressif Native USB OTG")
    private val sigFtdi = UsbSignature(0x0403, 0x6001, "FTDI FT232R")
    private val sigArduinoUnoR3 = UsbSignature(0x2341, 0x0043, "Arduino Uno R3 (16U2)")
    private val sigArduinoUnoR4 = UsbSignature(0x2341, 0x1002, "Arduino Uno R4 WiFi")
    private val sigArduinoMega = UsbSignature(0x2341, 0x0042, "Arduino Mega 2560")
    private val sigArduinoNanoEsp = UsbSignature(0x2341, 0x0070, "Arduino Nano ESP32")
    private val sigArduinoDue = UsbSignature(0x2341, 0x003D, "Arduino Due")
    private val sigRp2040Boot = UsbSignature(0x2E8A, 0x0003, "Raspberry Pi RP2040 BOOTSEL")
    private val sigRp2040Cdc = UsbSignature(0x2E8A, 0x0005, "Raspberry Pi Pico MicroPython")
    private val sigRp2350Boot = UsbSignature(0x2E8A, 0x000F, "Raspberry Pi RP2350 BOOTSEL")

    val boards: List<HardwareBoardSpec> = listOf(
        // ==================== M5STACK & PARTNER DEVICES ====================
        m5Esp32(
            id = "Core Series",
            displayName = "Core Series (Basic / Gray)",
            seriesGroup = "Core Series",
            chip = "ESP32-D0WDQ6-V3",
            flash = "16 MB",
            ram = "520 KB SRAM",
            sig = listOf(sigCp2104, sigCh9102),
            gpio = "Grove Port A (21/22), LCD SPI (23/18/14/27/33/32), SD (4), Speaker (25), Buttons (39/38/37)",
            notes = "Classic 5x5cm M5Core with 2.0\" ILI9342C IPS, 3 tactile buttons, and M-Bus expansion."
        ),
        m5Esp32(
            id = "Core2",
            displayName = "M5Stack Core2",
            seriesGroup = "Core Series",
            chip = "ESP32-D0WDQ6-V3",
            flash = "16 MB",
            ram = "520 KB + 8 MB PSRAM",
            sig = listOf(sigCp2104, sigCh9102),
            gpio = "Touch I2C (21/22), AXP192 PMIC, Port A (32/33), I2S Mic/Spk (12/0/2/34), SD (4)",
            notes = "Capacitive touchscreen, AXP192/AXP2101 PMIC, IMU MPU6886, vibration motor, RTC."
        ),
        m5Esp32S3(
            id = "CoreS3",
            displayName = "M5Stack CoreS3",
            seriesGroup = "Core Series",
            chip = "ESP32-S3FN8",
            flash = "16 MB",
            ram = "512 KB + 8 MB PSRAM",
            gpio = "Camera GC0308, Display ILI9342C (37/36/3/35), Port A (2/1), Port B (9/8), Port C (18/17)",
            notes = "ESP32-S3 dual-core 240MHz, 0.3MP front camera, proximity sensor, AXP2101, DIN-rail base."
        ),
        m5Esp32(
            id = "CoreInk",
            displayName = "M5Stack CoreInk",
            seriesGroup = "Core Series",
            chip = "ESP32-PICO-D4",
            flash = "4 MB",
            ram = "520 KB SRAM",
            sig = listOf(sigCp2104, sigCh9102),
            gpio = "1.54\" E-Ink GDEW0154M09 (18/23/9/15/0/4), RTC BM8563 (21/22), Buzzer (2), Multi-switch (37/38/39)",
            notes = "Ultra-low-power 200x200 E-Paper core with RTC wake alarm."
        ),
        m5Esp32P4(
            id = "CoreP4X",
            displayName = "M5Stack CoreP4X",
            seriesGroup = "Core Series",
            chip = "ESP32-P4 + C6 Coprocessor",
            flash = "32 MB",
            ram = "32 MB PSRAM",
            gpio = "MIPI-DSI/CSI, High-Speed USB OTG, Ethernet RMII, M-Bus GPIO header",
            notes = "Dual RISC-V 400MHz ESP32-P4 multimedia & edge-AI core with ESP32-C6 wireless link."
        ),
        m5Esp32S3(
            id = "CoreMatrix",
            displayName = "M5Stack CoreMatrix",
            seriesGroup = "Core Series",
            chip = "ESP32-S3",
            flash = "8 MB",
            ram = "512 KB + 8 MB PSRAM",
            gpio = "RGB LED Matrix driver, IMU, Grove Port A (2/1), USB-C Native JTAG",
            notes = "Compact matrix display controller based on ESP32-S3."
        ),
        m5Esp32(
            id = "Fire",
            displayName = "M5Stack Fire",
            seriesGroup = "Core Series",
            chip = "ESP32-D0WDQ6-V3",
            flash = "16 MB",
            ram = "520 KB + 8 MB PSRAM",
            sig = listOf(sigCp2104, sigCh9102),
            gpio = "Port A (21/22), Port B (26/36), Port C (16/17), 10x RGB SK6812 (15), 9-axis IMU",
            notes = "High-memory classic Core with PSRAM, 3 Grove ports, and magnetic charging base."
        ),
        m5Esp32(
            id = "M5GO",
            displayName = "M5Stack M5GO",
            seriesGroup = "Core Series",
            chip = "ESP32-D0WDQ6-V3",
            flash = "16 MB",
            ram = "520 KB + 8 MB PSRAM",
            sig = listOf(sigCp2104, sigCh9102),
            gpio = "Port A (21/22), Port B (26/36), Port C (16/17), LED Bar (15)",
            notes = "STEM-focused ESP32 Core kit with bottom RGB bars and LEGO-compatible base."
        ),
        m5Esp32(
            id = "Tough",
            displayName = "M5Stack Tough",
            seriesGroup = "Core Series",
            chip = "ESP32-D0WDQ6-V3",
            flash = "16 MB",
            ram = "520 KB + 8 MB PSRAM",
            sig = listOf(sigCp2104, sigCh9102),
            gpio = "RS485 (13/14/27), Waterproof M12, AXP192, Touch Screen",
            notes = "IP67 industrial waterproof ESP32 HMI with RS485 and 12V-24V input."
        ),
        m5Esp32C5(
            id = "ToughC5",
            displayName = "M5Stack ToughC5",
            seriesGroup = "Core Series",
            chip = "ESP32-C5",
            flash = "16 MB",
            ram = "384 KB HP SRAM",
            gpio = "Dual-band 2.4/5GHz Wi-Fi 6, RS485, Waterproof industrial I/O",
            notes = "Ruggedized ESP32-C5 dual-band Wi-Fi 6 industrial controller."
        ),
        m5Esp32P4(
            id = "Tab5",
            displayName = "M5Stack Tab5",
            seriesGroup = "Core Series",
            chip = "ESP32-P4 + ESP32-C6",
            flash = "32 MB",
            ram = "32 MB PSRAM",
            gpio = "5\" MIPI-DSI Touch, 2MP MIPI-CSI Camera, Dual Speakers, USB-A Host, Grove",
            notes = "5-inch portable RISC-V ESP32-P4 tablet with Wi-Fi 6/BLE/Zigbee via C6."
        ),
        m5Esp32(
            id = "Atom Series",
            displayName = "Atom Series",
            seriesGroup = "Atom Series",
            chip = "ESP32-PICO-D4",
            flash = "4 MB",
            ram = "520 KB SRAM",
            sig = listOf(sigFtdi, sigCh9102),
            gpio = "NeoPixel (27), Button (39), IR TX (12), Grove (26/32), Pin header (19/21/22/23/25/33)",
            notes = "24x24mm ultra-compact ESP32-PICO family."
        ),
        m5Esp32(
            id = "Atom-Lite",
            displayName = "M5Atom Lite",
            seriesGroup = "Atom Series",
            chip = "ESP32-PICO-D4",
            flash = "4 MB",
            ram = "520 KB SRAM",
            sig = listOf(sigFtdi, sigCh9102),
            gpio = "SK6812 RGB (27), Btn (39), IR (12), Grove (26/32), Header (21/25/22/19/23/33)",
            notes = "Single RGB LED minimalist ESP32-PICO controller."
        ),
        m5Esp32(
            id = "Atom-Matrix",
            displayName = "M5Atom Matrix",
            seriesGroup = "Atom Series",
            chip = "ESP32-PICO-D4",
            flash = "4 MB",
            ram = "520 KB SRAM",
            sig = listOf(sigFtdi, sigCh9102),
            gpio = "5x5 WS2812C Matrix (27), MPU6886 IMU (25/21), Btn (39), IR (12)",
            notes = "25-pixel RGB LED matrix + 6-axis IMU in 24x24mm footprint."
        ),
        m5Esp32S3(
            id = "AtomS3",
            displayName = "M5AtomS3",
            seriesGroup = "Atom Series",
            chip = "ESP32-S3FN8",
            flash = "8 MB",
            ram = "512 KB SRAM",
            gpio = "0.85\" GC9107 IPS (15/16/17/21/33/34), MPU6886 (38/39), Btn (41), IR (4), Grove (1/2)",
            notes = "ESP32-S3 with 128x128 color screen & USB-C native OTG."
        ),
        m5Esp32S3(
            id = "AtomS3-Lite",
            displayName = "M5AtomS3 Lite",
            seriesGroup = "Atom Series",
            chip = "ESP32-S3FN8",
            flash = "8 MB",
            ram = "512 KB SRAM",
            gpio = "WS2812 RGB (35), Btn (41), IR (4), Grove (1/2), Header (5/6/7/8/38/39)",
            notes = "Screenless ESP32-S3 24x24mm IoT node with native USB."
        ),
        m5Esp32S3(
            id = "AtomS3R",
            displayName = "M5AtomS3R",
            seriesGroup = "Atom Series",
            chip = "ESP32-S3-PICO-1-N8R8",
            flash = "8 MB",
            ram = "512 KB + 8 MB PSRAM",
            gpio = "0.85\" IPS, 9-axis BMI270+BMM150 IMU, IR (47), Grove (1/2), Native USB-C",
            notes = "Upgraded AtomS3 with 8MB PSRAM and 9-DoF IMU."
        ),
        m5Esp32S3(
            id = "Cardputer",
            displayName = "M5Stack Cardputer",
            seriesGroup = "Cardputer",
            chip = "ESP32-S3FN8",
            flash = "8 MB",
            ram = "512 KB SRAM",
            gpio = "1.14\" ST7789V2 (33/34/35/36/37/38), 56-key matrix, MicroSD (12/14/39/40), IR (44), Mic/Spk, Grove (1/2)",
            notes = "Credit-card ESP32-S3 handheld computer with QWERTY keyboard and dual battery."
        ),
        m5Esp32S3(
            id = "Cardputer ADV",
            displayName = "M5Stack Cardputer ADV",
            seriesGroup = "Cardputer",
            chip = "ESP32-S3",
            flash = "8 MB / 16 MB",
            ram = "512 KB + 8 MB PSRAM",
            gpio = "Enhanced 56-key keyboard, LoRa/Sub-GHz/EXT expansion header, 1.14\" IPS, MicroSD, I2S audio",
            notes = "Advanced Cardputer revision with expanded modular I/O and audio/RF support."
        ),
        m5Esp32(
            id = "StickC",
            displayName = "M5StickC",
            seriesGroup = "StickC",
            chip = "ESP32-PICO-D4",
            flash = "4 MB",
            ram = "520 KB SRAM",
            sig = listOf(sigFtdi),
            gpio = "0.96\" ST7735S, AXP192, Red LED (10), IR (9), Grove (32/33), Hat (0/26/36)",
            notes = "Original wearable stick ESP32-PICO with 80x160 TFT and IMU."
        ),
        m5Esp32(
            id = "StickC-Plus",
            displayName = "M5StickC Plus",
            seriesGroup = "StickC",
            chip = "ESP32-PICO-D4",
            flash = "4 MB",
            ram = "520 KB SRAM",
            sig = listOf(sigFtdi, sigCh9102),
            gpio = "1.14\" ST7789V2, Buzzer (2), Red LED (10), IR (9), Grove (32/33), Hat (0/26/36)",
            notes = "1.14-inch screen + passive buzzer + AXP192 PMIC."
        ),
        m5Esp32(
            id = "StickC-Plus2",
            displayName = "M5StickC Plus2",
            seriesGroup = "StickC",
            chip = "ESP32-PICO-V3-02",
            flash = "8 MB",
            ram = "520 KB + 2 MB PSRAM",
            sig = listOf(sigCh9102),
            gpio = "1.14\" ST7789V2, Green/IR LED (19), Buzzer (2), Hold Pin (4), Grove (32/33), Hat (0/26/36)",
            notes = "8MB Flash + 2MB PSRAM, 200mAh battery, GPIO4 power-hold latch."
        ),
        m5Esp32S3(
            id = "StickS3",
            displayName = "M5StickS3",
            seriesGroup = "StickC",
            chip = "ESP32-S3",
            flash = "8 MB",
            ram = "512 KB + 8 MB PSRAM",
            gpio = "1.14\" IPS, Native USB-C JTAG/OTG, IMU, IR, Grove & Hat header",
            notes = "ESP32-S3 generation StickC with AI/USB-HID capability."
        ),
        m5Esp32(
            id = "StickT2",
            displayName = "M5StickT2",
            seriesGroup = "StickC",
            chip = "ESP32-PICO-D4",
            flash = "4 MB",
            ram = "520 KB SRAM",
            sig = listOf(sigFtdi),
            gpio = "MLX90640 Thermal IR Array I2C (21/22), 1.14\" TFT, AXP192",
            notes = "Compact thermal imaging camera Stick."
        ),
        m5Esp32S3(
            id = "Dial",
            displayName = "M5Stack Dial",
            seriesGroup = "Display & HMI",
            chip = "ESP32-S3FN8",
            flash = "8 MB",
            ram = "512 KB SRAM",
            gpio = "1.28\" Round GC9A01 Touch, Rotary Encoder (40/41), WS1850S RFID, RTC BM8563, Buzzer (3), Port A/B",
            notes = "Smart rotary knob with circular touchscreen and RFID reader."
        ),
        m5Esp32S3(
            id = "DinMeter",
            displayName = "M5Stack DinMeter",
            seriesGroup = "Display & HMI",
            chip = "ESP32-S3FN8",
            flash = "8 MB",
            ram = "512 KB SRAM",
            gpio = "1.14\" ST7789V2, Rotary Encoder, RTC, Power Hold (46), Port A (13/15), Port B (1/2)",
            notes = "DIN-rail mountable rotary smart meter & controller."
        ),
        m5Esp32S3(
            id = "Capsule",
            displayName = "M5Capsule",
            seriesGroup = "IoT Nodes",
            chip = "ESP32-S3FN8",
            flash = "8 MB",
            ram = "512 KB SRAM",
            gpio = "MicroSD, Mic/Buzzer, IMU BMI270, IR, RTC BM8563, 250mAh battery",
            notes = "Pocket-sized ESP32-S3 voice/sensor capsule."
        ),
        m5Esp32S3(
            id = "Chain DualKey",
            displayName = "M5 Chain DualKey",
            seriesGroup = "IoT Nodes",
            chip = "ESP32-S3",
            flash = "8 MB",
            ram = "512 KB SRAM",
            gpio = "Dual mechanical hot-swap keys, RGB LEDs, Daisy-chain UART/Grove",
            notes = "Programmable dual mechanical key USB-HID/ESP-NOW input node."
        ),
        m5Esp32(
            id = "Paper",
            displayName = "M5Paper",
            seriesGroup = "Paper Series",
            chip = "ESP32-D0WDQ6-V3",
            flash = "16 MB",
            ram = "520 KB + 8 MB PSRAM",
            sig = listOf(sigCp2104, sigCh9102),
            gpio = "4.7\" IT8951 E-Ink Touch (960x540), SHT30 (21/22), MicroSD (4), 3 Grove ports",
            notes = "4.7-inch 16-grayscale E-Ink tablet with capacitive touch."
        ),
        m5Esp32S3(
            id = "PaperS3",
            displayName = "M5PaperS3",
            seriesGroup = "Paper Series",
            chip = "ESP32-S3R8",
            flash = "16 MB",
            ram = "512 KB + 8 MB PSRAM",
            gpio = "4.7\" E-Ink Touch, Gyroscope BMI270, MicroSD, Buzzer, 1800mAh LiPo",
            notes = "ESP32-S3 successor to M5Paper with faster refresh and lower standby draw."
        ),
        m5Esp32(
            id = "PaperDIY",
            displayName = "M5 PaperDIY",
            seriesGroup = "Paper Series",
            chip = "ESP32 / ESP32-S3",
            flash = "16 MB",
            ram = "8 MB PSRAM",
            sig = listOf(sigCh9102, sigEspJtag),
            gpio = "E-Paper SPI connector, Touch I2C, MicroSD, Battery PMIC",
            notes = "Modular DIY E-Paper development core."
        ),
        m5Esp32S3(
            id = "PaperColor",
            displayName = "M5 PaperColor",
            seriesGroup = "Paper Series",
            chip = "ESP32-S3",
            flash = "16 MB",
            ram = "8 MB PSRAM",
            gpio = "Color E-Paper Spectra/Kaleido SPI, Touch I2C, RTC, MicroSD",
            notes = "Color E-Ink reader & signage display core."
        ),
        m5Esp32S3(
            id = "PaperMono",
            displayName = "M5 PaperMono",
            seriesGroup = "Paper Series",
            chip = "ESP32-S3",
            flash = "16 MB",
            ram = "8 MB PSRAM",
            gpio = "High-contrast monochrome E-Paper SPI, RTC, MicroSD",
            notes = "Crisp monochrome E-Ink terminal & badge board."
        ),
        m5Esp32S3(
            id = "Air Quality",
            displayName = "M5 Air Quality (AirQ)",
            seriesGroup = "Sensors",
            chip = "ESP32-S3",
            flash = "8 MB",
            ram = "512 KB SRAM",
            gpio = "1.54\" E-Ink, SCD40 CO2 + SEN55 PM/VOC/NOx, RTC, Buzzer",
            notes = "All-in-one indoor CO2, PM2.5, VOC, temperature & humidity monitor."
        ),
        m5Esp32S3(
            id = "Arduino Nesso N1",
            displayName = "Arduino Nesso N1 (M5/Arduino)",
            seriesGroup = "Arduino & Partner",
            chip = "ESP32-C6 / ESP32-S3",
            flash = "16 MB",
            ram = "512 KB SRAM",
            gpio = "1.14\" Touchscreen, LoRa/Thread/Wi-Fi 6, Grove & Qwiic, IMU, Buzzer, Battery",
            notes = "Official Arduino + M5Stack co-designed pocket IoT multi-radio controller."
        ),
        m5Esp32C6(
            id = "NanoC6",
            displayName = "M5NanoC6",
            seriesGroup = "Nano & Units",
            chip = "ESP32-C6FH4",
            flash = "4 MB",
            ram = "512 KB HP SRAM",
            gpio = "IR TX (3), WS2812 (20), Blue LED (7), Btn (9), Grove (1/2)",
            notes = "Thumb-sized Wi-Fi 6, Zigbee 3.0, Thread & Matter controller."
        ),
        m5Esp32H2(
            id = "NanoH2",
            displayName = "M5NanoH2",
            seriesGroup = "Nano & Units",
            chip = "ESP32-H2FH4",
            flash = "4 MB",
            ram = "320 KB SRAM",
            gpio = "IEEE 802.15.4 Zigbee/Thread + BLE 5.3, Grove I2C/UART, RGB LED",
            notes = "Ultra-low-power Thread/Zigbee/BLE 5.3 endpoint."
        ),
        m5Esp32S3(
            id = "PowerHub",
            displayName = "M5 PowerHub",
            seriesGroup = "IoT Nodes",
            chip = "ESP32-S3",
            flash = "8 MB",
            ram = "512 KB SRAM",
            gpio = "USB-C PD / DC Monitor, INA226 Power Telemetry, RS485, CAN, Grove ports",
            notes = "Programmable smart power distribution & telemetry hub."
        ),
        m5Esp32S3(
            id = "StackChan",
            displayName = "M5 StackChan",
            seriesGroup = "Robotics",
            chip = "ESP32 / ESP32-S3",
            flash = "16 MB",
            ram = "8 MB PSRAM",
            gpio = "Dual PWM/SCS Servo Bus, I2S Mic/Speaker, Display, IMU",
            notes = "Desktop open-source AI companion robot platform."
        ),
        m5Esp32(
            id = "Station",
            displayName = "M5Station",
            seriesGroup = "Core Series",
            chip = "ESP32-D0WDQ6-V3",
            flash = "16 MB",
            ram = "520 KB + 8 MB PSRAM",
            sig = listOf(sigCp2104, sigCh9102),
            gpio = "2.4\" TFT, 6x Grove Ports, RS485, USB-A/C, AXP192",
            notes = "Multi-port desktop IoT workstation."
        ),
        m5Esp32(
            id = "StopWatch",
            displayName = "M5 StopWatch",
            seriesGroup = "Display & HMI",
            chip = "ESP32-PICO / S3",
            flash = "8 MB",
            ram = "512 KB SRAM",
            sig = listOf(sigCh9102, sigEspJtag),
            gpio = "LCD Display, Precision Timer Buttons, Buzzer, Grove",
            notes = "Portable precision sports/lab timer hardware."
        ),
        m5Esp32(
            id = "T-Lite",
            displayName = "M5 T-Lite",
            seriesGroup = "Sensors",
            chip = "ESP32-PICO",
            flash = "4 MB",
            ram = "520 KB SRAM",
            sig = listOf(sigCh9102),
            gpio = "MLX90640 IR Thermal Camera + TFT",
            notes = "Entry-level handheld thermal imaging unit."
        ),
        m5Esp32(
            id = "TimerCam",
            displayName = "M5 TimerCam X / F",
            seriesGroup = "Camera",
            chip = "ESP32-D0WDQ6-V3",
            flash = "4 MB",
            ram = "520 KB + 8 MB PSRAM",
            sig = listOf(sigCp2104, sigCh9102),
            gpio = "OV3660 3MP Camera, BM8563 RTC Wake, Grove (4/13), Status LED (2)",
            notes = "Battery-powered timed-wakeup ESP32 camera module."
        ),
        m5Esp32C6(
            id = "Unit C6L",
            displayName = "M5 Unit C6L",
            seriesGroup = "Nano & Units",
            chip = "ESP32-C6 + SX1262 LoRa",
            flash = "4 MB",
            ram = "512 KB SRAM",
            gpio = "LoRa Sub-GHz SPI, Wi-Fi 6 / BLE 5 / Zigbee, Grove UART/I2C",
            notes = "Smart LoRa + Wi-Fi 6 + Thread telemetry unit."
        ),
        m5Esp32P4(
            id = "Unit PoE-P4",
            displayName = "M5 Unit PoE-P4",
            seriesGroup = "Nano & Units",
            chip = "ESP32-P4",
            flash = "16 MB",
            ram = "32 MB PSRAM",
            gpio = "IEEE 802.3af PoE Ethernet, MIPI-CSI, RS485, Grove",
            notes = "Power-over-Ethernet ESP32-P4 edge vision/gateway unit."
        ),
        m5Esp32S3(
            id = "VAMeter",
            displayName = "M5 VAMeter",
            seriesGroup = "Tools",
            chip = "ESP32-S3",
            flash = "8 MB",
            ram = "512 KB SRAM",
            gpio = "INA226 High-Precision Voltage/Current ADC, IPS Screen, Touch/Button",
            notes = "USB/DC programmable voltmeter and ammeter."
        ),
        m5Esp32S3(
            id = "Xiaozhi Card Kit",
            displayName = "Xiaozhi Card Kit",
            seriesGroup = "AI & Assistants",
            chip = "ESP32-S3",
            flash = "16 MB",
            ram = "8 MB PSRAM",
            gpio = "I2S Mic + Speaker Amplifier, LCD, Voice Wake Button, Battery",
            notes = "Compact ESP32-S3 conversational voice assistant hardware kit."
        ),
        m5Esp32S3(
            id = "Stamp Series",
            displayName = "Stamp Series",
            seriesGroup = "Stamp Series",
            chip = "ESP32-S3 / C3 / PICO",
            flash = "8 MB",
            ram = "512 KB SRAM",
            gpio = "Castellated SMT/DIP 2.54mm pads, USB-C",
            notes = "Embeddable castellated stamp modules."
        ),
        m5Esp32S3(
            id = "Stamp-S3",
            displayName = "M5Stamp S3",
            seriesGroup = "Stamp Series",
            chip = "ESP32-S3FN8",
            flash = "8 MB",
            ram = "512 KB SRAM",
            gpio = "23x GPIOs on 2.54/1.27mm pads, WS2812 (21), Btn (0), Native USB-C",
            notes = "Postage-stamp ESP32-S3 module with 23 GPIOs."
        ),
        m5Esp32C3(
            id = "Stamp-C3",
            displayName = "M5Stamp C3",
            seriesGroup = "Stamp Series",
            chip = "ESP32-C3FN4",
            flash = "4 MB",
            ram = "400 KB SRAM",
            gpio = "13x GPIOs, WS2812 (2), Btn (3), USB-C CH9102",
            notes = "RISC-V 160MHz Wi-Fi + BLE 5.0 stamp."
        ),
        m5Esp32(
            id = "Stamp-Pico",
            displayName = "M5Stamp Pico",
            seriesGroup = "Stamp Series",
            chip = "ESP32-PICO-D4",
            flash = "4 MB",
            ram = "520 KB SRAM",
            sig = listOf(sigFtdi, sigCp2104),
            gpio = "12x GPIOs, SK6812 (27), Btn (39), UART TX/RX",
            notes = "Minimalist ESP32-PICO stamp requiring external USB-UART."
        ),
        m5Esp32P4(
            id = "Stamp-P4",
            displayName = "M5Stamp P4",
            seriesGroup = "Stamp Series",
            chip = "ESP32-P4",
            flash = "16 MB",
            ram = "32 MB PSRAM",
            gpio = "High-density MIPI/USB/GPIO castellated pins",
            notes = "High-speed RISC-V multimedia embeddable stamp."
        ),
        m5Esp32C5(
            id = "Stamp-C5",
            displayName = "M5Stamp C5",
            seriesGroup = "Stamp Series",
            chip = "ESP32-C5",
            flash = "8 MB",
            ram = "384 KB SRAM",
            gpio = "Dual-band 2.4/5GHz Wi-Fi 6 + BLE 5 + 802.15.4, Native USB-JTAG",
            notes = "Dual-band Wi-Fi 6 RISC-V embeddable stamp."
        ),
        m5Esp32C6(
            id = "Stamp-C6LoRa",
            displayName = "M5Stamp C6LoRa",
            seriesGroup = "Stamp Series",
            chip = "ESP32-C6 + SX1262",
            flash = "8 MB",
            ram = "512 KB SRAM",
            gpio = "LoRa RF + Wi-Fi 6 + Thread/Zigbee + BLE 5.3, Castellated GPIO",
            notes = "Combined sub-GHz LoRa and 2.4GHz multi-protocol stamp."
        ),

        // ==================== ALL ESP IOT BOARDS ====================
        HardwareBoardSpec(
            id = "ESP8266",
            displayName = "ESP8266 (NodeMCU / Wemos D1 Mini)",
            seriesGroup = "Generic ESP IoT",
            family = DeviceFamily.ESP_IOT,
            chip = "ESP8266EX (Tensilica L106)",
            architecture = "Xtensa 32-bit @ 80/160 MHz",
            cpuClockMhz = 160,
            flashSizeLabel = "4 MB",
            ramLabel = "80 KB User RAM",
            defaultBaud = 115200,
            bootloaderOffset = "0x0000",
            appOffset = "0x0000",
            flashMode = "DIO",
            chipDetectMagic = "0xFFF0C101",
            protocol = FlashProtocol.ESPTOOL_SLIP,
            logicVoltage = "3.3V",
            usbSignatures = listOf(sigCh340, sigCp2104),
            keyInterfaces = listOf("Wi-Fi 802.11 b/g/n", "UART", "SPI", "I2C", "1x 10-bit ADC"),
            gpioPinoutSummary = "D0(16), D1(5 SCL), D2(4 SDA), D3(0), D4(2 LED), D5(14 SCK), D6(12 MISO), D7(13 MOSI), D8(15), A0",
            supportedLanguages = listOf("Arduino C++", "MicroPython", "Lua (NodeMCU)", "ESPHome YAML"),
            notes = "Classic 2.4GHz Wi-Fi MCU. Bootloader and merged firmware always flash at 0x0000."
        ),
        m5Esp32(
            id = "ESP32 DevKit",
            displayName = "ESP32-WROOM / WROVER DevKit",
            seriesGroup = "Generic ESP IoT",
            family = DeviceFamily.ESP_IOT,
            chip = "ESP32-D0WD-V3",
            flash = "4 MB / 8 MB / 16 MB",
            ram = "520 KB SRAM (+ PSRAM on WROVER)",
            sig = listOf(sigCp2104, sigCh340, sigCh9102),
            gpio = "GPIO0(Boot), GPIO2(LED), I2C(21/22), SPI(23/19/18/5), UART0(1/3), UART2(16/17), ADC1(32-39)",
            notes = "Standard dual-core Xtensa ESP32. Bootloader flashes at 0x1000, partitions 0x8000, app 0x10000."
        ),
        HardwareBoardSpec(
            id = "ESP32-S2",
            displayName = "ESP32-S2 (Saola / Mini)",
            seriesGroup = "Generic ESP IoT",
            family = DeviceFamily.ESP_IOT,
            chip = "ESP32-S2FN4R2",
            architecture = "Xtensa LX7 Single-Core @ 240 MHz",
            cpuClockMhz = 240,
            flashSizeLabel = "4 MB",
            ramLabel = "320 KB SRAM + 2 MB PSRAM",
            defaultBaud = 921600,
            bootloaderOffset = "0x1000",
            appOffset = "0x10000",
            flashMode = "QIO",
            chipDetectMagic = "0x000007C6",
            protocol = FlashProtocol.ESPTOOL_SLIP,
            logicVoltage = "3.3V",
            usbSignatures = listOf(sigEspOtg, sigCp2104),
            keyInterfaces = listOf("Wi-Fi 2.4GHz", "USB OTG FS", "43x GPIO", "Touch", "DAC/ADC"),
            gpioPinoutSummary = "USB D-(19), USB D+(20), Boot(0), I2C(8/9), SPI(35/36/37/34)",
            supportedLanguages = listOf("Arduino C++", "CircuitPython", "MicroPython", "ESP-IDF C"),
            notes = "Native USB OTG with CircuitPython UF2 & HID support; no Bluetooth."
        ),
        m5Esp32S3(
            id = "ESP32-S3",
            displayName = "ESP32-S3 DevKitC / Xiao / LilyGO",
            seriesGroup = "Generic ESP IoT",
            family = DeviceFamily.ESP_IOT,
            chip = "ESP32-S3-WROOM-1",
            flash = "8 MB / 16 MB",
            ram = "512 KB + 8 MB Octal PSRAM",
            gpio = "USB-JTAG(19/20), Boot(0), RGB(48/38), I2C(8/9), SPI(10/11/12/13)",
            notes = "Dual-core LX7 with AI vector instructions, BLE 5.0, native USB OTG & JTAG."
        ),
        m5Esp32C3(
            id = "ESP32-C3",
            displayName = "ESP32-C3 SuperMini / DevKitM",
            seriesGroup = "Generic ESP IoT",
            family = DeviceFamily.ESP_IOT,
            chip = "ESP32-C3FH4",
            flash = "4 MB",
            ram = "400 KB SRAM",
            gpio = "USB-JTAG(18/19), LED(8), Boot(9), I2C(8/9 or 4/5), SPI(4/5/6/7)",
            notes = "RISC-V 160MHz Wi-Fi + BLE 5.0. Bootloader starts at 0x0000."
        ),
        m5Esp32C5(
            id = "ESP32-C5",
            displayName = "ESP32-C5 Dual-Band DevKit",
            seriesGroup = "Generic ESP IoT",
            family = DeviceFamily.ESP_IOT,
            chip = "ESP32-C5",
            flash = "8 MB / 16 MB",
            ram = "384 KB HP SRAM",
            gpio = "2.4/5 GHz Wi-Fi 6, BLE 5, 802.15.4, USB-Serial/JTAG",
            notes = "First Espressif 2.4 GHz + 5 GHz dual-band Wi-Fi 6 RISC-V SoC. Bootloader at 0x2000."
        ),
        m5Esp32C6(
            id = "ESP32-C6",
            displayName = "ESP32-C6 DevKitC / Thread-Zigbee",
            seriesGroup = "Generic ESP IoT",
            family = DeviceFamily.ESP_IOT,
            chip = "ESP32-C6-WROOM-1",
            flash = "8 MB",
            ram = "512 KB HP + 16 KB LP SRAM",
            gpio = "USB-JTAG(12/13), RGB(8), Boot(9), I2C(6/7), UART0(16/17)",
            notes = "Wi-Fi 6 + BLE 5.3 + Zigbee/Thread/Matter RISC-V SoC. Bootloader at 0x0000."
        ),
        m5Esp32H2(
            id = "ESP32-H2",
            displayName = "ESP32-H2 (Zigbee / Matter / BLE)",
            seriesGroup = "Generic ESP IoT",
            family = DeviceFamily.ESP_IOT,
            chip = "ESP32-H2-MINI-1",
            flash = "4 MB",
            ram = "320 KB SRAM",
            gpio = "USB-JTAG(26/27), RGB(8), Boot(9), UART(23/24)",
            notes = "Dedicated IEEE 802.15.4 + BLE 5.3 RISC-V SoC (no Wi-Fi). Bootloader at 0x0000."
        ),
        m5Esp32P4(
            id = "ESP32-P4",
            displayName = "ESP32-P4 Function EV Board",
            seriesGroup = "Generic ESP IoT",
            family = DeviceFamily.ESP_IOT,
            chip = "ESP32-P4NRW32",
            flash = "16 MB / 32 MB",
            ram = "768 KB SRAM + 32 MB PSRAM",
            gpio = "MIPI-DSI, MIPI-CSI, USB 2.0 HS OTG, RMII Ethernet, 55x GPIO",
            notes = "400MHz dual-core RISC-V high-performance multimedia & AI SoC."
        ),

        // ==================== ARDUINO BOARDS ====================
        HardwareBoardSpec(
            id = "Arduino Uno R3",
            displayName = "Arduino Uno R3",
            seriesGroup = "Arduino AVR",
            family = DeviceFamily.ARDUINO,
            chip = "ATmega328P",
            architecture = "8-bit AVR @ 16 MHz",
            cpuClockMhz = 16,
            flashSizeLabel = "32 KB (0.5 KB Boot)",
            ramLabel = "2 KB SRAM / 1 KB EEPROM",
            defaultBaud = 115200,
            bootloaderOffset = "0x7E00",
            appOffset = "0x0000",
            flashMode = "STK500v1",
            chipDetectMagic = "0x1E950F",
            protocol = FlashProtocol.AVR_STK500,
            logicVoltage = "5.0V",
            usbSignatures = listOf(sigArduinoUnoR3, sigCh340),
            keyInterfaces = listOf("14x Digital (6 PWM)", "6x 10-bit ADC", "UART(0/1)", "SPI(10-13)", "I2C(A4/A5)"),
            gpioPinoutSummary = "D0(RX), D1(TX), D2-D13 (LED=D13), A0-A5 (SDA=A4, SCL=A5)",
            supportedLanguages = listOf("Arduino C++ (.ino)", "AVR C", "Firmata"),
            notes = "Requires Intel .hex binary flashed via STK500v1 at 115200 baud after DTR reset pulse."
        ),
        HardwareBoardSpec(
            id = "Arduino Uno R4 WiFi",
            displayName = "Arduino Uno R4 WiFi",
            seriesGroup = "Arduino Renesas",
            family = DeviceFamily.ARDUINO,
            chip = "Renesas RA4M1 + ESP32-S3",
            architecture = "ARM Cortex-M4 @ 48 MHz",
            cpuClockMhz = 48,
            flashSizeLabel = "256 KB Flash",
            ramLabel = "32 KB SRAM / 8 KB EEPROM",
            defaultBaud = 115200,
            bootloaderOffset = "0x0000",
            appOffset = "0x4000",
            flashMode = "BOSSA / DFU",
            chipDetectMagic = "0x4D345241",
            protocol = FlashProtocol.SAMD_BOSSA,
            logicVoltage = "5.0V",
            usbSignatures = listOf(sigArduinoUnoR4),
            keyInterfaces = listOf("Wi-Fi / BLE (ESP32-S3)", "12x8 LED Matrix", "Qwiic I2C", "CAN Bus", "12-bit DAC (A0)"),
            gpioPinoutSummary = "D0-D13, A0(DAC), A4/A5(I2C), Qwiic 3.3V I2C, CAN(D4/D5)",
            supportedLanguages = listOf("Arduino C++ (.ino)", "MicroPython"),
            notes = "5V tolerant Cortex-M4 Uno with onboard ESP32-S3 Wi-Fi/BLE bridge."
        ),
        HardwareBoardSpec(
            id = "Arduino Mega 2560",
            displayName = "Arduino Mega 2560 Rev3",
            seriesGroup = "Arduino AVR",
            family = DeviceFamily.ARDUINO,
            chip = "ATmega2560",
            architecture = "8-bit AVR @ 16 MHz",
            cpuClockMhz = 16,
            flashSizeLabel = "256 KB (8 KB Boot)",
            ramLabel = "8 KB SRAM / 4 KB EEPROM",
            defaultBaud = 115200,
            bootloaderOffset = "0x3E000",
            appOffset = "0x0000",
            flashMode = "STK500v2",
            chipDetectMagic = "0x1E9801",
            protocol = FlashProtocol.AVR_STK500,
            logicVoltage = "5.0V",
            usbSignatures = listOf(sigArduinoMega, sigCh340),
            keyInterfaces = listOf("54x Digital (15 PWM)", "16x ADC", "4x Hardware UART", "SPI(50-53)", "I2C(20/21)"),
            gpioPinoutSummary = "TX0/RX0(1/0), TX1-3(14-19), SDA(20), SCL(21), SPI(50-53), A0-A15",
            supportedLanguages = listOf("Arduino C++ (.ino)", "Marlin / GRBL"),
            notes = "High-pinout AVR board ideal for CNC, 3D printers, and robotics."
        ),
        HardwareBoardSpec(
            id = "Arduino Nano",
            displayName = "Arduino Nano (ATmega328P)",
            seriesGroup = "Arduino AVR",
            family = DeviceFamily.ARDUINO,
            chip = "ATmega328P",
            architecture = "8-bit AVR @ 16 MHz",
            cpuClockMhz = 16,
            flashSizeLabel = "32 KB",
            ramLabel = "2 KB SRAM",
            defaultBaud = 115200,
            bootloaderOffset = "0x7E00",
            appOffset = "0x0000",
            flashMode = "STK500v1 (57600/115200)",
            chipDetectMagic = "0x1E950F",
            protocol = FlashProtocol.AVR_STK500,
            logicVoltage = "5.0V",
            usbSignatures = listOf(sigFtdi, sigCh340),
            keyInterfaces = listOf("14x Digital", "8x ADC (A0-A7)", "UART", "SPI", "I2C"),
            gpioPinoutSummary = "D0-D13, A0-A7 (Old bootloader uses 57600 baud, Optiboot uses 115200)",
            supportedLanguages = listOf("Arduino C++ (.ino)"),
            notes = "Supports both 115200 (Optiboot) and 57600 (Legacy Bootloader)."
        ),
        HardwareBoardSpec(
            id = "Arduino Nano ESP32",
            displayName = "Arduino Nano ESP32",
            seriesGroup = "Arduino ESP",
            family = DeviceFamily.ARDUINO,
            chip = "u-blox NORA-W106 (ESP32-S3)",
            architecture = "Xtensa LX7 Dual-Core @ 240 MHz",
            cpuClockMhz = 240,
            flashSizeLabel = "16 MB",
            ramLabel = "512 KB + 8 MB PSRAM",
            defaultBaud = 921600,
            bootloaderOffset = "0x0000",
            appOffset = "0x10000",
            flashMode = "QIO",
            chipDetectMagic = "0x00000009",
            protocol = FlashProtocol.ESPTOOL_SLIP,
            logicVoltage = "3.3V",
            usbSignatures = listOf(sigArduinoNanoEsp, sigEspJtag),
            keyInterfaces = listOf("Wi-Fi", "BLE 5.0", "USB-C HID/DFU", "RGB LED", "I2C/SPI/UART"),
            gpioPinoutSummary = "Nano form-factor pins D2-D13, A0-A7 mapped to ESP32-S3 GPIOs",
            supportedLanguages = listOf("Arduino C++ (.ino)", "MicroPython", "CircuitPython"),
            notes = "Combines classic Nano pinout with 16MB ESP32-S3 power."
        ),
        HardwareBoardSpec(
            id = "Arduino Due",
            displayName = "Arduino Due",
            seriesGroup = "Arduino SAM",
            family = DeviceFamily.ARDUINO,
            chip = "Atmel SAM3X8E",
            architecture = "ARM Cortex-M3 @ 84 MHz",
            cpuClockMhz = 84,
            flashSizeLabel = "512 KB",
            ramLabel = "96 KB SRAM",
            defaultBaud = 115200,
            bootloaderOffset = "0x0000",
            appOffset = "0x80000",
            flashMode = "SAM-BA BOSSA",
            chipDetectMagic = "0x285E0A60",
            protocol = FlashProtocol.SAMD_BOSSA,
            logicVoltage = "3.3V (Strict)",
            usbSignatures = listOf(sigArduinoDue),
            keyInterfaces = listOf("54x Digital", "12x 12-bit ADC", "2x 12-bit DAC", "CAN", "USB OTG"),
            gpioPinoutSummary = "3.3V ONLY I/O, Native USB + Programming USB port",
            supportedLanguages = listOf("Arduino C++ (.ino)"),
            notes = "3.3V logic only! Erase trigger via 1200bps touch on Programming Port."
        ),

        // ==================== RASPBERRY PI SBC & PICO ====================
        HardwareBoardSpec(
            id = "Raspberry Pi 5",
            displayName = "Raspberry Pi 5",
            seriesGroup = "Raspberry Pi SBC",
            family = DeviceFamily.RASPBERRY_PI,
            chip = "BCM2712 (Cortex-A76) + RP1",
            architecture = "Quad-Core ARM64 @ 2.4 GHz",
            cpuClockMhz = 2400,
            flashSizeLabel = "MicroSD / NVMe PCIe",
            ramLabel = "4 GB / 8 GB / 16 GB LPDDR4X",
            defaultBaud = 115200,
            bootloaderOffset = "MBR/GPT 0x0000",
            appOffset = "0x0000",
            flashMode = "Raw Block (.img/.xz)",
            chipDetectMagic = "BCM2712",
            protocol = FlashProtocol.RPI_RAW_IMG,
            logicVoltage = "3.3V GPIO",
            usbSignatures = emptyList(),
            keyInterfaces = listOf("PCIe 2.0", "Dual 4Kp60 HDMI", "2x USB 3.0", "Gigabit Eth", "Wi-Fi 5/BT 5", "40-pin GPIO", "3-pin Debug UART"),
            gpioPinoutSummary = "40-pin Header (UART14/15, I2C2/3, SPI10/9/11/8) + Dedicated JST-SH Debug UART (115200)",
            supportedLanguages = listOf("Python", "Bash", "C/C++", "Node.js", "Docker"),
            notes = "Flash Raspberry Pi OS 64-bit .img to MicroSD/NVMe via USB OTG card reader with SSH/Wi-Fi cloud-init."
        ),
        HardwareBoardSpec(
            id = "Raspberry Pi 4B",
            displayName = "Raspberry Pi 4 Model B",
            seriesGroup = "Raspberry Pi SBC",
            family = DeviceFamily.RASPBERRY_PI,
            chip = "BCM2711 (Cortex-A72)",
            architecture = "Quad-Core ARM64 @ 1.8 GHz",
            cpuClockMhz = 1800,
            flashSizeLabel = "MicroSD / USB SSD",
            ramLabel = "2 GB / 4 GB / 8 GB LPDDR4",
            defaultBaud = 115200,
            bootloaderOffset = "MBR 0x0000",
            appOffset = "0x0000",
            flashMode = "Raw Block (.img/.xz)",
            chipDetectMagic = "BCM2711",
            protocol = FlashProtocol.RPI_RAW_IMG,
            logicVoltage = "3.3V GPIO",
            usbSignatures = emptyList(),
            keyInterfaces = listOf("Gigabit Ethernet", "Wi-Fi 5 / BLE 5", "2x USB 3.0", "40-pin GPIO"),
            gpioPinoutSummary = "GPIO 14(TXD), 15(RXD), 2(SDA), 3(SCL), 10/9/11(SPI), PWM(12/13/18/19)",
            supportedLanguages = listOf("Python", "Bash", "C/C++", "Node.js"),
            notes = "Supports headless OS imaging + live SSH/Wi-Fi/Serial console management."
        ),
        HardwareBoardSpec(
            id = "Raspberry Pi Zero 2 W",
            displayName = "Raspberry Pi Zero 2 W",
            seriesGroup = "Raspberry Pi SBC",
            family = DeviceFamily.RASPBERRY_PI,
            chip = "RP3A0-AU (BCM2710A1)",
            architecture = "Quad-Core Cortex-A53 @ 1.0 GHz",
            cpuClockMhz = 1000,
            flashSizeLabel = "MicroSD",
            ramLabel = "512 MB LPDDR2",
            defaultBaud = 115200,
            bootloaderOffset = "MBR 0x0000",
            appOffset = "0x0000",
            flashMode = "Raw Block (.img/.xz)",
            chipDetectMagic = "BCM2710",
            protocol = FlashProtocol.RPI_RAW_IMG,
            logicVoltage = "3.3V GPIO",
            usbSignatures = emptyList(),
            keyInterfaces = listOf("Wi-Fi 802.11n", "BLE 4.2", "USB OTG Gadget", "Mini HDMI", "40-pin GPIO"),
            gpioPinoutSummary = "40-pin standard RPi header + USB OTG serial/ethernet gadget support",
            supportedLanguages = listOf("Python", "Bash", "C/C++"),
            notes = "Compact quad-core SBC; supports g_serial USB OTG terminal straight to Android."
        ),
        HardwareBoardSpec(
            id = "Raspberry Pi Pico",
            displayName = "Raspberry Pi Pico / Pico W",
            seriesGroup = "Raspberry Pi Pico",
            family = DeviceFamily.RASPBERRY_PI,
            chip = "RP2040 (+ CYW43439 on W)",
            architecture = "Dual-Core ARM Cortex-M0+ @ 133 MHz",
            cpuClockMhz = 133,
            flashSizeLabel = "2 MB QSPI Flash",
            ramLabel = "264 KB SRAM",
            defaultBaud = 115200,
            bootloaderOffset = "0x10000000 (XIP)",
            appOffset = "0x10000000",
            flashMode = "UF2 Block (512B)",
            chipDetectMagic = "0xE48BFF56",
            protocol = FlashProtocol.RP2040_UF2,
            logicVoltage = "3.3V",
            usbSignatures = listOf(sigRp2040Boot, sigRp2040Cdc),
            keyInterfaces = listOf("26x GPIO", "2x PIO (8 state machines)", "2x UART/I2C/SPI", "Wi-Fi/BLE on Pico W"),
            gpioPinoutSummary = "GP0-GP28, GP25(LED on Pico), WL_GPIO0(LED on Pico W), I2C0(0/1), SPI0(16-19)",
            supportedLanguages = listOf("MicroPython", "CircuitPython", "Pico C/C++ SDK", "Arduino C++"),
            notes = "Hold BOOTSEL while plugging USB OTG for UF2 mode, or connect via USB CDC for MicroPython REPL."
        ),
        HardwareBoardSpec(
            id = "Raspberry Pi Pico 2",
            displayName = "Raspberry Pi Pico 2 / 2 W",
            seriesGroup = "Raspberry Pi Pico",
            family = DeviceFamily.RASPBERRY_PI,
            chip = "RP2350 (Cortex-M33 / Hazard3)",
            architecture = "Dual Cortex-M33 / RISC-V @ 150 MHz",
            cpuClockMhz = 150,
            flashSizeLabel = "4 MB QSPI Flash",
            ramLabel = "520 KB SRAM",
            defaultBaud = 115200,
            bootloaderOffset = "0x10000000 (XIP)",
            appOffset = "0x10000000",
            flashMode = "UF2 (RP2350)",
            chipDetectMagic = "0xE48BFF59",
            protocol = FlashProtocol.RP2040_UF2,
            logicVoltage = "3.3V",
            usbSignatures = listOf(sigRp2350Boot, sigRp2040Cdc),
            keyInterfaces = listOf("26x GPIO", "3x PIO (12 state machines)", "TrustZone Security", "HSTX"),
            gpioPinoutSummary = "Drop-in pin compatible with Pico; 4MB Flash & 520KB SRAM",
            supportedLanguages = listOf("MicroPython", "Pico C/C++ SDK", "Rust", "Arduino C++"),
            notes = "Supports ARM Cortex-M33 and Hazard3 RISC-V execution modes via RP2350 UF2."
        ),
    )

    val allDeviceFilterNames: List<String> = listOf("All devices") + boards.map { it.id }

    fun findById(id: String): HardwareBoardSpec? =
        boards.firstOrNull { it.id.equals(id, ignoreCase = true) }

    fun identifyByUsb(vendorId: Int, productId: Int, productName: String? = null): HardwareBoardSpec {
        val nameHint = productName.orEmpty().lowercase()
        if (nameHint.contains("pico 2") || nameHint.contains("rp2350")) {
            return findById("Raspberry Pi Pico 2") ?: boards.first()
        }
        if (nameHint.contains("pico") || nameHint.contains("rp2040")) {
            return findById("Raspberry Pi Pico") ?: boards.first()
        }
        if (nameHint.contains("uno r4")) return findById("Arduino Uno R4 WiFi") ?: boards.first()
        if (nameHint.contains("mega")) return findById("Arduino Mega 2560") ?: boards.first()
        if (nameHint.contains("cardputer")) return findById("Cardputer") ?: boards.first()
        if (nameHint.contains("cores3")) return findById("CoreS3") ?: boards.first()
        if (nameHint.contains("stickc")) return findById("StickC-Plus2") ?: boards.first()

        return boards.firstOrNull { board ->
            board.usbSignatures.any { it.vendorId == vendorId && it.productId == productId }
        } ?: findById("ESP32 DevKit") ?: boards.first()
    }

    fun searchBoards(
        query: String,
        family: DeviceFamily? = null,
    ): List<HardwareBoardSpec> {
        val familyFiltered = if (family == null) {
            boards
        } else {
            boards.filter { it.family == family }
        }
        val tokens = query.trim()
            .lowercase()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return familyFiltered

        val matched = familyFiltered.filter { spec ->
            val usbIndex = spec.usbSignatures.joinToString(" ") { sig ->
                val hex = "%04x:%04x".format(sig.vendorId, sig.productId)
                "$hex ${sig.bridgeName}"
            }
            val searchableText = buildString {
                append(spec.id).append(' ')
                append(spec.displayName).append(' ')
                append(spec.seriesGroup).append(' ')
                append(spec.family.label).append(' ')
                append(spec.chip).append(' ')
                append(spec.architecture).append(' ')
                append(spec.protocol.label).append(' ')
                append(spec.flashMode).append(' ')
                append(spec.chipDetectMagic).append(' ')
                append(spec.gpioPinoutSummary).append(' ')
                append(spec.notes).append(' ')
                append(spec.keyInterfaces.joinToString(" ")).append(' ')
                append(spec.supportedLanguages.joinToString(" ")).append(' ')
                append(usbIndex)
            }.lowercase()

            tokens.all { token -> searchableText.contains(token) }
        }

        val normalizedQuery = query.trim().lowercase()
        return matched.sortedByDescending { spec ->
            when {
                spec.id.equals(normalizedQuery, ignoreCase = true) -> 3
                spec.id.lowercase().startsWith(normalizedQuery) -> 2
                spec.displayName.lowercase().startsWith(normalizedQuery) -> 1
                else -> 0
            }
        }
    }

    private fun m5Esp32(
        id: String,
        displayName: String,
        seriesGroup: String,
        chip: String,
        flash: String,
        ram: String,
        sig: List<UsbSignature>,
        gpio: String,
        notes: String,
        family: DeviceFamily = DeviceFamily.M5STACK,
    ) = HardwareBoardSpec(
        id = id,
        displayName = displayName,
        seriesGroup = seriesGroup,
        family = family,
        chip = chip,
        architecture = "Xtensa LX6 Dual-Core @ 240 MHz",
        cpuClockMhz = 240,
        flashSizeLabel = flash,
        ramLabel = ram,
        defaultBaud = 921600,
        bootloaderOffset = "0x1000",
        appOffset = "0x10000",
        flashMode = "DIO",
        chipDetectMagic = "0x00F01D83",
        protocol = FlashProtocol.ESPTOOL_SLIP,
        logicVoltage = "3.3V",
        usbSignatures = sig,
        keyInterfaces = listOf("Wi-Fi 2.4GHz", "BLE 4.2", "I2C", "SPI", "UART", "I2S"),
        gpioPinoutSummary = gpio,
        supportedLanguages = listOf("Arduino C++", "MicroPython", "UIFlow", "ESPHome YAML"),
        notes = notes
    )

    private fun m5Esp32S3(
        id: String,
        displayName: String,
        seriesGroup: String,
        chip: String,
        flash: String,
        ram: String,
        gpio: String,
        notes: String,
        family: DeviceFamily = DeviceFamily.M5STACK,
    ) = HardwareBoardSpec(
        id = id,
        displayName = displayName,
        seriesGroup = seriesGroup,
        family = family,
        chip = chip,
        architecture = "Xtensa LX7 Dual-Core @ 240 MHz",
        cpuClockMhz = 240,
        flashSizeLabel = flash,
        ramLabel = ram,
        defaultBaud = 921600,
        bootloaderOffset = "0x0000",
        appOffset = "0x10000",
        flashMode = "QIO",
        chipDetectMagic = "0x00000009",
        protocol = FlashProtocol.ESPTOOL_SLIP,
        logicVoltage = "3.3V",
        usbSignatures = listOf(sigEspJtag, sigEspOtg),
        keyInterfaces = listOf("Wi-Fi 2.4GHz", "BLE 5.0", "Native USB-OTG/JTAG", "I2C", "SPI", "I2S"),
        gpioPinoutSummary = gpio,
        supportedLanguages = listOf("Arduino C++", "MicroPython", "UIFlow 2.0", "CircuitPython", "ESPHome YAML"),
        notes = notes
    )

    private fun m5Esp32C3(
        id: String,
        displayName: String,
        seriesGroup: String,
        chip: String,
        flash: String,
        ram: String,
        gpio: String,
        notes: String,
        family: DeviceFamily = DeviceFamily.M5STACK,
    ) = HardwareBoardSpec(
        id = id,
        displayName = displayName,
        seriesGroup = seriesGroup,
        family = family,
        chip = chip,
        architecture = "RISC-V 32-bit @ 160 MHz",
        cpuClockMhz = 160,
        flashSizeLabel = flash,
        ramLabel = ram,
        defaultBaud = 921600,
        bootloaderOffset = "0x0000",
        appOffset = "0x10000",
        flashMode = "DIO",
        chipDetectMagic = "0x6921506F",
        protocol = FlashProtocol.ESPTOOL_SLIP,
        logicVoltage = "3.3V",
        usbSignatures = listOf(sigEspJtag, sigCh9102),
        keyInterfaces = listOf("Wi-Fi 2.4GHz", "BLE 5.0", "USB-JTAG", "I2C", "SPI", "UART"),
        gpioPinoutSummary = gpio,
        supportedLanguages = listOf("Arduino C++", "MicroPython", "ESPHome YAML"),
        notes = notes
    )

    private fun m5Esp32C5(
        id: String,
        displayName: String,
        seriesGroup: String,
        chip: String,
        flash: String,
        ram: String,
        gpio: String,
        notes: String,
        family: DeviceFamily = DeviceFamily.M5STACK,
    ) = HardwareBoardSpec(
        id = id,
        displayName = displayName,
        seriesGroup = seriesGroup,
        family = family,
        chip = chip,
        architecture = "RISC-V 32-bit @ 240 MHz",
        cpuClockMhz = 240,
        flashSizeLabel = flash,
        ramLabel = ram,
        defaultBaud = 921600,
        bootloaderOffset = "0x2000",
        appOffset = "0x10000",
        flashMode = "QIO",
        chipDetectMagic = "0x1101406F",
        protocol = FlashProtocol.ESPTOOL_SLIP,
        logicVoltage = "3.3V",
        usbSignatures = listOf(sigEspJtag),
        keyInterfaces = listOf("Wi-Fi 6 (2.4/5 GHz)", "BLE 5.0", "Zigbee/Thread", "USB-JTAG"),
        gpioPinoutSummary = gpio,
        supportedLanguages = listOf("Arduino C++", "ESP-IDF C", "MicroPython"),
        notes = notes
    )

    private fun m5Esp32C6(
        id: String,
        displayName: String,
        seriesGroup: String,
        chip: String,
        flash: String,
        ram: String,
        gpio: String,
        notes: String,
        family: DeviceFamily = DeviceFamily.M5STACK,
    ) = HardwareBoardSpec(
        id = id,
        displayName = displayName,
        seriesGroup = seriesGroup,
        family = family,
        chip = chip,
        architecture = "RISC-V HP 160 MHz + LP 20 MHz",
        cpuClockMhz = 160,
        flashSizeLabel = flash,
        ramLabel = ram,
        defaultBaud = 921600,
        bootloaderOffset = "0x0000",
        appOffset = "0x10000",
        flashMode = "DIO",
        chipDetectMagic = "0x2CE0806F",
        protocol = FlashProtocol.ESPTOOL_SLIP,
        logicVoltage = "3.3V",
        usbSignatures = listOf(sigEspJtag),
        keyInterfaces = listOf("Wi-Fi 6", "BLE 5.3", "Zigbee 3.0", "Thread/Matter", "USB-JTAG"),
        gpioPinoutSummary = gpio,
        supportedLanguages = listOf("Arduino C++", "MicroPython", "ESP-IDF C", "ESPHome YAML"),
        notes = notes
    )

    private fun m5Esp32H2(
        id: String,
        displayName: String,
        seriesGroup: String,
        chip: String,
        flash: String,
        ram: String,
        gpio: String,
        notes: String,
        family: DeviceFamily = DeviceFamily.M5STACK,
    ) = HardwareBoardSpec(
        id = id,
        displayName = displayName,
        seriesGroup = seriesGroup,
        family = family,
        chip = chip,
        architecture = "RISC-V 32-bit @ 96 MHz",
        cpuClockMhz = 96,
        flashSizeLabel = flash,
        ramLabel = ram,
        defaultBaud = 921600,
        bootloaderOffset = "0x0000",
        appOffset = "0x10000",
        flashMode = "DIO",
        chipDetectMagic = "0xD7B73E80",
        protocol = FlashProtocol.ESPTOOL_SLIP,
        logicVoltage = "3.3V",
        usbSignatures = listOf(sigEspJtag),
        keyInterfaces = listOf("IEEE 802.15.4", "Zigbee/Thread", "BLE 5.3", "USB-JTAG"),
        gpioPinoutSummary = gpio,
        supportedLanguages = listOf("ESP-IDF C", "Arduino C++", "MicroPython"),
        notes = notes
    )

    private fun m5Esp32P4(
        id: String,
        displayName: String,
        seriesGroup: String,
        chip: String,
        flash: String,
        ram: String,
        gpio: String,
        notes: String,
        family: DeviceFamily = DeviceFamily.M5STACK,
    ) = HardwareBoardSpec(
        id = id,
        displayName = displayName,
        seriesGroup = seriesGroup,
        family = family,
        chip = chip,
        architecture = "Dual RISC-V HP @ 400 MHz + LP @ 40 MHz",
        cpuClockMhz = 400,
        flashSizeLabel = flash,
        ramLabel = ram,
        defaultBaud = 921600,
        bootloaderOffset = "0x2000",
        appOffset = "0x20000",
        flashMode = "QIO",
        chipDetectMagic = "0x00000000",
        protocol = FlashProtocol.ESPTOOL_SLIP,
        logicVoltage = "3.3V",
        usbSignatures = listOf(sigEspJtag, sigEspOtg),
        keyInterfaces = listOf("MIPI-DSI", "MIPI-CSI", "USB 2.0 HS OTG", "Ethernet", "I2S/I2C/SPI"),
        gpioPinoutSummary = gpio,
        supportedLanguages = listOf("ESP-IDF C", "Arduino C++", "MicroPython"),
        notes = notes
    )
}
