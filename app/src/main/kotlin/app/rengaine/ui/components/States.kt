package app.rengaine.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.rengaine.R
import app.rengaine.ui.theme.Rengaine

/** Chargement en cours, centré. */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Rengaine.colors.accent, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
    }
}

/** Message centré, avec une action facultative (« Réessayer »). */
@Composable
fun MessageState(
    text: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text, style = Rengaine.type.body, color = Rengaine.colors.muted, textAlign = TextAlign.Center)
        if (actionText != null && onAction != null) {
            RengaineButton(text = actionText, onClick = onAction, kind = ButtonKind.Secondary)
        }
    }
}

/** Erreur de chargement avec « Réessayer ». */
@Composable
fun ErrorState(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    MessageState(
        text = stringResource(R.string.state_load_failed),
        actionText = stringResource(R.string.action_retry),
        onAction = onRetry,
        modifier = modifier,
    )
}
