package com.rasteplads.eventmeshandroid

import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.util.Log
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer

class ScanCallbackImpl(
    private val onDecodeSuccess: suspend (ByteArray) -> Unit
): ScanCallback() {
    @OptIn(ExperimentalStdlibApi::class)
    override fun onScanResult(callbackType: Int, result: ScanResult?) {
        super.onScanResult(callbackType, result)
        val bytes = result?.scanRecord?.bytes ?: return
        try {
            //Log.d(TAG, "Received bytes")
            val frame = bytes.toEventMeshFrame()
            val packet = frame.payload.toEventMeshDebugString()
            Log.d(TAG, "Parsed packet:\n$packet")
            GlobalScope.launch {
                onDecodeSuccess(frame.payload)
            }
        } catch (e: IllegalStateException){
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

class EventMeshFrame(private var bytes: ByteArray) {

    var length: Int
        get() = bytes[0].toInt()
        set(value) { bytes[0] = value.toByte() }

    var type: Byte
        get() = bytes[1]
        set(value) { bytes[1] = value }

    val eventMeshCode: ByteArray
        get() = bytes.sliceArray(2..3)

    var payload: ByteArray
        get() = bytes.drop(4).toByteArray()
        set(value) { value.copyInto(bytes, 4) }

    init {
        check(bytes.size == 31) { "The size of an EventMesh frame is 31 bytes." }
        check(length == 30) { "The length of the message should be 29 bytes, was $length" }
        check(eventMeshCode.contentEquals(ByteArray(2) {0xFF.toByte()})) { "The message must start with the magic string FF:FF" }
    }

}

fun ByteArray.toEventMeshFrame(): EventMeshFrame {
    return EventMeshFrame(this)
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