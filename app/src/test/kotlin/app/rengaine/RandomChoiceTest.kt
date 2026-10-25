package app.rengaine

import app.rengaine.domain.RandomChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Page Aléatoire (F4) : genres cochés et albums de côté, comme `Random.jsx`. */
class RandomChoiceTest {

    private val genres = listOf(1L, 2L, 3L)

    @Test
    fun `réglage vide - tout coché, aucun filtre`() {
        val c = RandomChoice.fromSetting(genres, emptyList(), emptyList())
        assertTrue(c.allChecked)
        assertEquals(emptyList<Long>(), c.genreFilter)
    }

    @Test
    fun `genres enregistrés repris, inconnus ignorés`() {
        val c = RandomChoice.fromSetting(genres, listOf(2L, 99L), listOf(7L))
        assertEquals(setOf(2L), c.checked)
        assertEquals(listOf(2L), c.genreFilter)
        assertEquals(setOf(7L), c.excludedAlbums)
    }

    @Test
    fun `plus aucun genre connu - tout coché`() {
        assertTrue(RandomChoice.fromSetting(genres, listOf(99L), emptyList()).allChecked)
    }

    @Test
    fun `décocher un genre puis le recocher revient à tout`() {
        val c = RandomChoice.fromSetting(genres, emptyList(), emptyList()).toggleGenre(2L)
        assertEquals(listOf(1L, 3L), c.genreFilter)
        assertEquals(emptyList<Long>(), c.toggleGenre(2L).genreFilter)
    }

    @Test
    fun `tout cocher remet les albums, tout décocher ne vide que les genres`() {
        val c = RandomChoice.fromSetting(genres, listOf(1L), listOf(5L))
        val none = c.uncheckAll()
        assertTrue(none.noneChecked)
        assertEquals(setOf(5L), none.excludedAlbums)
        val all = none.checkAll()
        assertTrue(all.allChecked)
        assertTrue(all.excludedAlbums.isEmpty())
    }

    @Test
    fun `pas plus de 500 albums de côté`() {
        var c = RandomChoice(genres, genres.toSet(), (1L..500L).toSet())
        assertFalse(c.canExclude())
        c = c.toggleAlbum(9999L)
        assertEquals(500, c.excludedAlbums.size)
        assertEquals(499, c.toggleAlbum(1L).excludedAlbums.size)
    }
}
