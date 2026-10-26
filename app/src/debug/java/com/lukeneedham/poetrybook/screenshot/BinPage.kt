package com.lukeneedham.poetrybook.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.lukeneedham.poetrybook.data.BinBook
import com.lukeneedham.poetrybook.data.BinPoem
import com.lukeneedham.poetrybook.ui.bin.BinPageContent
import com.lukeneedham.poetrybook.ui.theme.AppTheme

/** Rendered by CI to docs/screenshots/<position in order.txt>-BinPage.png: the bin with a deleted book and poem. */
@Preview
@Composable
private fun BinPageScreenshot() {
    AppTheme {
        BinPageContent(
            books = listOf(BinBook("b", "Winter", null, poemCount = 4, deletedAt = 1_700_000_000_000)),
            poems = listOf(BinPoem("p", "Evening", "The light goes slowly", "Love poems", deletedAt = 1_700_100_000_000)),
            onBack = {},
            onOpenBook = {},
            onOpenPoem = {},
        )
    }
}
