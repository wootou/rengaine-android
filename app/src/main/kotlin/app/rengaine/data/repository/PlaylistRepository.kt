package app.rengaine.data.repository

import app.rengaine.data.remote.ApiError
import app.rengaine.data.remote.ApiResult
import app.rengaine.data.remote.ServerAddress
import app.rengaine.data.remote.apiCall
import app.rengaine.data.remote.apiCallUnit
import app.rengaine.data.remote.dto.AlbumSummaryDto
import app.rengaine.data.remote.dto.ArtistSummaryDto
import app.rengaine.data.remote.dto.FavoriteDto
import app.rengaine.data.remote.dto.PlaylistAddDto
import app.rengaine.data.remote.dto.PlaylistDetailDto
import app.rengaine.data.remote.dto.PlaylistInputDto
import app.rengaine.data.remote.dto.PlaylistPatchDto
import app.rengaine.data.remote.dto.PlaylistSummaryDto
import app.rengaine.data.remote.dto.TrackDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Playlists (F10) et favoris (F9) du compte actif. Comme pour la
 * bibliothèque, les adresses d'images et de flux sortent d'ici absolues.
 */
@Singleton
class PlaylistRepository @Inject constructor(
    private val accounts: AccountRepository,
) {
    private val _changed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Une playlist a été créée, modifiée ou supprimée : les listes se rechargent. */
    val changed: SharedFlow<Unit> = _changed.asSharedFlow()

    suspend fun playlists(): ApiResult<List<PlaylistSummaryDto>> = withSession { s ->
        apiCall { s.api.listPlaylists() }.map { list -> list.items.map { it.resolved(s.account.address) } }
    }

    suspend fun playlist(id: Long): ApiResult<PlaylistDetailDto> = withSession { s ->
        apiCall { s.api.getPlaylist(id) }.map { it.resolved(s.account.address) }
    }

    suspend fun create(name: String, trackIds: List<Long> = emptyList()): ApiResult<PlaylistDetailDto> = withSession { s ->
        apiCall { s.api.createPlaylist(PlaylistInputDto(name = name.trim(), trackIds = trackIds)) }
            .map { it.resolved(s.account.address) }.also(::notifyIfOk)
    }

    suspend fun rename(id: Long, name: String): ApiResult<PlaylistDetailDto> = patch(id, PlaylistPatchDto(name = name.trim()))

    suspend fun setShared(id: Long, shared: Boolean): ApiResult<PlaylistDetailDto> = patch(id, PlaylistPatchDto(shared = shared))

    suspend fun delete(id: Long): ApiResult<Unit> = withSession { s ->
        apiCallUnit { s.api.deletePlaylist(id) }.also(::notifyIfOk)
    }

    suspend fun addTracks(id: Long, trackIds: List<Long>): ApiResult<PlaylistDetailDto> = withSession { s ->
        apiCall { s.api.addPlaylistItems(id, PlaylistAddDto(trackIds)) }.map { it.resolved(s.account.address) }.also(::notifyIfOk)
    }

    suspend fun removeEntry(id: Long, entryId: Long): ApiResult<PlaylistDetailDto> = withSession { s ->
        apiCall { s.api.removePlaylistItem(id, entryId) }.map { it.resolved(s.account.address) }.also(::notifyIfOk)
    }

    suspend fun favoriteTracks(): ApiResult<List<TrackDto>> = favorites("track") { it.track }

    suspend fun favoriteAlbums(): ApiResult<List<AlbumSummaryDto>> = favorites("album") { it.album }

    suspend fun favoriteArtists(): ApiResult<List<ArtistSummaryDto>> = favorites("artist") { it.artist }

    private suspend fun <T> favorites(type: String, pick: (FavoriteDto) -> T?): ApiResult<List<T>> = withSession { s ->
        val address = s.account.address
        apiCall { s.api.listFavorites(type) }.map { page ->
            page.items.map { f ->
                f.copy(
                    track = f.track?.resolved(address),
                    album = f.album?.let { a -> a.copy(coverUrl = a.coverUrl?.let(address::resolve)) },
                    artist = f.artist?.let { a -> a.copy(imageUrl = a.imageUrl?.let(address::resolve)) },
                )
            }.mapNotNull(pick)
        }
    }

    private suspend fun patch(id: Long, body: PlaylistPatchDto): ApiResult<PlaylistDetailDto> = withSession { s ->
        apiCall { s.api.updatePlaylist(id, body) }.map { it.resolved(s.account.address) }.also(::notifyIfOk)
    }

    private fun notifyIfOk(result: ApiResult<*>) {
        if (result is ApiResult.Ok) _changed.tryEmit(Unit)
    }

    private fun PlaylistSummaryDto.resolved(address: ServerAddress) = copy(coverUrls = coverUrls.map(address::resolve))

    private fun PlaylistDetailDto.resolved(address: ServerAddress) = copy(
        coverUrls = coverUrls.map(address::resolve),
        items = items.map { it.copy(track = it.track.resolved(address)) },
    )

    private suspend fun <T> withSession(block: suspend (ActiveSession) -> ApiResult<T>): ApiResult<T> {
        val session = accounts.activeSession()
            ?: return ApiResult.Err(ApiError.Http(401, "unauthorized", "", null, null, null))
        return block(session)
    }
}
