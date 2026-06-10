package com.openobdroid.app

import android.content.Context
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*

class ObdViewModel : ViewModel() {

    var adapterStatus by mutableStateOf("Disconnected")
    var carStatus by mutableStateOf("Disconnected")

    // UI state properties
    val isBusy by derivedStateOf { adapterStatus == "Connecting..." || carStatus == "Checking..." }
    val isCarConnected by derivedStateOf { carStatus == "Connected" }
    val isAdapterConnected by derivedStateOf { adapterStatus == "Connected" }

    var dtcs by mutableStateOf(emptyList<String>())
    val messages = mutableStateListOf<String>()

    private var usb: UsbObdManager? = null
    private var elm: Elm327Service? = null

    private fun addMessage(msg: String) {
        viewModelScope.launch(Dispatchers.Main) {
            messages.add(msg)
        }
    }

    fun connect(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            // Step 1: Connect to Adapter if needed
            if (usb == null || !isAdapterConnected) {
                withContext(Dispatchers.Main) {
                    adapterStatus = "Connecting..."
                    messages.clear()
                    addMessage("Initializing Adapter connection...")
                }

                val usbManager = UsbObdManager(context)
                if (usbManager.connect()) {
                    val service = Elm327Service(usbManager)
                    if (service.initialize()) {
                        usb = usbManager
                        elm = service
                        withContext(Dispatchers.Main) { adapterStatus = "Connected" }
                        addMessage("Adapter: ELM327 connected.")
                    } else {
                        addMessage("Error: ELM327 initialization failed.")
                        usbManager.close()
                        withContext(Dispatchers.Main) { adapterStatus = "Disconnected" }
                        return@launch
                    }
                } else {
                    addMessage("Error: No USB OBD adapter detected.")
                    withContext(Dispatchers.Main) { adapterStatus = "Disconnected" }
                    return@launch
                }
            }

            // Step 2: Connect to Car (ECU)
            val service = elm ?: return@launch
            withContext(Dispatchers.Main) {
                carStatus = "Checking..."
                addMessage("Verifying connection to Car ECU...")
            }

            var carFound = false
            for (i in 1..3) {
                addMessage("ECU check attempt $i of 3...")
                val response = service.readRpm()
                if (response.isNotBlank() && 
                    !response.contains("UNABLE", ignoreCase = true) && 
                    !response.contains("NO DATA", ignoreCase = true) &&
                    !response.contains("?", ignoreCase = true)) {
                    carFound = true
                    break
                }
                if (i < 3) delay(1000)
            }

            withContext(Dispatchers.Main) {
                if (carFound) {
                    carStatus = "Connected"
                    addMessage("System: Connected to vehicle.")
                } else {
                    carStatus = "Disconnected"
                    addMessage("System: Car ECU not responding. Is the ignition ON?")
                }
            }
        }
    }

    fun disconnect() {
        usb?.close()
        usb = null
        elm = null
        adapterStatus = "Disconnected"
        carStatus = "Disconnected"
        addMessage("Disconnected.")
    }

    fun readDtc() {
        if (elm == null) return
        addMessage("Reading Diagnostic Trouble Codes...")
        viewModelScope.launch(Dispatchers.IO) {
            val data = elm?.readDtc()
            if (data == null) {
                addMessage("Error: Failed to read from car.")
                return@launch
            }
            addMessage("Raw response: $data")
            val parsed = DtcParser.parse(data)
            withContext(Dispatchers.Main) {
                dtcs = parsed
                if (parsed.isEmpty()) {
                    addMessage("Success: No active DTCs found.")
                } else {
                    addMessage("Results: Found ${parsed.size} codes.")
                }
            }
        }
    }

    fun clearDtc() {
        if (elm == null) return
        addMessage("Clearing Diagnostic Trouble Codes...")
        viewModelScope.launch(Dispatchers.IO) {
            elm?.clearDtc()
            addMessage("Clear command sent to ECU.")
            withContext(Dispatchers.Main) {
                dtcs = emptyList()
            }
        }
    }
}
