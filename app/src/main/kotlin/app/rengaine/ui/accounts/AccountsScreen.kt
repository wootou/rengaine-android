package app.rengaine.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rengaine.R
import app.rengaine.domain.Account
import app.rengaine.ui.components.ButtonKind
import app.rengaine.ui.components.RengaineButton
import app.rengaine.ui.theme.Rengaine
import app.rengaine.ui.theme.RengaineShapes

/**
 * Comptes mémorisés (F1) : un actif, les autres en attente. Toucher un
 * compte le rend actif ; un compte dont le jeton a été refusé est marqué
 * « À reconnecter ». Dessin provisoire, à reprendre avec la maquette (U8).
 */
@Composable
fun AccountsScreen(
    onBack: () -> Unit,
    onAddAccount: () -> Unit,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = Rengaine.colors
    var confirmSignOut by remember { mutableStateOf<Account?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .safeDrawingPadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), tint = colors.text)
            }
            Text(
                stringResource(R.string.accounts_title),
                style = Rengaine.type.pageTitle,
                color = colors.text,
                modifier = Modifier.semantics { heading() },
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(state.accounts, key = { it.id }) { account ->
                AccountRow(
                    account = account,
                    active = account.id == state.activeId,
                    onSelect = { viewModel.switchTo(account) },
                    onSignOut = { confirmSignOut = account },
                )
            }
            item {
                RengaineButton(
                    text = stringResource(R.string.accounts_add),
                    onClick = onAddAccount,
                    kind = ButtonKind.Secondary,
                    icon = Icons.Filled.Add,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        }
    }

    confirmSignOut?.let { account ->
        SignOutDialog(
            account = account,
            onConfirm = {
                viewModel.signOut(account)
                confirmSignOut = null
            },
            onDismiss = { confirmSignOut = null },
        )
    }
}

/** Confirmation de déconnexion, aussi proposée dans Réglages › Compte. */
@Composable
fun SignOutDialog(account: Account, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = Rengaine.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RengaineShapes.dialog,
        containerColor = colors.surface,
        title = {
            Text(
                stringResource(R.string.accounts_sign_out_title, account.serverName.ifBlank { account.address.host }),
                style = Rengaine.type.sectionTitle,
                color = colors.text,
            )
        },
        text = { Text(stringResource(R.string.accounts_sign_out_text), style = Rengaine.type.body, color = colors.muted) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.accounts_sign_out), color = colors.danger, style = Rengaine.type.button)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = colors.muted, style = Rengaine.type.button)
            }
        },
    )
}

@Composable
private fun AccountRow(account: Account, active: Boolean, onSelect: () -> Unit, onSignOut: () -> Unit) {
    val colors = Rengaine.colors
    val type = Rengaine.type
    val activeLabel = stringResource(R.string.accounts_active)
    val reauthLabel = stringResource(R.string.accounts_needs_reauth)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (active) colors.accentSoft else colors.surface, RengaineShapes.card)
            .border(1.dp, if (active) colors.accent else colors.line, RengaineShapes.card)
            .clickable(onClick = onSelect)
            .semantics(mergeDescendants = true) {
                selected = active
                stateDescription = when {
                    account.needsReauth -> reauthLabel
                    active -> activeLabel
                    else -> ""
                }
            }
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                account.displayName.ifBlank { account.email },
                style = type.cardTitle,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(account.email, style = type.secondary, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.account_server_line, account.serverName.ifBlank { account.address.host }, account.address.display),
                style = type.secondary,
                color = colors.faint,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                when {
                    account.needsReauth -> {
                        Icon(Icons.Filled.ErrorOutline, null, tint = colors.danger, modifier = Modifier.size(14.dp))
                        Text(reauthLabel, style = type.small, color = colors.danger)
                    }
                    active -> {
                        Icon(Icons.Filled.CheckCircle, null, tint = colors.accent, modifier = Modifier.size(14.dp))
                        Text(activeLabel, style = type.small, color = colors.accent)
                    }
                }
            }
        }
        Box {
            IconButton(onClick = onSignOut) {
                Icon(
                    Icons.AutoMirrored.Filled.Logout,
                    contentDescription = stringResource(R.string.accounts_sign_out),
                    tint = colors.muted,
                )
            }
        }
    }
}
