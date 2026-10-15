package com.lukeneedham.poetrybook.ui.history

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lukeneedham.poetrybook.R
import com.lukeneedham.poetrybook.data.PoemVersion
import com.lukeneedham.poetrybook.ui.book.PoemBookViewModel
import com.lukeneedham.poetrybook.ui.book.formatTimestamp
import com.lukeneedham.poetrybook.ui.theme.PoemFont
import com.lukeneedham.poetrybook.ui.theme.paperColors

private sealed interface VersionState {
    data object Loading : VersionState
    data object Missing : VersionState
    data class Found(val version: PoemVersion) : VersionState
}

/**
 * An earlier version of a poem, as it was when [createdAt] saved it. It can't be edited or
 * deleted here; the text can be selected and copied.
 */
@Composable
fun HistoryScreen(
    viewModel: PoemBookViewModel,
    poemId: String,
    createdAt: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = paperColors()
    val state by produceState<VersionState>(VersionState.Loading, poemId, createdAt) {
        value = viewModel.version(poemId, createdAt)?.let(VersionState::Found) ?: VersionState.Missing
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.paper)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Box(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_left),
                    contentDescription = stringResource(R.string.back),
                    tint = colors.accent,
                )
            }
            Text(
                text = formatTimestamp(createdAt),
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        HorizontalDivider(color = colors.faintInk.copy(alpha = 0.25f))

        when (val current = state) {
            VersionState.Loading -> Unit
            VersionState.Missing -> Text(
                text = stringResource(R.string.version_not_found),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.faintInk,
                modifier = Modifier.padding(24.dp),
            )
            is VersionState.Found -> VersionPage(current.version)
        }
    }
}

@Composable
private fun VersionPage(version: PoemVersion) {
    val colors = paperColors()
    val titleStyle = TextStyle(
        fontFamily = PoemFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        textAlign = TextAlign.Center,
        color = colors.ink,
    )
    val bodyStyle = TextStyle(fontFamily = PoemFont, fontSize = 19.sp, lineHeight = 30.sp, color = colors.ink)

    SelectionContainer(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.history_read_only),
                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                color = colors.faintInk,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            )
            Text(
                text = version.title.ifBlank { stringResource(R.string.untitled) },
                style = if (version.title.isBlank()) {
                    titleStyle.copy(color = colors.faintInk, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Normal)
                } else {
                    titleStyle
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            )
            Text(text = version.body, style = bodyStyle)
        }
    }
}
