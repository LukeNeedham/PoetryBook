package com.lukeneedham.poetrybook.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PoemFileNamesTest {
    @Test
    fun `plain titles are used as they are`() {
        assertEquals("Ode to Spring", fileNameFor("Ode to Spring"))
    }

    @Test
    fun `characters not allowed in file names are dropped`() {
        assertEquals("Why do we dream", fileNameFor("Why/do: we *dream*?"))
        assertEquals("a b", fileNameFor("a\n\tb"))
    }

    @Test
    fun `blank titles become Untitled`() {
        assertEquals("Untitled", fileNameFor(""))
        assertEquals("Untitled", fileNameFor("   "))
        assertEquals("Untitled", fileNameFor("???"))
    }

    @Test
    fun `leading and trailing dots are removed`() {
        assertEquals("hidden", fileNameFor("..hidden"))
        assertEquals("end", fileNameFor("end. ."))
    }

    @Test
    fun `very long titles are shortened`() {
        assertEquals(100, fileNameFor("x".repeat(300)).length)
    }

    @Test
    fun `duplicate titles get a number, ignoring case`() {
        assertEquals(
            listOf("Rose", "Rose (2)", "rose (3)", "Lily"),
            uniqueFileNames(listOf("Rose", "Rose", "rose", "Lily")),
        )
    }

    @Test
    fun `a numbered name is not reused`() {
        assertEquals(listOf("Rose (2)", "Rose", "Rose (3)"), uniqueFileNames(listOf("Rose (2)", "Rose", "Rose")))
    }

    @Test
    fun `blank titles are numbered too`() {
        assertEquals(listOf("Untitled", "Untitled (2)"), uniqueFileNames(listOf("", "")))
    }
}
