package com.openobdroid.app

import androidx.compose.animation.*
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
                title = { Text("OpenOBDroid", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
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
                    text = { Text("Activity Log") },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) }
                )
            }

            Crossfade(targetState = selectedTab, label = "TabTransition") { tabIndex ->
                when (tabIndex) {
                    0 -> DashboardTab(vm, context)
                    1 -> LogTab(vm)
                }
            }
        }
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
