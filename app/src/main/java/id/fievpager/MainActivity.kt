package id.fievpager

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import id.fievpager.ui.AppRoot
import id.fievpager.ui.FievTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MeshState.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            FievTheme {
                AppRoot(onStart = { startMesh() }, onStop = { stopMesh() })
            }
        }
    }

    private fun startMesh() {
        ContextCompat.startForegroundService(this, Intent(this, MeshService::class.java))
    }

    private fun stopMesh() {
        startService(Intent(this, MeshService::class.java).setAction(MeshService.ACTION_STOP))
    }
}

object Perms {
    fun required(): Array<String> = when {
        Build.VERSION.SDK_INT >= 33 -> arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
        Build.VERSION.SDK_INT >= 31 -> arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
        else -> arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun toRequest(): Array<String> =
        if (Build.VERSION.SDK_INT >= 33) required() + Manifest.permission.POST_NOTIFICATIONS else required()

    fun hasRequired(ctx: Context): Boolean = required().all {
        ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
    }
}
