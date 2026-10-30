package com.lukeneedham.poetrybook.data

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** How many versions of a poem's history get their changes worked out at a time. */
const val HistoryChunkSize = 10

/**
 * The notepads and histories of poems, kept in memory once they have been read. The book page (its
 * notepad and history sheets) and the history page both show them, and the history page opens
 * straight onto what the book page has already worked out, so they live here and not in either page's
 * view model.
 */
class HistoryRepository(private val poetry: PoetryRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // --- Notepad ---

    /** Notes already read from the store, so the notepad can open without waiting for them. */
    private val notesCache = mutableStateMapOf<String, String>()

    /** The poem's notes if [loadNotes] has read them, else null. */
    fun loadedNotes(poemId: String): String? = notesCache[poemId]

    /** Reads the poem's notes into memory, unless that has been done already. */
    suspend fun loadNotes(poemId: String) {
        if (poemId in notesCache) return
        val notes = poetry.notes(poemId)
        // A save while we were reading is newer than what was read.
        notesCache.putIfAbsent(poemId, notes)
    }

    /** Saves the poem's notepad. This is not an edit of the poem, so it makes no new version. */
    fun saveNotes(poemId: String, notes: String) {
        notesCache[poemId] = notes
        poetry.setNotes(poemId, notes)
    }

    // --- History ---

    /** Every version of the poem, newest first, kept up to date. */
    fun history(poemId: String): Flow<List<PoemVersion>> = poetry.observeHistory(poemId)

    /** Every version of each poem being tracked, newest first. */
    private val historyVersions = HashMap<String, List<PoemVersion>>()

    /** The changes worked out so far, per poem and version time; a version's never change. */
    private val historyChanges = HashMap<String, HashMap<Long, VersionChange?>>()

    /** What the history sheet shows: each poem's versions that have their changes worked out. */
    private val historyCache = mutableStateMapOf<String, HistoryList>()

    /** Working out changes happens one job at a time. */
    private val historyLock = Mutex()

    /** The poem's history if [trackHistory] has read it, else null. */
    fun loadedHistory(poemId: String): HistoryList? = historyCache[poemId]

    /**
     * Keeps the poem's history up to date in memory until cancelled. Only the newest
     * [HistoryChunkSize] versions get their changes worked out at once, so the history is ready fast;
     * the next chunk follows straight away, and the rest when [loadMoreHistory] asks for it.
     */
    suspend fun trackHistory(poemId: String) {
        var first = true
        poetry.observeHistory(poemId).collect { versions ->
            historyVersions[poemId] = versions
            val have = historyCache[poemId]?.entries?.size ?: 0
            val target = maxOf(have, HistoryChunkSize)
            computeHistory(poemId, target)
            if (first && have == 0) computeHistory(poemId, target + HistoryChunkSize)
            first = false
        }
    }

    /** Works out the changes of the newest [count] versions of the poem, if that hasn't been done. */
    fun loadMoreHistory(poemId: String, count: Int) {
        scope.launch { computeHistory(poemId, count) }
    }

    private suspend fun computeHistory(poemId: String, count: Int) = historyLock.withLock {
        val versions = historyVersions[poemId] ?: return@withLock
        val target = count.coerceAtMost(versions.size)
        val cached = historyCache[poemId]
        if (cached != null && cached.total == versions.size && cached.entries.size >= target &&
            cached.entries.map { it.version.createdAt } == versions.take(cached.entries.size).map { it.createdAt }
        ) {
            return@withLock
        }
        val changes = historyChanges.getOrPut(poemId) { HashMap() }
        val entries = withContext(Dispatchers.Default) {
            versions.take(target).mapIndexed { index, version ->
                val change = if (version.createdAt in changes) {
                    changes[version.createdAt]
                } else {
                    versions.getOrNull(index + 1)?.let { changeBetween(it, version) }
                        .also { changes[version.createdAt] = it }
                }
                HistoryEntry(version, change)
            }
        }
        historyCache[poemId] = HistoryList(entries, versions.size)
    }
}
