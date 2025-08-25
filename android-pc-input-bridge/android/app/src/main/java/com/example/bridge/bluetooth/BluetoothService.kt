package com.example.bridge.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class BluetoothService(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    companion object {
        private const val TAG = "BluetoothService"
        private val UUID_SECURE = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    fun connect(device: BluetoothDevice) {
        try {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(UUID_SECURE)
            bluetoothSocket?.connect()
            inputStream = bluetoothSocket?.inputStream
            outputStream = bluetoothSocket?.outputStream
            Log.d(TAG, "Connected to ${device.name}")
        } catch (e: IOException) {
            Log.e(TAG, "Could not connect to device", e)
            closeConnection()
        }
    }

    fun sendData(data: ByteArray) {
        try {
            outputStream?.write(data)
            Log.d(TAG, "Data sent: ${data.contentToString()}")
        } catch (e: IOException) {
            Log.e(TAG, "Error sending data", e)
        }
    }

    fun receiveData(): ByteArray? {
        return try {
            val buffer = ByteArray(1024)
            val bytes = inputStream?.read(buffer) ?: -1
            if (bytes > 0) {
                buffer.copyOf(bytes)
            } else {
                null
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error receiving data", e)
            null
        }
    }

    fun closeConnection() {
        try {
            bluetoothSocket?.close()
            inputStream?.close()
            outputStream?.close()
            Log.d(TAG, "Connection closed")
        } catch (e: IOException) {
            Log.e(TAG, "Error closing connection", e)
        }
    }
}