package com.lukeneedham.poetrybook.ui.book

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.lukeneedham.poetrybook.R
import com.lukeneedham.poetrybook.data.AppSettings
import com.lukeneedham.poetrybook.data.Poem
import com.lukeneedham.poetrybook.data.PoemFolder
import com.lukeneedham.poetrybook.data.PoemRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

enum class BookStatus {
    /** Looking at the poems folder. */
    LOADING,

    /** There's no usable poems folder: first launch, or access to it was lost. */
    NEEDS_FOLDER,

    READY,
}

/**
 * Owns the poems in the book. They live as text files in a folder the user chose; this keeps
 * them in memory while the app is open and writes changes back shortly after each edit.
 */
class PoemBookViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = AppSettings(application)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val storageLock = Mutex()
    private var repository: PoemRepository? = null
    private var saveJob: Job? = null

    var status by mutableStateOf(BookStatus.LOADING)
        private set

    /** True when a folder was chosen before but can no longer be read. */
    var folderLost by mutableStateOf(false)
        private set

    var folderUri: Uri? by mutableStateOf(null)
        private set

    /** The poems in page order. The book's last page, for adding a poem, is not one of these. */
    val poems = mutableStateListOf<Poem>()

    init {
        scope.launch { restore() }
    }

    private suspend fun restore() {
        val uri = settings.folderUri
        if (uri == null) {
            status = BookStatus.NEEDS_FOLDER
            return
        }
        val loaded = if (hasAccess(uri)) openFolder(uri) else null
        if (loaded == null) {
            folderLost = true
            status = BookStatus.NEEDS_FOLDER
            return
        }
        adopt(uri, loaded.first, loaded.second, welcomeIfEmpty = false)
        status = BookStatus.READY
    }

    /**
     * Starts using [uri], a folder just picked with the system picker, and calls [onDone] with
     * whether it worked. The book then shows the poems in that folder. The poems from the previous
     * folder stay there, untouched.
     */
    fun chooseFolder(uri: Uri, onDone: (Boolean) -> Unit) {
        scope.launch {
            val granted = runCatching {
                getApplication<Application>().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }.isSuccess
            val opened = if (granted) openFolder(uri) else null
            if (opened == null) {
                onDone(false)
                return@launch
            }

            // Changes made just before switching belong to the folder they were made in.
            saveJob?.cancel()
            saveNow()

            val previous = folderUri
            // Someone setting up for the first time starts with a poem to show what a page looks like.
            adopt(uri, opened.first, opened.second, welcomeIfEmpty = previous == null)
            if (previous != null && previous != uri) releaseAccess(previous)
            status = BookStatus.READY
            saveNow()
            onDone(true)
        }
    }

    private suspend fun openFolder(uri: Uri): Pair<PoemRepository, List<Poem>>? =
        withContext(Dispatchers.IO) {
            val repo = PoemRepository(PoemFolder(getApplication(), uri), settings)
            runCatching { repo to repo.load() }.getOrNull()
        }

    private fun adopt(uri: Uri, repo: PoemRepository, existing: List<Poem>, welcomeIfEmpty: Boolean) {
        repository = repo
        folderUri = uri
        folderLost = false
        settings.folderUri = uri
        poems.clear()
        poems.addAll(existing)
        if (existing.isEmpty() && welcomeIfEmpty) poems.add(welcomePoem())
    }

    private fun hasAccess(uri: Uri): Boolean =
        getApplication<Application>().contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission && it.isWritePermission
        }

    private fun releaseAccess(uri: Uri) {
        runCatching {
            getApplication<Application>().contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    fun updateTitle(id: String, title: String) = update(id) { it.copy(title = title) }

    fun updateBody(id: String, body: String) = update(id) { it.copy(body = body) }

    /**
     * Adds a blank poem at the end of the poems and returns its page index. That is the page the
     * add-a-poem page was on; the add-a-poem page moves along to stay last.
     */
    fun addPoem(): Int {
        poems.add(Poem(id = newId()))
        return poems.lastIndex
    }

    /** Removes the poem (and its file) and returns the index of the page to show instead. */
    fun deletePoem(id: String): Int {
        val index = poems.indexOfFirst { it.id == id }
        if (index < 0) return 0
        poems.removeAt(index)
        scheduleSave()
        // Past the last poem is the add-a-poem page, which is always there to land on.
        return index.coerceAtMost(poems.size)
    }

    /** Writes any pending changes now, e.g. when the app goes to the background. */
    fun flush() {
        if (repository == null) return
        saveJob?.cancel()
        scope.launch { saveNow() }
    }

    private fun update(id: String, change: (Poem) -> Poem) {
        val index = poems.indexOfFirst { it.id == id }
        if (index < 0) return
        val updated = change(poems[index])
        if (updated == poems[index]) return
        poems[index] = updated
        scheduleSave()
    }

    private fun scheduleSave() {
        if (repository == null) return
        saveJob?.cancel()
        saveJob = scope.launch {
            // Wait for a pause in typing so a title isn't renamed on every keystroke.
            delay(SAVE_DEBOUNCE_MS)
            var attempts = 0
            while (!saveNow() && ++attempts <= MAX_SAVE_RETRIES) delay(RETRY_DELAY_MS)
        }
    }

    /** Returns true if the folder now matches the book. */
    private suspend fun saveNow(): Boolean {
        val repo = repository ?: return true
        val snapshot = poems.toList()
        return storageLock.withLock {
            // Finish what was started even if the app is going away: a half-saved poem is worse.
            withContext(Dispatchers.IO + NonCancellable) {
                runCatching { repo.save(snapshot) }.getOrDefault(false)
            }
        }
    }

    override fun onCleared() {
        scope.cancel()
    }

    private fun welcomePoem() = Poem(
        id = newId(),
        title = getApplication<Application>().getString(R.string.welcome_title),
        body = getApplication<Application>().getString(R.string.welcome_body),
    )

    private fun newId() = UUID.randomUUID().toString()

    private companion object {
        const val SAVE_DEBOUNCE_MS = 800L
        const val RETRY_DELAY_MS = 2_000L
        const val MAX_SAVE_RETRIES = 3
    }
}
