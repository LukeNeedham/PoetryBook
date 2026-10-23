package com.lukeneedham.poetrybook.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WordChangesTest {
    @Test
    fun `identical text has no changes`() {
        assertEquals(WordChanges(0, 0, 0), wordChanges("the red rose", "The red, rose"))
    }

    @Test
    fun `new and dropped words are counted`() {
        assertEquals(WordChanges(added = 2, removed = 0, moved = 0), wordChanges("the rose", "the old red rose"))
        assertEquals(WordChanges(added = 0, removed = 2, moved = 0), wordChanges("the old red rose", "the rose"))
    }

    @Test
    fun `a changed word is one removed and one added`() {
        assertEquals(WordChanges(added = 1, removed = 1, moved = 0), wordChanges("the red rose", "the white rose"))
    }

    @Test
    fun `a word in another place is moved`() {
        assertEquals(WordChanges(added = 0, removed = 0, moved = 1), wordChanges("red rose blooms", "rose blooms red"))
    }
}
