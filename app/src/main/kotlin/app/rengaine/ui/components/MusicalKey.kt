package app.rengaine.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import app.rengaine.R

/**
 * Tonalité lisible, dans la langue de l'app : « Am » → « La mineur »
 * (« A minor » en anglais), « F# » → « Fa♯ majeur ». Le contrat donne la
 * notation anglaise ; `null` si elle n'est pas reconnue.
 */
@Composable
fun musicalKeyLabel(key: String?): String? {
    val match = key?.trim()?.let { KEY.matchEntire(it) } ?: return null
    val (letter, accidental, minor) = match.destructured
    val notes = stringArrayResource(R.array.note_names)
    val note = notes.getOrNull("CDEFGAB".indexOf(letter.uppercase())) ?: return null
    val sign = when (accidental) {
        "#", "♯" -> "♯"
        "b", "♭" -> "♭"
        else -> ""
    }
    val isMinor = minor.isNotEmpty()
    return stringResource(if (isMinor) R.string.key_minor else R.string.key_major, note + sign)
}

private val KEY = Regex("^([A-Ga-g])([#♯b♭]?)(m|min|minor)?$")
