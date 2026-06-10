package com.openobdroid.app

import android.content.Context
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class ObdViewModel : ViewModel() {

    var adapterStatus by mutableStateOf("Disconnected")
    var carStatus by mutableStateOf("Disconnected")

    private val _events = MutableSharedFlow<ObdEvent>()
    val events = _events.asSharedFlow()

    sealed class ObdEvent {
        object CloseApp : ObdEvent()
    }

    // UI state properties
    val isBusy by derivedStateOf { adapterStatus == "Connecting..." || carStatus == "Checking..." }
    val isCarConnected by derivedStateOf { carStatus == "Connected" }
    val isAdapterConnected by derivedStateOf { adapterStatus == "Connected" }

    var dtcs by mutableStateOf(emptyList<String>())
    val messages = mutableStateListOf<String>()

    val availableCommands = listOf(
        "Read RPM",
        "Read Speed",
        "Read Coolant Temp",
        "Read VIN",
        "Read Pending DTCs",
        "Read Lambda",
        "Read O2 Voltage B1S1",
        "Read O2 Voltage B1S2"
    )

    private var usb: UsbObdManager? = null
    private var elm: Elm327Service? = null

    private fun addMessage(msg: String) {
        viewModelScope.launch(Dispatchers.Main) {
            messages.add(msg)
        }
    }

    fun connectAdapter(context: Context) {
        if (isAdapterConnected) return
        
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                adapterStatus = "Connecting..."
                if (messages.isEmpty() || messages.last().contains("failed", ignoreCase = true) || messages.last().contains("Error")) {
                    messages.clear()
                }
                addMessage("Connecting to Adapter...")
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
                }
            } else {
                addMessage("Error: No USB OBD adapter detected.")
                withContext(Dispatchers.Main) { adapterStatus = "Disconnected" }
            }
        }
    }

    fun connectCar() {
        val service = elm ?: return
        if (isCarConnected) return

        viewModelScope.launch(Dispatchers.IO) {
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
                if (i < 3) delay(500)
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
        
        viewModelScope.launch {
            _events.emit(ObdEvent.CloseApp)
        }
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

    fun runCommand(commandName: String) {
        val service = elm ?: return
        addMessage("Running: $commandName")
        viewModelScope.launch(Dispatchers.IO) {
            val response = when (commandName) {
                "Read RPM" -> service.readRpm()
                "Read Speed" -> service.readSpeed()
                "Read Coolant Temp" -> service.readCoolant()
                "Read VIN" -> service.readVin()
                "Read Pending DTCs" -> service.readPendingDtc()
                "Read Lambda" -> service.readLambda()
                "Read O2 Voltage B1S1" -> service.readO2VoltageB1S1()
                "Read O2 Voltage B1S2" -> service.readO2VoltageB1S2()
                else -> "Unknown command"
            }
            addMessage("Response: $response")
        }
    }
}
