package app.rengaine.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Jetons de couleur « Braise » (U1). Valeurs reprises de la feuille de style
 * du site en ligne, qui fait foi : plusieurs teintes y ont été corrigées
 * depuis les maquettes pour atteindre le contraste AA.
 */
@Immutable
data class RengaineColors(
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val raised: Color,
    val hover: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    val faint: Color,
    val accent: Color,
    val accentHover: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val danger: Color,
    val onDanger: Color,
    val ok: Color,
    val scrim: Color,
)

val DarkColors = RengaineColors(
    isDark = true,
    bg = Color(0xFF14110F),
    surface = Color(0xFF1D1916),
    raised = Color(0xFF26211D),
    hover = Color(0xFF302A25),
    line = Color(0xFF3A332D),
    text = Color(0xFFF4EDE4),
    muted = Color(0xFFB3A89C),
    faint = Color(0xFF968B80),
    accent = Color(0xFFFF7A2E),
    accentHover = Color(0xFFFF9152),
    onAccent = Color(0xFF1A0D04),
    accentSoft = Color(0x24FF7A2E),
    danger = Color(0xFFFF6B6B),
    onDanger = Color(0xFF1A0D04),
    ok = Color(0xFF4CC38A),
    scrim = Color(0x8C000000),
)

val LightColors = RengaineColors(
    isDark = false,
    bg = Color(0xFFF8F4EE),
    surface = Color(0xFFFFFDF9),
    raised = Color(0xFFEFE8DF),
    hover = Color(0xFFE8E0D5),
    line = Color(0xFFE0D6CA),
    text = Color(0xFF1F1A16),
    muted = Color(0xFF5C524A),
    faint = Color(0xFF6B6058),
    accent = Color(0xFFA64200),
    accentHover = Color(0xFF8F3900),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0x1AA64200),
    danger = Color(0xFFC0392B),
    onDanger = Color(0xFFFFFFFF),
    ok = Color(0xFF17603C),
    scrim = Color(0x5928190A),
)

val LocalRengaineColors = staticCompositionLocalOf { DarkColors }
