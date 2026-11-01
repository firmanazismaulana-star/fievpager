package id.fievpager

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlin.random.Random

/**
 * Jaringan mesh antar-HP.
 *
 * - Nearby Connections memilih sendiri jalurnya: Bluetooth, BLE, atau WiFi Direct.
 *   Tidak butuh internet, router, atau WiFi yang sama.
 * - Setiap HP otomatis meneruskan pesan (flooding dengan batas TTL dan penghapus duplikat).
 * - Simpan-teruskan: pesan 30 menit terakhir dikirim ulang ke HP yang baru tersambung,
 *   jadi pesan bisa "dibawa" oleh orang yang berpindah tempat.
 *
 * Semua state diakses di main thread (callback Nearby juga berjalan di main thread).
 */
class MeshEngine(ctx: Context, private val onIncoming: (Msg) -> Unit) {

    companion object {
        const val SERVICE_ID = "id.fievpager.mesh.v1"
        const val MAX_TTL = 6
        const val MAX_TEXT = 300
        private const val TAG = "FievMesh"
        private const val RECENT_WINDOW_MS = 30 * 60 * 1000L
        private const val MAX_RECENT = 100
    }

    var onPeersChanged: ((Int) -> Unit)? = null

    private val client = Nearby.getConnectionsClient(ctx)
    private val main = Handler(Looper.getMainLooper())
    private val strategy = Strategy.P2P_CLUSTER

    private val connected = HashMap<String, String>()               // endpointId -> nodeId
    private val known = HashMap<String, Pair<String, String>>()     // endpointId -> (nodeId, nama)
    private val pending = HashSet<String>()
    private val seen = object : LinkedHashMap<String, Long>(1024, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > 5000
    }
    private val recent = ArrayDeque<Pair<Packet, Long>>()
    private var running = false

    private val me: String get() = MeshState.myId
    private val myName: String get() = MeshState.myName.value

    private fun now() = System.currentTimeMillis()
    private fun endpointName() = "$me|${myName.replace("|", "")}"
    private fun parseName(s: String): Pair<String, String>? {
        val i = s.indexOf('|')
        if (i <= 0) return null
        return s.substring(0, i) to s.substring(i + 1)
    }

    // ---------------------------------------------------------------- start / stop

    fun start() {
        if (running) return
        running = true
        MeshState.running.value = true
        MeshState.error.value = null
        advertise()
        discover()
        main.postDelayed(tick, 20_000)
    }

    fun stop() {
        if (!running) return
        running = false
        main.removeCallbacksAndMessages(null)
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        connected.clear()
        pending.clear()
        known.clear()
        publishPeers()
        MeshState.running.value = false
    }

    fun rename(name: String) {
        MeshState.setName(name)
        if (running) {
            client.stopAdvertising()
            advertise()
        }
    }

    private fun advertise() {
        val opts = AdvertisingOptions.Builder().setStrategy(strategy).build()
        client.startAdvertising(endpointName(), SERVICE_ID, lifecycle, opts)
            .addOnSuccessListener { MeshState.error.value = null }
            .addOnFailureListener { e ->
                val code = (e as? ApiException)?.statusCode
                if (code == ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING) return@addOnFailureListener
                Log.w(TAG, "advertise gagal", e)
                MeshState.error.value = explain(code)
                main.postDelayed({ if (running) advertise() }, 15_000)
            }
    }

    private fun discover() {
        val opts = DiscoveryOptions.Builder().setStrategy(strategy).build()
        client.startDiscovery(SERVICE_ID, discovery, opts)
            .addOnFailureListener { e ->
                val code = (e as? ApiException)?.statusCode
                if (code == ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING) return@addOnFailureListener
                Log.w(TAG, "discovery gagal", e)
                MeshState.error.value = explain(code)
                main.postDelayed({ if (running) discover() }, 15_000)
            }
    }

    private fun restartDiscovery() {
        client.stopDiscovery()
        discover()
    }

    private fun explain(code: Int?): String = when (code) {
        ConnectionsStatusCodes.STATUS_BLUETOOTH_ERROR ->
            "Bluetooth bermasalah. Coba matikan lalu nyalakan Bluetooth."
        null -> "Gagal memulai jaringan. Pastikan Google Play Services terpasang."
        else -> "Gagal memulai jaringan (${ConnectionsStatusCodes.getStatusCodeString(code ?: 0)}). Pastikan Bluetooth, WiFi, dan Lokasi menyala."
    }

    /** Dijalankan tiap menit: kabar kehadiran, cari ulang jika sendirian, bersihkan antrean lama. */
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            if (connected.isNotEmpty()) sendPacket(hello()) else restartDiscovery()
            pruneRecent()
            main.postDelayed(this, 60_000)
        }
    }

    // ---------------------------------------------------------------- koneksi

    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (info.serviceId != SERVICE_ID) return
            val parsed = parseName(info.endpointName) ?: return
            if (parsed.first == me) return
            known[endpointId] = parsed
            if (connected.containsKey(endpointId) || endpointId in pending) return
            // Hanya satu sisi yang memulai, supaya tidak saling tabrak.
            if (me < parsed.first) {
                requestConnection(endpointId)
            } else {
                main.postDelayed({
                    if (running && known.containsKey(endpointId) &&
                        !connected.containsKey(endpointId) && endpointId !in pending
                    ) requestConnection(endpointId)
                }, 15_000)
            }
        }

        override fun onEndpointLost(endpointId: String) {
            if (!connected.containsKey(endpointId)) known.remove(endpointId)
        }
    }

    private fun requestConnection(endpointId: String) {
        pending += endpointId
        client.requestConnection(endpointName(), endpointId, lifecycle)
            .addOnFailureListener { e ->
                pending -= endpointId
                val code = (e as? ApiException)?.statusCode
                if (code == ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT) return@addOnFailureListener
                main.postDelayed({
                    if (running && known.containsKey(endpointId) && !connected.containsKey(endpointId)) {
                        requestConnection(endpointId)
                    }
                }, 8_000L + Random.nextLong(4_000L))
            }
    }

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            parseName(info.endpointName)?.let { known[endpointId] = it }
            pending += endpointId
            client.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            pending -= endpointId
            if (!result.status.isSuccess) return
            val (nodeId, name) = known[endpointId] ?: (endpointId to endpointId)
            connected[endpointId] = nodeId
            MeshState.upsertNode(Node(nodeId, name, 0, now(), true))
            publishPeers()
            onNewLink(endpointId)
        }

        override fun onDisconnected(endpointId: String) {
            connected.remove(endpointId)
            publishPeers()
        }
    }

    private fun publishPeers() {
        MeshState.setDirect(connected.values.toSet())
        onPeersChanged?.invoke(connected.size)
    }

    /** HP baru tersambung: perkenalkan diri dan bagikan pesan terbaru (simpan-teruskan). */
    private fun onNewLink(endpointId: String) {
        val h = hello()
        seen[h.id] = now()
        sendTo(listOf(endpointId), h)
        pruneRecent()
        recent.forEach { (p, _) -> sendTo(listOf(endpointId), p) }
        MeshState.markWaitingAsSent()
    }

    // ---------------------------------------------------------------- paket

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return
            val bytes = payload.asBytes() ?: return
            val p = Packet.parse(bytes) ?: return
            handle(endpointId, p)
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private fun handle(fromEndpoint: String, p: Packet) {
        if (p.src == me) return
        if (seen.containsKey(p.id)) return
        seen[p.id] = now()

        val isDirect = connected.values.contains(p.src)
        MeshState.upsertNode(Node(p.src, p.srcName, if (isDirect) 0 else p.hop, now(), isDirect))

        when (p.kind) {
            "msg" -> if (p.dst == BROADCAST || p.dst == me) {
                val m = Msg(
                    id = p.id, incoming = true, from = p.src, fromName = p.srcName,
                    to = p.dst, toName = if (p.dst == me) myName else "Semua",
                    text = p.text.take(MAX_TEXT), sos = p.sos, ts = now(), hops = p.hop,
                )
                MeshState.addMessage(m)
                onIncoming(m)
                if (p.dst == me) {
                    sendPacket(
                        Packet(
                            kind = "ack", id = Packet.newId(), src = me, srcName = myName,
                            dst = p.src, ttl = MAX_TTL, hop = 0, ts = now(), ref = p.id,
                        )
                    )
                }
            }
            "ack" -> if (p.dst == me) MeshState.markDelivered(p.ref)
        }

        // Teruskan ke HP lain, kecuali pesan memang untuk saya.
        if (p.dst != me && p.ttl > 0) {
            val r = p.relayed()
            if (r.kind != "hello") remember(r)
            val targets = connected.keys.filter { it != fromEndpoint }
            if (targets.isNotEmpty()) sendTo(targets, r)
        }
    }

    fun sendMessage(text: String, dst: String, sos: Boolean) {
        val clean = text.trim().take(MAX_TEXT)
        if (clean.isEmpty()) return
        val p = Packet(
            kind = "msg", id = Packet.newId(), src = me, srcName = myName,
            dst = dst, ttl = MAX_TTL, hop = 0, text = clean, sos = sos, ts = now(),
        )
        val sent = sendPacket(p)
        MeshState.addMessage(
            Msg(
                id = p.id, incoming = false, from = me, fromName = myName,
                to = dst, toName = MeshState.nameOf(dst), text = clean, sos = sos,
                ts = now(), status = if (sent) Status.SENT else Status.WAITING,
            )
        )
    }

    /** Kirim paket milik sendiri ke semua HP yang tersambung. */
    private fun sendPacket(p: Packet): Boolean {
        seen[p.id] = now()
        if (p.kind != "hello") remember(p)
        val targets = connected.keys.toList()
        if (targets.isEmpty()) return false
        sendTo(targets, p)
        return true
    }

    private fun sendTo(targets: List<String>, p: Packet) {
        client.sendPayload(targets, Payload.fromBytes(p.toBytes()))
            .addOnFailureListener { Log.w(TAG, "kirim gagal ke $targets", it) }
    }

    private fun hello() = Packet(
        kind = "hello", id = Packet.newId(), src = me, srcName = myName,
        dst = BROADCAST, ttl = 3, hop = 0, ts = now(),
    )

    private fun remember(p: Packet) {
        recent.addLast(p to now())
        while (recent.size > MAX_RECENT) recent.removeFirst()
    }

    private fun pruneRecent() {
        val cutoff = now() - RECENT_WINDOW_MS
        while (recent.isNotEmpty() && recent.first().second < cutoff) recent.removeFirst()
    }
}
