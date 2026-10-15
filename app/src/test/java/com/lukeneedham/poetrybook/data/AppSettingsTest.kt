package com.lukeneedham.poetrybook.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The small preferences store: shelf sort order, and what the old folder-based app remembered. */
@RunWith(RobolectricTestRunner::class)
class AppSettingsTest {
    private lateinit var context: Application
    private lateinit var settings: AppSettings

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
        settings = AppSettings(context)
    }

    private fun prefs() = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // --- Sort ---

    @Test
    fun `the shelf is sorted alphabetically until the user chooses otherwise`() {
        assertEquals(BookSort.ALPHABETICAL, settings.bookSort)
    }

    @Test
    fun `every sort order can be saved and read back`() {
        BookSort.entries.forEach { sort ->
            settings.bookSort = sort
            assertEquals(sort, settings.bookSort)
        }
    }

    @Test
    fun `the sort order is remembered by a new settings object`() {
        settings.bookSort = BookSort.LAST_UPDATED
        assertEquals(BookSort.LAST_UPDATED, AppSettings(context).bookSort)
    }

    @Test
    fun `the sort order can be changed back`() {
        settings.bookSort = BookSort.LAST_UPDATED
        settings.bookSort = BookSort.ALPHABETICAL
        assertEquals(BookSort.ALPHABETICAL, AppSettings(context).bookSort)
    }

    @Test
    fun `an unknown saved sort name falls back to alphabetical`() {
        prefs().edit().putString("bookSort", "BY_COLOUR").commit()
        assertEquals(BookSort.ALPHABETICAL, settings.bookSort)
    }

    @Test
    fun `an empty saved sort name falls back to alphabetical`() {
        prefs().edit().putString("bookSort", "").commit()
        assertEquals(BookSort.ALPHABETICAL, settings.bookSort)
    }

    @Test
    fun `sort names are matched exactly`() {
        prefs().edit().putString("bookSort", "last_updated").commit()
        assertEquals(BookSort.ALPHABETICAL, settings.bookSort)
    }

    // --- Legacy page order ---

    @Test
    fun `no saved page order gives an empty list`() {
        assertEquals(emptyList<String>(), settings.legacyPageOrder("any|key"))
    }

    @Test
    fun `a saved page order is read in order`() {
        prefs().edit().putString("order|folder|1", JSONArray(listOf("c", "a", "b")).toString()).commit()
        assertEquals(listOf("c", "a", "b"), settings.legacyPageOrder("folder|1"))
    }

    @Test
    fun `an empty saved page order is an empty list`() {
        prefs().edit().putString("order|k", "[]").commit()
        assertEquals(emptyList<String>(), settings.legacyPageOrder("k"))
    }

    @Test
    fun `page orders are kept per folder`() {
        prefs().edit()
            .putString("order|one", JSONArray(listOf("a")).toString())
            .putString("order|two", JSONArray(listOf("b")).toString())
            .commit()
        assertEquals(listOf("a"), settings.legacyPageOrder("one"))
        assertEquals(listOf("b"), settings.legacyPageOrder("two"))
        assertEquals(emptyList<String>(), settings.legacyPageOrder("three"))
    }

    @Test
    fun `names with odd characters survive the saved order`() {
        val names = listOf("Ünï", "日本語", "with \"quotes\"", "back\\slash", "new\nline", "[brackets]")
        prefs().edit().putString("order|k", JSONArray(names).toString()).commit()
        assertEquals(names, settings.legacyPageOrder("k"))
    }

    @Test
    fun `a saved order that is not valid json gives an empty list`() {
        listOf("", "not json", "{\"a\":1}", "[1, 2", "null", "[[\"nested\"]]").forEach { bad ->
            prefs().edit().putString("order|k", bad).commit()
            // Whatever comes back must be usable and must not throw.
            val order = settings.legacyPageOrder("k")
            assertEquals("for <$bad>", order, order.toList())
        }
    }

    @Test
    fun `garbage in the saved order gives an empty list for the clearly broken cases`() {
        prefs().edit().putString("order|k", "not json").commit()
        assertEquals(emptyList<String>(), settings.legacyPageOrder("k"))
        prefs().edit().putString("order|k", "{\"a\":1}").commit()
        assertEquals(emptyList<String>(), settings.legacyPageOrder("k"))
    }

    // --- Legacy colour and time ---

    @Test
    fun `no saved colour gives null`() {
        assertNull(settings.legacyBookColor("doc"))
    }

    @Test
    fun `saved colours are read back, including zero and negative values`() {
        listOf(0, 1, -1, 0xFF112233.toInt(), Int.MIN_VALUE, Int.MAX_VALUE).forEach { color ->
            prefs().edit().putInt("color|doc", color).commit()
            assertEquals(color, settings.legacyBookColor("doc"))
        }
    }

    @Test
    fun `colours are kept per book`() {
        prefs().edit().putInt("color|a", 1).putInt("color|b", 2).commit()
        assertEquals(1, settings.legacyBookColor("a"))
        assertEquals(2, settings.legacyBookColor("b"))
        assertNull(settings.legacyBookColor("c"))
    }

    @Test
    fun `no saved time gives zero`() {
        assertEquals(0L, settings.legacyBookUpdated("doc"))
    }

    @Test
    fun `saved times are read back per book`() {
        prefs().edit().putLong("updated|a", 5L).putLong("updated|b", Long.MAX_VALUE).commit()
        assertEquals(5L, settings.legacyBookUpdated("a"))
        assertEquals(Long.MAX_VALUE, settings.legacyBookUpdated("b"))
        assertEquals(0L, settings.legacyBookUpdated("c"))
    }

    @Test
    fun `reading the old settings never changes them`() {
        prefs().edit().putInt("color|a", 1).putLong("updated|a", 2L).putString("order|a", "[\"x\"]").commit()
        val before = prefs().all.toMap()
        settings.legacyBookColor("a"); settings.legacyBookUpdated("a"); settings.legacyPageOrder("a")
        assertEquals(before, prefs().all.toMap())
    }

    @Test
    fun `sort and legacy keys do not interfere`() {
        settings.bookSort = BookSort.LAST_UPDATED
        prefs().edit().putInt("color|bookSort", 7).commit()
        assertEquals(BookSort.LAST_UPDATED, settings.bookSort)
        assertEquals(7, settings.legacyBookColor("bookSort"))
    }
}
