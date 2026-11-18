package com.usc.speakercontroller

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager as AndroidBluetoothManager
import android.content.Context
import android.content.IntentFilter
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Intent
import android.os.Build

class BluetoothController(private val context: Context) {

    private val manager =
        context.getSystemService(Context.BLUETOOTH_SERVICE)
                as AndroidBluetoothManager

    private val adapter: BluetoothAdapter? =
        manager.adapter

    private val devices = mutableMapOf<String, SpeakerDevice>()

    fun isBluetoothAvailable(): Boolean {
        return adapter != null
    }

    fun isBluetoothEnabled(): Boolean {
        return adapter?.isEnabled == true
    }

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<SpeakerDevice> {

        val paired = adapter?.bondedDevices ?: emptySet()

        return paired.map { device ->
            SpeakerDevice(
                name = device.name ?: "Unknown device",
                address = device.address,
                bonded = true
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery(): Boolean {
        devices.clear()
        return adapter?.startDiscovery() ?: false
    }

    @SuppressLint("MissingPermission")
    fun cancelDiscovery() {
        adapter?.cancelDiscovery()
    }

    @SuppressLint("MissingPermission")
    fun pair(address: String): Boolean {
        val device = adapter?.getRemoteDevice(address)
            ?: return false

        return device.createBond()
    }

    fun registerDiscoveryReceiver(
        onDeviceFound: (SpeakerDevice) -> Unit
    ): BroadcastReceiver {

        val receiver = object : BroadcastReceiver() {

            @SuppressLint("MissingPermission")
            override fun onReceive(
                context: Context,
                intent: Intent
            ) {
                if (intent.action ==
                    BluetoothDevice.ACTION_FOUND
                ) {
                    val device: BluetoothDevice? =
                        if (Build.VERSION.SDK_INT >= 33) {
                            intent.getParcelableExtra(
                                BluetoothDevice.EXTRA_DEVICE,
                                BluetoothDevice::class.java
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(
                                BluetoothDevice.EXTRA_DEVICE
                            )
                        }

                    device?.let {
                        val result = SpeakerDevice(
                            name = it.name ?: "Unknown device",
                            address = it.address,
                            bonded = it.bondState ==
                                BluetoothDevice.BOND_BONDED,
                            rssi = intent.getShortExtra(
                                BluetoothDevice.EXTRA_RSSI,
                                Short.MIN_VALUE
                            ).toInt()
                        )

                        devices[result.address] = result
                        onDeviceFound(result)
                    }
                }
            }
        }

        val filter = IntentFilter(
            BluetoothDevice.ACTION_FOUND
        )

        if (Build.VERSION.SDK_INT >= 33) {
    context.registerReceiver(
        receiver,
        filter,
        Context.RECEIVER_EXPORTED
    )
} else {
    @Suppress("DEPRECATION")
    context.registerReceiver(receiver, filter)
        }

        return receiver
    }

    @SuppressLint("MissingPermission")
    fun getConnectedSpeakers(
        callback: (List<String>) -> Unit
    ) {
        adapter?.getProfileProxy(
            context,
            object : BluetoothProfile.ServiceListener {

                override fun onServiceConnected(
                    profile: Int,
                    proxy: BluetoothProfile
                ) {
                    if (profile == BluetoothProfile.A2DP) {
                        val connected = proxy.connectedDevices
                            .mapNotNull { it.name }

                        callback(connected)

                        adapter.closeProfileProxy(
                            profile,
                            proxy
                        )
                    }
                }

                override fun onServiceDisconnected(
                    profile: Int
                ) {}
            },
            BluetoothProfile.A2DP
        )
    }
}
