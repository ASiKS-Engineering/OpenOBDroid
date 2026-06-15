package com.openobdroid.app

import android.content.Context
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.io.IOException

class UsbLinManager(
    private val context: Context,
    private val onDataReceived: (String) -> Unit,
    private val onDebugMessage: (String) -> Unit
) : SerialInputOutputManager.Listener {

    private var port: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null

    fun connect(): Boolean {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        if (availableDrivers.isEmpty()) {
            onDebugMessage("No USB-Serial devices found")
            return false
        }

        val driver = availableDrivers[0]
        val connection = manager.openDevice(driver.device)
        if (connection == null) {
            onDebugMessage("Opening device failed")
            return false
        }

        port = driver.ports[0]
        try {
            port?.open(connection)
            port?.setParameters(115200, 8, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1)
            
            val ioManager = SerialInputOutputManager(port, this)
            this.ioManager = ioManager
            ioManager.start()
            
            onDebugMessage("Connected to SmartLIN-USB")
            return true
        } catch (e: IOException) {
            onDebugMessage("Connection error: ${e.message}")
            return false
        }
    }

    fun sendCommand(cmd: String) {
        try {
            port?.write((cmd + "\r").toByteArray(), 100)
        } catch (e: IOException) {
            onDebugMessage("Write error: ${e.message}")
        }
    }

    override fun onNewData(data: ByteArray) {
        val message = String(data)
        onDataReceived(message)
    }

    override fun onRunError(e: Exception) {
        onDebugMessage("IO Error: ${e.message}")
    }

    fun close() {
        ioManager?.stop()
        port?.close()
    }
}
