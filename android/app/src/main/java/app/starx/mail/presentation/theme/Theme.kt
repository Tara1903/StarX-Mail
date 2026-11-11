package app.starx.mail.presentation.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = StarXPrimary,
    onPrimary = StarXOnPrimary,
    primaryContainer = StarXPrimaryContainer,
    onPrimaryContainer = StarXOnPrimaryContainer,
    secondary = StarXSecondary,
    onSecondary = StarXOnSecondary,
    secondaryContainer = StarXSecondaryContainer,
    onSecondaryContainer = StarXOnSecondaryContainer,
    tertiary = StarXTertiary,
    onTertiary = StarXOnTertiary,
    background = StarXBackgroundDark,
    onBackground = StarXOnBackgroundDark,
    surface = StarXSurfaceDark,
    onSurface = StarXOnSurfaceDark,
    surfaceVariant = StarXSurfaceVariantDark,
    onSurfaceVariant = StarXOnSurfaceVariantDark,
    error = StarXErrorRed
)

private val LightColorScheme = lightColorScheme(
    primary = StarXPrimary,
    onPrimary = StarXOnPrimary,
    primaryContainer = StarXPrimaryContainer,
    onPrimaryContainer = StarXOnPrimaryContainer,
    secondary = StarXSecondary,
    onSecondary = StarXOnSecondary,
    secondaryContainer = StarXSecondaryContainer,
    onSecondaryContainer = StarXOnSecondaryContainer,
    tertiary = StarXTertiary,
    onTertiary = StarXOnTertiary,
    background = StarXBackgroundLight,
    onBackground = StarXOnBackgroundLight,
    surface = StarXSurfaceLight,
    onSurface = StarXOnSurfaceLight,
    surfaceVariant = StarXSurfaceVariantLight,
    onSurfaceVariant = StarXOnSurfaceVariantLight,
    error = StarXErrorRed
)

@Composable
fun StarXTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
