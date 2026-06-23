# OpenOBDroid

OpenOBDroid is an Android application designed for vehicle diagnostics via OBD-II, specifically optimized for use with the **STARDIAG CAN327-USB Adapter**. It provides real-time data monitoring, logging, and diagnostic code management.

## Features

- **Real-time Dashboard**: Monitor connection status to both the USB adapter and the vehicle's ECU.
- **Live Graphing**: Visualize sensor data in real-time with adjustable PID selection.
- **Advanced Recording**:
    - High-frequency data logging (adjustable interval from 10ms to 1000ms).
    - Automatic DTC (Diagnostic Trouble Code) capture at the start and end of every recording session.
    - Data stored in a local SQLite database (Room).
    - Export logs as CSV files for external analysis.
- **Diagnostics**: Read and clear Diagnostic Trouble Codes (DTCs), read VIN, and execute custom OBD-II commands.
- **Broad Compatibility**: Supports both FTDI D2XX drivers and standard USB-to-Serial chips via the `usb-serial-for-android` library.

## Hardware Requirements

- Android device with USB Host support (OTG).
- STARDIAG CAN327-USB Adapter (or compatible FTDI/ELM327 USB adapter).
- USB OTG cable/adapter.

## Technical Details

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Database**: Room Persistence Library
- **Dependencies**:
    - [usb-serial-for-android](https://github.com/mik3y/usb-serial-for-android) (v3.10.0)
    - FTDI D2XX Library
    - JitPack repository for extended library support

## Development Setup

1. Clone the repository: `git clone https://github.com/your-repo/OpenOBDroid.git`
2. Ensure you have the latest **Android Studio** installed.
3. The project uses the **JitPack** repository for certain dependencies, which is already configured in `settings.gradle.kts`.
4. Ensure the `libs/d2xx.jar` file is present in the `app/libs/` directory for FTDI support. You can download the FTDI D2XX library for Android from [here](https://ftdichip.com/software-examples/android-java-d2xx/).

## Build Instructions

### Using Android Studio
1. Open the project in Android Studio.
2. Wait for Gradle Sync to complete.
3. Click the **Run** button or use `Shift + F10` to build and deploy to a connected device.

### Using Command Line (Gradle)
To build the debug APK, run the following command in the project root:

```bash
# Windows
.\gradlew assembleDebug

# macOS/Linux
./gradlew assembleDebug
```

The generated APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

## License

This project is open source and available under the [MIT License](LICENSE).
