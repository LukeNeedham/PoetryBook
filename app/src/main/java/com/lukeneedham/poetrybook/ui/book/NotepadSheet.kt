package com.lukeneedham.poetrybook.ui.book

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lukeneedham.poetrybook.R
import com.lukeneedham.poetrybook.ui.theme.PoemFont
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.debounce

private const val SaveDebounceMillis = 400L

/**
 * The poem's notepad in a bottom sheet. It is always a text field, ready to type in, and what is
 * typed is saved as it goes. The notes belong to the whole poem, not to one version of it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotepadSheet(
    viewModel: PoemBookViewModel,
    poemId: String,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        var loaded by remember(poemId) { mutableStateOf<String?>(null) }
        LaunchedEffect(poemId) { loaded = viewModel.notes(poemId) }

        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .windowInsetsPadding(WindowInsets.ime)
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.notepad),
                style = MaterialTheme.typography.titleLarge,
            )
            loaded?.let { initial -> NotepadField(viewModel, poemId, initial) }
        }
    }
}

@OptIn(FlowPreview::class) // debounce
@Composable
private fun NotepadField(viewModel: PoemBookViewModel, poemId: String, initial: String) {
    val state = rememberTextFieldState(initial)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Save shortly after typing stops, and always when the sheet goes away.
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }
            .drop(1)
            .debounce(SaveDebounceMillis)
            .collect { viewModel.saveNotes(poemId, it) }
    }
    DisposableEffect(state) {
        onDispose {
            val text = state.text.toString()
            if (text != initial) viewModel.saveNotes(poemId, text)
        }
    }

    val style = TextStyle(
        fontFamily = PoemFont,
        fontSize = 17.sp,
        lineHeight = 26.sp,
        color = MaterialTheme.colorScheme.onSurface,
    )
    BasicTextField(
        state = state,
        textStyle = style,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorator = { inner ->
            Box {
                if (state.text.isEmpty()) {
                    Text(
                        text = stringResource(R.string.notepad_hint),
                        style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                }
                inner()
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp, max = 400.dp)
            .padding(top = 12.dp)
            .focusRequester(focusRequester),
    )
}
