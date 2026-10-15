package com.lukeneedham.poetrybook.ui.onboarding

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lukeneedham.poetrybook.R
import com.lukeneedham.poetrybook.ui.theme.PoemFont
import com.lukeneedham.poetrybook.ui.theme.TitleFont
import com.lukeneedham.poetrybook.ui.theme.PoetryBookTheme
import com.lukeneedham.poetrybook.ui.theme.paperColors

/**
 * First-run setup: a welcome, then the step where the user picks the folder their poems are saved
 * in and grants the app access to it. Also shown again if access to that folder is lost.
 *
 * @param onFolderPicked called with the folder from the system picker; reports back through its
 * callback whether the folder could be used.
 */
@Composable
fun OnboardingScreen(
    folderLost: Boolean,
    onFolderPicked: (Uri, onDone: (Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Someone who has already been through this only needs the folder step.
    var step by rememberSaveable { mutableIntStateOf(if (folderLost) 1 else 0) }
    var failed by rememberSaveable { mutableStateOf(false) }
    var working by rememberSaveable { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        failed = false
        working = true
        onFolderPicked(uri) { ok ->
            working = false
            failed = !ok
        }
    }

    OnboardingStep(
        step = step,
        modifier = modifier,
        firstStep = {
            StepContent(
                title = stringResource(R.string.onboarding_welcome_title),
                body = stringResource(R.string.onboarding_welcome_body),
                actionLabel = stringResource(R.string.onboarding_continue),
                onAction = { step = 1 },
            )
        },
        secondStep = {
            StepContent(
                title = stringResource(R.string.onboarding_folder_title),
                body = stringResource(R.string.onboarding_folder_body),
                note = when {
                    failed -> stringResource(R.string.onboarding_folder_failed)
                    folderLost -> stringResource(R.string.onboarding_folder_lost)
                    else -> null
                },
                noteIsError = failed || folderLost,
                actionLabel = stringResource(R.string.choose_folder),
                actionEnabled = !working,
                onAction = { picker.launch(null) },
            )
        },
    )
}

@Composable
private fun OnboardingStep(
    step: Int,
    modifier: Modifier,
    firstStep: @Composable () -> Unit,
    secondStep: @Composable () -> Unit,
) {
    val colors = paperColors()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.paper)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        AnimatedContent(targetState = step, label = "onboarding step") { current ->
            if (current == 0) firstStep() else secondStep()
        }
    }
}

@Composable
private fun StepContent(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    note: String? = null,
    noteIsError: Boolean = false,
    actionEnabled: Boolean = true,
) {
    val colors = paperColors()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = TitleFont),
            color = colors.ink,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = PoemFont),
            color = colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp),
        )
        if (note != null) {
            Text(
                text = note,
                style = MaterialTheme.typography.bodyMedium,
                color = if (noteIsError) MaterialTheme.colorScheme.error else colors.faintInk,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        Button(
            onClick = onAction,
            enabled = actionEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp),
        ) {
            Text(actionLabel)
        }
    }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun OnboardingPreview() {
    PoetryBookTheme {
        OnboardingScreen(folderLost = true, onFolderPicked = { _, _ -> })
    }
}
