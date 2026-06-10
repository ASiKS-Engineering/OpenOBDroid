package com.openobdroid.app

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
                    Column {
                        Text("OpenOBDroid", fontWeight = FontWeight.Bold)
                        Text(
                            text = "v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
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
                    text = { Text("Dashboard") },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Live Graph") },
                    icon = { Icon(Icons.Default.Timeline, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Cat Test") },
                    icon = { Icon(Icons.Default.Science, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("Activity Log") },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) }
                )
            }

            Crossfade(targetState = selectedTab, label = "TabTransition") { tabIndex ->
                when (tabIndex) {
                    0 -> DashboardTab(vm, context)
                    1 -> GraphTab(vm)
                    2 -> CatTestTab(vm)
                    3 -> LogTab(vm)
                }
            }
        }
    }
}

@Composable
fun CatTestTab(vm: ObdViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Catalysator Performance Test", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "This test checks the correlation between pre-cat and post-cat O2 sensors.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Prerequisites:", style = MaterialTheme.typography.titleSmall)
                BulletPoint("Coolant Temp >= 80°C")
                BulletPoint("RPM: 2000 - 3000")
                BulletPoint("Fuel System: Closed Loop")
                BulletPoint("Condition: Constant Load")
            }
        }

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Test Status", style = MaterialTheme.typography.labelLarge)
                Text(
                    text = vm.catTestStatus,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                
                LinearProgressIndicator(
                    progress = { vm.catTestProgress },
                    modifier = Modifier.fillMaxWidth().clip(CircleShape).height(8.dp)
                )
                
                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (vm.isCatTestRunning) vm.stopCatTest() else vm.startCatTest()
                    },
                    enabled = vm.isCarConnected,
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (vm.isCatTestRunning) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                             else ButtonDefaults.buttonColors()
                ) {
                    Icon(if (vm.isCatTestRunning) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (vm.isCatTestRunning) "Stop Test" else "Start Catalyst Test")
                }
            }
        }

        if (vm.catTestResult != null) {
            val isSuccess = vm.catTestResult?.contains("SUCCESS") == true
            val isWorn = vm.catTestResult?.contains("FAILED") == true
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        isSuccess -> Color(0xFFE8F5E9)
                        isWorn -> Color(0xFFFFEBEE)
                        else -> Color(0xFFFFF3E0)
                    }
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Test Result",
                        style = MaterialTheme.typography.titleMedium,
                        color = when {
                            isSuccess -> Color(0xFF2E7D32)
                            isWorn -> Color(0xFFC62828)
                            else -> Color(0xFFEF6C00)
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = vm.catTestResult ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun BulletPoint(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun DashboardTab(vm: ObdViewModel, context: android.content.Context) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
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
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Diagnostics", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(
                        icon = Icons.Default.Search,
                        label = "Read DTCs",
                        enabled = vm.isCarConnected,
                        onClick = { vm.readDtc() },
                        modifier = Modifier.weight(1f)
                    )
                    ActionButton(
                        icon = Icons.Default.DeleteSweep,
                        label = "Clear DTCs",
                        enabled = vm.isCarConnected,
                        onClick = { vm.clearDtc() },
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Spacer(Modifier.height(12.dp))
                
                CommandDropdown(vm)
            }
        }

        // Results Section
        if (vm.dtcs.isNotEmpty()) {
            Text("Active Trouble Codes", style = MaterialTheme.typography.titleMedium)
            vm.dtcs.forEach { dtc ->
                DtcItem(dtc)
            }
        } else if (vm.isCarConnected) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                    Spacer(Modifier.width(12.dp))
                    Text("No active DTCs found.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun GraphTab(vm: ObdViewModel) {
    val graphablePids = listOf(
        "Read RPM",
        "Read Speed",
        "Read Coolant Temp",
        "Read Lambda",
        "Read O2 Voltage B1S1",
        "Read O2 Voltage B1S2"
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
                        enabled = !vm.isGraphing
                    ) {
                        Text(vm.selectedPidForGraph)
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        graphablePids.forEach { pid ->
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
                    
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${maxVal.toInt()}", style = MaterialTheme.typography.labelSmall)
                            Text(vm.selectedPidForGraph, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text("${data.last().toInt()}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
                        }
                        
                        Canvas(modifier = Modifier.fillMaxSize().padding(vertical = 8.dp)) {
                            val width = size.width
                            val height = size.height
                            val spacing = width / 50f
                            
                            val path = Path()
                            data.forEachIndexed { index, value ->
                                val x = index * spacing
                                val y = height - ((value - minVal) / (maxVal - minVal + 1) * height)
                                
                                if (index == 0) path.moveTo(x, y)
                                else path.lineTo(x, y)
                            }
                            
                            drawPath(
                                path = path,
                                color = Color(0xFF2196F3),
                                style = Stroke(width = 3.dp.toPx())
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
fun ActionButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = MaterialTheme.shapes.small
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun DtcItem(dtc: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(12.dp))
            Text(
                text = dtc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandDropdown(vm: ObdViewModel) {
    var expanded by remember { mutableStateOf(false) }
    var selectedCommand by remember { mutableStateOf(vm.availableCommands[0]) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedCommand,
            onValueChange = {},
            readOnly = true,
            label = { Text("Extra Commands") },
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
                        selectedCommand = command
                        expanded = false
                        vm.runCommand(command)
                    }
                )
            }
        }
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
