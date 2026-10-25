package app.rengaine.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.rengaine.R

/**
 * Bricolage Grotesque (OFL, `licences/`), fonte variable : axes `wght`
 * (200–800) et `opsz` (12–96), largeur figée à 100. Réservée aux titres et
 * au logotype (U2) ; tout le reste est en police système.
 *
 * Le navigateur règle l'axe `opsz` sur la taille du texte
 * (`font-optical-sizing: auto`) ; Compose ne le fait pas : chaque famille
 * fixe donc le sien, proche de la taille où elle sert.
 */
@OptIn(ExperimentalTextApi::class)
private fun bricolage(opticalSize: Float): FontFamily = FontFamily(
    listOf(500, 600, 700, 800).map { weight ->
        Font(
            resId = R.font.bricolage_grotesque,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight),
                FontVariation.Setting("opsz", opticalSize),
            ),
        )
    },
)

/** Grands titres : logotype de connexion, titres de page. */
val BricolageDisplay: FontFamily = bricolage(opticalSize = 32f)

/** Titres moyens : sections, logotype de l'en-tête. */
val BricolageTitle: FontFamily = bricolage(opticalSize = 20f)

private val tabular = "tnum"

/** Échelle typographique du téléphone (U2). Tailles en `sp` : tout doit tenir à 200 %. */
@Immutable
data class RengaineTypography(
    /** « rengaine » de l'écran de connexion (2,1 rem sur le site). */
    val logotypeLarge: TextStyle,
    /** « rengaine » de l'en-tête. */
    val logotype: TextStyle,
    /** « Albums », « Réglages ». */
    val pageTitle: TextStyle,
    /** « Mes playlists », « Volume ». */
    val sectionTitle: TextStyle,
    val cardTitle: TextStyle,
    /** Texte courant : 15 / 1,45. */
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val secondary: TextStyle,
    val small: TextStyle,
    /** Sur-titre : « COMPILATION », « EN LECTURE ». À écrire en majuscules. */
    val overline: TextStyle,
    /** Étiquette au-dessus d'un champ (0,85 rem, 600). */
    val fieldLabel: TextStyle,
    /** Texte saisi dans un champ (1 rem). */
    val field: TextStyle,
    val button: TextStyle,
    val buttonLarge: TextStyle,
    /** Durées et compteurs : chiffres tabulaires. */
    val numeric: TextStyle,
)

private val trimNone = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

val DefaultTypography = RengaineTypography(
    logotypeLarge = TextStyle(
        fontFamily = BricolageDisplay,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 37.sp,
        letterSpacing = (-0.03).em,
    ),
    logotype = TextStyle(
        fontFamily = BricolageTitle,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.03).em,
    ),
    pageTitle = TextStyle(
        fontFamily = BricolageDisplay,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 33.sp,
        letterSpacing = (-0.03).em,
    ),
    sectionTitle = TextStyle(
        fontFamily = BricolageTitle,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.02).em,
    ),
    cardTitle = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    body = TextStyle(fontSize = 15.sp, lineHeight = 21.75.sp, lineHeightStyle = trimNone),
    bodyStrong = TextStyle(fontSize = 15.sp, lineHeight = 21.75.sp, fontWeight = FontWeight.SemiBold),
    secondary = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    small = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    overline = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.12.em,
    ),
    fieldLabel = TextStyle(fontSize = 13.6.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    field = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    button = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    buttonLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    numeric = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = tabular),
)

val LocalRengaineTypography = staticCompositionLocalOf { DefaultTypography }
