package app.rengaine.data.remote

import app.rengaine.data.remote.dto.AcceptedResponse
import app.rengaine.data.remote.dto.AlbumDetailDto
import app.rengaine.data.remote.dto.AlbumPageDto
import app.rengaine.data.remote.dto.ArtistDetailDto
import app.rengaine.data.remote.dto.ArtistPageDto
import app.rengaine.data.remote.dto.CollectionListDto
import app.rengaine.data.remote.dto.DecadeListDto
import app.rengaine.data.remote.dto.FavoritePageDto
import app.rengaine.data.remote.dto.ForgotPasswordRequest
import app.rengaine.data.remote.dto.GenreListDto
import app.rengaine.data.remote.dto.LoginRequest
import app.rengaine.data.remote.dto.LoginResponse
import app.rengaine.data.remote.dto.MeResponse
import app.rengaine.data.remote.dto.UserDto
import app.rengaine.data.remote.dto.UpdateMeRequest
import app.rengaine.data.remote.dto.PairingCodeDto
import app.rengaine.data.remote.dto.DeviceListDto
import app.rengaine.data.remote.dto.DeleteMeRequest
import app.rengaine.data.remote.dto.ChangePasswordRequest
import app.rengaine.data.remote.dto.MoodListDto
import app.rengaine.data.remote.dto.PairRequest
import app.rengaine.data.remote.dto.PlayQueueDto
import app.rengaine.data.remote.dto.PlayQueueInputDto
import app.rengaine.data.remote.dto.PlaylistAddDto
import app.rengaine.data.remote.dto.PlaylistDetailDto
import app.rengaine.data.remote.dto.PlaylistInputDto
import app.rengaine.data.remote.dto.PlaylistListDto
import app.rengaine.data.remote.dto.PlaylistPatchDto
import kotlinx.serialization.json.JsonObject
import app.rengaine.data.remote.dto.HistoryClearedDto
import app.rengaine.data.remote.dto.WaveformDto
import app.rengaine.data.remote.dto.PlayHistoryPageDto
import app.rengaine.data.remote.dto.RecordPlaysDto
import app.rengaine.data.remote.dto.SearchResultsDto
import app.rengaine.data.remote.dto.ServerInfoDto
import app.rengaine.data.remote.dto.TokenResponse
import app.rengaine.data.remote.dto.TrackListDto
import app.rengaine.data.remote.dto.NextTracksRequest
import app.rengaine.data.remote.dto.TrackPageDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * API v2 de Rengaine (contrat : `docs/api/openapi.yaml` du serveur). Les
 * chemins sont relatifs à `<adresse du serveur>/api/v2/` : pas de `/` en
 * tête, sinon Retrofit perdrait le sous-chemin d'un serveur installé dans
 * un dossier (`https://exemple.fr/rengaine`).
 *
 * Les opérations s'ajoutent tâche par tâche ; l'annexe du listing dit
 * laquelle couvre chaque route.
 */
interface RengaineApi {

    // --- Serveur (F1) -------------------------------------------------------

    @GET("server")
    suspend fun getServer(): Response<ServerInfoDto>

    // --- Authentification (F1) ----------------------------------------------

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<LoginResponse>

    @POST("auth/pair")
    suspend fun pair(@Body body: PairRequest): Response<TokenResponse>

    @POST("auth/logout")
    suspend fun logout(): Response<Unit>

    @POST("auth/password/forgot")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest): Response<AcceptedResponse>

    // --- Compte (F13) --------------------------------------------------------

    @GET("me")
    suspend fun getMe(): Response<MeResponse>

    /** Nom affiché. */
    @PATCH("me")
    suspend fun updateMe(@Body body: UpdateMeRequest): Response<UserDto>

    /** `204` ; révoque les autres sessions et jetons, pas celui-ci. */
    @POST("me/password")
    suspend fun changePassword(@Body body: ChangePasswordRequest): Response<Unit>

    @GET("me/devices")
    suspend fun listDevices(): Response<DeviceListDto>

    @DELETE("me/devices/{tokenId}")
    suspend fun revokeDevice(@Path("tokenId") tokenId: Long): Response<Unit>

    /** Code d'appairage pour un autre appareil (QR dessiné par l'app). */
    @POST("me/pairing-codes")
    suspend fun createPairingCode(): Response<PairingCodeDto>

    /**
     * Toutes ses données (JSON), à enregistrer **telles quelles** : flux
     * brut, jamais décodé ni journalisé (données personnelles).
     */
    @Streaming
    @GET("me/export")
    suspend fun exportMyData(): Response<okhttp3.ResponseBody>

    /** Suppression immédiate et définitive (`204`) ; le client laisse 15 s pour annuler avant. */
    @HTTP(method = "DELETE", path = "me", hasBody = true)
    suspend fun deleteMe(@Body body: DeleteMeRequest): Response<Unit>

    // --- File d'attente (F6) ----------------------------------------------------

    /** File enregistrée, pistes comprises ; l'`ETag` sert au prochain `PUT`. */
    @GET("me/queue")
    suspend fun getQueue(): Response<PlayQueueDto>

    /**
     * `If-Match` obligatoire (`428` sinon) ; `412` si un autre appareil l'a
     * changée entre-temps. Corps construit par [PlayQueueInputDto.toJson] :
     * `currentIndex` doit partir même nul, ce que `ApiJson` omettrait.
     */
    @PUT("me/queue")
    suspend fun putQueue(@Header("If-Match") ifMatch: String, @Body body: JsonObject): Response<PlayQueueDto>

    // --- Historique (F11) -------------------------------------------------------

    /** Écoutes par lots (500 au plus), envoyées après coup si besoin ; `204`. */
    @POST("me/history")
    suspend fun recordPlays(@Body body: RecordPlaysDto): Response<Unit>

    /** Écoutes, de la plus récente à la plus ancienne. */
    @GET("me/history")
    suspend fun listHistory(@Query("limit") limit: Int, @Query("offset") offset: Int): Response<PlayHistoryPageDto>

    /** Efface une écoute (`204`, `404` si elle n'existe plus). Depuis 2.26.0. */
    @DELETE("me/history/{id}")
    suspend fun deletePlay(@Path("id") id: Long): Response<Unit>

    /** Efface tout l'historique du compte. Depuis 2.26.0. */
    @DELETE("me/history")
    suspend fun clearHistory(): Response<HistoryClearedDto>

    // --- Recherche (F3) ---------------------------------------------------------

    /** Artistes, albums, pistes ; [limit] par groupe (50 au plus). Insensible aux accents. */
    @GET("search")
    suspend fun search(@Query("q") query: String, @Query("limit") limit: Int): Response<SearchResultsDto>

    // --- Réglages du compte (F12) ----------------------------------------------

    /**
     * Document `{version, updatedAt, settings}` gardé **brut** : les champs
     * que l'app ne connaît pas doivent repartir tels quels (contrat).
     */
    @GET("me/settings")
    suspend fun getSettings(): Response<JsonObject>

    /** Remplace tous les réglages ; `If-Match` obligatoire, `412` si changés ailleurs. */
    @PUT("me/settings")
    suspend fun putSettings(@Header("If-Match") ifMatch: String, @Body settings: JsonObject): Response<JsonObject>

    // --- Bibliothèque (F2) ------------------------------------------------------

    /** Albums filtrés et triés, par pages (`limit` 500 au plus). */
    @GET("albums")
    suspend fun listAlbums(
        @Query("sort") sort: String = "name",
        @Query("order") order: String? = null,
        @Query("genre", encoded = true) genre: String? = null,
        @Query("yearFrom") yearFrom: Int? = null,
        @Query("yearTo") yearTo: Int? = null,
        @Query("artist") artist: Long? = null,
        @Query("collection") collection: Long? = null,
        @Query("seed") seed: Long? = null,
        @Query("limit") limit: Int = 60,
        @Query("offset") offset: Int = 0,
    ): Response<AlbumPageDto>

    @GET("albums/{id}")
    suspend fun getAlbum(@Path("id") id: Long): Response<AlbumDetailDto>

    @GET("genres")
    suspend fun listGenres(): Response<GenreListDto>

    /** Artistes, filtrés par `q` (insensible à la casse et aux accents). */
    @GET("artists")
    suspend fun listArtists(
        @Query("q") query: String? = null,
        @Query("sort") sort: String = "name",
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
    ): Response<ArtistPageDto>

    @GET("artists/{id}")
    suspend fun getArtist(@Path("id") id: Long): Response<ArtistDetailDto>

    /** Pistes ; avec `artist`, celles où il est principal ou invité. */
    @GET("tracks")
    suspend fun listTracks(
        @Query("artist") artist: Long? = null,
        @Query("sort") sort: String = "name",
        @Query("seed") seed: Long? = null,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
    ): Response<TrackPageDto>

    /** Forme d'onde (U12) ; `404` tant que la piste n'est pas mesurée. */
    @GET("tracks/{id}/waveform")
    suspend fun getWaveform(@Path("id") id: Long): Response<WaveformDto>

    /** Ambiances calculées ; liste vide si le serveur ne mesure pas l'énergie. */
    @GET("moods")
    suspend fun listMoods(): Response<MoodListDto>

    @GET("collections")
    suspend fun listCollections(@Query("kind") kind: String = "collection"): Response<CollectionListDto>

    @GET("decades")
    suspend fun listDecades(): Response<DecadeListDto>

    // --- Playlists (F10) et favoris (F9) ---------------------------------------

    /** Les miennes et celles partagées par les autres, les plus récemment modifiées d'abord. */
    @GET("playlists")
    suspend fun listPlaylists(@Query("scope") scope: String = "all"): Response<PlaylistListDto>

    @GET("playlists/{id}")
    suspend fun getPlaylist(@Path("id") id: Long): Response<PlaylistDetailDto>

    @POST("playlists")
    suspend fun createPlaylist(@Body body: PlaylistInputDto): Response<PlaylistDetailDto>

    /** Renommer ou partager (créateur seulement, `403` sinon). */
    @PATCH("playlists/{id}")
    suspend fun updatePlaylist(@Path("id") id: Long, @Body body: PlaylistPatchDto): Response<PlaylistDetailDto>

    @DELETE("playlists/{id}")
    suspend fun deletePlaylist(@Path("id") id: Long): Response<Unit>

    /** Ajout à la fin ; `409` au-delà de 5000 pistes. */
    @POST("playlists/{id}/items")
    suspend fun addPlaylistItems(@Path("id") id: Long, @Body body: PlaylistAddDto): Response<PlaylistDetailDto>

    @DELETE("playlists/{id}/items/{itemId}")
    suspend fun removePlaylistItem(@Path("id") id: Long, @Path("itemId") itemId: Long): Response<PlaylistDetailDto>

    /** Favoris d'un type (`track`, `album`, `artist`), du plus récent au plus ancien. */
    @GET("me/favorites")
    suspend fun listFavorites(
        @Query("type") type: String,
        @Query("limit") limit: Int = 500,
        @Query("offset") offset: Int = 0,
    ): Response<FavoritePageDto>

    /** Ajouter aux favoris (`204`, sans effet si déjà présent). */
    @PUT("me/favorites/{type}/{id}")
    suspend fun addFavorite(@Path("type") type: String, @Path("id") id: Long): Response<Unit>

    /** Retirer des favoris (`204`, sans effet si absent). */
    @DELETE("me/favorites/{type}/{id}")
    suspend fun removeFavorite(@Path("type") type: String, @Path("id") id: Long): Response<Unit>

    // --- Catalogue (F4, F5) ----------------------------------------------------

    /**
     * Pistes au hasard. Les listes (`genre`, `exclude`) sont séparées par
     * des virgules (`style: form, explode: false`) : voir [idList]. Passées
     * telles quelles (`encoded = true`) : des chiffres et des virgules, que
     * Retrofit transformerait sinon en `%2C`.
     */
    /** Lecture continue : pistes qui s'enchaînent selon les règles `continuous` (501 si le serveur ne le fait pas). */
    @POST("playback/next")
    suspend fun nextTracks(@Body body: NextTracksRequest): Response<TrackListDto>

    @GET("random/tracks")
    suspend fun randomTracks(
        @Query("count") count: Int = 50,
        @Query("genre", encoded = true) genre: String? = null,
        @Query("exclude", encoded = true) exclude: String? = null,
        /** Albums décochés dans la page Aléatoire ; 500 au plus (limite d'adresse du serveur). */
        @Query("excludeAlbum", encoded = true) excludeAlbum: String? = null,
        @Query("mood") mood: String? = null,
        @Query("yearFrom") yearFrom: Int? = null,
        @Query("yearTo") yearTo: Int? = null,
        @Query("collection") collection: Long? = null,
    ): Response<TrackListDto>
}

/** Liste d'identifiants au format `1,2,3` attendu par le contrat ; `null` si vide. */
fun idList(ids: Collection<Long>): String? = ids.takeIf { it.isNotEmpty() }?.joinToString(",")
