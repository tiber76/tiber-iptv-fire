package com.tiberiptv.fire

import android.os.storage.StorageVolume

data class UsbVolume(
    val uuid: String,
    val description: String,
    val state: String,
    val volume: StorageVolume
)
