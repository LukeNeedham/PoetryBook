package com.lukeneedham.poetrybook.ui.book

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lukeneedham.poetrybook.R
import com.lukeneedham.poetrybook.data.Poem
import com.lukeneedham.poetrybook.ui.theme.PoemFont
import com.lukeneedham.poetrybook.ui.theme.paperColors

/**
 * The first page of every book: a generated page listing every poem, like the contents sheet.
 * Tapping a row calls [onSelect] with that poem's index.
 */
@Composable
fun ContentsPage(
    poems: List<Poem>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = paperColors()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.paper)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.contents),
            style = TextStyle(
                fontFamily = PoemFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                textAlign = TextAlign.Center,
                color = colors.ink,
            ),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp),
        )
        HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = colors.faintInk.copy(alpha = 0.4f))
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(poems, key = { _, poem -> poem.id }) { index, poem ->
                // The blank poem at the end has nothing to list yet.
                if (index == poems.lastIndex && poem.isBlank) return@itemsIndexed
                ContentsRow(
                    number = index + 1,
                    poem = poem,
                    current = false,
                    onClick = { onSelect(index) },
                    textColor = colors.ink,
                    currentColor = colors.accent,
                    numberColor = colors.faintInk,
                )
            }
        }
    }
}
