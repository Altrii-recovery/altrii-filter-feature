package com.altrii.filter.utils

import java.net.InetAddress

object PacketUtils {
    const val UDP_HEADER_LENGTH = 8

    fun getIpv4Address(data: ByteArray, offset: Int): InetAddress =
        InetAddress.getByAddress(data.copyOfRange(offset, offset + 4))

    fun setIpv4Address(data: ByteArray, offset: Int, address: ByteArray) {
        require(address.size == 4)
        for (i in 0 until 4) {
            data[offset + i] = address[i]
        }
    }

    fun ipv4HeaderChecksum(data: ByteArray, headerLength: Int): Int {
        var sum = 0L
        var i = 0
        while (i < headerLength) {
            if (i == 10) {
                i += 2
                continue
            }
            val word = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            sum += word
            if ((sum and 0xFFFF0000L) != 0L) {
                sum = (sum and 0xFFFF) + (sum ushr 16)
            }
            i += 2
        }
        while ((sum ushr 16) != 0L) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return sum.inv().toInt() and 0xFFFF
    }

    fun buildUdpResponse(
        originalPacket: ByteArray,
        dnsPayload: ByteArray,
        ipHeaderLength: Int
    ): ByteArray {
        val totalLength = ipHeaderLength + UDP_HEADER_LENGTH + dnsPayload.size
        val response = ByteArray(totalLength)
        System.arraycopy(originalPacket, 0, response, 0, ipHeaderLength)
        // swap IPv4 addresses
        for (i in 0 until 4) {
            response[12 + i] = originalPacket[16 + i]
            response[16 + i] = originalPacket[12 + i]
        }
        response[8] = 64.toByte() // TTL
        response[2] = (totalLength shr 8).toByte()
        response[3] = totalLength.toByte()
        response[10] = 0
        response[11] = 0
        val checksum = ipv4HeaderChecksum(response, ipHeaderLength)
        response[10] = (checksum shr 8).toByte()
        response[11] = checksum.toByte()

        // UDP header
        System.arraycopy(originalPacket, ipHeaderLength, response, ipHeaderLength, UDP_HEADER_LENGTH)
        response[ipHeaderLength] = originalPacket[ipHeaderLength + 2]
        response[ipHeaderLength + 1] = originalPacket[ipHeaderLength + 3]
        response[ipHeaderLength + 2] = originalPacket[ipHeaderLength]
        response[ipHeaderLength + 3] = originalPacket[ipHeaderLength + 1]
        val udpLength = UDP_HEADER_LENGTH + dnsPayload.size
        response[ipHeaderLength + 4] = (udpLength shr 8).toByte()
        response[ipHeaderLength + 5] = udpLength.toByte()
        response[ipHeaderLength + 6] = 0
        response[ipHeaderLength + 7] = 0

        System.arraycopy(dnsPayload, 0, response, ipHeaderLength + UDP_HEADER_LENGTH, dnsPayload.size)
        return response
    }
}
