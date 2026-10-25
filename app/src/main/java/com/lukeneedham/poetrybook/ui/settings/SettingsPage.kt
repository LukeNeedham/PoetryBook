package com.lukeneedham.poetrybook.ui.settings

import com.lukeneedham.poetrybook.ui.appViewModel
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lukeneedham.poetrybook.R
import com.lukeneedham.poetrybook.ui.theme.PoetryBookTheme
import com.lukeneedham.poetrybook.data.ImportResult

/**
 * Settings: the bin of deleted books and poems, and a one-off import of poems from a folder.
 *
 * Importing copies the books and poems in the picked folder into the app. The folder isn't kept.
 *
 * @param onOpenDebug opens the debug page. Null (the default) hides the entry, so release builds
 * show none.
 */
@Composable
fun SettingsPage(
    onBack: () -> Unit,
    onOpenBin: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenDebug: (() -> Unit)? = null,
) {
    val viewModel = appViewModel { SettingsViewModel(it) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.importDirectory(uri)
    }

    SettingsPageContent(
        importState = viewModel.importState,
        onChooseFolder = { picker.launch(null) },
        onBack = onBack,
        onOpenBin = onOpenBin,
        modifier = modifier,
        onOpenDebug = onOpenDebug,
    )
}
