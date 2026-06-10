package com.openobdroid.app

import android.content.Context
import com.ftdi.j2xx.D2xxManager
import com.ftdi.j2xx.FT_Device

class UsbObdManager(
    private val context: Context,
) {

    private lateinit var manager: D2xxManager
    private var device: FT_Device? = null

    fun connect(): Boolean {

        manager = D2xxManager.getInstance(context)

        val count =
            manager.createDeviceInfoList(context)

        if (count <= 0)
            return false

        device =
            manager.openByIndex(context, 0)

        device?.apply {

            setBaudRate(38400)

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

        return device?.isOpen == true
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