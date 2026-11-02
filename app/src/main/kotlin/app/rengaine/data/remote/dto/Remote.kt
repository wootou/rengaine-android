package app.rengaine.data.remote.dto

import kotlinx.serialization.Serializable

/** Ce que peut faire l'invité, en plus de voir la file et la piste en cours (FUT-02). */
@Serializable
data class QueueShareRightsDto(
    /** Chercher et ajouter des pistes ou des albums. */
    val add: Boolean = false,
    /** Déplacer et retirer des pistes de la file. */
    val edit: Boolean = false,
    /** Lecture, pause, piste suivante, volume. */
    val control: Boolean = false,
)

/** Un lien de télécommande encore valable. */
@Serializable
data class QueueShareDto(
    val id: Long,
    val label: String = "",
    /** Début du secret, pour reconnaître le lien. */
    val prefix: String = "",
    val rights: QueueShareRightsDto = QueueShareRightsDto(),
    val createdAt: String = "",
    val expiresAt: String = "",
    val lastUsedAt: String? = null,
)

@Serializable
data class QueueShareListDto(val items: List<QueueShareDto> = emptyList())

@Serializable
data class QueueShareInputDto(
    val label: String? = null,
    val hours: Int,
    val rights: QueueShareRightsDto,
)

/** Lien créé : le secret ([token], [url]) n'est montré qu'une fois. */
@Serializable
data class QueueShareCreatedDto(
    val id: Long,
    val label: String = "",
    val prefix: String = "",
    val rights: QueueShareRightsDto = QueueShareRightsDto(),
    val createdAt: String = "",
    val expiresAt: String = "",
    val token: String = "",
    val url: String = "",
)

/** Abonnement au hub Mercure du lecteur (`GET /me/realtime`). */
@Serializable
data class RealtimeChannelDto(
    val hubUrl: String,
    val topics: List<String> = emptyList(),
    val token: String,
    val expiresAt: String,
    /** Au moins un lien valable : il faut envoyer `PUT /me/player`. */
    val sharesActive: Boolean = false,
)

/** État du lecteur pour les invités (`PUT /me/player`). */
@Serializable
data class PlayerReportDto(
    val deviceKey: String,
    val trackId: Long?,
    val playing: Boolean,
    val positionMs: Long,
    val queueVersion: Long,
    val volume: Int? = null,
)

/** Commande d'un invité, relayée par le serveur au lecteur qui joue. */
@Serializable
data class RemoteCommandDto(
    val type: String,
    val trackIds: List<Long> = emptyList(),
    val albumId: Long? = null,
    val where: String? = null,
    val from: Int? = null,
    val to: Int? = null,
    val index: Int? = null,
    val trackId: Long? = null,
    val volume: Int? = null,
    val name: String? = null,
)

/**
 * Message du hub. `command` : [target] = `deviceKey` du lecteur visé, [by] =
 * nom du lien, [tracks] = pistes complètes pour `add`. `shares` : relire
 * `GET /me/realtime`. `disabled` : télécommande coupée par l'admin.
 */
@Serializable
data class RealtimeMessageDto(
    val type: String = "",
    val target: String? = null,
    val by: String? = null,
    val command: RemoteCommandDto? = null,
    val tracks: List<TrackDto> = emptyList(),
)
