package com.lukeneedham.poetrybook.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.lukeneedham.poetrybook.data.SearchPoem
import com.lukeneedham.poetrybook.ui.search.SearchPageContent
import com.lukeneedham.poetrybook.ui.theme.PoetryBookTheme

/** Rendered by CI to docs/screenshots/<position in order.txt>-SearchPage.png: the search across every poem. */
@Preview
@Composable
private fun SearchPageScreenshot() {
    PoetryBookTheme {
        SearchPageContent(
            all = listOf(
                SearchPoem("1", "a", "Love poems", "Evening", "The light goes slowly", 0xFF8E3B46.toInt()),
                SearchPoem("2", "b", "Winter", "First frost", "Silver on the grass", 0xFF2F4858.toInt()),
                SearchPoem("3", "b", "Winter", "Snow", "Everything quiet", 0xFF2F4858.toInt()),
            ),
            onBack = {},
            onOpenPoem = {},
        )
    }
}
