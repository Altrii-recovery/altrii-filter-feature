package com.altrii.filter.vpn

import android.net.VpnService
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

class DnsForwarder(vpnService: VpnService) {

    private val socket: DatagramSocket = DatagramSocket().apply {
        soTimeout = TIMEOUT_MS
    }

    init {
        vpnService.protect(socket)
    }

    @Synchronized
    fun query(server: InetAddress, payload: ByteArray): ByteArray? {
        return try {
            val packet = DatagramPacket(payload, payload.size, server, DNS_PORT)
            socket.send(packet)
            val buffer = ByteArray(1500)
            val responsePacket = DatagramPacket(buffer, buffer.size)
            socket.receive(responsePacket)
            responsePacket.copyOf(responsePacket.length)
        } catch (ex: SocketTimeoutException) {
            null
        } catch (ex: IOException) {
            null
        }
    }

    fun close() {
        socket.close()
    }

    companion object {
        private const val DNS_PORT = 53
        private const val TIMEOUT_MS = 5000
    }
}
