package id.fievpager

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.Collections
import java.util.concurrent.Executors

/**
 * Mode WiFi lokal: kirim paket lewat jaringan WiFi yang sama (router atau hotspot HP),
 * tanpa internet. Memakai UDP broadcast, jadi semua HP FievPager di jaringan itu
 * menerima paketnya. Paket diserahkan ke MeshEngine di main thread.
 */
class LanLink(ctx: Context, private val onPacket: (Packet) -> Unit) {

    companion object {
        const val PORT = 47474
        private const val TAG = "FievLan"
    }

    private val app = ctx.applicationContext
    private val wifi = app.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val main = Handler(Looper.getMainLooper())
    private val tx = Executors.newSingleThreadExecutor()

    @Volatile
    private var running = false

    @Volatile
    private var rxSocket: DatagramSocket? = null
    private var lock: WifiManager.MulticastLock? = null

    val isRunning: Boolean get() = running

    fun start() {
        if (running) return
        running = true
        try {
            lock = wifi.createMulticastLock("fievpager").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "multicast lock gagal", e)
        }
        Thread({ rxLoop() }, "fiev-lan-rx").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        try {
            rxSocket?.close()
        } catch (_: Exception) {
        }
        rxSocket = null
        try {
            lock?.release()
        } catch (_: Exception) {
        }
        lock = null
    }

    private fun rxLoop() {
        val buf = ByteArray(16 * 1024)
        while (running) {
            var s: DatagramSocket? = null
            try {
                s = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(InetSocketAddress(PORT))
                }
                rxSocket = s
                while (running) {
                    val pk = DatagramPacket(buf, buf.size)
                    s.receive(pk)
                    val p = Packet.parse(pk.data.copyOf(pk.length)) ?: continue
                    main.post { if (running) onPacket(p) }
                }
            } catch (e: Exception) {
                if (running) {
                    Log.w(TAG, "terima gagal, coba lagi", e)
                    try {
                        Thread.sleep(3_000)
                    } catch (_: InterruptedException) {
                    }
                }
            } finally {
                try {
                    s?.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun send(p: Packet) {
        if (!running) return
        val bytes = p.toBytes()
        tx.execute {
            val targets = broadcastAddresses()
            // Sekali lewat jaringan default (berlaku juga saat HP ini jadi hotspot),
            // sekali lagi terikat ke WiFi, untuk HP yang memakai data seluler sebagai default.
            sendVia(null, targets, bytes)
            wifiNetwork()?.let { sendVia(it, targets, bytes) }
        }
    }

    private fun sendVia(network: Network?, targets: List<InetAddress>, bytes: ByteArray) {
        try {
            DatagramSocket().use { s ->
                s.broadcast = true
                if (network != null) network.bindSocket(s)
                for (addr in targets) {
                    try {
                        s.send(DatagramPacket(bytes, bytes.size, addr, PORT))
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "kirim gagal", e)
        }
    }

    private fun broadcastAddresses(): List<InetAddress> {
        val out = LinkedHashSet<InetAddress>()
        try {
            val ifaces = NetworkInterface.getNetworkInterfaces()
            if (ifaces != null) {
                for (ni in Collections.list(ifaces)) {
                    if (!ni.isUp || ni.isLoopback) continue
                    for (ia in ni.interfaceAddresses) {
                        ia.broadcast?.let { out.add(it) }
                    }
                }
            }
        } catch (_: Exception) {
        }
        try {
            out.add(InetAddress.getByName("255.255.255.255"))
        } catch (_: Exception) {
        }
        return out.toList()
    }

    @Suppress("DEPRECATION")
    private fun wifiNetwork(): Network? = try {
        cm.allNetworks.firstOrNull {
            cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    } catch (_: Exception) {
        null
    }
}
