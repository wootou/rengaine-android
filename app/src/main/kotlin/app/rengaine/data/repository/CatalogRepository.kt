package app.rengaine.data.repository

import app.rengaine.data.remote.ApiError
import app.rengaine.data.remote.ApiResult
import app.rengaine.data.remote.ServerAddress
import app.rengaine.data.remote.apiCall
import app.rengaine.data.remote.dto.AlbumDetailDto
import app.rengaine.data.remote.dto.AlbumPageDto
import app.rengaine.data.remote.dto.ArtistDetailDto
import app.rengaine.data.remote.dto.ArtistPageDto
import app.rengaine.data.remote.dto.CollectionDto
import app.rengaine.data.remote.dto.DecadeDto
import app.rengaine.data.remote.dto.GenreDto
import app.rengaine.data.remote.dto.MoodDto
import app.rengaine.data.remote.dto.NextTracksOverrides
import app.rengaine.data.remote.dto.NextTracksRequest
import app.rengaine.data.remote.dto.SearchResultsDto
import app.rengaine.data.remote.dto.TrackDto
import app.rengaine.data.remote.idList
import javax.inject.Inject
import javax.inject.Singleton

/** Tri des albums (`sort` du contrat). */
enum class AlbumSort(val api: String) { NAME("name"), ADDED("added"), YEAR("year"), ARTIST("artist") }

/** Filtres de la liste d'albums ; `null` = pas de filtre. */
data class AlbumFilter(
    val sort: AlbumSort = AlbumSort.NAME,
    val genreId: Long? = null,
    /** Décennie : 1980 pour 1980-1989. */
    val decade: Int? = null,
    val collectionId: Long? = null,
)

/**
 * Ciblage d'un tirage au hasard (Explorer, mode infini — F5) : genres,
 * ambiance, années, collection. Tout `null` = toute la bibliothèque.
 */
data class RandomFilter(
    val genreIds: List<Long> = emptyList(),
    val mood: String? = null,
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val collectionId: Long? = null,
) {
    /** Un ciblage choisi (ambiance, années, collection) plutôt que les seuls genres. */
    val hasTarget: Boolean get() = mood != null || yearFrom != null || yearTo != null || collectionId != null
}

/**
 * Bibliothèque du compte actif (F2). Les adresses d'images sont rendues
 * **absolues** ici, une fois pour toutes : l'interface et le lecteur n'ont
 * plus à connaître le serveur.
 */
@Singleton
class CatalogRepository @Inject constructor(
    private val accounts: AccountRepository,
) {
    suspend fun albums(filter: AlbumFilter, offset: Int, limit: Int = PAGE_SIZE): ApiResult<AlbumPageDto> =
        withSession { session ->
            val result = apiCall {
                session.api.listAlbums(
                    sort = filter.sort.api,
                    // Ajouts récents et année : les plus récents d'abord.
                    order = if (filter.sort == AlbumSort.ADDED || filter.sort == AlbumSort.YEAR) "desc" else null,
                    genre = filter.genreId?.let { idList(listOf(it)) },
                    yearFrom = filter.decade,
                    yearTo = filter.decade?.plus(9),
                    collection = filter.collectionId,
                    limit = limit,
                    offset = offset,
                )
            }
            result.map { page ->
                page.copy(items = page.items.map { it.copy(coverUrl = it.coverUrl?.let(session.account.address::resolve)) })
            }
        }

    suspend fun album(id: Long): ApiResult<AlbumDetailDto> = withSession { session ->
        val address = session.account.address
        apiCall { session.api.getAlbum(id) }.map { album ->
            album.copy(
                coverUrl = album.coverUrl?.let(address::resolve),
                tracks = album.tracks.map { it.resolved(address) },
            )
        }
    }

    suspend fun genres(): ApiResult<List<GenreDto>> = withSession { session ->
        apiCall { session.api.listGenres() }.map { it.items }
    }

    suspend fun decades(): ApiResult<List<DecadeDto>> = withSession { session ->
        apiCall { session.api.listDecades() }.map { it.items }
    }

    suspend fun randomTracks(
        count: Int,
        filter: RandomFilter = RandomFilter(),
        exclude: Collection<Long> = emptyList(),
        excludeAlbums: Collection<Long> = emptyList(),
    ): ApiResult<List<TrackDto>> = withSession { session ->
        apiCall {
            session.api.randomTracks(
                count = count,
                genre = idList(filter.genreIds),
                // Le contrat plafonne `exclude` à 1000 identifiants, `excludeAlbum` à 500.
                exclude = idList(exclude.toList().takeLast(1000)),
                excludeAlbum = idList(excludeAlbums.take(MAX_EXCLUDED_ALBUMS)),
                mood = filter.mood,
                yearFrom = filter.yearFrom,
                yearTo = filter.yearTo,
                collection = filter.collectionId,
            )
        }.map { list -> list.items.map { it.resolved(session.account.address) } }
    }

    /**
     * Lecture continue (`POST /playback/next`). Le ciblage n'est envoyé que
     * s'il y en a un (genres choisis ou filtre d'Explorer), comme le site.
     */
    suspend fun nextTracks(
        count: Int,
        currentTrackId: Long?,
        recent: List<Long>,
        filter: RandomFilter,
    ): ApiResult<List<TrackDto>> = withSession { session ->
        val overrides = if (filter.genreIds.isNotEmpty() || filter.hasTarget) {
            NextTracksOverrides(
                genres = filter.genreIds.takeIf { it.isNotEmpty() },
                mood = filter.mood,
                yearFrom = filter.yearFrom,
                yearTo = filter.yearTo,
                collection = filter.collectionId,
            )
        } else {
            null
        }
        val body = NextTracksRequest(count, currentTrackId, recent.takeLast(MAX_RECENT_NEXT), overrides)
        apiCall { session.api.nextTracks(body) }
            .map { list -> list.items.map { it.resolved(session.account.address) } }
    }

    suspend fun artists(query: String?, offset: Int, limit: Int = 100): ApiResult<ArtistPageDto> = withSession { s ->
        apiCall { s.api.listArtists(query = query?.trim()?.takeIf { it.isNotEmpty() }, limit = limit, offset = offset) }
            .map { page -> page.copy(items = page.items.map { it.copy(imageUrl = it.imageUrl?.let(s.account.address::resolve)) }) }
    }

    suspend fun artist(id: Long): ApiResult<ArtistDetailDto> = withSession { s ->
        val address = s.account.address
        apiCall { s.api.getArtist(id) }.map { a ->
            a.copy(
                imageUrl = a.imageUrl?.let(address::resolve),
                albums = a.albums.map { it.copy(coverUrl = it.coverUrl?.let(address::resolve)) },
                appearsOn = a.appearsOn.map { it.copy(coverUrl = it.coverUrl?.let(address::resolve)) },
            )
        }
    }

    /** Pistes d'un artiste (principal ou invité), par titre ; 500 au plus. */
    suspend fun artistTracks(id: Long): ApiResult<List<TrackDto>> = withSession { s ->
        apiCall { s.api.listTracks(artist = id, limit = 500) }.map { page -> page.items.map { it.resolved(s.account.address) } }
    }

    /** Recherche pendant la frappe (F3) ; adresses rendues absolues. */
    suspend fun search(query: String, limit: Int): ApiResult<SearchResultsDto> = withSession { s ->
        val address = s.account.address
        apiCall { s.api.search(query, limit) }.map { r ->
            r.copy(
                artists = r.artists.map { it.copy(imageUrl = it.imageUrl?.let(address::resolve)) },
                albums = r.albums.map { it.copy(coverUrl = it.coverUrl?.let(address::resolve)) },
                tracks = r.tracks.map { it.resolved(address) },
            )
        }
    }

    suspend fun moods(): ApiResult<List<MoodDto>> = withSession { s ->
        apiCall { s.api.listMoods() }.map { it.items }
    }

    suspend fun collections(): ApiResult<List<CollectionDto>> = withSession { s ->
        apiCall { s.api.listCollections() }.map { list ->
            list.items.map { c -> c.copy(coverUrls = c.coverUrls.map(s.account.address::resolve)) }
        }
    }

    private suspend fun <T> withSession(block: suspend (ActiveSession) -> ApiResult<T>): ApiResult<T> {
        val session = accounts.activeSession()
            ?: return ApiResult.Err(ApiError.Http(401, "unauthorized", "", null, null, null))
        return block(session)
    }

    companion object {
        const val PAGE_SIZE = 60
        /** Plafond du contrat pour `excludeAlbum` (adresse sous 8 Ko). */
        const val MAX_EXCLUDED_ALBUMS = 500
        /** `recentTrackIds` : 200 au plus (contrat). */
        const val MAX_RECENT_NEXT = 200
    }
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Ok -> ApiResult.Ok(transform(value), etag)
    is ApiResult.Err -> this
}
