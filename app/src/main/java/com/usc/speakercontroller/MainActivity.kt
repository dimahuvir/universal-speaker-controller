package com.usc.speakercontroller

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import android.content.BroadcastReceiver

class MainActivity : Activity() {

    private lateinit var controller: BluetoothController
    private lateinit var status: TextView
    private lateinit var list: ListView

    private val devices = mutableListOf<SpeakerDevice>()
    private var selectedDevice: SpeakerDevice? = null
    private var receiver: BroadcastReceiver? = null

    private val permissionRequest = 100

    private val permissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        controller = BluetoothController(this)

        status = findViewById(R.id.statusText)
        list = findViewById(R.id.deviceList)

        findViewById<Button>(R.id.scanButton)
            .setOnClickListener {
                checkPermissionsAndScan()
            }

        findViewById<Button>(R.id.pairedButton)
            .setOnClickListener {
                loadPairedDevices()
            }

        findViewById<Button>(R.id.connectButton)
            .setOnClickListener {
                pairSelectedDevice()
            }

        findViewById<Button>(R.id.disconnectButton)
            .setOnClickListener {
                openBluetoothSettings()
            }

        findViewById<Button>(R.id.settingsButton)
            .setOnClickListener {
                openBluetoothSettings()
            }

        findViewById<Button>(R.id.volumeUpButton)
            .setOnClickListener {
                changeVolume(AudioManager.ADJUST_RAISE)
            }

        findViewById<Button>(R.id.volumeDownButton)
            .setOnClickListener {
                changeVolume(AudioManager.ADJUST_LOWER)
            }

        list.setOnItemClickListener { _, _, position, _ ->
            selectedDevice = devices.getOrNull(position)
            selectedDevice?.let {
                status.text = getString(
                    R.string.selected_device,
                    it.name
                )
            }
        }

        checkPermissionsAndScan()
    }

    private fun checkPermissionsAndScan() {
        val missing = permissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            requestPermissions(
                missing.toTypedArray(),
                permissionRequest
            )
        } else {
            startScan()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == permissionRequest &&
            grantResults.isNotEmpty() &&
            grantResults.all {
                it == PackageManager.PERMISSION_GRANTED
            }
        ) {
            startScan()
        } else {
            status.text = getString(R.string.permission_denied)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startScan() {

        if (!controller.isBluetoothAvailable()) {
            status.text = getString(R.string.bluetooth_unavailable)
            return
        }

        if (!controller.isBluetoothEnabled()) {
            startActivity(
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            )
            return
        }

        receiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
        }

        devices.clear()
        updateList()

        receiver = controller.registerDiscoveryReceiver { device ->

            runOnUiThread {
                val index = devices.indexOfFirst {
                    it.address == device.address
                }

                if (index >= 0) {
                    devices[index] = device
                } else {
                    devices.add(device)
                }

                updateList()
                status.text = getString(
                    R.string.devices_found,
                    devices.size
                )
            }
        }

        val started = controller.startDiscovery()

        status.text = if (started) {
            getString(R.string.scanning)
        } else {
            getString(R.string.scan_failed)
        }
    }

    @SuppressLint("MissingPermission")
    private fun loadPairedDevices() {
        devices.clear()
        devices.addAll(controller.getPairedDevices())
        updateList()

        status.text = getString(
            R.string.paired_count,
            devices.size
        )
    }

    private fun updateList() {
        val items = devices.map {
            val connection = if (it.bonded) {
                getString(R.string.paired_label)
            } else {
                getString(R.string.not_paired_label)
            }

            "${it.name}\n${it.address}\n$connection"
        }

        list.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            items
        )
    }

    @SuppressLint("MissingPermission")
    private fun pairSelectedDevice() {

        val device = selectedDevice

        if (device == null) {
            status.text = getString(R.string.select_device)
            return
        }

        if (device.bonded) {
            status.text = getString(R.string.already_paired)
            return
        }

        val success = controller.pair(device.address)

        status.text = if (success) {
            getString(R.string.pairing_started)
        } else {
            getString(R.string.pairing_failed)
        }
    }

    private fun openBluetoothSettings() {
        startActivity(
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        )
    }

    private fun changeVolume(direction: Int) {
        val audio = getSystemService(AUDIO_SERVICE)
                as AudioManager

        audio.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            direction,
            AudioManager.FLAG_SHOW_UI
        )
    }

    override fun onDestroy() {
        controller.cancelDiscovery()

        receiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
        }

        super.onDestroy()
    }
}
