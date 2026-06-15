package com.openobdroid.app

class Elm327Service(
    private val usb: UsbObdManager,
    private val onDebugMessage: ((String) -> Unit)? = null
) {

    fun initialize(): Boolean {
        onDebugMessage?.invoke("Initializing ELM327...")

        // Fast check: Try ATI first. If it works, we skip ATZ to save 1+ seconds.
        val id = send("ATI")
        if (id.isNotBlank() && !id.contains("?")) {
            onDebugMessage?.invoke("Fast connection: ELM327 ID: $id")
        } else {
            onDebugMessage?.invoke("Cold start: Performing Reset (ATZ)...")
            send("ATZ")
            Thread.sleep(200) // Reduced from 500ms
            val retryId = send("ATI")
            if (retryId.isBlank()) return false
            onDebugMessage?.invoke("Reset complete: ELM327 ID: $retryId")
        }
        
        // Common setup commands
        send("ATE0") 
        send("ATL0")
        send("ATH0")
        send("ATS0")
        onDebugMessage?.invoke("Configured: Echo, Linefeeds, Headers, Spaces OFF")
        
        send("ATSP0")
        onDebugMessage?.invoke("Protocol: Auto")

        return true
    }

    fun readDtc() =
        send("03")

    fun clearDtc() =
        send("04")

    fun readPendingDtc() =
        send("07")

    fun readVin() =
        send("0902")

    fun readRpm() =
        send("010C")

    fun readSpeed() =
        send("010D")

    fun readCoolant() =
        send("0105")

    fun readLoad() =
        send("0104")

    fun readFuelSystemStatus() =
        send("0103")

    fun readLambda() =
        send("0124")

    fun readO2VoltageB1S1() =
        send("0114")

    fun readO2VoltageB1S2() =
        send("0115")

    fun readSTFT1() =
        send("0106")

    fun readLTFT1() =
        send("0107")

    fun readMAF() =
        send("0110")

    fun readMAP() =
        send("010B")

    fun getSupportedPids(): List<String> {
        val supported = mutableListOf<String>()
        val ranges = listOf("00", "20", "40", "60", "80", "A0", "C0")
        
        for (range in ranges) {
            val response = send("01$range")
            val clean = response.replace(" ", "").uppercase()
            val header = "41$range"
            
            if (clean.contains(header)) {
                val dataIndex = clean.indexOf(header) + 4
                if (dataIndex + 8 <= clean.length) {
                    val bitmaskHex = clean.substring(dataIndex, dataIndex + 8)
                    try {
                        val bitmask = bitmaskHex.toLong(16)
                        
                        for (i in 0 until 32) {
                            if ((bitmask and (1L shl (31 - i))) != 0L) {
                                val pidNum = range.toInt(16) + i + 1
                                supported.add(pidNum.toString(16).padStart(2, '0').uppercase())
                            }
                        }
                        
                        // Bit 32 (LSB) indicates if the next range is supported
                        if ((bitmask and 1L) == 0L) break
                    } catch (e: Exception) {
                        break
                    }
                } else break
            } else break
        }
        return supported
    }

    fun runCommandDirect(cmd: String): String {
        usb.write(cmd)

        val rawResponse = usb.readUntilPrompt()
            .replace(">", "")
            .trim()

        // Split by lines and remove the echoed command if it's the first line
        val lines = rawResponse.split(Regex("[\r\n]+")).filter { it.isNotBlank() }
        
        return if (lines.isNotEmpty() && lines[0].equals(cmd, ignoreCase = true)) {
            lines.drop(1).joinToString(" ").trim()
        } else {
            lines.joinToString(" ").trim()
        }
    }

    private fun send(cmd: String) = runCommandDirect(cmd)
}
