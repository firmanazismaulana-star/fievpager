package id.fievpager.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.fievpager.BROADCAST
import id.fievpager.MeshEngine
import id.fievpager.MeshState
import id.fievpager.Msg
import id.fievpager.Node
import id.fievpager.Perms
import id.fievpager.Status
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val QUICK = listOf(
    "Saya aman",
    "Butuh bantuan",
    "Butuh air & makanan",
    "Ada korban luka",
    "Menuju titik kumpul",
    "OK, diterima",
)
private const val SOS_TEXT = "SOS! Butuh pertolongan segera."

// ==================================================================== root

@Composable
fun AppRoot(onStart: () -> Unit, onStop: () -> Unit) {
    val ctx = LocalContext.current
    val name by MeshState.myName.collectAsState()
    var granted by remember { mutableStateOf(Perms.hasRequired(ctx)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = Perms.hasRequired(ctx)
        if (granted) onStart()
    }

    when {
        name.isBlank() -> NameSetup { MeshState.setName(it) }
        !granted -> PermissionScreen { launcher.launch(Perms.toRequest()) }
        else -> {
            LaunchedEffect(Unit) { if (!MeshState.running.value) onStart() }
            MainScreen(onStart, onStop)
        }
    }
}

@Composable
private fun NameSetup(onDone: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Brand()
        Text(
            "Pager tanpa internet. HP saling terhubung lewat Bluetooth dan WiFi, " +
                "lalu pesan diteruskan dari HP ke HP sampai ke tujuan.",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= 20) name = it.replace("|", "") },
            label = { Text("Nama Anda atau nama posko") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Nama ini terlihat oleh HP lain di jaringan.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
        Button(
            onClick = { onDone(name.trim()) },
            enabled = name.trim().length >= 2,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Mulai") }
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Brand()
        Text("Izinkan akses perangkat di sekitar", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "FievPager perlu izin Bluetooth, perangkat WiFi di sekitar, dan lokasi untuk " +
                "menemukan HP lain. Lokasi Anda tidak disimpan atau dikirim ke mana pun.\n\n" +
                "Izin notifikasi dipakai untuk bunyi saat pesan atau SOS masuk.",
            fontSize = 15.sp,
        )
        Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) { Text("Izinkan") }
        Text(
            "Jika tombol tidak memunculkan apa-apa, buka Pengaturan > Aplikasi > FievPager > Izin.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun Brand() {
    Text(
        "FIEVPAGER",
        fontFamily = Mono,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = 4.sp,
        color = MaterialTheme.colorScheme.secondary,
    )
}

// ==================================================================== main

@Composable
private fun MainScreen(onStart: () -> Unit, onStop: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var to by rememberSaveable { mutableStateOf(BROADCAST) }
    val msgs by MeshState.messages.collectAsState()
    val nodes by MeshState.nodes.collectAsState()
    val running by MeshState.running.collectAsState()
    val error by MeshState.error.collectAsState()
    val name by MeshState.myName.collectAsState()
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }

    val tabs: List<Pair<String, ImageVector>> = listOf(
        "Pesan" to Icons.Default.Email,
        "Sekitar" to Icons.Default.Person,
        "Atur" to Icons.Default.Settings,
    )

    Scaffold(
        topBar = {
            TopStrip(
                name, running, error,
                nodes.values.count { it.direct }, nodes.values.count { it.lan }, nodes.size,
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(label) },
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> MessagesTab(msgs, nodes, to, { to = it }, running)
                1 -> NearbyTab(nodes, now) { to = it; tab = 0 }
                else -> SettingsTab(name, running, onStart, onStop)
            }
        }
    }
}

@Composable
private fun TopStrip(name: String, running: Boolean, error: String?, direct: Int, viaLan: Int, known: Int) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Pal.case)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "FIEVPAGER", color = Pal.caseText, fontFamily = Mono,
                fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 3.sp,
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(9.dp)
                    .background(if (running) Pal.live else Pal.off, CircleShape)
            )
            Text(
                if (running) "  AKTIF" else "  MATI",
                color = Pal.caseText, fontFamily = Mono, fontSize = 13.sp,
            )
        }
        Text("$name · ID ${MeshState.myId}", color = Pal.caseDim, fontFamily = Mono, fontSize = 13.sp)
        val status = when {
            error != null -> error
            !running -> "Jaringan mati. Nyalakan di tab Atur."
            direct == 0 -> "Mencari HP di sekitar…"
            viaLan > 0 -> "$direct HP terhubung ($viaLan lewat WiFi lokal) · $known dikenal"
            else -> "$direct HP terhubung langsung · $known dikenal di jaringan"
        }
        Text(status, color = if (error != null) Pal.warn else Pal.caseText, fontSize = 13.sp)
    }
}

// ==================================================================== pesan

@Composable
private fun MessagesTab(
    msgs: List<Msg>,
    nodes: Map<String, Node>,
    to: String,
    onTo: (String) -> Unit,
    running: Boolean,
) {
    val cs = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    var text by rememberSaveable { mutableStateOf("") }
    var askSos by remember { mutableStateOf(false) }

    LaunchedEffect(msgs.size) {
        if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.lastIndex)
    }

    fun send(t: String, dst: String, sos: Boolean) {
        MeshState.engine?.sendMessage(t, dst, sos)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        if (msgs.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "Belum ada pesan.\nKirim pesan siaran untuk menyapa HP di sekitar.",
                    textAlign = TextAlign.Center,
                    color = cs.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(msgs, key = { it.id }) { MessageItem(it) }
            }
        }

        // ---- penyusun pesan
        Surface(color = cs.surfaceContainer) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(QUICK) { q -> AssistChip(onClick = { text = q }, label = { Text(q) }) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RecipientPicker(to, nodes, onTo)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${text.length}/${MeshEngine.MAX_TEXT}",
                        fontFamily = Mono, fontSize = 12.sp,
                        color = cs.onBackground.copy(alpha = 0.6f),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { askSos = true },
                        enabled = running,
                        colors = ButtonDefaults.buttonColors(containerColor = cs.error, contentColor = cs.onError),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                    ) { Text("SOS", fontWeight = FontWeight.Bold) }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { if (it.length <= MeshEngine.MAX_TEXT) text = it },
                        placeholder = { Text(if (running) "Tulis pesan" else "Jaringan mati") },
                        maxLines = 4,
                        modifier = Modifier.weight(1f),
                    )
                    FilledIconButton(
                        onClick = { send(text, to, false); text = "" },
                        enabled = running && text.isNotBlank(),
                    ) { Icon(Icons.Default.Send, contentDescription = "Kirim") }
                }
            }
        }
    }

    if (askSos) {
        AlertDialog(
            onDismissRequest = { askSos = false },
            title = { Text("Kirim SOS?") },
            text = { Text("Pesan darurat dikirim ke semua HP di jaringan dan berbunyi sebagai alarm.") },
            confirmButton = {
                TextButton(onClick = { send(SOS_TEXT, BROADCAST, true); askSos = false }) {
                    Text("Kirim SOS", color = cs.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { askSos = false }) { Text("Batal") } },
        )
    }
}

@Composable
private fun RecipientPicker(to: String, nodes: Map<String, Node>, onTo: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = if (to == BROADCAST) "Semua (siaran)" else nodes[to]?.name ?: to
    Box {
        TextButton(onClick = { open = true }, contentPadding = PaddingValues(horizontal = 4.dp)) {
            Text("Ke: $label", maxLines = 1)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Semua (siaran)") }, onClick = { onTo(BROADCAST); open = false })
            nodes.values.sortedBy { it.name.lowercase() }.forEach { n ->
                DropdownMenuItem(
                    text = { Text("${n.name}  ·  ${nodeLabel(n)}") },
                    onClick = { onTo(n.id); open = false },
                )
            }
        }
    }
}

@Composable
private fun MessageItem(m: Msg) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (m.incoming) Alignment.Start else Alignment.End,
    ) {
        Surface(
            color = if (m.sos) cs.error.copy(alpha = 0.16f).compositeOver(cs.surface) else cs.surface,
            contentColor = cs.onSurface,
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(if (m.sos) 2.dp else 1.dp, if (m.sos) cs.error else cs.outline),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Column(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val who = if (m.incoming) m.fromName else "Saya"
                val whom = when {
                    m.to == BROADCAST -> "Semua"
                    m.incoming -> "Saya"
                    else -> m.toName
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (m.sos) {
                        Text("SOS  ", color = cs.error, fontWeight = FontWeight.Bold, fontFamily = Mono, fontSize = 14.sp)
                    }
                    Text("$who → $whom", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Text(m.text, fontFamily = Mono, fontSize = 17.sp, lineHeight = 22.sp)
                val meta = buildString {
                    append(clock(m.ts))
                    if (m.incoming) {
                        append(" · ")
                        append(hopLabel(m.hops, m.hops == 0))
                    } else {
                        append(" · ")
                        append(
                            when (m.status) {
                                Status.WAITING -> "Menunggu HP lain"
                                Status.SENT -> if (m.to == BROADCAST) "Terkirim ke jaringan" else "Terkirim, menunggu tanda terima"
                                Status.DELIVERED -> "Diterima ✓"
                            }
                        )
                    }
                }
                Text(meta, fontFamily = Mono, fontSize = 12.sp, color = cs.onSurfaceVariant)
            }
        }
    }
}

// ==================================================================== sekitar

@Composable
private fun NearbyTab(nodes: Map<String, Node>, now: Long, onMessage: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val list = nodes.values.sortedWith(
        compareByDescending<Node> { it.direct }.thenByDescending { it.lastSeen }
    )
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Belum ada HP lain terdeteksi.\n\n" +
                    "Minta teman memasang FievPager dan membukanya dalam jarak sekitar 50 meter. " +
                    "Pastikan Bluetooth, WiFi, dan Lokasi menyala di kedua HP.",
                textAlign = TextAlign.Center,
                color = cs.onBackground.copy(alpha = 0.75f),
                modifier = Modifier.padding(32.dp),
            )
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(list, key = { it.id }) { n ->
            Surface(
                color = cs.surface,
                contentColor = cs.onSurface,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, cs.outline),
            ) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(n.name, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            "ID ${n.id} · ${nodeLabel(n)}",
                            fontFamily = Mono, fontSize = 13.sp, color = cs.onSurfaceVariant,
                        )
                        Text("Terlihat ${ago(n.lastSeen, now)}", fontSize = 12.sp, color = cs.onSurfaceVariant)
                    }
                    TextButton(onClick = { onMessage(n.id) }) { Text("Kirim pesan") }
                }
            }
        }
    }
}

// ==================================================================== atur

@Composable
private fun SettingsTab(name: String, running: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var draft by rememberSaveable(name) { mutableStateOf(name) }
    var confirmClear by remember { mutableStateOf(false) }
    var batteryOk by remember { mutableStateOf(isIgnoringBattery(ctx)) }
    val lanOn by MeshState.lanEnabled.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Section("Jaringan") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Jaringan aktif", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (running) "HP ini menerima dan ikut meneruskan pesan orang lain. Biarkan aktif agar jaringan makin luas."
                        else "HP ini tidak menerima atau meneruskan pesan.",
                        fontSize = 13.sp,
                    )
                }
                Switch(checked = running, onCheckedChange = { if (it) onStart() else onStop() })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Mode WiFi lokal", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Kirim pesan lewat router WiFi yang sama, walau internetnya mati. " +
                            "Tanpa router? Satu HP menyalakan hotspot, HP lain menyambung ke hotspot itu.",
                        fontSize = 13.sp,
                    )
                }
                Switch(
                    checked = lanOn,
                    onCheckedChange = { on ->
                        val e = MeshState.engine
                        if (e != null) e.setLanEnabled(on) else MeshState.setLanEnabled(on)
                    },
                )
            }
            if (!batteryOk) {
                OutlinedButton(onClick = {
                    requestIgnoreBattery(ctx)
                    batteryOk = isIgnoringBattery(ctx)
                }) { Text("Izinkan tetap aktif saat layar mati") }
                Text(
                    "Beberapa HP (Xiaomi, Oppo, Vivo, Samsung) menghentikan aplikasi di latar untuk hemat baterai. " +
                        "Izinkan agar pesan tetap masuk.",
                    fontSize = 13.sp,
                )
            }
        }

        Section("Nama") {
            OutlinedTextField(
                value = draft,
                onValueChange = { if (it.length <= 20) draft = it.replace("|", "") },
                label = { Text("Nama Anda atau nama posko") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    val n = draft.trim()
                    val e = MeshState.engine
                    if (e != null) e.rename(n) else MeshState.setName(n)
                },
                enabled = draft.trim().length >= 2 && draft.trim() != name,
            ) { Text("Simpan nama") }
        }

        Section("Cara kerja") {
            Text(
                "HP terhubung langsung ke HP lain di sekitar lewat Bluetooth atau WiFi Direct, tanpa internet " +
                    "dan tanpa router. Jarak satu sambungan sekitar 10–100 meter. Setiap HP yang aktif ikut " +
                    "meneruskan pesan, maksimal ${MeshEngine.MAX_TTL} kali lompatan.\n\n" +
                    "Dengan Mode WiFi lokal, semua HP di jaringan WiFi yang sama (router atau hotspot HP) " +
                    "saling terhubung sejauh jangkauan WiFi itu. Internet tidak dipakai. HP yang tersambung " +
                    "ke WiFi sekaligus dekat HP lain menjadi jembatan antara kedua jalur.\n\n" +
                    "Pesan 30 menit terakhir juga dibagikan ke HP yang baru tersambung, jadi orang yang " +
                    "berpindah tempat ikut membawa pesan.",
                fontSize = 14.sp,
            )
        }

        Section("Agar lancar") {
            Text(
                "• Bluetooth, WiFi, dan Lokasi menyala (WiFi tidak perlu tersambung ke jaringan mana pun)\n" +
                    "• Aplikasi dibuka minimal sekali setelah HP dinyalakan\n" +
                    "• Makin banyak HP yang aktif, makin jauh jangkauan pesan\n" +
                    "• Mode WiFi lokal: semua HP tersambung ke WiFi yang sama. WiFi tamu di kafe atau kantor " +
                    "sering memblokir sambungan antar-perangkat, jadi pakai router sendiri atau hotspot HP",
                fontSize = 14.sp,
            )
        }

        Section("Privasi") {
            Text(
                "Sambungan Bluetooth/WiFi Direct dienkripsi, tetapi isi pesan bisa dibaca HP yang meneruskannya. " +
                    "Di Mode WiFi lokal, pesan juga bisa dilihat perangkat lain di jaringan WiFi yang sama. " +
                    "Jangan kirim kata sandi, PIN, atau data pribadi lewat FievPager.",
                fontSize = 14.sp,
            )
        }

        OutlinedButton(onClick = { confirmClear = true }) { Text("Hapus riwayat pesan") }
        Text(
            "FievPager 0.2 · ID ${MeshState.myId}",
            fontFamily = Mono, fontSize = 12.sp, color = cs.onBackground.copy(alpha = 0.6f),
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Hapus semua pesan?") },
            text = { Text("Riwayat pesan di HP ini akan hilang. Pesan di HP lain tidak terpengaruh.") },
            confirmButton = {
                TextButton(onClick = { MeshState.clearHistory(); confirmClear = false }) {
                    Text("Hapus", color = cs.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Batal") } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title.uppercase(),
            fontSize = 12.sp,
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
        )
        content()
    }
}

// ==================================================================== util

private fun hopLabel(hops: Int, direct: Boolean): String =
    if (direct || hops <= 0) "langsung" else "lewat $hops HP"

private fun nodeLabel(n: Node): String =
    if (n.lan) "lewat WiFi lokal" else hopLabel(n.hops, n.direct)

private fun clock(ts: Long): String = SimpleDateFormat("HH:mm", Locale("id", "ID")).format(Date(ts))

private fun ago(ts: Long, now: Long): String {
    val s = ((now - ts) / 1000).coerceAtLeast(0)
    return when {
        s < 60 -> "baru saja"
        s < 3600 -> "${s / 60} mnt lalu"
        s < 86400 -> "${s / 3600} jam lalu"
        else -> "${s / 86400} hari lalu"
    }
}

private fun isIgnoringBattery(ctx: Context): Boolean {
    val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(ctx.packageName)
}

@SuppressLint("BatteryLife")
private fun requestIgnoreBattery(ctx: Context) {
    try {
        ctx.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
        ctx.startActivity(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
