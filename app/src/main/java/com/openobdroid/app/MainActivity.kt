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
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collectLatest
import java.io.File

class MainActivity : ComponentActivity() {

    private var obdViewModel: ObdViewModel? = null

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    obdViewModel?.disconnect()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val filter = IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED)
        registerReceiver(usbReceiver, filter)

        setContent {
            val viewModel: ObdViewModel = viewModel()
            obdViewModel = viewModel

            LaunchedEffect(Unit) {
                viewModel.events.collectLatest { event ->
                    when (event) {
                        is ObdViewModel.ObdEvent.CloseApp -> finish()
                        is ObdViewModel.ObdEvent.ShareFile -> shareFile(event.file)
                    }
                }
            }

            ObdScreen(viewModel)
        }
    }

    private fun shareFile(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share Recording"))
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
        // Close USB OBD connection
        obdViewModel?.disconnect()
    }
}
