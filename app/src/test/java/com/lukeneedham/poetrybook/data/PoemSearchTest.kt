package com.lukeneedham.poetrybook.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PoemSearchTest {
    private fun poem(title: String, body: String = "", id: String = title) =
        SearchPoem(id, "book", "Book", title, body)

    private fun titles(poems: List<SearchPoem>) = poems.map { it.title }

    @Test
    fun `no query lists everything alphabetically ignoring case, untitled last`() {
        val all = listOf(poem("winter"), poem("", id = "x"), poem("Autumn"), poem("Love"))
        assertEquals(listOf("Autumn", "Love", "winter", ""), titles(searchPoems(all, "  ")))
    }

    @Test
    fun `only poems with the query in the title or text match, ignoring case`() {
        val all = listOf(poem("Sea", "salt wind"), poem("Hill", "green"), poem("Wind", "x"))
        assertEquals(listOf("Wind", "Sea"), titles(searchPoems(all, "WIND")))
    }

    @Test
    fun `title matches rank above text matches, and exact titles above partial ones`() {
        val all = listOf(
            poem("A walk", "the rose bloomed"),
            poem("Roses", "nothing"),
            poem("Rose", "nothing"),
            poem("The rose garden", "nothing"),
        )
        assertEquals(
            listOf("Rose", "Roses", "The rose garden", "A walk"),
            titles(searchPoems(all, "rose")),
        )
    }

    @Test
    fun `every word of the query must appear`() {
        val all = listOf(poem("Night", "cold moon"), poem("Day", "cold sun"))
        assertEquals(listOf("Night"), titles(searchPoems(all, "moon cold")))
    }

    @Test
    fun `equal matches are ordered alphabetically`() {
        val all = listOf(poem("B", "sea"), poem("A", "sea"))
        assertEquals(listOf("A", "B"), titles(searchPoems(all, "sea")))
    }
}
