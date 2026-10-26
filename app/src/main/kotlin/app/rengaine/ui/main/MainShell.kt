package app.rengaine.ui.main

import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalFocusManager
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rengaine.ui.history.HistoryScreen
import app.rengaine.ui.random.RandomScreen
import app.rengaine.ui.search.SearchBoxViewModel
import app.rengaine.ui.search.SearchField
import app.rengaine.ui.search.SearchScreen
import app.rengaine.ui.search.SearchSuggestions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.rengaine.R
import app.rengaine.ui.components.LogoMark
import app.rengaine.ui.library.AlbumScreen
import app.rengaine.ui.library.AlbumsScreen
import app.rengaine.ui.library.ArtistScreen
import app.rengaine.ui.library.ArtistsScreen
import app.rengaine.ui.library.ExploreScreen
import androidx.navigation.toRoute
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.rengaine.ui.player.MiniPlayer
import app.rengaine.ui.playlists.FavoritesScreen
import app.rengaine.ui.playlists.PlaylistScreen
import app.rengaine.ui.playlists.PlaylistsScreen
import app.rengaine.ui.settings.LicencesScreen
import app.rengaine.ui.settings.SettingsScreen
import app.rengaine.ui.theme.Rengaine
import kotlin.reflect.KClass
import kotlinx.serialization.Serializable

@Serializable data object AlbumsTab
@Serializable data object ArtistsTab
@Serializable data object ExploreTab
@Serializable data object RandomTab
@Serializable data object PlaylistsTab

@Serializable
data class AlbumRoute(val id: Long)

@Serializable
data class PlaylistRoute(val id: Long)

@Serializable
data object FavoritesRoute

@Serializable
data class ArtistRoute(val id: Long)

/** Tous les résultats d'une recherche (F3) : Entrée ou « Tous les résultats ». */
@Serializable
data class SearchRoute(val query: String)

/** Historique d'écoute (2.26.0), depuis Mes favoris et Réglages › Compte. */
@Serializable
data object HistoryRoute

/** Réglages (F12), ouverts par la roue de l'en-tête. */
@Serializable
data object SettingsRoute

/** Licences open source, depuis Réglages › À propos. */
@Serializable
data object LicencesRoute

/** Albums d'un genre, d'une décennie ou d'une collection (depuis Explorer) ; -1 = sans. */
@Serializable
data class AlbumListRoute(val title: String, val genreId: Long = -1, val decade: Int = -1, val collectionId: Long = -1)

/** Les cinq onglets, dans l'ordre choisi pour l'app. */
private enum class Tab(val route: Any, val routeClass: KClass<*>, val label: Int, val icon: ImageVector) {
    ALBUMS(AlbumsTab, AlbumsTab::class, R.string.tab_albums, Icons.Filled.GridView),
    ARTISTS(ArtistsTab, ArtistsTab::class, R.string.tab_artists, Icons.Filled.Person),
    EXPLORE(ExploreTab, ExploreTab::class, R.string.tab_explore, Icons.Filled.Sell),
    RANDOM(RandomTab, RandomTab::class, R.string.tab_random, Icons.Filled.Shuffle),
    PLAYLISTS(PlaylistsTab, PlaylistsTab::class, R.string.tab_playlists, Icons.AutoMirrored.Filled.QueueMusic),
}

/**
 * Cadre de l'app connectée (captures `05`, `07`, `11`) : en-tête, contenu,
 * mini-lecteur flottant au-dessus de la barre d'onglets. Chaque onglet garde
 * son historique et sa position quand on passe de l'un à l'autre.
 *
 * L'en-tête porte la recherche (F3) comme le site : pendant la frappe, une
 * liste déroulante couvre le contenu ; choisir un résultat ou valider vide
 * le champ. La roue ouvre Réglages, d'où l'on gère les comptes.
 */
@Composable
fun MainShell(onOpenAccounts: () -> Unit, onOpenPlayer: () -> Unit, onOpenQueue: () -> Unit) {
    val nav = rememberNavController()
    val colors = Rengaine.colors
    val search: SearchBoxViewModel = hiltViewModel()
    val query by search.query.collectAsStateWithLifecycle()
    val suggestions by search.state.collectAsStateWithLifecycle()
    var searchFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val showSuggestions = searchFocused && query.isNotBlank()

    // Choisir un résultat ou valider : le champ se vide et se referme (site).
    fun closeSearch() {
        search.clear()
        focusManager.clearFocus()
    }
    fun openAll() {
        val q = query.trim()
        if (q.isEmpty()) return
        closeSearch()
        nav.navigate(SearchRoute(q))
    }
    // Retour avec la liste ouverte : on la ferme, le texte reste.
    BackHandler(enabled = searchFocused) { focusManager.clearFocus() }

    Scaffold(
        containerColor = colors.bg,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopBar(
                query = query,
                onQueryChange = search::setQuery,
                onSubmit = ::openAll,
                onFocusChange = { searchFocused = it },
                onOpenSettings = {
                    focusManager.clearFocus()
                    nav.navigate(SettingsRoute) { launchSingleTop = true }
                },
            )
        },
        bottomBar = {
            // Les touchers sur ce bloc s'arrêtent ici : sans cela, ceux qui
            // tombent entre deux boutons atteignent la liste dessous.
            Column(Modifier.background(colors.bg).blockTouchesBelow()) {
                MiniPlayer(
                    onOpenPlayer = onOpenPlayer,
                    onOpenQueue = onOpenQueue,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                )
                BottomTabs(nav)
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            TabsNavHost(nav, padding, onOpenAccounts)
            if (showSuggestions) {
                // Toucher à côté de la liste la ferme, sans vider le champ.
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(colors.scrim)
                        .clickable(interactionSource = null, indication = null) { focusManager.clearFocus() },
                )
                SearchSuggestions(
                    state = suggestions,
                    onArtist = {
                        closeSearch()
                        nav.navigate(ArtistRoute(it.id))
                    },
                    onAlbum = {
                        closeSearch()
                        nav.navigate(AlbumRoute(it.id))
                    },
                    onTrack = { tracks, track ->
                        search.play(tracks, track)
                        closeSearch()
                    },
                    onAll = ::openAll,
                    // Au-dessus du clavier s'il est ouvert, sinon au-dessus du mini-lecteur.
                    modifier = Modifier
                        .padding(top = padding.calculateTopPadding())
                        .windowInsetsPadding(WindowInsets.ime.union(WindowInsets(bottom = padding.calculateBottomPadding())))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
internal fun TopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = Rengaine.colors
    Column(Modifier.background(colors.bg).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(60.dp).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LogoMark(size = 36.dp)
            SearchField(
                query = query,
                onQueryChange = onQueryChange,
                onSubmit = onSubmit,
                onFocusChange = onFocusChange,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, stringResource(R.string.settings_title), tint = colors.accent)
            }
        }
        HorizontalDivider(color = colors.line)
    }
}

@Composable
internal fun BottomTabs(nav: NavHostController) {
    val colors = Rengaine.colors
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    // L'onglet allumé est le dernier ouvert : une page (album, artiste…)
    // garde celui d'où l'on vient.
    var lastTab by rememberSaveable { mutableStateOf(Tab.ALBUMS) }
    LaunchedEffect(destination) {
        Tab.entries.firstOrNull { tab -> destination?.hasRoute(tab.routeClass) == true }?.let { lastTab = it }
    }
    Column {
        HorizontalDivider(color = colors.line)
        NavigationBar(
            containerColor = colors.bg,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets(0),
            modifier = Modifier.navigationBarsPadding(),
        ) {
            Tab.entries.forEach { tab ->
                // L'album ouvert depuis l'onglet Albums garde « Albums » allumé.
                val selected = tab == lastTab
                NavigationBarItem(
                    selected = selected,
                    onClick = {
                        // L'onglet rouvert peut revenir sur une page (album…) :
                        // c'est lui qu'on allume, pas le dernier onglet vu.
                        lastTab = tab
                        nav.navigate(tab.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(tab.icon, contentDescription = null) },
                    // Texte agrandi (Q1) : le libellé rétrécit pour tenir en
                    // entier plutôt que d'être coupé en plein mot.
                    label = {
                        val style = Rengaine.type.small
                        Text(
                            stringResource(tab.label),
                            style = style,
                            maxLines = 1,
                            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = style.fontSize),
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.accent,
                        selectedTextColor = colors.accent,
                        unselectedIconColor = colors.muted,
                        unselectedTextColor = colors.muted,
                        indicatorColor = Color.Transparent,
                    ),
                )
            }
        }
    }
}

@Composable
private fun TabsNavHost(nav: NavHostController, padding: PaddingValues, onOpenAccounts: () -> Unit) {
    NavHost(
        navController = nav,
        startDestination = AlbumsTab,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable<AlbumsTab> {
            AlbumsScreen(onOpenAlbum = { nav.navigate(AlbumRoute(it)) }, contentPadding = padding)
        }
        composable<AlbumRoute> {
            AlbumScreen(onBack = { nav.popBackStack() }, contentPadding = padding)
        }
        composable<ArtistsTab> {
            ArtistsScreen(contentPadding = padding, onOpenArtist = { nav.navigate(ArtistRoute(it)) })
        }
        composable<ArtistRoute> {
            ArtistScreen(onBack = { nav.popBackStack() }, onOpenAlbum = { nav.navigate(AlbumRoute(it)) }, contentPadding = padding)
        }
        composable<ExploreTab> {
            ExploreScreen(
                contentPadding = padding,
                onBrowse = { title, genre, decade, collection ->
                    nav.navigate(AlbumListRoute(title, genre ?: -1, decade ?: -1, collection ?: -1))
                },
            )
        }
        composable<AlbumListRoute> { entry ->
            val route = entry.toRoute<AlbumListRoute>()
            AlbumsScreen(
                onOpenAlbum = { nav.navigate(AlbumRoute(it)) },
                contentPadding = padding,
                title = route.title,
                onBack = { nav.popBackStack() },
            )
        }
        composable<RandomTab> { RandomScreen(padding) }
        composable<PlaylistsTab> {
            PlaylistsScreen(
                contentPadding = padding,
                onOpenPlaylist = { nav.navigate(PlaylistRoute(it)) },
                onOpenFavorites = { nav.navigate(FavoritesRoute) },
            )
        }
        composable<PlaylistRoute> {
            PlaylistScreen(onBack = { nav.popBackStack() }, contentPadding = padding)
        }
        composable<SearchRoute> {
            SearchScreen(
                onBack = { nav.popBackStack() },
                onOpenArtist = { nav.navigate(ArtistRoute(it)) },
                onOpenAlbum = { nav.navigate(AlbumRoute(it)) },
                contentPadding = padding,
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onOpenAccounts = onOpenAccounts,
                onOpenHistory = { nav.navigate(HistoryRoute) { launchSingleTop = true } },
                onOpenLicences = { nav.navigate(LicencesRoute) { launchSingleTop = true } },
                contentPadding = padding,
            )
        }
        composable<LicencesRoute> {
            LicencesScreen(onBack = { nav.popBackStack() }, contentPadding = padding)
        }
        composable<HistoryRoute> {
            HistoryScreen(onBack = { nav.popBackStack() }, contentPadding = padding)
        }
        composable<FavoritesRoute> {
            FavoritesScreen(
                onBack = { nav.popBackStack() },
                onOpenAlbum = { nav.navigate(AlbumRoute(it)) },
                onOpenArtist = { nav.navigate(ArtistRoute(it)) },
                onOpenHistory = { nav.navigate(HistoryRoute) { launchSingleTop = true } },
                contentPadding = padding,
            )
        }
    }
}

/**
 * Arrête les touchers sur ce bloc : un bloc qui écoute le doigt cache à
 * Compose ce qui est dessous. Il ne faut rien « consommer » : un bouton
 * annule son appui si le parent consomme un mouvement, et certains écrans
 * (Samsung) signalent un mouvement d'un pixel au milieu de chaque appui.
 */
internal fun Modifier.blockTouchesBelow(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent()
    }
}

/** Onglet pas encore écrit : son titre et un mot d'explication. */
@Composable
private fun ComingSoon(title: Int, padding: PaddingValues) {
    val colors = Rengaine.colors
    Box(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(title), style = Rengaine.type.pageTitle, color = colors.text)
            Text(stringResource(R.string.tab_coming_soon), style = Rengaine.type.body, color = colors.muted)
        }
    }
}
