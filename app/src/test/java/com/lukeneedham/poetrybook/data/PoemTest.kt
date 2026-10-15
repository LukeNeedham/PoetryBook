package com.lukeneedham.poetrybook.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PoemTest {
    private fun poem(id: String, title: String = "", body: String = "") = Poem(id, title, body)
    private fun ids(poems: List<Poem>) = poems.map { it.id }
    private fun fresh() = poem("new")

    @Test
    fun `a poem is blank until a title or text is written`() {
        assertTrue(poem("a").isBlank)
        assertTrue(poem("a", title = "  ", body = "\n").isBlank)
        assertFalse(poem("a", title = "Rose").isBlank)
        assertFalse(poem("a", body = "Roses are red").isBlank)
    }

    @Test
    fun `an empty book gets one blank poem`() {
        assertEquals(listOf("new"), ids(withOneBlankLast(emptyList(), ::fresh)))
    }

    @Test
    fun `a book ending in a written poem gets a blank one after it`() {
        val poems = listOf(poem("a", "A"), poem("b", "B"))
        assertEquals(listOf("a", "b", "new"), ids(withOneBlankLast(poems, ::fresh)))
    }

    @Test
    fun `a book already ending in one blank is left alone`() {
        val poems = listOf(poem("a", "A"), poem("blank"))
        assertEquals(poems, withOneBlankLast(poems) { error("should not be needed") })
    }

    @Test
    fun `surplus blanks at the end are dropped, keeping the first`() {
        val poems = listOf(poem("a", "A"), poem("emptied"), poem("blank"))
        assertEquals(listOf("a", "emptied"), ids(withOneBlankLast(poems, ::fresh)))
    }

    @Test
    fun `blanks in the middle are not touched`() {
        val poems = listOf(poem("a", "A"), poem("gap"), poem("b", "B"))
        assertEquals(listOf("a", "gap", "b", "new"), ids(withOneBlankLast(poems, ::fresh)))
    }

    @Test
    fun `saving the same title and text is not a new version`() {
        val latest = PoemVersion(createdAt = 1, title = "Rose", body = "Roses are red")
        assertFalse(isNewVersion(latest, "Rose", "Roses are red"))
    }

    @Test
    fun `a changed title or text is a new version`() {
        val latest = PoemVersion(createdAt = 1, title = "Rose", body = "Roses are red")
        assertTrue(isNewVersion(latest, "Roses", "Roses are red"))
        assertTrue(isNewVersion(latest, "Rose", "Roses are red,"))
    }

    @Test
    fun `a poem with no versions yet always gets its first`() {
        assertTrue(isNewVersion(null, "Rose", "Roses are red"))
    }
}
