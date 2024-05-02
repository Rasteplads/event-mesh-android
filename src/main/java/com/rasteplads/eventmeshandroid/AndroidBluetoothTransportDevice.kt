package com.rasteplads.eventmeshandroid

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import rasteplads.api.TransportDevice
import rasteplads.util.toByteArray
import java.nio.ByteBuffer
import java.util.UUID

const val TAG = "EventMesh"

class AndroidBluetoothTransportDevice(): TransportDevice {
    override val transmissionInterval: Long
        get() = TODO("Not yet implemented")

    private lateinit var advertiseCallback: AdvertiseCallbackImpl
    private lateinit var scanCallback: ScanCallbackImpl
    private lateinit var _contextProvider: () -> Context
    private lateinit var _bluetoothProvider: () -> BluetoothAdapter

    public var contextProvider: () -> Context
        get() = _contextProvider
        set(value) {
            _contextProvider = value
        }

    public var bluetoothProvider: () -> BluetoothAdapter
        get() = _bluetoothProvider
        set(value) {
            _bluetoothProvider = value
        }


    override fun beginTransmitting(message: ByteArray) {
        val requiredPermissions = arrayOf(
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
        val neededPermissions = requiredPermissions.takeWhile {
            permission -> (
                ActivityCompat.checkSelfPermission(contextProvider(), permission)
                        != PackageManager.PERMISSION_GRANTED
            )
        }
        if (neededPermissions.isNotEmpty()){
            throw PermissionsDenied(neededPermissions.toTypedArray())
        }

        Log.d(TAG, "Sending Message: ${message.decodeToString()}")

        // Create a packet of size 31, with the first two bytes being FF:FF
        val packet = createPacket(message)
        val uuid = packet.first
        val rest = packet.second
        Log.d(TAG, "Attempting to send ${16 + rest.size} bytes")

        Log.w(TAG, "Advertising: $uuid")
        advertiseCallback = AdvertiseCallbackImpl()
        bluetoothProvider().bluetoothLeAdvertiser?.startAdvertising(
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
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        val neededPermissions = requiredPermissions.takeWhile {
                permission -> (
                ActivityCompat.checkSelfPermission(contextProvider(), permission)
                        != PackageManager.PERMISSION_GRANTED
                )
        }
        if (neededPermissions.isNotEmpty()){
            throw PermissionsDenied(neededPermissions.toTypedArray())
        }

        Log.d(TAG, "Receiving packets.")
        val scanFilters = listOf(ScanFilter.Builder()
            .build())
        scanCallback = ScanCallbackImpl(callback)
        bluetoothProvider().bluetoothLeScanner.startScan(
            scanFilters,
            ScanSettings.Builder().setLegacy(false).setScanMode(SCAN_MODE_LOW_LATENCY).build(),
            scanCallback
        )
    }

    override fun stopTransmitting() {
        val requiredPermissions = arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE)
        val neededPermissions = requiredPermissions.takeWhile {
                permission -> (
                ActivityCompat.checkSelfPermission(contextProvider(), permission)
                        != PackageManager.PERMISSION_GRANTED
                )
        }
        if (neededPermissions.isNotEmpty()){
            throw PermissionsDenied(neededPermissions.toTypedArray())
        }
        if (!::advertiseCallback.isInitialized)
            return
        bluetoothProvider().bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        Log.d(TAG, "Stopped sending message.")
    }

    override fun stopReceiving() {
        val requiredPermissions = arrayOf(Manifest.permission.BLUETOOTH_SCAN)
        val neededPermissions = requiredPermissions.takeWhile {
                permission -> (
                ActivityCompat.checkSelfPermission(contextProvider(), permission)
                        != PackageManager.PERMISSION_GRANTED
                )
        }
        if (neededPermissions.isNotEmpty()){
            throw PermissionsDenied(neededPermissions.toTypedArray())
        }
        bluetoothProvider().bluetoothLeScanner.stopScan(scanCallback)
        Log.d(TAG, "Stopped receiving packets.")
    }
}

fun permissionActivityResultHandler(permissions: Map<String, Boolean>, permissionsGrantedCallback: () -> Unit){
        // TODO: Currently ignores permission status: Assumes permission granted.
        Log.d(TAG, "Got permissions")
        permissionsGrantedCallback()
}

val REQUIRED_PERMISSIONS = arrayOf(
    Manifest.permission.BLUETOOTH_SCAN,
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.BLUETOOTH_ADVERTISE,
    Manifest.permission.BLUETOOTH_CONNECT
)

fun permissionsGranted(context: Context): Boolean {
    if (REQUIRED_PERMISSIONS.any { permission ->
            (ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED)
        }) {
        Log.d(TAG, "Permissions needed.")
        return false
    }
    return true
}

fun requestPermissions(context: Context, requestPermissionsLauncher: ActivityResultLauncher<Array<String>>){
    if (REQUIRED_PERMISSIONS.any {
                permission -> (ContextCompat.checkSelfPermission(context, permission)
                != PackageManager.PERMISSION_GRANTED)
        }){
        Log.d(TAG, "Permissions needed. Requesting them now.")

        requestPermissionsLauncher.launch(REQUIRED_PERMISSIONS)
    }
}

class PermissionDenied(private val permission: String) :
    Exception("Permission denied: $permission") {}
class PermissionsDenied(private val permissions: Array<String>) :
    Exception("Permissions denied: ${permissions.toList()}") {}

@OptIn(ExperimentalStdlibApi::class)
fun createPacket(message: ByteArray): Pair<UUID, ByteArray> {
    // Create a packet of size 29, with the first two bytes being FF:FF
    val zeroArray = ByteArray(27) { 0 }
    val packet = (message).copyInto(zeroArray)
    val wrapper = ByteBuffer.wrap(packet)
    val uuidString = "%S%S%S%S-%S%S-%S%S-%S%S-%S%S%S%Sffff".format(*(Array(14) { _ ->
        wrapper.get().toHexString()
    }).reversed().toTypedArray())

    return Pair(
        UUID.fromString(uuidString),
        packet.sliceArray(wrapper.position()..<packet.size)
    )
}
/*
 ___ Least Significant ___ ___ Most Significant ____
| FF FF XX XX XX XX XX XX | XX XX XX XX XX XX XX XX |
|_________________________|_________________________|
 */
