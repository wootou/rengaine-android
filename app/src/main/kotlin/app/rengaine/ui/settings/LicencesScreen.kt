package app.rengaine.ui.settings

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.rengaine.R
import app.rengaine.ui.theme.Rengaine
import app.rengaine.ui.theme.RengaineShapes
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Licences des bibliothèques et de la police (Réglages › À propos).
 * La liste vient de `res/raw/aboutlibraries.json`, produit par
 * AboutLibraries à partir des dépendances de la release, avec le texte
 * complet de chaque licence (lisible sans réseau).
 */
@Composable
fun LicencesScreen(onBack: () -> Unit, contentPadding: PaddingValues) {
    val colors = Rengaine.colors
    val context = LocalContext.current
    val libraries by produceState<List<Library>?>(null) { value = withContext(Dispatchers.IO) { loadLibraries(context) } }
    var opened by remember { mutableStateOf<Library?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding() + 16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header") {
            Column(Modifier.padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), tint = colors.text)
                    }
                    Text(
                        stringResource(R.string.licences_title),
                        style = Rengaine.type.pageTitle,
                        color = colors.text,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                }
                Text(
                    stringResource(R.string.licences_intro),
                    style = Rengaine.type.secondary,
                    color = colors.muted,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
        items(libraries.orEmpty(), key = { it.uniqueId }) { library ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.licences_read)) { opened = library }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    listOfNotNull(library.name, library.artifactVersion?.takeIf { it.isNotBlank() }).joinToString(" "),
                    style = Rengaine.type.bodyStrong,
                    color = colors.text,
                )
                Text(
                    listOfNotNull(author(library), library.licenses.joinToString { it.name }).joinToString(" · "),
                    style = Rengaine.type.secondary,
                    color = colors.muted,
                )
            }
        }
    }

    opened?.let { library -> LicenceDialog(library, onDismiss = { opened = null }) }
}

@Composable
private fun LicenceDialog(library: Library, onDismiss: () -> Unit) {
    val colors = Rengaine.colors
    val uriHandler = LocalUriHandler.current
    val website = library.website?.takeIf { it.startsWith("https://") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RengaineShapes.dialog,
        containerColor = colors.surface,
        title = { Text(library.name, style = Rengaine.type.sectionTitle, color = colors.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                author(library)?.let { Text(it, style = Rengaine.type.secondary, color = colors.muted) }
                for (licence in library.licenses) {
                    Text(licence.name, style = Rengaine.type.bodyStrong, color = colors.text)
                    Text(licence.licenseContent?.takeIf { it.isNotBlank() } ?: licence.url.orEmpty(), style = Rengaine.type.small, color = colors.text)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close), style = Rengaine.type.button, color = colors.text) }
        },
        dismissButton = website?.let {
            {
                TextButton(onClick = { uriHandler.openUri(it) }) {
                    Text(stringResource(R.string.licences_website), style = Rengaine.type.button, color = colors.text)
                }
            }
        },
    )
}

private fun author(library: Library): String? =
    library.organization?.name?.takeIf { it.isNotBlank() }
        ?: library.developers.mapNotNull { it.name?.takeIf { n -> n.isNotBlank() } }.joinToString().takeIf { it.isNotBlank() }

private fun loadLibraries(context: Context): List<Library> {
    val json = context.resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() }
    return Libs.Builder().withJson(json).build().libraries.sortedBy { it.name.lowercase() }
}
