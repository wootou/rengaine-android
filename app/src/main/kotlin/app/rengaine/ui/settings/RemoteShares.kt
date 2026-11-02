package app.rengaine.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import app.rengaine.R
import app.rengaine.data.remote.ApiError
import app.rengaine.data.remote.ApiResult
import app.rengaine.data.remote.dto.QueueShareDto
import app.rengaine.data.remote.dto.QueueShareRightsDto
import app.rengaine.data.repository.RemoteRepository
import app.rengaine.data.repository.ServerFeatures
import app.rengaine.playback.RemoteControl
import app.rengaine.ui.components.ButtonKind
import app.rengaine.ui.components.LinkButton
import app.rengaine.ui.components.RengaineButton
import app.rengaine.ui.components.RengaineTextField
import app.rengaine.ui.components.SelectOption
import app.rengaine.ui.components.SelectPill
import app.rengaine.ui.theme.Rengaine
import app.rengaine.ui.theme.RengaineShapes
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Lien tout juste créé : son adresse n'est montrée qu'une fois. */
data class CreatedLink(val id: Long, val label: String, val url: String)

data class RemoteSharesUiState(
    val label: String = "",
    val hours: Int = 6,
    val rights: QueueShareRightsDto = QueueShareRightsDto(add = true, edit = true, control = true),
    val creating: Boolean = false,
    val error: String? = null,
    val created: CreatedLink? = null,
    val shares: List<QueueShareDto> = emptyList(),
    /** Message après « Copier le lien » ou « Révoquer ». */
    val message: String? = null,
)

/** Réglages › Télécommande partagée, comme `RemoteShares.jsx` du site (FUT-02). */
@HiltViewModel
class RemoteSharesViewModel @Inject constructor(
    private val repository: RemoteRepository,
    private val remote: RemoteControl,
    server: ServerFeatures,
) : ViewModel() {

    /** La section n'existe que si le serveur a un hub temps réel. */
    val available: StateFlow<Boolean> = server.features.map { it?.remoteControl == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _state = MutableStateFlow(RemoteSharesUiState())
    val state: StateFlow<RemoteSharesUiState> = _state.asStateFlow()

    fun refresh() = viewModelScope.launch {
        (repository.shares() as? ApiResult.Ok)?.let { r -> _state.update { it.copy(shares = r.value) } }
    }

    fun setLabel(value: String) = _state.update { it.copy(label = value.take(100)) }
    fun setHours(value: Int) = _state.update { it.copy(hours = value) }
    fun setRights(value: QueueShareRightsDto) = _state.update { it.copy(rights = value) }
    fun showMessage(value: String?) = _state.update { it.copy(message = value) }

    fun create(fallbackError: String) {
        val s = _state.value
        if (s.creating) return
        _state.update { it.copy(creating = true, error = null, message = null) }
        viewModelScope.launch {
            when (val r = repository.create(s.label, s.hours, s.rights)) {
                is ApiResult.Ok -> {
                    _state.update { it.copy(creating = false, created = CreatedLink(r.value.id, r.value.label, r.value.url)) }
                    remote.setActive(true)
                    refresh()
                }
                is ApiResult.Err -> _state.update {
                    it.copy(creating = false, error = (r.error as? ApiError.Http)?.message?.takeIf { m -> m.isNotBlank() } ?: fallbackError)
                }
            }
        }
    }

    fun revoke(share: QueueShareDto, revokedMessage: String) = viewModelScope.launch {
        if (repository.revoke(share.id) is ApiResult.Ok) {
            _state.update {
                it.copy(
                    message = revokedMessage,
                    created = it.created?.takeIf { c -> c.id != share.id },
                    shares = it.shares.filter { s -> s.id != share.id },
                )
            }
            remote.setActive(_state.value.shares.isNotEmpty())
        }
        refresh()
    }
}

private val HOURS = listOf(1, 3, 6, 24, 72, 168)

/** Section « Télécommande partagée » des Réglages ; rien si le serveur n'en a pas. */
@Composable
internal fun RemoteSharesSection(viewModel: RemoteSharesViewModel = hiltViewModel()) {
    val available by viewModel.available.collectAsStateWithLifecycle()
    if (!available) return
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }
    val colors = Rengaine.colors
    val context = LocalContext.current
    val guest = stringResource(R.string.remote_guest)

    Section(stringResource(R.string.remote_title)) {
        Help(stringResource(R.string.remote_help))
        RengaineTextField(
            value = state.label,
            onValueChange = viewModel::setLabel,
            label = stringResource(R.string.remote_for_whom),
            placeholder = guest,
            modifier = Modifier.fillMaxWidth(),
        )
        FieldLabel(stringResource(R.string.remote_valid))
        SelectPill(
            options = buildList { HOURS.forEach { add(SelectOption(it, hoursLabel(it))) } },
            selected = state.hours,
            onSelect = viewModel::setHours,
            label = stringResource(R.string.remote_valid),
            field = true,
            modifier = Modifier.fillMaxWidth(),
        )
        FieldLabel(stringResource(R.string.remote_guest_can))
        Column {
            ToggleRow(stringResource(R.string.remote_right_add), state.rights.add, true) { viewModel.setRights(state.rights.copy(add = it)) }
            ToggleRow(stringResource(R.string.remote_right_edit), state.rights.edit, true) { viewModel.setRights(state.rights.copy(edit = it)) }
            ToggleRow(stringResource(R.string.remote_right_control), state.rights.control, true) { viewModel.setRights(state.rights.copy(control = it)) }
        }
        val failed = stringResource(R.string.remote_create_failed)
        RengaineButton(
            text = stringResource(R.string.remote_create),
            onClick = { viewModel.create(failed) },
            kind = ButtonKind.Secondary,
            loading = state.creating,
        )
        state.error?.let { Text(it, style = Rengaine.type.secondary, color = colors.danger) }

        state.created?.let { link -> CreatedLinkCard(link.copy(label = link.label.ifBlank { guest }), context, viewModel) }

        state.message?.let {
            Text(
                it,
                style = Rengaine.type.secondary,
                color = colors.muted,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        if (state.shares.isNotEmpty()) {
            Column {
                state.shares.forEach { share ->
                    HorizontalDivider(color = colors.line)
                    ShareRow(share, viewModel)
                }
            }
        }
    }
}

@Composable
private fun CreatedLinkCard(link: CreatedLink, context: Context, viewModel: RemoteSharesViewModel) {
    val colors = Rengaine.colors
    val copied = stringResource(R.string.remote_copied)
    val copyFailed = stringResource(R.string.remote_copy_failed)
    val shareTitle = stringResource(R.string.remote_share_title)
    val shareText = stringResource(R.string.remote_share_text)
    val sendLabel = stringResource(R.string.remote_send)
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RengaineShapes.card)
            .padding(16.dp),
    ) {
        Text(stringResource(R.string.remote_link_for, link.label), style = Rengaine.type.bodyStrong, color = colors.text)
        SelectionContainer {
            Text(link.url, style = Rengaine.type.secondary, color = colors.text)
        }
        // QR en plus du site : l'invité le scanne sur l'écran de ce téléphone.
        val qr = remember(link.url) { qrBitmap(link.url) }
        Image(
            bitmap = qr,
            contentDescription = stringResource(R.string.remote_qr, link.label),
            filterQuality = FilterQuality.None,
            modifier = Modifier.align(Alignment.CenterHorizontally).size(220.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RengaineButton(
                text = stringResource(R.string.remote_copy),
                onClick = {
                    val ok = runCatching {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText(shareTitle, link.url))
                    }.isSuccess
                    viewModel.showMessage(if (ok) copied else copyFailed)
                },
                kind = ButtonKind.Secondary,
                icon = Icons.Filled.ContentCopy,
            )
            RengaineButton(
                text = sendLabel,
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, shareTitle)
                        putExtra(Intent.EXTRA_TITLE, shareTitle)
                        putExtra(Intent.EXTRA_TEXT, "$shareText ${link.url}")
                    }
                    runCatching { context.startActivity(Intent.createChooser(send, shareTitle)) }
                },
                kind = ButtonKind.Secondary,
                icon = Icons.Filled.Share,
            )
        }
    }
}

@Composable
private fun ShareRow(share: QueueShareDto, viewModel: RemoteSharesViewModel) {
    val colors = Rengaine.colors
    val name = share.label.ifBlank { stringResource(R.string.remote_guest) }
    val rights = listOfNotNull(
        stringResource(R.string.remote_list_add).takeIf { share.rights.add },
        stringResource(R.string.remote_list_edit).takeIf { share.rights.edit },
        stringResource(R.string.remote_list_control).takeIf { share.rights.control },
    ).joinToString(" · ").ifEmpty { stringResource(R.string.remote_view_only) }
    val details = listOf(
        rights,
        stringResource(R.string.remote_until, formatDate(share.expiresAt)),
        stringResource(if (share.lastUsedAt != null) R.string.remote_opened else R.string.remote_not_opened),
    ).joinToString(" · ")
    val revoked = stringResource(R.string.remote_revoked, name)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = Rengaine.type.bodyStrong, color = colors.text)
            Text(details, style = Rengaine.type.small, color = colors.muted)
        }
        val revokeLabel = stringResource(R.string.remote_revoke_label, name)
        LinkButton(
            text = stringResource(R.string.remote_revoke),
            onClick = { viewModel.revoke(share, revoked) },
            modifier = Modifier.semantics { contentDescription = revokeLabel },
        )
    }
}

@Composable
private fun hoursLabel(hours: Int): String = stringResource(
    when (hours) {
        1 -> R.string.remote_hours_1
        3 -> R.string.remote_hours_3
        6 -> R.string.remote_hours_6
        24 -> R.string.remote_hours_24
        72 -> R.string.remote_hours_72
        else -> R.string.remote_hours_168
    },
)

/** Jour, mois court, heure et minute, à la façon du téléphone. */
private fun formatDate(iso: String): String = runCatching {
    val locale = Locale.getDefault()
    val pattern = DateFormat.getBestDateTimePattern(locale, "dMMMHHmm")
    java.time.format.DateTimeFormatter.ofPattern(pattern, locale)
        .format(Instant.parse(iso).atZone(ZoneId.systemDefault()))
}.getOrDefault(iso)
