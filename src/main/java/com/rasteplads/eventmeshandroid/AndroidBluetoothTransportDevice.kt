package com.rasteplads.eventmeshandroid

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import rasteplads.api.TransportDevice
import java.nio.ByteBuffer
import java.util.UUID

const val TAG = "EventMesh"

class AndroidBluetoothTransportDevice(
    private val activity: Activity,
    private val bluetoothAdapter: BluetoothAdapter
): TransportDevice {
    override val transmissionInterval: Long
        get() = TODO("Not yet implemented")

    val permissionGrantedResultChannel = Channel<String>()
    private lateinit var advertiseCallback: AdvertiseCallbackImpl
    private lateinit var scanCallback: ScanCallbackImpl

    override fun beginTransmitting(message: ByteArray) {
        val requiredPermissions = arrayOf(
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
        val resultList = mutableListOf<String>()

        for (permission in requiredPermissions){
            if (ActivityCompat.checkSelfPermission(
                    activity,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ){
                Log.w(TAG, "Permission $permission not granted.")
                resultList.add(permission)
            }
        }
        if (resultList.isNotEmpty()){
            Log.d(TAG, "Permissions needed to begins sending!")
            ActivityCompat.requestPermissions(activity, resultList.toTypedArray(), 2)
            runBlocking {
                waitForPermissionGranted(permissionGrantedResultChannel, *resultList.toTypedArray())
            }
        }
        Log.d(TAG, "Sending Message: ${message.decodeToString()}")

        // Create a packet of size 31, with the first two bytes being FF:FF
        val packet = createPacket(message)
        val uuid = packet.first
        val rest = packet.second
        Log.d(TAG, "Attempting to send ${16 + rest.size} bytes")

        Log.w(TAG, "Advertising: $uuid")
        advertiseCallback = AdvertiseCallbackImpl()
        bluetoothAdapter.bluetoothLeAdvertiser?.startAdvertising(
            AdvertiseSettings.Builder()
                .setConnectable(false)
                .setAdvertiseMode(2)
                .build(),
            AdvertiseData.Builder()
                .addServiceData(ParcelUuid(uuid), rest)
                .build(),

            AdvertiseData.Builder().build(),
            advertiseCallback
        )
    }

    override fun beginReceiving(callback: suspend (ByteArray) -> Unit) {
        val requiredPermissions = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        val resultList = mutableListOf<String>()

        for (permission in requiredPermissions){
            if (ActivityCompat.checkSelfPermission(
                    activity,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ){
                Log.w(TAG, "Permission $permission not granted.")
                resultList.add(permission)
            }
        }
        if (resultList.isNotEmpty()){
            Log.d(TAG, "Permissions needed to begins receiving!")
            ActivityCompat.requestPermissions(activity, resultList.toTypedArray(), 2)
            runBlocking {
                waitForPermissionGranted(permissionGrantedResultChannel, *resultList.toTypedArray())
            }
            Log.w(TAG, "All permissions were granted.")
        }
        Log.d(TAG, "Receiving packets.")
        val scanFilters = listOf(ScanFilter.Builder()
            .build())
        scanCallback = ScanCallbackImpl(callback)
        bluetoothAdapter.bluetoothLeScanner.startScan(
            scanFilters,
            ScanSettings.Builder().setLegacy(false).build(),
            scanCallback
        )
    }

    override fun stopTransmitting() {
        if (ActivityCompat.checkSelfPermission(
                activity,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ) != PackageManager.PERMISSION_GRANTED
            || ActivityCompat.checkSelfPermission(activity,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Permission Denied, requesting access.")
            val permissions = arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE)
            ActivityCompat.requestPermissions(activity, permissions, 1)
        }
        val callback = AdvertiseCallbackImpl()
        bluetoothAdapter.bluetoothLeAdvertiser?.stopAdvertising(callback)
        Log.d(TAG, "Stopped sending message.")
    }

    override fun stopReceiving() {
        val requiredPermissions = arrayOf(Manifest.permission.BLUETOOTH_SCAN)
        val resultList = mutableListOf<String>()

        for (permission in requiredPermissions) {
            if (ActivityCompat.checkSelfPermission(
                    activity,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "Permission $permission not granted.")
                resultList.add(permission)
            }
        }
        bluetoothAdapter.bluetoothLeScanner.stopScan(scanCallback)
        Log.d(TAG, "Stopped receiving packets.")
    }
}


suspend fun waitForPermissionGranted(permissionGrantedResultChannel: Channel<String>,
                                             vararg permissions: String){
    Log.d(TAG, "Waiting for permissions.")
    repeat(permissions.size){
        val permission = permissionGrantedResultChannel.receive()
        Log.w(TAG, "Permission: $permission received")
        if (permission !in permissions)
            Log.wtf(TAG, "Permission: $permission was granted but was never asked for.")
    }
}

fun createPacket(message: ByteArray): Pair<UUID, ByteArray> {
    // Create a packet of size 29, with the first two bytes being FF:FF
    val zeroArray = ByteArray(29) { 0 }
    val packet = (ByteArray(2) {0xFF.toByte()} + message).copyInto(zeroArray)
    val wrapper = ByteBuffer.wrap(packet)

    //Create a UUID from the first 16 bytes.
    val leastSig = wrapper.getLong()
    val mostSig = wrapper.getLong()
    return Pair(
        UUID(mostSig, leastSig),
        packet.sliceArray(wrapper.position()..<packet.size)
    )
}