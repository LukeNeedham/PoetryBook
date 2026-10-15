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

    @Test
    fun `a book of only blanks keeps just the first blank`() {
        val poems = listOf(poem("a"), poem("b"), poem("c"))
        assertEquals(listOf("a"), ids(withOneBlankLast(poems, ::fresh)))
    }

    @Test
    fun `surplus trailing blanks go but a blank in the middle stays`() {
        val poems = listOf(poem("a", "A"), poem("b"), poem("c", "C"), poem("d"), poem("e"))
        assertEquals(listOf("a", "b", "c", "d"), ids(withOneBlankLast(poems, ::fresh)))
    }

    @Test
    fun `fixing the end is stable when applied twice`() {
        val poems = listOf(poem("a", "A"), poem("b"), poem("c"))
        val once = withOneBlankLast(poems, ::fresh)
        assertEquals(once, withOneBlankLast(once, ::fresh))
    }

    @Test
    fun `a title with only whitespace and a body with only newlines is still blank`() {
        assertTrue(poem("a", title = " \t ", body = "\n\r\n \n").isBlank)
        assertFalse(poem("a", title = ".", body = "").isBlank)
    }

    @Test
    fun `the blank maker is only called when a blank is needed`() {
        var calls = 0
        val maker = { calls++; fresh() }
        withOneBlankLast(listOf(poem("a", "A"), poem("b")), maker)
        assertEquals(0, calls)
        withOneBlankLast(listOf(poem("a", "A")), maker)
        assertEquals(1, calls)
    }

    @Test
    fun `isNewVersion treats null as always new and compares both fields`() {
        val v = PoemVersion(1, "t", "b")
        assertTrue(isNewVersion(null, "", ""))
        assertFalse(isNewVersion(v, "t", "b"))
        assertTrue(isNewVersion(v, "T", "b"))
        assertTrue(isNewVersion(v, "t", "B"))
        assertTrue(isNewVersion(v, "t ", "b"))
        assertTrue(isNewVersion(v, "", ""))
    }

    @Test
    fun `isNewVersion ignores the timestamp of the latest version`() {
        assertFalse(isNewVersion(PoemVersion(1, "t", "b"), "t", "b"))
        assertFalse(isNewVersion(PoemVersion(Long.MAX_VALUE, "t", "b"), "t", "b"))
    }

    @Test
    fun `poems are equal when all their fields are`() {
        assertEquals(Poem("a", "t", "b"), Poem("a", "t", "b"))
        assertFalse(Poem("a", "t", "b") == Poem("a", "t", "c"))
        assertFalse(Poem("a", "t", "b") == Poem("z", "t", "b"))
    }
}
