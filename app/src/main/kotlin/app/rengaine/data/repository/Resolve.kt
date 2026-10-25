package app.rengaine.data.repository

import app.rengaine.data.remote.ServerAddress
import app.rengaine.data.remote.dto.TrackDto

/** Pochette et flux d'une piste en adresses absolues (le contrat les donne relatives à la racine du serveur). */
internal fun TrackDto.resolved(address: ServerAddress) = copy(
    coverUrl = coverUrl?.let(address::resolve),
    streamUrl = streamUrl.takeIf { it.isNotBlank() }?.let(address::resolve).orEmpty(),
)
