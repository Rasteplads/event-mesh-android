package com.rasteplads.eventmeshandroid

import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.util.Log
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer

class ScanCallbackImpl(
    private val onDecodeSuccess: suspend (ByteArray) -> Unit
): ScanCallback() {
    @OptIn(ExperimentalStdlibApi::class)
    override fun onScanResult(callbackType: Int, result: ScanResult?) {
        super.onScanResult(callbackType, result)
        val bytes = result?.scanRecord?.bytes ?: return
        if (bytes.size < 5)
            return
        val buffer = ByteBuffer.wrap(bytes)
        val length = buffer.get()
        if (length < 5)
            return
        // TODO: Check that the type is correct.
        val type = buffer.get()
        repeat(2){
            if(buffer.get() != 0xFF.toByte())
                return
        }

        val content = ByteArray(length - 3)
        buffer.get(content,0, length - 3)

        val out = bytes.joinToString(":") { byte -> byte.toHexString() }
        Log.d(TAG, "Parsed packet:\n" + Charsets.UTF_8.decode(buffer).toString() + "\n" + out)

        runBlocking {
            onDecodeSuccess(content)
        }
    }

    override fun onScanFailed(errorCode: Int) {
        super.onScanFailed(errorCode)
        val reason = when (errorCode){
            SCAN_FAILED_ALREADY_STARTED -> "Scan already started."
            SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "Application registration failed."
            SCAN_FAILED_INTERNAL_ERROR -> "Internal error"
            SCAN_FAILED_FEATURE_UNSUPPORTED -> "Feature unsupported"
            SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> "Out of Hardware resources."
            SCAN_FAILED_SCANNING_TOO_FREQUENTLY -> "Scanning too frequently."
            else -> "Unknown Error."
        }
        Log.w(TAG, "Failed to scan for advertisements: $reason")
    }
}