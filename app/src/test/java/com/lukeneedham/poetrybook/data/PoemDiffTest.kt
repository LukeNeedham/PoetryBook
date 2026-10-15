package com.lukeneedham.poetrybook.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoemDiffTest {
    private fun changes(diff: VersionDiff) = diff.stanzas.map { s -> s.lines.map { it.change } }

    @Test
    fun `identical poems are the same`() {
        val poem = "one\ntwo\n\nthree\nfour"
        val diff = diffPoems(poem, poem)
        assertTrue(diff.isSame)
        assertEquals(2, diff.currentStanzaCount)
        assertEquals(listOf(listOf(LineChange.SAME, LineChange.SAME), listOf(LineChange.SAME, LineChange.SAME)), changes(diff))
    }

    @Test
    fun `spacing and trailing whitespace do not count as changes`() {
        assertTrue(diffPoems("a  b \nc", "a b\nc").isSame)
    }

    @Test
    fun `a line moved within its stanza is moved, the others stay`() {
        val diff = diffPoems("a one\nb two\nc three\nd four", "b two\nc three\nd four\na one")
        val lines = diff.stanzas.single().lines
        // The longest run in order (b, c, d) stays; a one moved to the end.
        assertEquals(listOf(LineChange.MOVED, LineChange.SAME, LineChange.SAME, LineChange.SAME), lines.map { it.change })
        assertEquals(LinePlace(1, 4), lines[0].movedTo)
        assertNull(lines[1].movedTo)
        assertFalse(diff.isSame)
    }

    @Test
    fun `a whole stanza moved is marked once, not line by line`() {
        val old = "red rose\nblue sky\n\ngreen leaf\nwhite snow\n\nblack night\nyellow sun"
        val now = "green leaf\nwhite snow\n\nblack night\nyellow sun\n\nred rose\nblue sky"
        val diff = diffPoems(old, now)
        assertEquals(3, diff.currentStanzaCount)
        assertEquals(listOf(3, null, null), diff.stanzas.map { it.movedTo })
        assertTrue(diff.stanzas.all { s -> s.lines.all { it.change == LineChange.SAME } })
        assertFalse(diff.isSame)
    }

    @Test
    fun `a tweaked line is edited with only the changed words marked`() {
        val diff = diffPoems("the old grey sea\nrolls on", "the old blue sea\nrolls on")
        val line = diff.stanzas.single().lines[0]
        assertEquals(LineChange.EDITED, line.change)
        assertEquals(listOf("grey"), line.edited.map { line.text.substring(it.first, it.last + 1) })
        assertEquals(LineChange.SAME, diff.stanzas.single().lines[1].change)
    }

    @Test
    fun `a line that is gone is removed and a new line is added after its neighbour`() {
        val diff = diffPoems("first\nsecond\nthird", "first\nsecond\nbrand new idea\nthird")
        val lines = diff.stanzas.single().lines
        assertEquals(listOf(LineChange.SAME, LineChange.SAME, LineChange.SAME), lines.map { it.change })
        assertEquals(listOf(0, 1, 0), lines.map { it.addedAfter })

        val removed = diffPoems("first\ndoomed words here\nthird", "first\nthird")
        assertEquals(LineChange.REMOVED, removed.stanzas.single().lines[1].change)
    }

    @Test
    fun `lines added before everything are counted at the start`() {
        val diff = diffPoems("kept line", "new first\nnew second\nkept line")
        assertEquals(2, diff.addedAtStart)
        assertFalse(diff.isSame)
    }

    @Test
    fun `an empty older version has everything added`() {
        val diff = diffPoems("", "one\ntwo")
        assertEquals(emptyList<DiffStanza>(), diff.stanzas)
        assertEquals(2, diff.addedAtStart)
    }

    @Test
    fun `a repeated chorus is not reported as moved`() {
        val poem = "verse one\n\nla la la\nsing along\n\nverse two\n\nla la la\nsing along"
        assertTrue(diffPoems(poem, poem).isSame)
    }

    @Test
    fun `a line moved to another stanza is moved with its new place`() {
        val diff = diffPoems("alpha one\nbeta two\n\ngamma three\ndelta four", "alpha one\n\ngamma three\ndelta four\nbeta two")
        val moved = diff.stanzas[0].lines[1]
        assertEquals(LineChange.MOVED, moved.change)
        assertEquals(LinePlace(2, 3), moved.movedTo)
    }
}
