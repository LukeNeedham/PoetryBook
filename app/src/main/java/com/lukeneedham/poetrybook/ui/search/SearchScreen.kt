package com.lukeneedham.poetrybook.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lukeneedham.poetrybook.R
import com.lukeneedham.poetrybook.data.SearchPoem
import com.lukeneedham.poetrybook.data.searchPoems
import com.lukeneedham.poetrybook.ui.book.PoemBookViewModel
import com.lukeneedham.poetrybook.ui.theme.PoemFont
import com.lukeneedham.poetrybook.ui.theme.paperColors

/**
 * Every poem in every book, filtered by what is typed in the search bar and ordered by how well
 * they match it, or alphabetically by title while the bar is empty. Searches the current title and
 * text of each poem only.
 */
@Composable
fun SearchScreen(
    viewModel: PoemBookViewModel,
    onBack: () -> Unit,
    onOpenPoem: (SearchPoem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = paperColors()
    var all by remember { mutableStateOf<List<SearchPoem>?>(null) }
    LaunchedEffect(Unit) { all = viewModel.searchablePoems() }

    val queryState = rememberTextFieldState()
    val query = queryState.text.toString()
    val results = remember(all, query) { all?.let { searchPoems(it, query) } }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val searchStyle = TextStyle(fontFamily = PoemFont, fontSize = 18.sp, color = colors.ink)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.paper)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_left),
                    contentDescription = stringResource(R.string.back),
                    tint = colors.accent,
                )
            }
            BasicTextField(
                state = queryState,
                textStyle = searchStyle,
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                cursorBrush = SolidColor(colors.accent),
                decorator = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (queryState.text.isEmpty()) {
                            Text(
                                text = stringResource(R.string.search_hint),
                                style = searchStyle.copy(color = colors.faintInk),
                            )
                        }
                        inner()
                    }
                },
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { queryState.edit { replace(0, length, "") } }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = null,
                        tint = colors.accent,
                    )
                }
            }
        }
        HorizontalDivider(color = colors.faintInk.copy(alpha = 0.25f))

        if (results != null && results.isEmpty()) {
            Text(
                text = stringResource(R.string.search_no_results),
                style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                color = colors.faintInk,
                modifier = Modifier.padding(24.dp),
            )
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(results.orEmpty(), key = { it.id }) { poem ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenPoem(poem) }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = poem.title.ifBlank { stringResource(R.string.untitled) },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = PoemFont,
                            fontStyle = if (poem.title.isBlank()) FontStyle.Italic else FontStyle.Normal,
                        ),
                        color = if (poem.title.isBlank()) colors.faintInk else colors.ink,
                    )
                    Text(
                        text = poem.bookName,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.faintInk,
                    )
                }
            }
        }
    }
}
