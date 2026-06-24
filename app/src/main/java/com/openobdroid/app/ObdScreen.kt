package com.openobdroid.app

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObdScreen(
    vm: ObdViewModel,
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("OpenOBDroid", fontWeight = FontWeight.Bold)
                            Text(
                                text = "v${BuildConfig.VERSION_NAME} by ASiKS-Engineering",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            GlobalStatusIndicator(
                                icon = Icons.Default.Usb,
                                label = "Adapter",
                                isConnected = vm.isAdapterConnected,
                                isBusy = vm.adapterStatus == "Connecting..."
                            )
                            GlobalStatusIndicator(
                                icon = Icons.Default.DirectionsCar,
                                label = "Car",
                                isConnected = vm.isCarConnected,
                                isBusy = vm.carStatus == "Checking..."
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = { vm.exitApp() }) {
                        Icon(Icons.Default.Close, contentDescription = "Close App")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (vm.isGraphing) {
                                    Badge(containerColor = Color(0xFF4CAF50))
                                }
                            }
                        ) {
                            Icon(Icons.Default.Timeline, contentDescription = "Live Graph")
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (vm.isRecording) {
                                    Badge(containerColor = MaterialTheme.colorScheme.error)
                                }
                            }
                        ) {
                            Icon(Icons.Default.RadioButtonChecked, contentDescription = "Record")
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Log") }
                )
                Tab(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                )
            }

            Crossfade(targetState = selectedTab, label = "TabTransition") { tabIndex ->
                when (tabIndex) {
                    0 -> DashboardTab(vm, context)
                    1 -> GraphTab(vm)
                    2 -> RecordingsTab(vm)
                    3 -> LogTab(vm)
                    4 -> SettingsTab(vm)
                }
            }
        }
    }
}

@Composable
fun DashboardTab(vm: ObdViewModel, context: android.content.Context) {
    var selectedCommand by remember { mutableStateOf(if (vm.availableCommands.isNotEmpty()) vm.availableCommands[0] else "") }
    
    // Ensure selectedCommand is valid when list updates
    LaunchedEffect(vm.availableCommands) {
        if (selectedCommand.isEmpty() && vm.availableCommands.isNotEmpty()) {
            selectedCommand = vm.availableCommands[0]
        } else if (selectedCommand.isNotEmpty() && !vm.availableCommands.contains(selectedCommand)) {
            // Keep it if it's a static command, otherwise reset
            if (!selectedCommand.startsWith("Read ")) {
                // okay
            } else if (vm.availableCommands.isNotEmpty()) {
                selectedCommand = vm.availableCommands[0]
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Connection Status", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                
                StatusIndicator(
                    label = "Adapter",
                    status = vm.adapterStatus,
                    isConnected = vm.isAdapterConnected
                )
                Spacer(Modifier.height(8.dp))
                StatusIndicator(
                    label = "Vehicle ECU",
                    status = vm.carStatus,
                    isConnected = vm.isCarConnected
                )
            }
        }

        // Action Button
        Button(
            onClick = {
                when {
                    vm.isCarConnected -> vm.disconnect()
                    vm.isAdapterConnected -> vm.connectCar()
                    else -> vm.connectAdapter(context)
                }
            },
            enabled = !vm.isBusy,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            contentPadding = PaddingValues(16.dp)
        ) {
            val buttonData: Pair<ImageVector, String> = when {
                vm.isCarConnected -> Pair(Icons.Default.UsbOff, "Disconnect")
                vm.isAdapterConnected -> Pair(Icons.Default.DirectionsCar, "Connect to Car")
                else -> Pair(Icons.Default.Usb, "Connect Adapter")
            }
            val (icon, text) = buttonData
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }

        if (vm.isBusy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        // Diagnostics Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Diagnostics", style = MaterialTheme.typography.titleMedium)
                
                CommandSelectionRow(
                    vm = vm,
                    selectedCommand = selectedCommand,
                    onCommandSelected = { selectedCommand = it }
                )

                if (vm.isCommandExecuting) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                Button(
                    onClick = { vm.runCommand(selectedCommand) },
                    enabled = vm.isCarConnected && !vm.isCommandExecuting,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(if (vm.isCommandExecuting) Icons.Default.HourglassEmpty else Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (vm.isCommandExecuting) "Executing..." else "Execute Command")
                }
            }
        }

        // Results Section - Scrollable List
        Text("Command Results", style = MaterialTheme.typography.titleMedium)
        
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            val results = vm.messages.filter { it.startsWith("Result:") || it.startsWith("Success:") || it.startsWith("Error:") || it.contains("Results:") }
            val listState = rememberLazyListState()
            
            LaunchedEffect(results.size) {
                if (results.isNotEmpty()) {
                    listState.animateScrollToItem(results.size - 1)
                }
            }

            if (results.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No results to display", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(results) { result ->
                        ResultItem(result)
                    }
                }
            }
        }
    }
}

@Composable
fun ResultItem(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                text.startsWith("Error:") -> MaterialTheme.colorScheme.errorContainer
                text.startsWith("Success:") -> Color(0xFFE8F5E9)
                else -> MaterialTheme.colorScheme.secondaryContainer
            }
        )
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(8.dp),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandSelectionRow(
    vm: ObdViewModel,
    selectedCommand: String,
    onCommandSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedCommand,
            onValueChange = {},
            readOnly = true,
            label = { Text("Commands") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            enabled = vm.isCarConnected,
            textStyle = MaterialTheme.typography.bodyMedium
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            vm.availableCommands.forEach { command ->
                DropdownMenuItem(
                    text = { Text(command) },
                    onClick = {
                        onCommandSelected(command)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun GraphTab(vm: ObdViewModel) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 10.sp
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Live Data Plot", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                
                var expanded by remember { mutableStateOf(false) }
                
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !vm.isGraphing && vm.graphablePids.isNotEmpty()
                    ) {
                        Text(if (vm.graphablePids.isEmpty()) "Connect car to see PIDs" else vm.selectedPidForGraph)
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        vm.graphablePids.forEach { pid ->
                            DropdownMenuItem(
                                text = { Text(pid) },
                                onClick = {
                                    vm.selectedPidForGraph = pid
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                Button(
                    onClick = {
                        if (vm.isGraphing) vm.stopGraphing() else vm.startGraphing()
                    },
                    enabled = vm.isCarConnected,
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (vm.isGraphing) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                             else ButtonDefaults.buttonColors()
                ) {
                    Icon(if (vm.isGraphing) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (vm.isGraphing) "Stop Live Reading" else "Start Live Reading")
                }
            }
        }

        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                if (vm.graphData.isEmpty()) {
                    Text(
                        "No data yet. Start reading to see the graph.",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    val data = vm.graphData.toList()
                    val maxVal = (data.maxOrNull() ?: 1f).coerceAtLeast(1f)
                    val minVal = (data.minOrNull() ?: 0f)
                    val range = (maxVal - minVal).coerceAtLeast(0.1f)
                    
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    val axisColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)

                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            Text(vm.selectedPidForGraph, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Text("${data.last().toInt()}", color = primaryColor, fontWeight = FontWeight.ExtraBold)
                        }
                        
                        Canvas(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
                            val labelAreaWidth = 45.dp.toPx()
                            val labelAreaHeight = 20.dp.toPx()
                            val graphWidth = size.width - labelAreaWidth
                            val graphHeight = size.height - labelAreaHeight
                            
                            // 1. Draw Grid lines and Y-axis scale labels
                            val ySteps = 4
                            for (i in 0..ySteps) {
                                val y = graphHeight - (i * graphHeight / ySteps)
                                val labelValue = minVal + (i * (maxVal - minVal) / ySteps)
                                
                                // Grid line
                                drawLine(
                                    color = gridColor,
                                    start = androidx.compose.ui.geometry.Offset(labelAreaWidth, y),
                                    end = androidx.compose.ui.geometry.Offset(size.width, y),
                                    strokeWidth = 1.dp.toPx()
                                )
                                
                                // Label
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = labelValue.toInt().toString(),
                                    style = labelStyle,
                                    topLeft = androidx.compose.ui.geometry.Offset(5.dp.toPx(), y - 7.dp.toPx())
                                )
                            }
                            
                            // 2. Draw Axis Lines
                            drawLine( // Y-Axis
                                color = axisColor,
                                start = androidx.compose.ui.geometry.Offset(labelAreaWidth, 0f),
                                end = androidx.compose.ui.geometry.Offset(labelAreaWidth, graphHeight),
                                strokeWidth = 2.dp.toPx()
                            )
                            drawLine( // X-Axis
                                color = axisColor,
                                start = androidx.compose.ui.geometry.Offset(labelAreaWidth, graphHeight),
                                end = androidx.compose.ui.geometry.Offset(size.width, graphHeight),
                                strokeWidth = 2.dp.toPx()
                            )

                            // 3. Draw X-Axis Label
                            drawText(
                                textMeasurer = textMeasurer,
                                text = "Samples (History)",
                                style = labelStyle,
                                topLeft = androidx.compose.ui.geometry.Offset(labelAreaWidth + (graphWidth / 2) - 40.dp.toPx(), size.height - 18.dp.toPx())
                            )

                            // 4. Draw Data Path
                            val xSpacing = graphWidth / 100f
                            val path = Path()
                            data.forEachIndexed { index, value ->
                                val x = labelAreaWidth + (index * xSpacing)
                                val y = graphHeight - ((value - minVal) / range * graphHeight)
                                
                                if (index == 0) path.moveTo(x, y)
                                else path.lineTo(x, y)
                            }
                            
                            drawPath(
                                path = path,
                                color = primaryColor,
                                style = Stroke(
                                    width = 3.dp.toPx(),
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusIndicator(label: String, status: String, isConnected: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (isConnected) Color(0xFF4CAF50) else if (status == "Connecting..." || status == "Checking...") Color(0xFFFFC107) else Color(0xFFF44336))
        )
        Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            status,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SettingsTab(vm: ObdViewModel) {
    val settings = vm.settings
    
    var baudRate by remember { mutableStateOf(settings.baudRate.toString()) }
    var latencyTimer by remember { mutableStateOf(settings.latencyTimer.toString()) }
    var readTimeout by remember { mutableStateOf(settings.readTimeout.toString()) }
    var bufferSize by remember { mutableStateOf(settings.bufferSize.toString()) }
    var promptTimeout by remember { mutableStateOf(settings.promptTimeout.toString()) }
    var recordingInterval by remember { mutableStateOf(settings.recordingIntervalMs.toFloat()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("USB & Timing Settings", style = MaterialTheme.typography.titleLarge)
        Text(
            "Changes will take effect the next time you connect to the adapter.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )

        SettingsTextField(
            label = "Baud Rate",
            value = baudRate,
            onValueChange = { 
                baudRate = it
                it.toIntOrNull()?.let { v -> settings.baudRate = v }
            },
            helperText = "Default: 38400 (Standard ELM327)"
        )

        SettingsTextField(
            label = "Latency Timer (ms)",
            value = latencyTimer,
            onValueChange = { 
                latencyTimer = it
                it.toIntOrNull()?.let { v -> settings.latencyTimer = v }
            },
            helperText = "Default: 2 (Lower is faster, but may be unstable)"
        )

        SettingsTextField(
            label = "Read Timeout (ms)",
            value = readTimeout,
            onValueChange = { 
                readTimeout = it
                it.toIntOrNull()?.let { v -> 
                    settings.readTimeout = v
                    // Sync promptTimeout UI if it was adjusted by manager
                    promptTimeout = settings.promptTimeout.toString()
                }
            },
            helperText = "Internal D2XX driver timeout. Default: 500"
        )

        SettingsTextField(
            label = "Read Buffer Size (bytes)",
            value = bufferSize,
            onValueChange = { 
                bufferSize = it
                it.toIntOrNull()?.let { v -> settings.bufferSize = v }
            },
            helperText = "Default: 256"
        )

        SettingsTextField(
            label = "Prompt Wait Timeout (ms)",
            value = promptTimeout,
            onValueChange = { 
                promptTimeout = it
                it.toLongOrNull()?.let { v -> 
                    settings.promptTimeout = v
                    // Sync UI back if the manager clamped the value
                    if (v < settings.readTimeout + 100) {
                        promptTimeout = settings.promptTimeout.toString()
                    }
                }
            },
            helperText = "Must be > Read Timeout. Default: 600"
        )

        Text("Recording Interval: ${recordingInterval.toInt()} ms", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = recordingInterval,
            onValueChange = { 
                recordingInterval = it
                settings.recordingIntervalMs = it.toInt()
            },
            valueRange = 10f..1000f,
            steps = 98
        )
        
        Spacer(Modifier.height(16.dp))
        
        OutlinedButton(
            onClick = {
                settings.baudRate = 38400
                settings.latencyTimer = 2
                settings.readTimeout = 500
                settings.bufferSize = 256
                settings.promptTimeout = 600L
                settings.recordingIntervalMs = 1000
                
                baudRate = "38400"
                latencyTimer = "2"
                readTimeout = "500"
                bufferSize = "256"
                promptTimeout = "600"
                recordingInterval = 1000f
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reset to Defaults")
        }
    }
}

@Composable
fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    helperText: String
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )
        Text(
            text = helperText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalStatusIndicator(icon: ImageVector, label: String, isConnected: Boolean, isBusy: Boolean) {
    val color = when {
        isConnected -> Color(0xFF4CAF50) // Green
        isBusy -> Color(0xFFFFC107)      // Yellow
        else -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
    }
    
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(if (isConnected) "$label: Connected" else if (isBusy) "$label: Connecting..." else "$label: Disconnected")
            }
        },
        state = rememberTooltipState()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun LogTab(vm: ObdViewModel) {
    val listState = rememberLazyListState()
    
    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isNotEmpty()) {
            listState.animateScrollToItem(vm.messages.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .background(MaterialTheme.colorScheme.surface),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        items(vm.messages) { msg ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        msg.contains("failed", ignoreCase = true) || msg.startsWith("Error") -> MaterialTheme.colorScheme.errorContainer
                        msg.contains("Connected", ignoreCase = true) || msg.contains("Ready") -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp),
                    color = when {
                        msg.contains("failed", ignoreCase = true) || msg.startsWith("Error") -> MaterialTheme.colorScheme.onErrorContainer
                        msg.contains("Connected", ignoreCase = true) || msg.contains("Ready") -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
fun RecordingsTab(vm: ObdViewModel) {
    val sessions by vm.sessions.collectAsState(initial = emptyList())
    var intervalMs by remember { mutableStateOf(vm.settings.recordingIntervalMs.toFloat()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Sensor Data Recording", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                
                // Interval Setting
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Target Interval: ${intervalMs.toInt()} ms", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = intervalMs,
                            onValueChange = { 
                                intervalMs = it
                                vm.settings.recordingIntervalMs = it.toInt()
                            },
                            valueRange = 10f..1000f,
                            steps = 98,
                            enabled = !vm.isRecording
                        )
                        
                        if (vm.isRecording) {
                            Text(
                                "Achieved Cycle: ${vm.actualRecordingIntervalMs} ms",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (vm.actualRecordingIntervalMs > vm.settings.recordingIntervalMs + 50) 
                                        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                Text(
                    "Note: Cycle time depends on the number of selected PIDs.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (vm.isRecording) vm.stopRecording() else vm.startRecording()
                    },
                    enabled = vm.isCarConnected,
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (vm.isRecording) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                             else ButtonDefaults.buttonColors()
                ) {
                    Icon(if (vm.isRecording) Icons.Default.Stop else Icons.Default.RadioButtonChecked, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (vm.isRecording) "Stop Recording" else "Start Recording")
                }
            }
        }

        // PID Selection Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Select PIDs to Record", style = MaterialTheme.typography.titleSmall)
                    
                    if (vm.supportedPids.isNotEmpty() && !vm.isRecording) {
                        TextButton(onClick = {
                            val allWithNames = vm.supportedPids.filter { vm.pidNames.containsKey(it) }
                            if (vm.selectedPidsToRecord.size == allWithNames.size) {
                                vm.selectedPidsToRecord.clear()
                            } else {
                                vm.selectedPidsToRecord.clear()
                                vm.selectedPidsToRecord.addAll(allWithNames)
                            }
                        }) {
                            val allWithNames = vm.supportedPids.filter { vm.pidNames.containsKey(it) }
                            Text(if (vm.selectedPidsToRecord.size == allWithNames.size) "Deselect All" else "Select All")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                
                if (vm.supportedPids.isEmpty()) {
                    Text("Connect to car to see available PIDs.", style = MaterialTheme.typography.bodySmall)
                } else {
                    // Group PIDs by their names
                    val pidsWithNames = vm.supportedPids.filter { vm.pidNames.containsKey(it) }
                    
                    pidsWithNames.forEach { pid ->
                        val name = vm.pidNames[pid] ?: pid
                        val isSelected = vm.selectedPidsToRecord.contains(pid)
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    if (checked) vm.selectedPidsToRecord.add(pid)
                                    else vm.selectedPidsToRecord.remove(pid)
                                },
                                enabled = !vm.isRecording
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        Text("Saved Recordings", style = MaterialTheme.typography.titleMedium)

        if (sessions.isEmpty()) {
            Text("No recordings found.", style = MaterialTheme.typography.bodyMedium)
        } else {
            // Since parent is scrollable, we shouldn't use LazyColumn here directly with fillMaxSize
            // instead we can just map the sessions to SessionItems
            sessions.forEach { session ->
                SessionItem(
                    session = session,
                    onShare = { vm.shareSession(session) },
                    onDelete = { vm.deleteSession(session) }
                )
            }
        }
    }
}

@Composable
fun SessionItem(
    session: RecordingSession,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(session.name, style = MaterialTheme.typography.titleSmall)
                val duration = if (session.endTime != null) {
                    val diff = session.endTime - session.startTime
                    "${diff / 1000}s"
                } else {
                    "In progress..."
                }
                Text("Duration: $duration", style = MaterialTheme.typography.labelSmall)
                if (!session.dtcsAtStart.isNullOrBlank()) {
                    Text("DTC Start: ${session.dtcsAtStart}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                if (!session.dtcsAtEnd.isNullOrBlank()) {
                    Text("DTC End: ${session.dtcsAtEnd}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
            
            Row {
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = "Share")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
