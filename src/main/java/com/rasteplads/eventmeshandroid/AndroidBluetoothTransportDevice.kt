package com.rasteplads.eventmeshandroid

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import rasteplads.api.TransportDevice

const val TAG = "EventMesh"

val REQUIRED_PERMISSIONS = arrayOf(
    Manifest.permission.BLUETOOTH_SCAN,
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.BLUETOOTH_ADVERTISE,
    Manifest.permission.BLUETOOTH_CONNECT
)

class AndroidBluetoothTransportDevice: TransportDevice<ScanCallback, AdvertiseCallback> {
    override val transmissionInterval: Long
        get() = TODO("Not yet implemented")

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


    override fun beginTransmitting(message: ByteArray): AdvertiseCallback {
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

        val advertiseCallback = AdvertiseCallbackImpl()

        bluetoothProvider().bluetoothLeAdvertiser?.startAdvertising(
            AdvertiseSettings.Builder()
                .setConnectable(false)
                .setAdvertiseMode(ADVERTISE_MODE_LOW_LATENCY)
                .build(),
            AdvertiseData.Builder().addManufacturerData(0xF1A9, message)
                .build(),

            AdvertiseData.Builder().build(),
            advertiseCallback
        )
        return advertiseCallback
    }

    override fun beginReceiving(callback: suspend (ByteArray) -> Unit): ScanCallback {
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
        val scanCallback = ScanCallbackImpl(callback)

        bluetoothProvider().bluetoothLeScanner.startScan(
            scanFilters,
            ScanSettings.Builder().setLegacy(false).setScanMode(SCAN_MODE_LOW_LATENCY).build(),
            scanCallback
        )
        return scanCallback
    }

    override fun stopTransmitting(callback: AdvertiseCallback) {
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

        bluetoothProvider().bluetoothLeAdvertiser?.stopAdvertising(callback)
        Log.d(TAG, "Stopped sending message.")
    }

    override fun stopReceiving(callback: ScanCallback) {
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

        bluetoothProvider().bluetoothLeScanner.stopScan(callback)
        Log.d(TAG, "Stopped receiving packets.")
    }
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

class PermissionsDenied(private val permissions: Array<String>) :
    Exception("Permissions denied: ${permissions.toList()}") {}

