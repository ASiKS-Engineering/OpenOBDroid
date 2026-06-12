package com.openobdroid.app

import android.content.Context
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.app.Application
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.math.sqrt

class ObdViewModel(application: Application) : AndroidViewModel(application) {

    val settings = SettingsManager(application)

    var adapterStatus by mutableStateOf("Disconnected")
    var carStatus by mutableStateOf("Disconnected")
    var isCommandExecuting by mutableStateOf(false)

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
        "Read DTCs",
        "Clear DTCs",
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
    var catTestRemainingSeconds by mutableStateOf(0)
    var wasPrereqViolated by mutableStateOf(false)
    var lastViolationMessage by mutableStateOf<String?>(null)
    
    // Pre-condition monitoring state
    var isPreMonitorRunning by mutableStateOf(false)
    var currentCoolantTemp by mutableStateOf<Float?>(null)
    var currentRpm by mutableStateOf<Float?>(null)
    var currentLoad by mutableStateOf<Float?>(null)
    var currentMaf by mutableStateOf<Float?>(null)
    var isClosedLoop by mutableStateOf(false)
    
    // Stability indicators
    var isRpmStable by mutableStateOf(false)
    var isLoadStable by mutableStateOf(false)
    
    private val rpmBuffer = mutableListOf<Float>()
    private val loadBuffer = mutableListOf<Float>()
    
    val canStartCatTest by derivedStateOf {
        isCarConnected && 
        (currentCoolantTemp ?: 0f) >= 80f && 
        (currentRpm ?: 0f) in 2000f..3000f && 
        isClosedLoop && 
        isRpmStable && 
        isLoadStable
    }

    private var catTestJob: Job? = null
    private var preMonitorJob: Job? = null

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
                addMessage("Scanning for USB Adapter...")
            }

            var hardwareFound = false
            val scanStartTime = System.currentTimeMillis()
            val scanTimeoutMs = 10000L // 10 second timeout for scanning/permission
            
            // 1. Hardware Scan Loop (Wait for permission/detection)
            var currentUsbManager = usb
            while (isActive && (System.currentTimeMillis() - scanStartTime) < scanTimeoutMs) {
                if (currentUsbManager == null) {
                    currentUsbManager = UsbObdManager(getApplication(), settings) { debugMsg ->
                        addMessage("DEBUG: $debugMsg")
                    }
                }

                if (currentUsbManager.connect()) {
                    usb = currentUsbManager
                    hardwareFound = true
                    break
                }
                
                delay(1000) // Retry every second
                withContext(Dispatchers.Main) {
                    val remaining = ((scanTimeoutMs - (System.currentTimeMillis() - scanStartTime)) / 1000).toInt()
                    addMessage("No adapter found. Retrying for $remaining s... (Check for permission popup)")
                }
            }

            if (!hardwareFound) {
                withContext(Dispatchers.Main) {
                    adapterStatus = "Disconnected"
                    addMessage("Error: No USB adapter detected or permission denied after 10s.")
                }
                return@launch
            }

            // 2. ELM327 Initialization (Now that hardware is confirmed)
            addMessage("Hardware link established. Initializing ELM327...")
            var serviceSuccess = false
            val maxServiceRetries = 3
            
            for (i in 1..maxServiceRetries) {
                if (i > 1) addMessage("ELM327 retry attempt $i...")
                
                val service = Elm327Service(usb!!) { debugMsg ->
                    addMessage("DEBUG: $debugMsg")
                }
                
                if (service.initialize()) {
                    elm = service
                    serviceSuccess = true
                    break
                }
                
                if (i < maxServiceRetries) delay(1000)
            }

            withContext(Dispatchers.Main) {
                if (serviceSuccess) {
                    adapterStatus = "Connected"
                    addMessage("Adapter: ELM327 connected and ready.")
                } else {
                    adapterStatus = "Disconnected"
                    addMessage("Error: Hardware found, but ELM327 protocol failed to respond.")
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

    fun cleanup() {
        stopGraphing()
        stopCatTest()
        stopPreMonitor()
        
        currentCoolantTemp = null
        currentRpm = null
        isClosedLoop = false

        usb?.close()
        usb = null
        elm = null
        adapterStatus = "Disconnected"
        carStatus = "Disconnected"
    }

    fun disconnect() {
        stopPreMonitor()
        stopGraphing()
        cleanup()
        addMessage("Disconnected.")
    }

    override fun onCleared() {
        super.onCleared()
        cleanup()
    }

    fun exitApp() {
        viewModelScope.launch {
            _events.emit(ObdEvent.CloseApp)
        }
    }

    fun readDtc() {
        if (elm == null || isCommandExecuting) return
        isCommandExecuting = true
        addMessage("Reading Diagnostic Trouble Codes...")
        viewModelScope.launch(Dispatchers.IO) {
            val data = elm?.readDtc()
            if (data == null) {
                addMessage("Error: Failed to read from car.")
                isCommandExecuting = false
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
                    parsed.forEach { code ->
                        addMessage("Result: $code")
                    }
                }
                isCommandExecuting = false
            }
        }
    }

    fun clearDtc() {
        if (elm == null || isCommandExecuting) return
        isCommandExecuting = true
        addMessage("Clearing Diagnostic Trouble Codes...")
        viewModelScope.launch(Dispatchers.IO) {
            elm?.clearDtc()
            addMessage("Clear command sent to ECU.")
            withContext(Dispatchers.Main) {
                dtcs = emptyList()
                isCommandExecuting = false
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
                            if (graphData.size > 100) { // Increased history for faster sampling
                                graphData.removeAt(0)
                            }
                        }
                    }
                }
                delay(100) // 10Hz sampling (increased from 5Hz)
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
        catTestRemainingSeconds = 30
        wasPrereqViolated = false
        lastViolationMessage = null
        
        catTestJob = viewModelScope.launch(Dispatchers.IO) {
            val s1Data = mutableListOf<Float>()
            val s2Data = mutableListOf<Float>()
            val totalSamples = 150 // 30 seconds at 5Hz
            val startTime = System.currentTimeMillis()
            
            while (isActive && isCatTestRunning && s1Data.size < totalSamples) {
                // Check prerequisites
                val temp = currentCoolantTemp ?: 0f
                val rpm = currentRpm ?: 0f
                
                val isTempOk = temp >= 80f
                val isRpmOk = rpm in 2000f..3000f
                val isLoopOk = isClosedLoop
                
                if (!isTempOk || !isRpmOk || !isLoopOk) {
                    wasPrereqViolated = true
                    val reason = buildString {
                        if (!isTempOk) append("Temp low. ")
                        if (!isRpmOk) append("RPM out of range. ")
                        if (!isLoopOk) append("Open Loop. ")
                    }
                    withContext(Dispatchers.Main) {
                        lastViolationMessage = "Warning: $reason"
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        lastViolationMessage = null
                    }
                }
                
                // Collect data regardless of violations (per requirement)
                val s1 = LiveDataParser.parse(service.readO2VoltageB1S1(), "0114")
                val s2 = LiveDataParser.parse(service.readO2VoltageB1S2(), "0115")
                
                if (s1 != null && s2 != null) {
                    s1Data.add(s1)
                    s2Data.add(s2)
                }

                val elapsed = System.currentTimeMillis() - startTime
                withContext(Dispatchers.Main) {
                    catTestProgress = s1Data.size.toFloat() / totalSamples
                    catTestRemainingSeconds = (30 - (elapsed / 1000)).toInt().coerceAtLeast(0)
                    catTestStatus = "Running Test... ${catTestRemainingSeconds}s left"
                }
                
                delay(200) // 5Hz sampling
            }
            
            if (s1Data.size >= 50) { // Require at least some data to calculate
                val corr = calculateCorrelation(s1Data, s2Data)
                val stdS1 = calculateStandardDeviation(s1Data)
                val stdS2 = calculateStandardDeviation(s2Data)
                
                val damping = if (stdS1 > 0) (1f - (stdS2 / stdS1)).coerceIn(0f, 1f) else 0f
                val invCorr = (1f - corr).coerceIn(0f, 1f)
                
                val efficiency = (0.5f * invCorr) + (0.5f * damping)
                val efficiencyPct = (efficiency * 100).toInt()
                
                withContext(Dispatchers.Main) {
                    isCatTestRunning = false
                    catTestProgress = 1f
                    catTestStatus = "Test Complete"
                    val baseResult = when {
                        efficiencyPct >= 75 -> "SUCCESS ($efficiencyPct%)"
                        efficiencyPct >= 50 -> "BORDERLINE ($efficiencyPct%)"
                        else -> "FAILED ($efficiencyPct%)"
                    }
                    
                    catTestResult = if (wasPrereqViolated) {
                        "Result: $baseResult. (Warning: Prerequisites were violated during test, result might be inaccurate)."
                    } else {
                        "Result: $baseResult. Catalyst is healthy."
                    }
                    addMessage("Catalyst Test: $catTestResult")
                    lastViolationMessage = null
                }
            } else {
                withContext(Dispatchers.Main) {
                    isCatTestRunning = false
                    catTestStatus = "Test Failed: Insufficient Data"
                }
            }
        }
    }

    fun stopCatTest() {
        isCatTestRunning = false
        catTestJob?.cancel()
        catTestJob = null
        catTestStatus = "Stopped"
    }

    fun startPreMonitor() {
        if (preMonitorJob != null || elm == null) return
        
        isPreMonitorRunning = true
        preMonitorJob = viewModelScope.launch(Dispatchers.IO) {
            val service = elm ?: return@launch
            addMessage("Starting prerequisite monitoring...")
            
            while (isActive) {
                try {
                    // Poll data from ECU
                    val coolantResp = service.readCoolant()
                    val rpmResp = service.readRpm()
                    val fuelResp = service.readFuelSystemStatus()
                    
                    val temp = LiveDataParser.parse(coolantResp, "0105")
                    val rpmValue = LiveDataParser.parse(rpmResp, "010C")
                    val statusValue = LiveDataParser.parse(fuelResp, "0103")?.toInt() ?: 0
                    
                    // Automatic Load/MAF detection for the constant load prerequisite
                    val loadResp = service.readLoad()
                    var loadValue = LiveDataParser.parse(loadResp, "0104")
                    var mafValue: Float? = null
                    
                    if (loadValue == null) {
                        val mafResp = service.readMAF()
                        mafValue = LiveDataParser.parse(mafResp, "0110")
                    }
                    
                    withContext(Dispatchers.Main) {
                        currentCoolantTemp = temp
                        currentRpm = rpmValue
                        currentLoad = loadValue
                        currentMaf = mafValue
                        isClosedLoop = (statusValue and 0x02) != 0
                        
                        // Update stability buffers (last 5 samples)
                        if (rpmValue != null) {
                            rpmBuffer.add(rpmValue)
                            if (rpmBuffer.size > 5) rpmBuffer.removeAt(0)
                        }
                        
                        val activeLoadValue = loadValue ?: mafValue
                        if (activeLoadValue != null) {
                            loadBuffer.add(activeLoadValue)
                            if (loadBuffer.size > 5) loadBuffer.removeAt(0)
                        }
                        
                        // Check Stability logic
                        if (rpmBuffer.size >= 3) {
                            val rpmRange = rpmBuffer.max() - rpmBuffer.min()
                            isRpmStable = rpmRange < 100f
                        } else {
                            isRpmStable = false
                        }
                        
                        if (loadBuffer.size >= 3) {
                            val avgLoad = loadBuffer.average().toFloat()
                            val loadStdDev = calculateStandardDeviation(loadBuffer)
                            // Stable if fluctuation is < 5% of average
                            isLoadStable = if (avgLoad > 0) (loadStdDev / avgLoad) < 0.05f else false
                        } else {
                            isLoadStable = false
                        }
                    }
                } catch (e: Exception) {
                    // Log transient errors to debug if needed
                }
                
                // Use a smaller delay for better responsiveness, or adjust based on loop time
                delay(800)
            }
        }
    }

    fun stopPreMonitor() {
        isPreMonitorRunning = false
        preMonitorJob?.cancel()
        preMonitorJob = null
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

    private fun calculateStandardDeviation(data: List<Float>): Float {
        if (data.isEmpty()) return 0f
        val mean = data.average().toFloat()
        val variance = data.map { (it - mean) * (it - mean) }.average().toFloat()
        return sqrt(variance)
    }

    fun runCommand(commandName: String) {
        if (isCommandExecuting) return
        
        when (commandName) {
            "Read DTCs" -> readDtc()
            "Clear DTCs" -> clearDtc()
            else -> {
                val service = elm ?: return
                isCommandExecuting = true
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
                    withContext(Dispatchers.Main) {
                        addMessage("Result: $response")
                        isCommandExecuting = false
                    }
                }
            }
        }
    }
}
