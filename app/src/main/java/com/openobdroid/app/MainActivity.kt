package com.openobdroid.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    private var obdViewModel: ObdViewModel? = null

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    finish()
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    context?.let { obdViewModel?.connectAdapter(it) }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        }
        registerReceiver(usbReceiver, filter)

        setContent {
            val viewModel: ObdViewModel = viewModel()
            obdViewModel = viewModel
            val context = LocalContext.current

            LaunchedEffect(Unit) {
                // Auto-detect adapter on app start
                viewModel.connectAdapter(context)

                viewModel.events.collectLatest { event ->
                    when (event) {
                        is ObdViewModel.ObdEvent.CloseApp -> finish()
                    }
                }
            }

            ObdScreen(viewModel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
    }
}
