package com.example.gamepadbridge

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class UdpClient(private val serverAddress: String, private val serverPort: Int) {

    private val socket = DatagramSocket()

    fun send(data: ByteArray) {
        val packet = DatagramPacket(data, data.size, InetAddress.getByName(serverAddress), serverPort)
        socket.send(packet)
    }

    fun close() {
        socket.close()
    }
}