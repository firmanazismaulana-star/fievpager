package id.fievpager

import org.json.JSONObject
import java.util.UUID

const val BROADCAST = "*"

/** Paket yang berpindah dari HP ke HP. Dikirim sebagai JSON. */
data class Packet(
    val kind: String,          // "msg", "ack", "hello"
    val id: String,
    val src: String,           // ID node pengirim asli
    val srcName: String,
    val dst: String,           // BROADCAST atau ID node tujuan
    val ttl: Int,              // sisa lompatan
    val hop: Int,              // sudah berapa kali diteruskan
    val text: String = "",
    val sos: Boolean = false,
    val ts: Long,
    val ref: String = "",      // untuk ack: ID pesan yang dibalas
) {
    fun toBytes(): ByteArray {
        val o = JSONObject()
        o.put("v", 1)
        o.put("k", kind)
        o.put("id", id)
        o.put("src", src)
        o.put("sn", srcName)
        o.put("dst", dst)
        o.put("ttl", ttl)
        o.put("hop", hop)
        if (text.isNotEmpty()) o.put("txt", text)
        if (sos) o.put("sos", true)
        o.put("ts", ts)
        if (ref.isNotEmpty()) o.put("ref", ref)
        return o.toString().toByteArray(Charsets.UTF_8)
    }

    fun relayed(): Packet = copy(ttl = ttl - 1, hop = hop + 1)

    companion object {
        fun parse(bytes: ByteArray): Packet? = try {
            val o = JSONObject(String(bytes, Charsets.UTF_8))
            if (o.optInt("v") != 1) null else Packet(
                kind = o.getString("k"),
                id = o.getString("id"),
                src = o.getString("src"),
                srcName = o.optString("sn"),
                dst = o.optString("dst", BROADCAST),
                ttl = o.optInt("ttl"),
                hop = o.optInt("hop"),
                text = o.optString("txt"),
                sos = o.optBoolean("sos"),
                ts = o.optLong("ts"),
                ref = o.optString("ref"),
            )
        } catch (e: Exception) {
            null
        }

        fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(16)
    }
}

enum class Status { WAITING, SENT, DELIVERED }

data class Msg(
    val id: String,
    val incoming: Boolean,
    val from: String,
    val fromName: String,
    val to: String,
    val toName: String,
    val text: String,
    val sos: Boolean,
    val ts: Long,
    val hops: Int = 0,
    val status: Status = Status.SENT,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("in", incoming).put("f", from).put("fn", fromName)
        .put("t", to).put("tn", toName).put("x", text).put("s", sos)
        .put("ts", ts).put("h", hops).put("st", status.name)

    companion object {
        fun fromJson(o: JSONObject): Msg? = try {
            Msg(
                id = o.getString("id"),
                incoming = o.getBoolean("in"),
                from = o.getString("f"),
                fromName = o.optString("fn"),
                to = o.optString("t", BROADCAST),
                toName = o.optString("tn"),
                text = o.getString("x"),
                sos = o.optBoolean("s"),
                ts = o.getLong("ts"),
                hops = o.optInt("h"),
                status = Status.valueOf(o.optString("st", "SENT")),
            )
        } catch (e: Exception) {
            null
        }
    }
}

data class Node(
    val id: String,
    val name: String,
    val hops: Int,
    val lastSeen: Long,
    val direct: Boolean,
    val lan: Boolean = false,     // terhubung lewat WiFi lokal (router/hotspot)
)
