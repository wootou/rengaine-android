package app.rengaine.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

/** Rayons (U3) : 6 pochettes · 10 cartes et champs · 14 mini-lecteur · 16 panneaux · pilule. */
object RengaineShapes {
    val cover = RoundedCornerShape(6.dp)
    val card = RoundedCornerShape(10.dp)
    val miniPlayer = RoundedCornerShape(14.dp)
    val panel = RoundedCornerShape(16.dp)
    val dialog = RoundedCornerShape(20.dp)
    val pill = RoundedCornerShape(percent = 50)
}

/** Accès aux jetons : `Rengaine.colors.accent`, `Rengaine.type.body`. */
object Rengaine {
    val colors: RengaineColors
        @Composable @ReadOnlyComposable get() = LocalRengaineColors.current
    val type: RengaineTypography
        @Composable @ReadOnlyComposable get() = LocalRengaineTypography.current
}

/**
 * Thème de l'app. Pas de couleurs dynamiques Material You (D9) : la
 * direction « Braise » est pensée contraste par contraste. Le `ColorScheme`
 * Material est dérivé des jetons pour que les composants standard (menus,
 * dialogues, curseurs) suivent sans retouche.
 */
@Composable
fun RengaineTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            primaryContainer = colors.accentSoft,
            onPrimaryContainer = colors.text,
            secondary = colors.accent,
            onSecondary = colors.onAccent,
            background = colors.bg,
            onBackground = colors.text,
            surface = colors.surface,
            onSurface = colors.text,
            surfaceVariant = colors.raised,
            onSurfaceVariant = colors.muted,
            surfaceContainerLowest = colors.bg,
            surfaceContainerLow = colors.surface,
            surfaceContainer = colors.surface,
            surfaceContainerHigh = colors.raised,
            surfaceContainerHighest = colors.hover,
            outline = colors.line,
            outlineVariant = colors.line,
            error = colors.danger,
            onError = colors.onDanger,
            scrim = colors.scrim,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            primaryContainer = colors.accentSoft,
            onPrimaryContainer = colors.text,
            secondary = colors.accent,
            onSecondary = colors.onAccent,
            background = colors.bg,
            onBackground = colors.text,
            surface = colors.surface,
            onSurface = colors.text,
            surfaceVariant = colors.raised,
            onSurfaceVariant = colors.muted,
            surfaceContainerLowest = colors.surface,
            surfaceContainerLow = colors.surface,
            surfaceContainer = colors.surface,
            surfaceContainerHigh = colors.raised,
            surfaceContainerHighest = colors.hover,
            outline = colors.line,
            outlineVariant = colors.line,
            error = colors.danger,
            onError = colors.onDanger,
            scrim = colors.scrim,
        )
    }
    val type = DefaultTypography
    val materialType = Typography(
        bodyLarge = type.body,
        bodyMedium = type.body,
        bodySmall = type.secondary,
        labelLarge = type.button,
        titleLarge = type.sectionTitle,
        headlineSmall = type.sectionTitle,
    )
    CompositionLocalProvider(
        LocalRengaineColors provides colors,
        LocalRengaineTypography provides type,
    ) {
        MaterialTheme(colorScheme = scheme, typography = materialType, content = content)
    }
}
