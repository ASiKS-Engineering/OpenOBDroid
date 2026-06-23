package com.openobdroid.app

import android.content.Context
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.app.Application
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sqrt

class ObdViewModel(application: Application) : AndroidViewModel(application) {

    val settings = SettingsManager(application)
    private val db = AppDatabase.getDatabase(application)
    private val recordingDao = db.recordingDao()

    var adapterStatus by mutableStateOf("Disconnected")
    var carStatus by mutableStateOf("Disconnected")
    var isCommandExecuting by mutableStateOf(false)

    private val _events = MutableSharedFlow<ObdEvent>()
    val events = _events.asSharedFlow()

    sealed class ObdEvent {
        object CloseApp : ObdEvent()
        data class ShareFile(val file: File) : ObdEvent()
    }

    // UI state properties
    val isBusy by derivedStateOf { adapterStatus == "Connecting..." || carStatus == "Checking..." }
    val isCarConnected by derivedStateOf { carStatus == "Connected" }
    val isAdapterConnected by derivedStateOf { adapterStatus == "Connected" }

    var dtcs by mutableStateOf(emptyList<String>())
    val messages = mutableStateListOf<String>()

    private val staticCommands = listOf(
        "Read DTCs",
        "Clear DTCs",
        "Read Pending DTCs",
        "Read VIN"
    )

    val availableCommands by derivedStateOf {
        staticCommands + supportedPids
            .filter { pidNames.containsKey(it) }
            .map { "Read ${pidNames[it]}" }
    }

    private val pidNames = mapOf(
        "0C" to "Engine RPM",
        "0D" to "Vehicle Speed",
        "05" to "Coolant Temp",
        "04" to "Engine Load",
        "0B" to "MAP Sensor",
        "0F" to "Intake Air Temp",
        "10" to "MAF Flow Rate",
        "11" to "Throttle Position",
        "14" to "O2 Voltage B1S1",
        "15" to "O2 Voltage B1S2",
        "06" to "STFT Bank 1",
        "07" to "LTFT Bank 1",
        "24" to "Lambda B1S1"
    )

    var supportedPids by mutableStateOf(emptyList<String>())
    val graphablePids by derivedStateOf {
        // Only numeric sensor data makes sense for graphs
        val sensors = listOf("0C", "0D", "04", "0B", "10", "11", "14", "15", "06", "07", "24", "05", "0F")
        supportedPids
            .filter { sensors.contains(it) && pidNames.containsKey(it) }
            .map { pidNames[it]!! }
    }

    // Graphing state
    var isGraphing by mutableStateOf(false)
    var graphData = mutableStateListOf<Float>()
    var selectedPidForGraph by mutableStateOf("Engine RPM")
    private var graphingJob: Job? = null
    private var connectionJob: Job? = null

    // Recording state
    var isRecording by mutableStateOf(false)
    private var currentSessionId: Long? = null
    private var recordingJob: Job? = null
    val sessions = recordingDao.getAllSessions()

    fun startRecording() {
        if (isRecording || elm == null) return
        isRecording = true
        addMessage("Recording started. Reading DTCs...")
        
        viewModelScope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val name = "Log ${sdf.format(Date(startTime))}"
            
            // Read DTCs at start
            val dtcData = elm?.readDtc()
            val dtcsStart = if (dtcData != null) DtcParser.parse(dtcData).joinToString(",") else null
            if (dtcsStart != null && dtcsStart.isNotEmpty()) addMessage("DTCs at start: $dtcsStart")
            
            currentSessionId = recordingDao.insertSession(
                RecordingSession(startTime = startTime, name = name, dtcsAtStart = dtcsStart)
            )
            
            recordingJob = launch {
                while (isActive && isRecording) {
                    val sessionId = currentSessionId ?: break
                    val dataPoints = mutableListOf<SensorDataPoint>()
                    val timestamp = System.currentTimeMillis()
                    
                    // Poll all supported sensors that we have names for
                    for (pid in supportedPids) {
                        if (pidNames.containsKey(pid)) {
                            val response = elm?.runCommandDirect("01$pid")
                            val value = LiveDataParser.parse(response ?: "", "01$pid")
                            if (value != null) {
                                dataPoints.add(
                                    SensorDataPoint(
                                        sessionId = sessionId,
                                        timestamp = timestamp,
                                        pid = pid,
                                        name = pidNames[pid] ?: pid,
                                        value = value
                                    )
                                )
                            }
                        }
                    }
                    
                    if (dataPoints.isNotEmpty()) {
                        recordingDao.insertDataPoints(dataPoints)
                    }
                    
                    delay(settings.recordingIntervalMs.toLong())
                }
            }
        }
    }

    fun stopRecording() {
        if (!isRecording) return
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null
        addMessage("Recording stopped. Reading DTCs...")
        
        viewModelScope.launch(Dispatchers.IO) {
            val sessionId = currentSessionId ?: return@launch
            
            // Read DTCs at end
            val dtcData = elm?.readDtc()
            val dtcsEnd = if (dtcData != null) DtcParser.parse(dtcData).joinToString(",") else null
            if (dtcsEnd != null && dtcsEnd.isNotEmpty()) addMessage("DTCs at end: $dtcsEnd")

            val session = recordingDao.getSessionById(sessionId)
            if (session != null) {
                recordingDao.updateSession(session.copy(
                    endTime = System.currentTimeMillis(),
                    dtcsAtEnd = dtcsEnd
                ))
            }
        }
    }

    fun deleteSession(session: RecordingSession) {
        viewModelScope.launch(Dispatchers.IO) {
            recordingDao.deleteSession(session)
        }
    }

    fun shareSession(session: RecordingSession) {
        viewModelScope.launch(Dispatchers.IO) {
            val data = recordingDao.getDataForSession(session.id)
            if (data.isEmpty()) return@launch

            val csvFile = File(getApplication<Application>().cacheDir, "${session.name.replace(" ", "_")}.csv")
            try {
                FileWriter(csvFile).use { writer ->
                    if (session.dtcsAtStart != null) writer.write("# DTCs at start: ${session.dtcsAtStart}\n")
                    if (session.dtcsAtEnd != null) writer.write("# DTCs at end: ${session.dtcsAtEnd}\n")
                    writer.write("Timestamp,TimeDelta(ms),PID,Name,Value\n")
                    data.forEach { point ->
                        writer.write("${point.timestamp},${point.timestamp - session.startTime},${point.pid},${point.name},${point.value}\n")
                    }
                }
                _events.emit(ObdEvent.ShareFile(csvFile))
            } catch (e: Exception) {
                addMessage("Error exporting data: ${e.message}")
            }
        }
    }

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
                // Use LiveDataParser to verify we got a valid RPM response (header 41 0C + data)
                if (LiveDataParser.parse(response, "010C") != null) {
                    carFound = true
                    break
                }
                if (i < 3) delay(500)
            }

            withContext(Dispatchers.Main) {
                if (carFound) {
                    carStatus = "Connected"
                    addMessage("System: Connected to vehicle.")
                    
                    // Fetch supported PIDs
                    viewModelScope.launch(Dispatchers.IO) {
                        val pids = service.getSupportedPids()
                        withContext(Dispatchers.Main) {
                            supportedPids = pids
                            addMessage("Detected ${pids.size} supported PIDs.")
                            if (pids.contains("0C")) selectedPidForGraph = "Engine RPM"
                            else if (graphablePids.isNotEmpty()) selectedPidForGraph = graphablePids.first()
                        }
                    }
                } else {
                    carStatus = "Disconnected"
                    addMessage("System: Car ECU not responding. Is the ignition ON?")
                }
            }
        }
    }

    fun cleanup() {
        stopGraphing()
        
        usb?.close()
        usb = null
        elm = null
        adapterStatus = "Disconnected"
        carStatus = "Disconnected"
    }

    fun disconnect() {
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
                val pidHex = pidNames.entries.find { it.value == selectedPidForGraph }?.key
                val command = if (pidHex != null) "01$pidHex" else null
                
                if (command != null) {
                    val response = service.runCommandDirect(command)
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

    fun runCommand(commandName: String) {
        if (isCommandExecuting) return
        
        when (commandName) {
            "Read DTCs" -> readDtc()
            "Clear DTCs" -> clearDtc()
            "Read Pending DTCs" -> {
                if (elm == null) return
                isCommandExecuting = true
                viewModelScope.launch(Dispatchers.IO) {
                    val response = elm?.readPendingDtc() ?: "Error"
                    withContext(Dispatchers.Main) {
                        addMessage("Result: $response")
                        isCommandExecuting = false
                    }
                }
            }
            "Read VIN" -> {
                if (elm == null) return
                isCommandExecuting = true
                viewModelScope.launch(Dispatchers.IO) {
                    val response = elm?.readVin() ?: "Error"
                    withContext(Dispatchers.Main) {
                        addMessage("Result: $response")
                        isCommandExecuting = false
                    }
                }
            }
            else -> {
                val service = elm ?: return
                // Check if it's one of our dynamic PIDs
                val displayName = commandName.removePrefix("Read ")
                val pidHex = pidNames.entries.find { it.value == displayName }?.key
                
                if (pidHex != null) {
                    isCommandExecuting = true
                    addMessage("Running: $commandName")
                    viewModelScope.launch(Dispatchers.IO) {
                        val command = "01$pidHex"
                        val response = service.runCommandDirect(command)
                        withContext(Dispatchers.Main) {
                            addMessage("Result: $response")
                            isCommandExecuting = false
                        }
                    }
                } else {
                    addMessage("Unknown command: $commandName")
                }
            }
        }
    }
}
