package com.lukeneedham.poetrybook.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Navigation 3 back stack keys: one member per page, so [Shelf] is `ShelfPage`. */
@Serializable
sealed interface Route : NavKey {
    /** The bookshelf, the app's home. */
    @Serializable
    data object Shelf : Route

    /** A book being read. [poemId] is a poem of it to open on, from a search result, or null. */
    @Serializable
    data class Book(val bookId: String, val poemId: String? = null) : Route

    /** An earlier version of a poem, read-only. [createdAt] is the version's timestamp. */
    @Serializable
    data class History(val poemId: String, val createdAt: Long) : Route

    /** Search across every poem. */
    @Serializable
    data object Search : Route

    @Serializable
    data object Settings : Route

    /** Debug hub (debug builds only), with the logs and crashes pages below it. */
    @Serializable
    data object Debug : Route

    @Serializable
    data object Logs : Route

    @Serializable
    data object Crashes : Route

    /** Deleted books and poems. */
    @Serializable
    data object Bin : Route

    @Serializable
    data class BinPoem(val poemId: String) : Route

    @Serializable
    data class BinBook(val bookId: String) : Route
}
