package id.fievpager

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import java.io.File
import java.util.concurrent.Executors
import kotlin.random.Random

/** Status bersama antara layanan latar (MeshService) dan tampilan. */
object MeshState {
    private const val MAX_HISTORY = 500

    val messages = MutableStateFlow<List<Msg>>(emptyList())
    val nodes = MutableStateFlow<Map<String, Node>>(emptyMap())
    val running = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val myName = MutableStateFlow("")
    val lanEnabled = MutableStateFlow(true)
    var myId: String = ""
        private set

    @Volatile
    var engine: MeshEngine? = null

    private var store: Store? = null

    fun init(ctx: Context) {
        if (store != null) return
        val s = Store(ctx.applicationContext)
        store = s
        myId = s.nodeId()
        myName.value = s.name()
        lanEnabled.value = s.lan()
        messages.value = s.loadMessages()
    }

    fun setName(n: String) {
        myName.value = n
        store?.saveName(n)
    }

    fun addMessage(m: Msg) {
        messages.update { (it + m).takeLast(MAX_HISTORY) }
        persist()
    }

    fun markDelivered(id: String) {
        messages.update { list ->
            list.map { if (it.id == id && !it.incoming) it.copy(status = Status.DELIVERED) else it }
        }
        persist()
    }

    fun markWaitingAsSent() {
        if (messages.value.none { it.status == Status.WAITING }) return
        messages.update { list ->
            list.map { if (it.status == Status.WAITING) it.copy(status = Status.SENT) else it }
        }
        persist()
    }

    fun clearHistory() {
        messages.value = emptyList()
        persist()
    }

    fun upsertNode(n: Node) {
        nodes.update { it + (n.id to n) }
    }

    fun setDirect(nearby: Set<String>, viaLan: Set<String>) {
        nodes.update { m ->
            m.mapValues { (k, v) -> v.copy(direct = k in nearby || k in viaLan, lan = k in viaLan && k !in nearby) }
        }
    }

    fun setLanEnabled(on: Boolean) {
        lanEnabled.value = on
        store?.saveLan(on)
    }

    fun nameOf(id: String): String =
        if (id == BROADCAST) "Semua" else nodes.value[id]?.name ?: id.take(4)

    private fun persist() {
        store?.saveMessages(messages.value)
    }
}

/** Penyimpanan lokal: ID node, nama, dan riwayat pesan. */
class Store(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("fievpager", Context.MODE_PRIVATE)
    private val file = File(ctx.filesDir, "messages.json")
    private val io = Executors.newSingleThreadExecutor()

    fun nodeId(): String {
        prefs.getString("id", null)?.let { return it }
        val id = Random.nextBytes(4).joinToString("") { "%02X".format(it.toInt() and 0xFF) }
        prefs.edit().putString("id", id).apply()
        return id
    }

    fun name(): String = prefs.getString("name", "") ?: ""

    fun saveName(n: String) {
        prefs.edit().putString("name", n).apply()
    }

    fun lan(): Boolean = prefs.getBoolean("lan", true)

    fun saveLan(on: Boolean) {
        prefs.edit().putBoolean("lan", on).apply()
    }

    fun loadMessages(): List<Msg> = try {
        if (!file.exists()) emptyList() else {
            val a = JSONArray(file.readText())
            (0 until a.length()).mapNotNull { Msg.fromJson(a.getJSONObject(it)) }
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun saveMessages(list: List<Msg>) {
        val snapshot = list.toList()
        io.execute {
            try {
                val a = JSONArray()
                snapshot.forEach { a.put(it.toJson()) }
                val tmp = File(file.path + ".tmp")
                tmp.writeText(a.toString())
                tmp.renameTo(file)
            } catch (_: Exception) {
            }
        }
    }
}
