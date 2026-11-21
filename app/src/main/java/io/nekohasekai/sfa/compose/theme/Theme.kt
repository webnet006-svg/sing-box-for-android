package io.nekohasekai.sfa.compose.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import io.nekohasekai.sfa.compose.screen.am.A2MDesignTokens
import io.nekohasekai.sfa.compose.screen.am.A2MFontCatalog
import io.nekohasekai.sfa.compose.screen.am.A2MThemeState

private val DarkColorScheme =
    darkColorScheme(
        primary = A2MDesignTokens.cyan,
        onPrimary = A2MDesignTokens.backgroundDeep,
        secondary = A2MDesignTokens.blue,
        onSecondary = A2MDesignTokens.text,
        tertiary = A2MDesignTokens.violet,
        background = A2MDesignTokens.backgroundDeep,
        onBackground = A2MDesignTokens.text,
        surface = A2MDesignTokens.background,
        onSurface = A2MDesignTokens.text,
        surfaceVariant = Color(0xFF14325F),
        onSurfaceVariant = A2MDesignTokens.textSecondary,
        outline = A2MDesignTokens.border,
        error = A2MDesignTokens.error,
    )

private val LightColorScheme =
    lightColorScheme(
        primary = SingBoxPrimary,
        secondary = SingBoxPrimaryDark,
        tertiary = LogBlue,
    )

@Composable
fun Theme(
    darkTheme: Boolean = true,
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
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
    val typography = remember(A2MThemeState.fontId) {
        Typography.withFontFamily(A2MFontCatalog.family(A2MThemeState.fontId))
    }
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
        typography = typography,
        shapes = Shapes,
        content = content,
    )
}
