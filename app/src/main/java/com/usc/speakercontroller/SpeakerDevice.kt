package com.usc.speakercontroller

data class SpeakerDevice(
    val name: String,
    val address: String,
    val bonded: Boolean,
    val connected: Boolean = false,
    val rssi: Int? = null
)
