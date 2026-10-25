package app.rengaine.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.rengaine.R
import app.rengaine.ui.components.SelectOption
import app.rengaine.ui.components.SelectPill
import app.rengaine.ui.theme.Rengaine
import kotlin.math.roundToInt

/**
 * Réglages › Volume, comme `Settings.jsx` du site : normalisation, volume
 * visé (−23 à −8 LUFS, par pas de 1), éviter la saturation. Appliqués par
 * `VolumeNormalizer` dès qu'ils changent.
 */
@Composable
internal fun VolumeSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    Section(stringResource(R.string.settings_volume), first = true) {
        FieldLabel(stringResource(R.string.settings_normalization))
        SelectPill(
            options = listOf(
                SelectOption("auto", stringResource(R.string.normalization_auto)),
                SelectOption("track", stringResource(R.string.normalization_track)),
                SelectOption("album", stringResource(R.string.normalization_album)),
                SelectOption("off", stringResource(R.string.normalization_off)),
            ),
            selected = state.normalization,
            onSelect = viewModel::setNormalization,
            label = stringResource(R.string.settings_normalization),
            field = true,
            modifier = Modifier.fillMaxWidth(),
        )
        val off = state.normalization == "off"
        Column(Modifier.alpha(if (off) DISABLED_ALPHA else 1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Valeur suivie pendant le glissement, enregistrée au lâcher : une
            // seule écriture du réglage du compte, pas une par cran.
            var dragging by remember { mutableFloatStateOf(Float.NaN) }
            val shown = if (dragging.isNaN()) state.targetLufs else dragging.roundToInt()
            val label = stringResource(R.string.settings_target_lufs, shown)
            FieldLabel(label)
            Slider(
                value = shown.toFloat(),
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    if (!dragging.isNaN()) viewModel.setTargetLufs(dragging.roundToInt())
                    dragging = Float.NaN
                },
                valueRange = -23f..-8f,
                steps = 14,
                enabled = state.loaded && !off,
                colors = SliderDefaults.colors(
                    thumbColor = Rengaine.colors.accent,
                    activeTrackColor = Rengaine.colors.accent,
                    inactiveTrackColor = Rengaine.colors.line,
                    activeTickColor = Rengaine.colors.accent,
                    inactiveTickColor = Rengaine.colors.line,
                ),
                modifier = Modifier.semantics {
                    contentDescription = label
                    stateDescription = label
                },
            )
            Help(stringResource(R.string.settings_target_lufs_help))
            CheckRow(
                text = stringResource(R.string.settings_prevent_clipping),
                checked = state.preventClipping,
                enabled = state.loaded && !off,
                onChange = viewModel::setPreventClipping,
            )
        }
    }
}

/**
 * Bloc de la lecture continue, sous le mode infini (section Enchaînement).
 * Elle ne sert qu'à compléter le mode infini : grisée tant qu'il est coupé,
 * comme sur le site. Chaque règle n'apparaît que si l'analyse du serveur la
 * permet.
 */
@Composable
internal fun ContinuousBlock(state: SettingsUiState, viewModel: SettingsViewModel) {
    val rules = state.continuous
    val active = state.loaded && state.infiniteEnabled
    Column(
        Modifier.alpha(if (state.infiniteEnabled) 1f else DISABLED_ALPHA),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CheckRow(
            text = stringResource(R.string.settings_continuous),
            checked = rules.enabled,
            enabled = active,
            onChange = viewModel::setContinuous,
        )
        if (!rules.enabled) return@Column
        if (rules.keyAvailable) {
            CheckRow(
                text = stringResource(R.string.settings_harmonic),
                checked = rules.harmonicMixing,
                enabled = active,
                onChange = viewModel::setHarmonicMixing,
            )
        }
        if (rules.bpmAvailable) {
            Choice(
                label = stringResource(R.string.settings_bpm_tolerance),
                options = listOf(
                    SelectOption(null, stringResource(R.string.bpm_tolerance_none)),
                    SelectOption(4, stringResource(R.string.bpm_tolerance_4)),
                    SelectOption(8, stringResource(R.string.bpm_tolerance_8)),
                    SelectOption(15, stringResource(R.string.bpm_tolerance_15)),
                ),
                selected = rules.bpmTolerancePercent,
                enabled = active,
                onSelect = viewModel::setBpmTolerance,
            )
        }
        if (rules.energyAvailable) {
            Choice(
                label = stringResource(R.string.settings_energy),
                options = listOf(
                    SelectOption("any", stringResource(R.string.energy_any)),
                    SelectOption("steady", stringResource(R.string.energy_steady)),
                    SelectOption("rising", stringResource(R.string.energy_rising)),
                    SelectOption("falling", stringResource(R.string.energy_falling)),
                ),
                selected = rules.energyTrend,
                enabled = active,
                onSelect = viewModel::setEnergyTrend,
            )
        }
        Choice(
            label = stringResource(R.string.settings_favorites_weight),
            options = FAVORITES_WEIGHTS.map { (w, res) -> SelectOption(w, stringResource(res)) },
            // Valeur réglée ailleurs hors des quatre crans : la plus proche.
            selected = FAVORITES_WEIGHTS.minBy { (w, _) -> kotlin.math.abs(w - rules.favoritesWeight) }.first,
            enabled = active,
            onSelect = viewModel::setFavoritesWeight,
        )
        Choice(
            label = stringResource(R.string.settings_no_repeat),
            options = (NO_REPEAT_HOURS.keys + rules.noRepeatHours).distinct().sorted().map { SelectOption(it, noRepeatLabel(it)) },
            selected = rules.noRepeatHours,
            enabled = active,
            onSelect = viewModel::setNoRepeatHours,
        )
        Choice(
            label = stringResource(R.string.settings_artist_spacing),
            options = (ARTIST_SPACINGS + rules.artistSpacing).distinct().sorted().map {
                SelectOption(it, if (it == 0) stringResource(R.string.artist_spacing_none) else pluralStringResource(R.plurals.artist_spacing_tracks, it, it))
            },
            selected = rules.artistSpacing,
            enabled = active,
            onSelect = viewModel::setArtistSpacing,
        )
        ExcludedGenres(rules.excludedGenres, active, viewModel)
        Help(stringResource(R.string.settings_continuous_help))
    }
}

/**
 * « Genres à ne jamais proposer : … » : bloc replié qui résume le choix ;
 * déplié, une case par genre. Les genres ne sont chargés qu'à l'ouverture.
 */
@Composable
private fun ExcludedGenres(excluded: Set<Long>, enabled: Boolean, viewModel: SettingsViewModel) {
    val colors = Rengaine.colors
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(excluded.isNotEmpty() || open) { if (excluded.isNotEmpty() || open) viewModel.loadGenres() }
    val names = genres?.filter { it.id in excluded }?.joinToString { it.name }
    val summary = when {
        excluded.isEmpty() -> stringResource(R.string.excluded_genres_none)
        names.isNullOrEmpty() -> "…"
        else -> names
    }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(enabled = enabled, onClickLabel = stringResource(R.string.settings_excluded_genres)) { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (open) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.muted,
            )
            Text(
                stringResource(R.string.settings_excluded_genres) + " " + summary,
                style = Rengaine.type.body,
                color = colors.text,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
        }
        if (open) {
            val list = genres
            if (list == null) {
                Help("…")
            } else {
                list.forEach { genre ->
                    CheckRow(
                        text = genre.name,
                        checked = genre.id in excluded,
                        enabled = enabled,
                        onChange = { viewModel.toggleExcludedGenre(genre.id) },
                    )
                }
            }
        }
    }
}

/** Libellés du site ; une valeur réglée ailleurs s'affiche en heures. */
@Composable
private fun noRepeatLabel(hours: Int): String =
    NO_REPEAT_HOURS[hours]?.let { stringResource(it) } ?: pluralStringResource(R.plurals.no_repeat_hours, hours, hours)

@Composable
private fun <T> Choice(label: String, options: List<SelectOption<T>>, selected: T, enabled: Boolean, onSelect: (T) -> Unit) {
    FieldLabel(label)
    SelectPill(
        options = options,
        selected = selected,
        onSelect = { if (enabled) onSelect(it) },
        label = label,
        field = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Case à cocher à gauche, libellé à droite ; toute la ligne se touche. */
@Composable
private fun CheckRow(text: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val colors = Rengaine.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = CheckboxDefaults.colors(checkedColor = colors.accent, uncheckedColor = colors.muted, checkmarkColor = colors.onAccent),
        )
        Text(text, style = Rengaine.type.body, color = colors.text, modifier = Modifier.weight(1f).padding(start = 12.dp))
    }
}

private const val DISABLED_ALPHA = 0.5f

private val FAVORITES_WEIGHTS = listOf(
    0.0 to R.string.favorites_weight_0,
    0.3 to R.string.favorites_weight_03,
    0.6 to R.string.favorites_weight_06,
    1.0 to R.string.favorites_weight_1,
)

private val NO_REPEAT_HOURS = mapOf(
    0 to R.string.no_repeat_off,
    6 to R.string.no_repeat_6h,
    24 to R.string.no_repeat_24h,
    72 to R.string.no_repeat_3d,
    168 to R.string.no_repeat_1w,
    720 to R.string.no_repeat_30d,
)

private val ARTIST_SPACINGS = listOf(0, 2, 3, 5, 10)
