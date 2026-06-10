package com.openobdroid.app

import android.content.Context
import com.ftdi.j2xx.D2xxManager
import com.ftdi.j2xx.FT_Device
import kotlinx.coroutines.*
import kotlin.math.min

/**
 * Optimized USB OBD Manager using FTDI D2XX driver
 * 
 * Performance improvements:
 * - Aggressive latency timer optimization (1ms)
 * - Larger buffer (4096 bytes) for batch reads
 * - Minimal sleep time (1ms) instead of 10ms
 * - Asynchronous reading with Coroutines
 * - Queue status checking to reduce CPU spinning
 * - Flow-based streaming for real-time data
 */
class UsbObdManager(
    private val context: Context,
) {

    private lateinit var manager: D2xxManager
    private var device: FT_Device? = null
    private var readJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    companion object {
        private const val BAUD_RATE = 115200 // Increased from 38400 for faster communication
        private const val LATENCY_TIMER = 1 // Aggressive: 1ms instead of 2ms
        private const val BUFFER_SIZE = 4096 // Large buffer for batch reads
        private const val READ_SLEEP_MS = 1L // Minimal sleep to reduce blocking
        private const val QUEUE_CHECK_INTERVAL_MS = 0L // No sleep for queue checking
    }

    fun connect(): Boolean {
        manager = D2xxManager.getInstance(context)

        val count = manager.createDeviceInfoList(context)

        if (count <= 0)
            return false

        device = manager.openByIndex(context, 0)

        device?.apply {
            // Optimized serial parameters for faster OBD-II communication
            setBaudRate(BAUD_RATE)
            setLatencyTimer(LATENCY_TIMER)

            setDataCharacteristics(
                D2xxManager.FT_DATA_BITS_8,
                D2xxManager.FT_STOP_BITS_1,
                D2xxManager.FT_PARITY_NONE
            )

            setFlowControl(
                D2xxManager.FT_FLOW_NONE,
                0,
                0
            )

            // Set larger buffer sizes for faster throughput
            setUSBParameters(65536, 65536)
        }

        return device?.isOpen == true
    }

    /**
     * Write command to OBD device (blocking)
     */
    fun write(command: String) {
        device?.write((command + "\r").toByteArray())
    }

    /**
     * Write command and read response asynchronously
     * Optimized for faster data retrieval
     */
    suspend fun writeAndReadAsync(
        command: String,
        timeoutMs: Long = 2000,
        promptChar: Char = '>'
    ): String = withContext(Dispatchers.IO) {
        write(command)
        readUntilPromptOptimized(timeoutMs, promptChar)
    }

    /**
     * Optimized synchronous read until prompt
     * - Uses queue status to avoid unnecessary reads
     * - Larger buffer for batch operations
     * - Reduced sleep times
     */
    fun readUntilPrompt(): String {
        return readUntilPromptOptimized(2000, '>')
    }

    /**
     * High-performance read implementation
     */
    private fun readUntilPromptOptimized(
        timeoutMs: Long,
        promptChar: Char
    ): String {
        val result = StringBuilder()
        val buffer = ByteArray(BUFFER_SIZE)
        val startTime = System.currentTimeMillis()

        while ((System.currentTimeMillis() - startTime) < timeoutMs) {
            try {
                // Check queue status before reading (non-blocking)
                val bytesAvailable = device?.getQueueStatus() ?: 0

                if (bytesAvailable > 0) {
                    // Read available bytes (up to buffer size)
                    val count = device?.read(buffer) ?: 0

                    if (count > 0) {
                        result.append(String(buffer, 0, count))

                        if (result.contains(promptChar)) {
                            break
                        }
                    }
                } else {
                    // Minimal sleep when no data available
                    Thread.sleep(READ_SLEEP_MS)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                break
            }
        }

        return result.toString()
    }

    /**
     * Batch read: Read multiple responses in quick succession
     * Useful for high-frequency OBD-II polling
     */
    suspend fun batchReadAsync(
        commands: List<String>,
        timeoutPerCommand: Long = 1000
    ): List<String> = withContext(Dispatchers.IO) {
        commands.map { command ->
            try {
                writeAndReadAsync(command, timeoutPerCommand)
            } catch (e: Exception) {
                "ERROR: ${e.message}"
            }
        }
    }

    /**
     * Start continuous data streaming (for real-time monitoring)
     * Returns raw data bytes as they arrive
     */
    fun startContinuousRead(onDataReceived: (ByteArray, Int) -> Unit): Job {
        readJob?.cancel()
        readJob = scope.launch {
            val buffer = ByteArray(BUFFER_SIZE)
            while (isActive) {
                try {
                    val count = device?.read(buffer) ?: 0
                    if (count > 0) {
                        onDataReceived(buffer, count)
                    } else {
                        delay(READ_SLEEP_MS)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    cancel()
                }
            }
        }
        return readJob!!
    }

    /**
     * Stop continuous reading
     */
    fun stopContinuousRead() {
        readJob?.cancel()
        readJob = null
    }

    /**
     * Purge all buffers to clear any stale data
     */
    fun purgeBuffers() {
        try {
            device?.purge()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Get queue status (bytes waiting to be read)
     */
    fun getAvailableBytes(): Int {
        return device?.getQueueStatus() ?: 0
    }

    fun close() {
        scope.cancel()
        readJob?.cancel()
        try {
            device?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        device = null
    }
}
