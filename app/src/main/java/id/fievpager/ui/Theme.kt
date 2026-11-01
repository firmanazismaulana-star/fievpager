package id.fievpager.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/** Warna "casing" pager: sama di tema terang dan gelap. */
object Pal {
    val case = Color(0xFF2C302D)
    val caseText = Color(0xFFE9EBE3)
    val caseDim = Color(0xFFA9AEA3)
    val live = Color(0xFF8FC46A)
    val off = Color(0xFF8A8E86)
    val warn = Color(0xFFE8952A)
}

val Mono = FontFamily.Monospace

// Terang: layar LCD hijau-zaitun seperti pager klasik.
private val Light = lightColorScheme(
    background = Color(0xFFDCDED5),
    onBackground = Color(0xFF22261F),
    surface = Color(0xFFB6C199),
    onSurface = Color(0xFF1D2516),
    surfaceVariant = Color(0xFFC9D1B0),
    onSurfaceVariant = Color(0xFF46523A),
    surfaceContainer = Color(0xFFCFD3C6),
    primary = Color(0xFF2C302D),
    onPrimary = Color(0xFFE9EBE3),
    secondary = Color(0xFFD9821C),
    onSecondary = Color(0xFF1F1405),
    secondaryContainer = Color(0xFFE9C38F),
    onSecondaryContainer = Color(0xFF2A1A04),
    error = Color(0xFFB8321C),
    onError = Color(0xFFFFFFFF),
    outline = Color(0xFF8E9A72),
)

// Gelap: LCD dengan lampu latar amber.
private val Dark = darkColorScheme(
    background = Color(0xFF121414),
    onBackground = Color(0xFFD8DAD2),
    surface = Color(0xFF261E11),
    onSurface = Color(0xFFF3B95C),
    surfaceVariant = Color(0xFF31281A),
    onSurfaceVariant = Color(0xFFC49A55),
    surfaceContainer = Color(0xFF1B1E1D),
    primary = Color(0xFFE8952A),
    onPrimary = Color(0xFF1A1004),
    secondary = Color(0xFFE8952A),
    onSecondary = Color(0xFF1A1004),
    secondaryContainer = Color(0xFF4A3613),
    onSecondaryContainer = Color(0xFFF3C27A),
    error = Color(0xFFE5533A),
    onError = Color(0xFF1A0500),
    outline = Color(0xFF5A4828),
)

@Composable
fun FievTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
