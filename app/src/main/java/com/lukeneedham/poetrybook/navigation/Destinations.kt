package com.lukeneedham.poetrybook.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Navigation 3 back stack keys. Add one per screen. */

/** The bookshelf, the app's home. */
@Serializable
data object ShelfNavKey : NavKey

/** The book being read; which one is held by the view model. */
@Serializable
data object BookNavKey : NavKey

/** An earlier version of a poem, read-only. [createdAt] is the version's timestamp. */
@Serializable
data class HistoryNavKey(val poemId: String, val createdAt: Long) : NavKey

/** Search across every poem. */
@Serializable
data object SearchNavKey : NavKey

@Serializable
data object SettingsNavKey : NavKey

/** Debug hub (debug builds only), with the logs and crashes pages below it. */
@Serializable
data object DebugNavKey : NavKey

@Serializable
data object LogsNavKey : NavKey

@Serializable
data object CrashesNavKey : NavKey

/** Deleted books and poems. */
@Serializable
data object BinNavKey : NavKey

@Serializable
data class BinPoemNavKey(val poemId: String) : NavKey

@Serializable
data class BinBookNavKey(val bookId: String) : NavKey
