package com.lukeneedham.poetrybook.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Navigation 3 back stack keys. Add one per screen. */

/** The bookshelf, the app's home. */
@Serializable
data object Shelf : NavKey

/** The book being read; which one is held by the view model. */
@Serializable
data object Book : NavKey

/** An earlier version of a poem, read-only. [createdAt] is the version's timestamp. */
@Serializable
data class History(val poemId: String, val createdAt: Long) : NavKey

/** Search across every poem. */
@Serializable
data object Search : NavKey

@Serializable
data object Settings : NavKey

/** Deleted books and poems. */
@Serializable
data object Bin : NavKey

@Serializable
data class BinPoemPreview(val poemId: String) : NavKey

@Serializable
data class BinBookPreview(val bookId: String) : NavKey
