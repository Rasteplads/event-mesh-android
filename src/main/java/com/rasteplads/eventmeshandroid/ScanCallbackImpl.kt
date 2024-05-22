package com.rasteplads.eventmeshandroid

import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.util.Log
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

class ScanCallbackImpl(
    private val onDecodeSuccess: suspend (ByteArray) -> Unit
): ScanCallback() {

    override fun onScanResult(callbackType: Int, result: ScanResult?) {
        super.onScanResult(callbackType, result)
        val bytes = result?.scanRecord?.getManufacturerSpecificData(0xF1A9) ?: return
        val packet = bytes.toEventMeshDebugString()
        Log.d(TAG, "Parsed packet:\n$packet")
        try {
            GlobalScope.launch {
                onDecodeSuccess(bytes)
            }
        } catch (e: Exception){
            // Ignore packets that cannot be parsed.
            return
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

fun ByteArray.toEventMeshDebugString(): String {
    val buffer = ByteBuffer.wrap(this)
    val ttl = buffer.get()
    val rec = buffer.getShort().toUShort()
    val inc = buffer.get().toUByte()
    val userID = buffer.get().toUByte()
    val type = buffer.get().toUByte()
    val longitude = buffer.getFloat()
    val latitude = buffer.getFloat()
    val timestamp = buffer.getLong()
    return "ttl:$ttl, r:$rec, i:$inc, uid:$userID, t:$type, lo: $longitude, la:$latitude, t:$timestamp"
}