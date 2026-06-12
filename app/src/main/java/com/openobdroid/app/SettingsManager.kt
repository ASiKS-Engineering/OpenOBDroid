package com.openobdroid.app

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("obd_settings", Context.MODE_PRIVATE)

    var baudRate: Int
        get() = prefs.getInt("baud_rate", 38400)
        set(value) = prefs.edit().putInt("baud_rate", value).apply()

    var latencyTimer: Int
        get() = prefs.getInt("latency_timer", 2)
        set(value) = prefs.edit().putInt("latency_timer", value).apply()

    var readTimeout: Int
        get() = prefs.getInt("read_timeout", 500)
        set(value) {
            prefs.edit().putInt("read_timeout", value).apply()
            // Ensure promptTimeout is at least readTimeout + 100ms
            if (promptTimeout < value + 100) {
                promptTimeout = (value + 100).toLong()
            }
        }

    var bufferSize: Int
        get() = prefs.getInt("buffer_size", 256)
        set(value) = prefs.edit().putInt("buffer_size", value).apply()

    var promptTimeout: Long
        get() = prefs.getLong("prompt_timeout", 600L)
        set(value) {
            val validatedValue = if (value < readTimeout + 100) (readTimeout + 100).toLong() else value
            prefs.edit().putLong("prompt_timeout", validatedValue).apply()
        }
}
