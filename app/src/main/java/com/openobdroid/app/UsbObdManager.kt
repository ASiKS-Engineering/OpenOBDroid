package com.openobdroid.app

import android.content.Context
import com.ftdi.j2xx.D2xxManager
import com.ftdi.j2xx.FT_Device

class UsbObdManager(
    private val context: Context,
    private val onDebugMessage: ((String) -> Unit)? = null
) {

    private lateinit var manager: D2xxManager
    private var device: FT_Device? = null

    fun connect(): Boolean {
        onDebugMessage?.invoke("D2xxManager: Requesting instance...")
        manager = D2xxManager.getInstance(context)

        onDebugMessage?.invoke("D2xxManager: Creating device info list...")
        val count =
            manager.createDeviceInfoList(context)
        onDebugMessage?.invoke("D2xxManager: Found $count FTDI devices.")

        if (count <= 0)
            return false

        onDebugMessage?.invoke("D2xxManager: Opening device at index 0...")
        device =
            manager.openByIndex(context, 0)

        device?.apply {
            onDebugMessage?.invoke("FT_Device: Configuring baudrate 38400...")
            setBaudRate(38400)

            onDebugMessage?.invoke("FT_Device: Setting latency timer 2ms...")
            setLatencyTimer(2)

            setDataCharacteristics(
                D2xxManager.FT_DATA_BITS_8,
                D2xxManager.FT_STOP_BITS_1,
                D2xxManager.FT_PARITY_NONE
            )

            setFlowControl(
                D2xxManager.FT_FLOW_NONE,
                0,
                0
            )
        }

        val isOpen = device?.isOpen == true
        onDebugMessage?.invoke("FT_Device: isOpen = $isOpen")
        return isOpen
    }

    fun write(command: String) {
        device?.write(
            (command + "\r").toByteArray()
        )
    }

    fun readUntilPrompt(): String {

        val result = StringBuilder()
        val buffer = ByteArray(256)

        val start =
            System.currentTimeMillis()

        while (
            (System.currentTimeMillis() - start) < 2000
        ) {

            val count =
                device?.read(buffer)
                    ?: 0

            if (count > 0) {

                result.append(
                    String(buffer, 0, count)
                )

                if (result.contains(">"))
                    break
            } else {
                // Short sleep to prevent CPU spiking while waiting
                Thread.sleep(10)
            }
        }

        return result.toString()
    }

    fun close() {
        device?.close()
        device = null
    }
}
