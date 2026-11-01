package id.fievpager

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/** Layanan latar: menjaga jaringan tetap hidup walau layar mati, seperti pager. */
class MeshService : Service() {

    companion object {
        const val ACTION_STOP = "id.fievpager.STOP"
    }

    private var engine: MeshEngine? = null

    override fun onCreate() {
        super.onCreate()
        MeshState.init(applicationContext)
        Notifs.createChannels(this)
        val e = MeshEngine(this) { m -> Notifs.showIncoming(this, m) }
        e.onPeersChanged = { n -> Notifs.updateOngoing(this, n) }
        engine = e
        MeshState.engine = e
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            engine?.stop()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
            ServiceCompat.startForeground(this, Notifs.ONGOING_ID, Notifs.ongoing(this, 0), type)
        } catch (e: Exception) {
            Log.e("FievMesh", "Tidak bisa berjalan di latar", e)
            MeshState.error.value = "Tidak bisa berjalan di latar. Pastikan izin Bluetooth sudah diberikan."
            stopSelf()
            return START_NOT_STICKY
        }
        engine?.start()
        return START_STICKY
    }

    override fun onDestroy() {
        engine?.stop()
        if (MeshState.engine === engine) MeshState.engine = null
        engine = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

object Notifs {
    const val ONGOING_ID = 1
    private const val CH_STATUS = "status"
    private const val CH_MSG = "pesan"
    private const val CH_SOS = "sos"

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_STATUS, "Status jaringan", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_MSG, "Pesan masuk", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 120, 200, 120, 200)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_SOS, "SOS darurat", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 300, 800, 300, 800)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
        )
    }

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun ongoing(ctx: Context, peers: Int): Notification {
        val stop = PendingIntent.getService(
            ctx, 1,
            Intent(ctx, MeshService::class.java).setAction(MeshService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (peers == 0) "Mencari HP di sekitar…" else "$peers HP terhubung langsung"
        return NotificationCompat.Builder(ctx, CH_STATUS)
            .setSmallIcon(R.drawable.ic_stat_pager)
            .setContentTitle("FievPager aktif")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(ctx))
            .addAction(0, "Matikan", stop)
            .build()
    }

    private fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun updateOngoing(ctx: Context, peers: Int) {
        if (!canNotify(ctx)) return
        try {
            NotificationManagerCompat.from(ctx).notify(ONGOING_ID, ongoing(ctx, peers))
        } catch (_: SecurityException) {
        }
    }

    @SuppressLint("MissingPermission")
    fun showIncoming(ctx: Context, m: Msg) {
        if (!canNotify(ctx)) return
        val n = NotificationCompat.Builder(ctx, if (m.sos) CH_SOS else CH_MSG)
            .setSmallIcon(R.drawable.ic_stat_pager)
            .setContentTitle(if (m.sos) "SOS dari ${m.fromName}" else m.fromName)
            .setContentText(m.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(m.text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (m.sos) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(m.id.hashCode(), n)
        } catch (_: SecurityException) {
        }
    }
}
