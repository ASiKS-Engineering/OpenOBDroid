package com.openobdroid.app

class Elm327Service(
    private val usb: UsbObdManager,
) {

    fun initialize(): Boolean {

        send("ATZ")
        send("ATE0")
        send("ATL0")
        send("ATH0")
        send("ATS0")
        send("ATSP0")

        val id =
            send("ATI")

        return id.isNotBlank()
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

    private fun send(
        cmd:String
    ):String {

        usb.write(cmd)

        Thread.sleep(150)

        return usb.readUntilPrompt()
            .replace(">", "")
            .trim()
    }
}