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

1. Clone the repository.
2. Ensure you have the latest Android Studio installed.
3. The project uses the JitPack repository for certain dependencies, which is configured in `settings.gradle.kts`.
4. Build and deploy to an Android device (API Level 26+ recommended).

## License

This project is developed by ASiKS-Engineering.
