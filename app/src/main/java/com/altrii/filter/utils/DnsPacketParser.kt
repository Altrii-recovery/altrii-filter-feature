package com.altrii.filter.utils

object DnsPacketParser {

    data class Question(val domain: String, val endIndex: Int)

    fun extractQuestion(data: ByteArray, length: Int): Question? {
        if (length < 12) return null
        val questionStart = 12
        var offset = questionStart
        val labels = mutableListOf<String>()
        while (offset < length) {
            val labelLength = data[offset].toInt() and 0xFF
            if (labelLength == 0) {
                offset += 1
                break
            }
            if (offset + labelLength >= length) {
                return null
            }
            val label = String(data, offset + 1, labelLength, Charsets.US_ASCII)
            labels.add(label)
            offset += labelLength + 1
        }
        if (offset + 4 > length) return null
        val domain = labels.joinToString(".")
        val endIndex = offset + 4 // includes QTYPE and QCLASS
        return Question(domain, endIndex)
    }

    fun buildBlockedResponse(request: ByteArray, question: Question): ByteArray {
        val header = request.copyOf(12)
        header[2] = 0x81.toByte()
        header[3] = 0x83.toByte() // response + recursion available + NXDOMAIN
        header[4] = request[4]
        header[5] = request[5]
        header[6] = 0
        header[7] = 0
        header[8] = 0
        header[9] = 0
        header[10] = 0
        header[11] = 0
        val response = ByteArray(header.size + question.endIndex - 12)
        System.arraycopy(header, 0, response, 0, header.size)
        System.arraycopy(request, 12, response, 12, question.endIndex - 12)
        return response
    }
}
