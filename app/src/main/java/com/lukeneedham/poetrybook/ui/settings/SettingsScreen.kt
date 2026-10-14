package com.lukeneedham.poetrybook.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lukeneedham.poetrybook.R
import com.lukeneedham.poetrybook.data.folderLabel
import com.lukeneedham.poetrybook.data.openFolderInFileBrowser

/**
 * Settings: where the poems are stored, with shortcuts to look at that folder in the system file
 * browser or to pick a different one.
 *
 * @param onFolderPicked see [com.lukeneedham.poetrybook.ui.onboarding.OnboardingScreen].
 */
@Composable
fun SettingsScreen(
    folderUri: Uri?,
    onBack: () -> Unit,
    onFolderPicked: (Uri, onDone: (Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var pickFailed by rememberSaveable { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        pickFailed = false
        onFolderPicked(uri) { ok -> pickFailed = !ok }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                Text(
                    text = stringResource(R.string.settings),
                    style = MaterialTheme.typography.titleLarge,
                )
            }

            Text(
                text = stringResource(R.string.poems_folder),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp, top = 24.dp),
            )
            Text(
                text = stringResource(R.string.poems_folder_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp),
            )

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 16.dp),
            ) {
                val label = remember(folderUri) {
                    folderUri?.let { folderLabel(context, it) }
                }
                Text(
                    text = label ?: stringResource(R.string.no_folder),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp),
                )
            }

            Button(
                onClick = {
                    val opened = folderUri?.let { openFolderInFileBrowser(context, it) } == true
                    if (!opened) {
                        Toast.makeText(context, R.string.no_file_browser, Toast.LENGTH_LONG).show()
                    }
                },
                enabled = folderUri != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 16.dp),
            ) {
                Text(stringResource(R.string.open_in_file_browser))
            }
            OutlinedButton(
                onClick = { picker.launch(folderUri) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 8.dp),
            ) {
                Text(stringResource(R.string.choose_different_folder))
            }

            Text(
                text = if (pickFailed) {
                    stringResource(R.string.onboarding_folder_failed)
                } else {
                    stringResource(R.string.change_folder_note)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (pickFailed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 24.dp),
            )
        }
    }
}
