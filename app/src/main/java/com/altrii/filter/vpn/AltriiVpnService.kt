package com.altrii.filter.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.altrii.filter.R
import com.altrii.filter.data.FilterPreferences
import com.altrii.filter.vpn.BlockCategory
import com.altrii.filter.ui.BlockedActivity
import com.altrii.filter.ui.MainActivity
import com.altrii.filter.utils.DnsPacketParser
import com.altrii.filter.utils.PacketUtils
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class AltriiVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnThread: Thread? = null
    private val running = AtomicBoolean(false)
    private lateinit var preferences: FilterPreferences
    private lateinit var domainBlocker: DomainBlocker
    private var dnsForwarder: DnsForwarder? = null

    override fun onCreate() {
        super.onCreate()
        preferences = FilterPreferences(this)
        domainBlocker = DomainBlocker(preferences)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startVpn()
            ACTION_STOP -> stopVpn()
        }
        return START_STICKY
    }

    override fun onRevoke() {
        super.onRevoke()
        stopVpn()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun startVpn() {
        if (running.get()) {
            startForeground(NOTIFICATION_ID, buildNotification())
            return
        }

        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .setBlocking(true)
            .addAddress("10.8.0.2", 32)
            .addAddress("fd66:1:fd00::1", 128)

        CLEAN_BROWSING_IPV4.forEach { dns ->
            builder.addDnsServer(dns)
            builder.addRoute(dns, 32)
        }
        CLEAN_BROWSING_IPV6.forEach { dns ->
            builder.addDnsServer(dns)
            builder.addRoute(dns, 128)
        }

        val interfaceDescriptor = builder.establish()
        if (interfaceDescriptor == null) {
            stopSelf()
            return
        }
        vpnInterface = interfaceDescriptor
        dnsForwarder = DnsForwarder(this)
        running.set(true)

        startForeground(NOTIFICATION_ID, buildNotification())

        vpnThread = Thread({ runVpnLoop() }, "AltriiDnsThread").apply { start() }
    }

    private fun runVpnLoop() {
        val interfaceDescriptor = vpnInterface ?: return
        val inputStream = FileInputStream(interfaceDescriptor.fileDescriptor)
        val outputStream = FileOutputStream(interfaceDescriptor.fileDescriptor)
        val buffer = ByteArray(32767)
        try {
            while (running.get()) {
                val length = inputStream.read(buffer)
                if (length > 0) {
                    val packet = buffer.copyOf(length)
                    handlePacket(packet, outputStream)
                }
            }
        } catch (ex: IOException) {
            if (running.get()) {
                ex.printStackTrace()
            }
        } finally {
            try {
                inputStream.close()
            } catch (_: IOException) {
            }
            try {
                outputStream.close()
            } catch (_: IOException) {
            }
        }
    }

    private fun handlePacket(packet: ByteArray, outputStream: FileOutputStream) {
        if (packet.isEmpty()) return
        val version = (packet[0].toInt() shr 4) and 0xF
        if (version != 4) return
        val headerLength = (packet[0].toInt() and 0x0F) * 4
        if (packet.size < headerLength + PacketUtils.UDP_HEADER_LENGTH) return
        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 17) return
        val destPort = ((packet[headerLength + 2].toInt() and 0xFF) shl 8) or (packet[headerLength + 3].toInt() and 0xFF)
        if (destPort != DNS_PORT) return
        val udpLength = ((packet[headerLength + 4].toInt() and 0xFF) shl 8) or (packet[headerLength + 5].toInt() and 0xFF)
        val dnsLength = udpLength - PacketUtils.UDP_HEADER_LENGTH
        if (dnsLength <= 0 || headerLength + PacketUtils.UDP_HEADER_LENGTH + dnsLength > packet.size) return
        val dnsOffset = headerLength + PacketUtils.UDP_HEADER_LENGTH
        val dnsPayload = packet.copyOfRange(dnsOffset, dnsOffset + dnsLength)

        val question = DnsPacketParser.extractQuestion(dnsPayload, dnsPayload.size)
        val destAddress = PacketUtils.getIpv4Address(packet, 16)

        if (question != null) {
            val blockCategory = domainBlocker.evaluate(question.domain)
            if (blockCategory != null) {
                sendBlockedResponse(packet, dnsPayload, question, blockCategory, outputStream)
                return
            }
        }

        val responsePayload = dnsForwarder?.query(destAddress, dnsPayload) ?: return
        val responsePacket = PacketUtils.buildUdpResponse(packet, responsePayload, headerLength)
        outputStream.write(responsePacket)
    }

    private fun sendBlockedResponse(
        originalPacket: ByteArray,
        dnsRequest: ByteArray,
        question: DnsPacketParser.Question,
        category: BlockCategory,
        outputStream: FileOutputStream
    ) {
        val blockedPayload = DnsPacketParser.buildBlockedResponse(dnsRequest, question)
        val headerLength = (originalPacket[0].toInt() and 0x0F) * 4
        val responsePacket = PacketUtils.buildUdpResponse(originalPacket, blockedPayload, headerLength)
        outputStream.write(responsePacket)
        notifyDomainBlocked(question.domain, category)
    }

    private fun notifyDomainBlocked(domain: String, category: BlockCategory) {
        val categoryLabel = when (category) {
            BlockCategory.SOCIAL -> getString(R.string.category_social_media)
            BlockCategory.YOUTUBE -> getString(R.string.category_youtube)
            BlockCategory.GAMBLING -> getString(R.string.category_gambling)
        }
        val broadcast = Intent(ACTION_DOMAIN_BLOCKED).apply {
            putExtra(EXTRA_DOMAIN, domain)
            putExtra(EXTRA_CATEGORY, categoryLabel)
        }
        sendBroadcast(broadcast)

        val activityIntent = Intent(this, BlockedActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(BlockedActivity.EXTRA_DOMAIN, domain)
            putExtra(BlockedActivity.EXTRA_CATEGORY, categoryLabel)
        }
        ContextCompat.startActivity(this, activityIntent, null)
    }

    private fun stopVpn() {
        if (!running.getAndSet(false)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            return
        }
        vpnThread?.interrupt()
        vpnThread = null
        dnsForwarder?.close()
        dnsForwarder = null
        vpnInterface?.close()
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_description)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val mainIntent = Intent(this, MainActivity::class.java)
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AltriiVpnService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_description))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentIntent)
            .addAction(R.drawable.ic_notification, getString(R.string.notification_stop_action), stopIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.altrii.filter.action.START"
        const val ACTION_STOP = "com.altrii.filter.action.STOP"
        const val ACTION_DOMAIN_BLOCKED = "com.altrii.filter.action.DOMAIN_BLOCKED"
        const val EXTRA_DOMAIN = "com.altrii.filter.extra.DOMAIN"
        const val EXTRA_CATEGORY = "com.altrii.filter.extra.CATEGORY"

        private const val CHANNEL_ID = "altrii_filter_channel"
        private const val NOTIFICATION_ID = 1001
        private const val DNS_PORT = 53
        private val CLEAN_BROWSING_IPV4 = listOf(
            "185.228.168.168",
            "185.228.169.168"
        )
        private val CLEAN_BROWSING_IPV6 = listOf(
            "2a0d:2a00:1::",
            "2a0d:2a00:2::"
        )
    }
}
