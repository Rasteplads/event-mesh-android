package com.rasteplads.eventmeshandroid

import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseSettings
import android.util.Log

class AdvertiseCallbackImpl: AdvertiseCallback() {
    override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
        super.onStartSuccess(settingsInEffect)
        Log.d(TAG, "Started advertising")
    }

    override fun onStartFailure(errorCode: Int) {
        super.onStartFailure(errorCode)
        val reason = when (errorCode){
            ADVERTISE_FAILED_DATA_TOO_LARGE -> "Size of advertisement data packet is too large."
            ADVERTISE_FAILED_ALREADY_STARTED -> "Device was already advertising."
            ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Too many advertisers."
            ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "Feature unsupported."
            else -> "Unknown error."
        }
        Log.w(TAG, "Failed to start advertising: $reason")
    }

}