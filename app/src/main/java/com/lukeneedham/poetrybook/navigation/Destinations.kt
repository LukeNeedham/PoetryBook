package com.lukeneedham.poetrybook.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Navigation 3 back stack keys. Add one per screen. */
@Serializable
data object Onboarding : NavKey

/** The bookshelf, the app's home. */
@Serializable
data object Shelf : NavKey

/** The book being read; which one is held by the view model. */
@Serializable
data object Book : NavKey

@Serializable
data object Settings : NavKey
