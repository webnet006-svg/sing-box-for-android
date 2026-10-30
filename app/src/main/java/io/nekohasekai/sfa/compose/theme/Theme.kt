package io.nekohasekai.sfa.compose.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme =
    darkColorScheme(
        primary = SingBoxPrimary,
        onPrimary = VpnBackground,
        primaryContainer = Color(0xFF004D61),
        onPrimaryContainer = SingBoxPrimaryLight,
        secondary = Color(0xFF38BDF8),
        onSecondary = Color(0xFF003549),
        secondaryContainer = Color(0xFF004D68),
        onSecondaryContainer = Color(0xFFCBE6FF),
        tertiary = LogBlue,
        background = VpnBackground,
        onBackground = Color(0xFFE2E8F0),
        surface = VpnSurface,
        onSurface = Color(0xFFF1F5F9),
        surfaceVariant = VpnSurfaceVariant,
        onSurfaceVariant = VpnTextSecondary,
        outline = Color(0xFF1E3A5F),
        outlineVariant = Color(0xFF102A44),
    )

private val LightColorScheme =
    lightColorScheme(
        primary = SingBoxPrimaryDark,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFB2EBF2),
        onPrimaryContainer = Color(0xFF00363F),
        secondary = Color(0xFF0288D1),
        onSecondary = Color.White,
        tertiary = LogBlue,
        background = Color(0xFFF4F8FA),
        onBackground = Color(0xFF0F172A),
        surface = Color.White,
        onSurface = Color(0xFF0F172A),
        surfaceVariant = Color(0xFFE2E8F0),
        onSurfaceVariant = Color(0xFF475569),
        outline = Color(0xFFCBD5E1),
    )

@Composable
fun Theme(
    darkTheme: Boolean = true, // Default to ZALAM dark theme
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Keep ZALAM VPN signature theme
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= 31 -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}
