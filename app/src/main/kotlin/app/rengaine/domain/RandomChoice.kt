package app.rengaine.domain

/**
 * Sélection de la page Aléatoire (F4), comme `Random.jsx` du site : des
 * **genres à plat** cochés (tous = pas de filtre) et des **albums mis de
 * côté**, 500 au plus (limite d'adresse du serveur). C'est aussi la forme
 * du réglage `random` du compte.
 */
data class RandomChoice(
    /** Genres proposés (ceux qui ont des pistes), dans l'ordre affiché. */
    val genreIds: List<Long>,
    val checked: Set<Long>,
    val excludedAlbums: Set<Long>,
) {
    val allChecked: Boolean get() = genreIds.isNotEmpty() && checked.containsAll(genreIds)
    val noneChecked: Boolean get() = genreIds.none { it in checked }

    /** `genreIds` du réglage et du tirage : vide = tous les genres. */
    val genreFilter: List<Long> get() = if (allChecked) emptyList() else genreIds.filter { it in checked }

    fun toggleGenre(id: Long) = copy(checked = if (id in checked) checked - id else checked + id)

    /** `false` si le plafond est atteint : l'album n'est pas mis de côté. */
    fun canExclude(): Boolean = excludedAlbums.size < MAX_EXCLUDED_ALBUMS

    fun toggleAlbum(id: Long): RandomChoice = when {
        id in excludedAlbums -> copy(excludedAlbums = excludedAlbums - id)
        canExclude() -> copy(excludedAlbums = excludedAlbums + id)
        else -> this
    }

    /** « Tout cocher » remet aussi les albums ; « Tout décocher » ne vide que les genres. */
    fun checkAll() = copy(checked = genreIds.toSet(), excludedAlbums = emptySet())
    fun uncheckAll() = copy(checked = emptySet())

    companion object {
        const val MAX_EXCLUDED_ALBUMS = 500

        /** Depuis le réglage : `genreIds` vide (ou plus aucun genre connu) = tout coché. */
        fun fromSetting(genreIds: List<Long>, savedGenres: List<Long>, savedExcluded: List<Long>): RandomChoice {
            val known = savedGenres.filter { it in genreIds }.toSet()
            return RandomChoice(
                genreIds = genreIds,
                checked = known.ifEmpty { genreIds.toSet() },
                excludedAlbums = savedExcluded.take(MAX_EXCLUDED_ALBUMS).toSet(),
            )
        }
    }
}
