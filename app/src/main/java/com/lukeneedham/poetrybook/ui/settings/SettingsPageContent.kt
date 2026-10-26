package com.lukeneedham.poetrybook.ui.settings

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

internal sealed interface ImportState {
    data object Idle : ImportState
    data object Running : ImportState
    data object Failed : ImportState
    data class Done(val result: ImportResult) : ImportState
}

@Composable
internal fun SettingsPageContent(
    importState: ImportState,
    onChooseFolder: () -> Unit,
    onBack: () -> Unit,
    onOpenBin: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenDebug: (() -> Unit)? = null,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_left),
                        contentDescription = stringResource(R.string.back),
                    )
                }
                Text(
                    text = stringResource(R.string.settings),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }

            Text(
                text = stringResource(R.string.bin),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenBin)
                    .padding(horizontal = 8.dp, vertical = 16.dp),
            )
            HorizontalDivider()

            if (onOpenDebug != null) {
                Text(
                    text = stringResource(R.string.debug),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenDebug)
                        .padding(horizontal = 8.dp, vertical = 16.dp),
                )
                HorizontalDivider()
            }

            Text(
                text = stringResource(R.string.import_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp, top = 24.dp),
            )
            Text(
                text = stringResource(R.string.import_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp),
            )
            Button(
                onClick = onChooseFolder,
                enabled = importState != ImportState.Running,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 16.dp),
            ) {
                Text(stringResource(R.string.import_choose_folder))
            }

            val status = when (val state = importState) {
                ImportState.Idle -> null
                ImportState.Running -> stringResource(R.string.importing)
                ImportState.Failed -> stringResource(R.string.import_failed)
                is ImportState.Done -> stringResource(R.string.import_done, state.result.books, state.result.poems)
            }
            if (status != null) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (importState == ImportState.Failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 24.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun Preview() {
    PoetryBookTheme {
        SettingsPageContent(
            importState = ImportState.Done(ImportResult(books = 3, poems = 42)),
            onChooseFolder = {},
            onBack = {},
            onOpenBin = {},
        )
    }
}
