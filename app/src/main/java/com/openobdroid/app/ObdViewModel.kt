package com.openobdroid.app

import android.content.Context
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.math.sqrt

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

    // Graphing state
    var isGraphing by mutableStateOf(false)
    var graphData = mutableStateListOf<Float>()
    var selectedPidForGraph by mutableStateOf("Read RPM")
    private var graphingJob: Job? = null
    private var connectionJob: Job? = null

    // Catalyst Test state
    var isCatTestRunning by mutableStateOf(false)
    var catTestStatus by mutableStateOf("Not started")
    var catTestResult by mutableStateOf<String?>(null)
    var catTestProgress by mutableStateOf(0f)
    
    private var catTestJob: Job? = null

    private var usb: UsbObdManager? = null
    private var elm: Elm327Service? = null

    private fun addMessage(msg: String) {
        viewModelScope.launch(Dispatchers.Main) {
            messages.add(msg)
        }
    }

    fun connectAdapter(context: Context) {
        if (isAdapterConnected || isBusy) return
        
        connectionJob?.cancel()
        connectionJob = viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                adapterStatus = "Connecting..."
                if (messages.isEmpty() || messages.last().contains("failed", ignoreCase = true) || messages.last().contains("Error")) {
                    messages.clear()
                }
                addMessage("Connecting to Adapter...")
            }

            var success = false
            val maxRetries = 3
            
            for (i in 1..maxRetries) {
                if (i > 1) addMessage("Retry attempt $i of $maxRetries...")
                
                val usbManager = UsbObdManager(context)
                if (usbManager.connect()) {
                    val service = Elm327Service(usbManager)
                    if (service.initialize()) {
                        usb = usbManager
                        elm = service
                        success = true
                        break
                    } else {
                        addMessage("ELM327 initialization failed.")
                        usbManager.close()
                    }
                } else {
                    addMessage("No USB OBD adapter detected.")
                }
                
                if (i < maxRetries) delay(1500)
            }

            withContext(Dispatchers.Main) {
                if (success) {
                    adapterStatus = "Connected"
                    addMessage("Adapter: ELM327 connected.")
                } else {
                    adapterStatus = "Disconnected"
                    addMessage("Error: Failed to connect to adapter after $maxRetries attempts.")
                }
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
        stopGraphing()
        usb?.close()
        usb = null
        elm = null
        adapterStatus = "Disconnected"
        carStatus = "Disconnected"
        addMessage("Disconnected.")
    }

    fun exitApp() {
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

    fun startGraphing() {
        if (elm == null || isGraphing) return
        isGraphing = true
        graphData.clear()
        
        graphingJob = viewModelScope.launch(Dispatchers.IO) {
            val service = elm ?: return@launch
            while (isActive && isGraphing) {
                val command = when (selectedPidForGraph) {
                    "Read RPM" -> "010C"
                    "Read Speed" -> "010D"
                    "Read Coolant Temp" -> "0105"
                    "Read Lambda" -> "0124"
                    "Read O2 Voltage B1S1" -> "0114"
                    "Read O2 Voltage B1S2" -> "0115"
                    else -> null
                }
                
                if (command != null) {
                    val response = when (selectedPidForGraph) {
                        "Read RPM" -> service.readRpm()
                        "Read Speed" -> service.readSpeed()
                        "Read Coolant Temp" -> service.readCoolant()
                        "Read Lambda" -> service.readLambda()
                        "Read O2 Voltage B1S1" -> service.readO2VoltageB1S1()
                        "Read O2 Voltage B1S2" -> service.readO2VoltageB1S2()
                        else -> ""
                    }
                    
                    val value = LiveDataParser.parse(response, command)
                    if (value != null) {
                        withContext(Dispatchers.Main) {
                            graphData.add(value)
                            if (graphData.size > 50) {
                                graphData.removeAt(0)
                            }
                        }
                    }
                }
                delay(200) // 5Hz sampling
            }
        }
    }

    fun stopGraphing() {
        isGraphing = false
        graphingJob?.cancel()
        graphingJob = null
    }

    fun startCatTest() {
        val service = elm ?: return
        if (isCatTestRunning) return
        
        isCatTestRunning = true
        catTestResult = null
        catTestProgress = 0f
        
        catTestJob = viewModelScope.launch(Dispatchers.IO) {
            val s1Data = mutableListOf<Float>()
            val s2Data = mutableListOf<Float>()
            
            while (isActive && isCatTestRunning) {
                // Check prerequisites
                val temp = LiveDataParser.parse(service.readCoolant(), "0105") ?: 0f
                val rpm = LiveDataParser.parse(service.readRpm(), "010C") ?: 0f
                val statusValue = LiveDataParser.parse(service.readFuelSystemStatus(), "0103")?.toInt() ?: 0
                
                val isClosedLoop = (statusValue and 0x02) != 0
                val isTempOk = temp >= 80f
                val isRpmOk = rpm in 2000f..3000f
                
                if (!isTempOk || !isRpmOk || !isClosedLoop) {
                    withContext(Dispatchers.Main) {
                        catTestStatus = buildString {
                            append("Waiting for prerequisites: ")
                            if (!isTempOk) append("Temp < 80°C (${temp.toInt()}°C). ")
                            if (!isRpmOk) append("RPM 2000-3000 (${rpm.toInt()}). ")
                            if (!isClosedLoop) append("Need Closed Loop. ")
                        }
                        catTestProgress = 0f
                    }
                    s1Data.clear()
                    s2Data.clear()
                    delay(1000)
                    continue
                }
                
                // Prerequisites met, collect data
                withContext(Dispatchers.Main) {
                    catTestStatus = "Collecting samples... (${s1Data.size}/50)"
                    catTestProgress = s1Data.size / 50f
                }
                
                val s1 = LiveDataParser.parse(service.readO2VoltageB1S1(), "0114")
                val s2 = LiveDataParser.parse(service.readO2VoltageB1S2(), "0115")
                
                if (s1 != null && s2 != null) {
                    s1Data.add(s1)
                    s2Data.add(s2)
                }
                
                if (s1Data.size >= 50) {
                    val corr = calculateCorrelation(s1Data, s2Data)
                    withContext(Dispatchers.Main) {
                        isCatTestRunning = false
                        catTestProgress = 1f
                        catTestStatus = "Test Complete"
                        catTestResult = when {
                            corr < 0.3f -> "Result: SUCCESS (Corr: ${"%.2f".format(corr)}). Catalysator is fine."
                            corr <= 0.6f -> "Result: BORDERLINE (Corr: ${"%.2f".format(corr)}). Catalysator is aging."
                            else -> "Result: FAILED (Corr: ${"%.2f".format(corr)}). Catalysator is worn."
                        }
                        addMessage("Catalyst Test Result: $catTestResult")
                    }
                    break
                }
                delay(200) // 5Hz
            }
        }
    }

    fun stopCatTest() {
        isCatTestRunning = false
        catTestJob?.cancel()
        catTestJob = null
        catTestStatus = "Stopped"
    }

    private fun calculateCorrelation(x: List<Float>, y: List<Float>): Float {
        if (x.size != y.size || x.isEmpty()) return 0f
        val n = x.size
        val sumX = x.sum()
        val sumY = y.sum()
        val sumXY = x.zip(y).sumOf { (it.first * it.second).toDouble() }.toFloat()
        val sumX2 = x.sumOf { (it * it).toDouble() }.toFloat()
        val sumY2 = y.sumOf { (it * it).toDouble() }.toFloat()
        
        val numerator = n * sumXY - sumX * sumY
        val denominator = sqrt((n * sumX2 - sumX * sumX) * (n * sumY2 - sumY * sumY))
        return if (denominator == 0f) 0f else numerator / denominator
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
