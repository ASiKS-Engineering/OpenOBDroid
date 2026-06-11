package com.openobdroid.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receiver to handle USB_DEVICE_ATTACHED without launching the MainActivity.
 * This allows the system to recognize the app as a handler for the device
 * (enabling persistent permission via "Always allow") without forcing the UI to open.
 */
class UsbConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        // We don't need to do anything specific here.
        // The presence of this receiver in the manifest with the device-filter
        // allows Android to grant/remember permissions silently.
    }
}
