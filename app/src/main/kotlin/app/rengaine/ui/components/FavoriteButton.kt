package app.rengaine.ui.components

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.rengaine.R
import app.rengaine.data.repository.FavoriteKey
import app.rengaine.data.repository.FavoritesRepository
import app.rengaine.ui.theme.Rengaine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

@HiltViewModel
class FavoriteButtonViewModel @Inject constructor(
    private val favorites: FavoritesRepository,
) : ViewModel() {
    val known = favorites.known
    private val _failed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val failed = _failed.asSharedFlow()

    fun toggle(key: FavoriteKey, current: Boolean) {
        val pending = favorites.setAsync(key, !current)
        viewModelScope.launch { if (!pending.await()) _failed.emit(Unit) }
    }
}

/**
 * Cœur (F9, U6) : contour quand ce n'est pas un favori, plein et braise
 * quand c'en est un, comme sur le site. [fromServer] est la valeur
 * `favorite` reçue ; un changement fait dans l'app la remplace partout.
 */
@Composable
fun FavoriteButton(
    key: FavoriteKey,
    fromServer: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = Rengaine.colors.text,
    viewModel: FavoriteButtonViewModel = hiltViewModel(),
) {
    val known by viewModel.known.collectAsStateWithLifecycle()
    val favorite = known[key] ?: fromServer
    val context = LocalContext.current
    val failedText = stringResource(R.string.favorite_not_saved)
    LaunchedEffect(viewModel) { viewModel.failed.collect { Toast.makeText(context, failedText, Toast.LENGTH_SHORT).show() } }

    IconToggleButton(checked = favorite, onCheckedChange = { viewModel.toggle(key, favorite) }, modifier = modifier) {
        Icon(
            if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = stringResource(if (favorite) R.string.favorite_remove else R.string.favorite_add),
            tint = if (favorite) Rengaine.colors.accent else tint,
        )
    }
}
