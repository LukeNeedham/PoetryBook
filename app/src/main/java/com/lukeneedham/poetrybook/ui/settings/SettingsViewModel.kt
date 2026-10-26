package com.lukeneedham.poetrybook.ui.settings

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lukeneedham.poetrybook.data.PoetryRepository
import kotlinx.coroutines.launch

/** The settings page: importing poems from a folder. */
internal class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val poetry = PoetryRepository.get(application)

    /** How the import is going. */
    var importState: ImportState by mutableStateOf(ImportState.Idle)
        private set

    /** Copies the books and poems in the directory [treeUri] into the app, once. */
    fun importDirectory(treeUri: Uri) {
        importState = ImportState.Running
        viewModelScope.launch {
            val result = poetry.importDirectory(treeUri)
            importState = if (result == null) ImportState.Failed else ImportState.Done(result)
        }
    }
}
