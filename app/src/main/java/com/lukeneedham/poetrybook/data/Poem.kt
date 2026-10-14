package com.lukeneedham.poetrybook.data

import kotlinx.serialization.Serializable

/** One page of the book. Poems are plain text; line breaks in [body] are the poem's lines. */
@Serializable
data class Poem(
    val id: String,
    val title: String = "",
    val body: String = "",
)
