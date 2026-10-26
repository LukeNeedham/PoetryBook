package com.lukeneedham.poetrybook.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.lukeneedham.poetrybook.data.ImportResult
import com.lukeneedham.poetrybook.ui.settings.ImportState
import com.lukeneedham.poetrybook.ui.settings.SettingsPageContent
import com.lukeneedham.poetrybook.ui.theme.PoetryBookTheme

/** Rendered by CI to docs/screenshots/<position in order.txt>-SettingsPage.png: the settings after an import. */
@Preview
@Composable
private fun SettingsPageScreenshot() {
    PoetryBookTheme {
        SettingsPageContent(
            importState = ImportState.Done(ImportResult(books = 3, poems = 42)),
            onChooseFolder = {},
            onBack = {},
            onOpenBin = {},
        )
    }
}
