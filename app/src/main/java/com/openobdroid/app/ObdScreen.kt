package com.openobdroid.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun ObdScreen(
    vm: ObdViewModel,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Adapter: ${vm.adapterStatus}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (vm.adapterStatus == "Connected") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Car: ${vm.carStatus}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (vm.carStatus == "Connected") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                if (vm.isCarConnected) {
                    vm.disconnect()
                } else {
                    vm.connect(context)
                }
            },
            enabled = !vm.isBusy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (vm.isCarConnected) "Disconnect" else "Connect")
        }

        if (vm.isBusy) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { vm.readDtc() },
                enabled = vm.isCarConnected,
                modifier = Modifier.weight(1f)
            ) {
                Text("Read DTCs")
            }

            Button(
                onClick = { vm.clearDtc() },
                enabled = vm.isCarConnected,
                modifier = Modifier.weight(1f)
            ) {
                Text("Clear DTCs")
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Activity Log:",
            style = MaterialTheme.typography.titleSmall
        )

        Spacer(Modifier.height(8.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 2.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            val listState = rememberLazyListState()
            
            // Auto-scroll to bottom when messages change
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
            ) {
                items(vm.messages) { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            msg.contains("failed", ignoreCase = true) || msg.startsWith("Error") -> 
                                MaterialTheme.colorScheme.error
                            msg.contains("Initialized", ignoreCase = true) || msg.contains("Ready") || msg.contains("Found") -> 
                                MaterialTheme.colorScheme.primary
                            else -> 
                                MaterialTheme.colorScheme.secondary
                        },
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

                if (vm.dtcs.isNotEmpty()) {
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text("DTC List:", style = MaterialTheme.typography.labelLarge)
                    }
                    items(vm.dtcs) { dtc ->
                        Text(
                            text = dtc,
                            modifier = Modifier.padding(vertical = 4.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                if (vm.dtcs.isEmpty() && vm.messages.isEmpty()) {
                    item {
                        Text(
                            text = "Awaiting activity...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}