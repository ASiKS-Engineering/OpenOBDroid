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
        
        // Grouping common setup commands
        send("ATE0 L0 H0 S0") 
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

    private fun send(cmd: String): String {
        usb.write(cmd)

        val rawResponse = usb.readUntilPrompt()
            .replace(">", "")
            .trim()
            
        // Log raw response for debugging
        if (rawResponse.isNotBlank()) {
            val hex = rawResponse.map { "%02X".format(it.code) }.joinToString(" ")
            onDebugMessage?.invoke("RAW [$cmd]: ${rawResponse.replace("\r", "\\r").replace("\n", "\\n")}")
            onDebugMessage?.invoke("HEX [$cmd]: $hex")
        }

        // Split by lines and remove the echoed command if it's the first line
        val lines = rawResponse.split(Regex("[\r\n]+")).filter { it.isNotBlank() }
        
        return if (lines.isNotEmpty() && lines[0].equals(cmd, ignoreCase = true)) {
            lines.drop(1).joinToString(" ").trim()
        } else {
            lines.joinToString(" ").trim()
        }
    }
}
