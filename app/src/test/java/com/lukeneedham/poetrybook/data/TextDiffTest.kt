package com.lukeneedham.poetrybook.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TextDiffTest {
    private fun marked(version: String, current: String) =
        differingRanges(version, current).map { version.substring(it.first, it.last + 1) }

    @Test
    fun `identical text has no differences`() {
        assertEquals(emptyList<String>(), marked("a b\nc", "a b\nc"))
        assertEquals(emptyList<String>(), marked("", ""))
    }

    @Test
    fun `a changed word is marked and nothing else`() {
        assertEquals(listOf("red"), marked("the red rose", "the white rose"))
    }

    @Test
    fun `words the current text no longer has are marked together`() {
        assertEquals(listOf("old and grey"), marked("the old and grey rose", "the rose"))
    }

    @Test
    fun `words only the current text has mark nothing in the version`() {
        assertEquals(emptyList<String>(), marked("the rose", "the old rose"))
    }

    @Test
    fun `a whole version that differs is marked`() {
        assertEquals(listOf("one two"), marked("one two", "three"))
        assertEquals(listOf("text"), marked("text", ""))
    }

    @Test
    fun `punctuation is compared on its own`() {
        assertEquals(listOf(";"), marked("sea; sky", "sea, sky"))
    }

    @Test
    fun `lines are compared by word across line breaks`() {
        assertEquals(listOf("cold"), marked("a\ncold\nb", "a\nwarm\nb"))
    }
}
