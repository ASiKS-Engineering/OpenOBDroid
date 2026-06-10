package com.openobdroid.app

object LiveDataParser {

    fun parse(response: String, command: String): Float? {
        val clean = response.replace(" ", "").replace("\r", "").replace("\n", "").uppercase()
        
        // Mode 1 response starts with 41 + PID
        val pid = command.takeLast(2)
        val header = "41$pid"
        
        if (!clean.contains(header)) return null
        
        val dataIndex = clean.indexOf(header) + 4
        if (dataIndex + 2 > clean.length) return null
        
        return try {
            when (pid) {
                "0C" -> { // RPM
                    val a = clean.substring(dataIndex, dataIndex + 2).toInt(16)
                    val b = clean.substring(dataIndex + 2, dataIndex + 4).toInt(16)
                    ((a * 256) + b) / 4.0f
                }
                "0D" -> { // Speed
                    clean.substring(dataIndex, dataIndex + 2).toInt(16).toFloat()
                }
                "05" -> { // Coolant
                    clean.substring(dataIndex, dataIndex + 2).toInt(16).toFloat() - 40f
                }
                "04" -> { // Load
                    clean.substring(dataIndex, dataIndex + 2).toInt(16) * 100f / 255f
                }
                "03" -> { // Fuel System Status
                    clean.substring(dataIndex, dataIndex + 2).toInt(16).toFloat()
                }
                "14", "15" -> { // O2 Voltage
                    clean.substring(dataIndex, dataIndex + 2).toInt(16) / 200.0f
                }
                "24" -> { // Lambda
                    val a = clean.substring(dataIndex, dataIndex + 2).toInt(16)
                    val b = clean.substring(dataIndex + 2, dataIndex + 4).toInt(16)
                    ((a * 256) + b) / 32768.0f
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
