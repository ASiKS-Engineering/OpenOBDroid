package com.openobdroid.app

object DtcParser {

    private val commonCodes = mapOf(
        // Powertrain (P)
        "P0100" to "Mass or Volume Air Flow Circuit Malfunction",
        "P0101" to "Mass or Volume Air Flow Circuit Range/Performance Problem",
        "P0102" to "Mass or Volume Air Flow Circuit Low Input",
        "P0103" to "Mass or Volume Air Flow Circuit High Input",
        "P0110" to "Intake Air Temperature Circuit Malfunction",
        "P0115" to "Engine Coolant Temperature Circuit Malfunction",
        "P0116" to "Engine Coolant Temperature Circuit Range/Performance Problem",
        "P0120" to "Throttle/Pedal Position Sensor/Switch A Circuit Malfunction",
        "P0121" to "Throttle/Pedal Position Sensor/Switch A Circuit Range/Performance",
        "P0130" to "O2 Sensor Circuit Malfunction (Bank 1 Sensor 1)",
        "P0131" to "O2 Sensor Circuit Low Voltage (Bank 1 Sensor 1)",
        "P0132" to "O2 Sensor Circuit High Voltage (Bank 1 Sensor 1)",
        "P0133" to "O2 Sensor Circuit Slow Response (Bank 1 Sensor 1)",
        "P0134" to "O2 Sensor Circuit No Activity Detected (Bank 1 Sensor 1)",
        "P0135" to "O2 Sensor Heater Circuit Malfunction (Bank 1 Sensor 1)",
        "P0141" to "O2 Sensor Heater Circuit Malfunction (Bank 1 Sensor 2)",
        "P0171" to "System Too Lean (Bank 1)",
        "P0172" to "System Too Rich (Bank 1)",
        "P0174" to "System Too Lean (Bank 2)",
        "P0175" to "System Too Rich (Bank 2)",
        "P0201" to "Injector Circuit Malfunction - Cylinder 1",
        "P0202" to "Injector Circuit Malfunction - Cylinder 2",
        "P0203" to "Injector Circuit Malfunction - Cylinder 3",
        "P0204" to "Injector Circuit Malfunction - Cylinder 4",
        "P0300" to "Random/Multiple Cylinder Misfire Detected",
        "P0301" to "Cylinder 1 Misfire Detected",
        "P0302" to "Cylinder 2 Misfire Detected",
        "P0303" to "Cylinder 3 Misfire Detected",
        "P0304" to "Cylinder 4 Misfire Detected",
        "P0325" to "Knock Sensor 1 Circuit Malfunction (Bank 1 or Single Sensor)",
        "P0335" to "Crankshaft Position Sensor A Circuit Malfunction",
        "P0340" to "Camshaft Position Sensor A Circuit Malfunction",
        "P0401" to "Exhaust Gas Recirculation Flow Insufficient Detected",
        "P0402" to "Exhaust Gas Recirculation Flow Excessive Detected",
        "P0420" to "Catalyst System Efficiency Below Threshold (Bank 1)",
        "P0430" to "Catalyst System Efficiency Below Threshold (Bank 2)",
        "P0440" to "Evaporative Emission Control System Malfunction",
        "P0442" to "Evaporative Emission Control System Leak Detected (Small Leak)",
        "P0500" to "Vehicle Speed Sensor Malfunction",
        "P0505" to "Idle Control System Malfunction",
        "P0601" to "Internal Control Module Memory Check Sum Error",
        "P0700" to "Transmission Control System Malfunction",
        "P0705" to "Transmission Range Sensor Circuit Malfunction (PRNDL Input)",
        "P0305" to "Cylinder 5 Misfire Detected",
        "P0306" to "Cylinder 6 Misfire Detected",
        "P0307" to "Cylinder 7 Misfire Detected",
        "P0308" to "Cylinder 8 Misfire Detected",
        "P0411" to "Secondary Air Injection System Incorrect Flow Detected",
        "P0422" to "Main Catalyst Efficiency Below Threshold (Bank 1)",
        "P0441" to "Evaporative Emission Control System Incorrect Purge Flow",
        "P0443" to "Evaporative Emission Control System Purge Control Valve Circuit Malfunction",
        "P0446" to "Evaporative Emission Control System Vent Control Circuit Malfunction",
        "P0455" to "Evaporative Emission Control System Leak Detected (Gross Leak)",
        "P0460" to "Fuel Level Sensor Circuit Malfunction",
        "P0501" to "Vehicle Speed Sensor Range/Performance",
        "P0506" to "Idle Control System RPM Lower Than Expected",
        "P0507" to "Idle Control System RPM Higher Than Expected",
        "P0603" to "Internal Control Module Keep Alive Memory (KAM) Error",
        "P1135" to "Air/Fuel Sensor Heater Circuit (Bank 1 Sensor 1)",
        "P1155" to "Air/Fuel Sensor Heater Circuit (Bank 2 Sensor 1)",
        
        // Chassis (C)
        "C0035" to "Left Front Wheel Speed Sensor Malfunction",
        "C0040" to "Right Front Wheel Speed Sensor Malfunction",
        "C0221" to "Right Front Wheel Speed Sensor Circuit Open",
        
        // Body (B)
        "B0001" to "Driver Frontal Air Bag Deployment Control 1 - Circuit Malfunction",
        "B1200" to "Climate Control Pushbutton Circuit Malfunction",
        
        // Network (U)
        "U0001" to "High Speed CAN Communication Bus",
        "U0100" to "Lost Communication With ECM/PCM A"
    )

    fun parse(
        response: String,
    ): List<String> {

        val clean =
            response
                .replace(" ","")
                .replace("\r","")
                .replace("\n","")
                .uppercase()

        // Standard OBD-II Mode 03 response starts with 43
        // Mode 07 (Pending) starts with 47
        if (!clean.startsWith("43") && !clean.startsWith("47"))
            return emptyList()

        val result =
            mutableListOf<String>()

        var index = 2

        while (
            (index + 3) < clean.length
        ) {

            val codeHex =
                clean.substring(
                    index,
                    index + 4
                )

            if (codeHex == "0000")
                break

            val decodedCode = decode(codeHex)
            val description = commonCodes[decodedCode] ?: getGenericDescription(decodedCode)
            
            result.add("$decodedCode: $description")

            index += 4
        }

        return result
    }

    private fun getGenericDescription(code: String): String {
        val type = when (code.firstOrNull()) {
            'P' -> "Powertrain"
            'C' -> "Chassis"
            'B' -> "Body"
            'U' -> "Network"
            else -> "Unknown"
        }
        val isManufacturer = code.getOrNull(1) != '0'
        val origin = if (isManufacturer) "Manufacturer Specific" else "Generic OBD-II"
        
        return "$origin $type Code"
    }

    private fun decode(
        hex:String
    ): String {

        val firstHex = hex.substring(0, 1)
        val secondHex = hex.substring(1, 2)
        val remaining = hex.substring(2, 4)

        val firstChar = when (firstHex) {
            "0", "1", "2", "3" -> "P"
            "4", "5", "6", "7" -> "C"
            "8", "9", "A", "B" -> "B"
            else -> "U"
        }

        val firstDigit = firstHex.toInt(16) and 0x3

        return "$firstChar$firstDigit$secondHex$remaining"
    }
}
