package com.sesliplanlayici.app.capture.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sesliplanlayici.app.R
import com.sesliplanlayici.app.capture.domain.ExtractionFailure
import com.sesliplanlayici.app.capture.domain.ExtractionResult
import com.sesliplanlayici.app.capture.domain.SpeechError
import com.sesliplanlayici.app.core.ui.findActivity
import com.sesliplanlayici.app.core.ui.openAppSettings
import com.sesliplanlayici.app.core.ui.theme.SesliPlanlayiciTheme

private const val MIC_PERMISSION = Manifest.permission.RECORD_AUDIO

/**
 * Capture screen: microphone permission, live speech-to-text, sending the transcript to the AI.
 * A plan opens the review screen ([onOpenReview]); other outcomes are shown here.
 * The permission is asked when the user taps the mic, not on launch.
 */
@Composable
fun CaptureScreen(onOpenReview: () -> Unit, viewModel: CaptureViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                CaptureEvent.OpenReview -> onOpenReview()
            }
        }
    }
    fun hasPermission() =
        ContextCompat.checkSelfPermission(context, MIC_PERMISSION) == PackageManager.PERMISSION_GRANTED

    // Mirrors system state, so it is re-read on every resume instead of living in the ViewModel.
    var permission by rememberSaveable {
        mutableStateOf(if (hasPermission()) MicPermissionStatus.Granted else MicPermissionStatus.NotGranted)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val canAskAgain = context.findActivity()?.shouldShowRequestPermissionRationale(MIC_PERMISSION) == true
        permission = MicPermissionStatus.afterRequest(granted, canAskAgain)
        if (granted) viewModel.startListening()
    }

    LifecycleResumeEffect(Unit) {
        permission = MicPermissionStatus.onResume(permission, hasPermission())
        onPauseOrDispose { }
    }

    LightStatusBarIcons(light = state.phase == CapturePhase.Listening)

    CaptureContent(
        state = state,
        permission = permission,
        onMicClick = {
            when (permission) {
                MicPermissionStatus.Granted -> viewModel.startListening()
                MicPermissionStatus.NotGranted -> launcher.launch(MIC_PERMISSION)
                MicPermissionStatus.Blocked -> context.openAppSettings()
            }
        },
        onOpenSettings = { context.openAppSettings() },
        onFinish = viewModel::finish,
        onCancel = viewModel::reset,
        onSpeakAgain = viewModel::reset,
        onListenAgain = viewModel::startListening,
        onRetryExtraction = viewModel::retryExtraction,
        onAddManually = viewModel::addManually,
    )
}

/** The recording screen is dark; flip the status bar icons while it is shown. */
@Composable
private fun LightStatusBarIcons(light: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(light) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = !light
        onDispose { controller?.isAppearanceLightStatusBars = true }
    }
}

@Composable
fun CaptureContent(
    state: CaptureUiState,
    permission: MicPermissionStatus,
    onMicClick: () -> Unit,
    onOpenSettings: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    onSpeakAgain: () -> Unit,
    onListenAgain: () -> Unit,
    onRetryExtraction: () -> Unit,
    onAddManually: () -> Unit,
) {
    when (state.phase) {
        CapturePhase.Idle -> IdleContent(permission, state.error, onMicClick, onOpenSettings)
        CapturePhase.Listening -> ListeningContent(state, onFinish, onCancel)
        CapturePhase.NothingHeard -> NothingHeardContent(
            onTryAgain = onListenAgain,
            onAddManually = onAddManually,
            onGiveUp = onCancel,
        )
        CapturePhase.Extracting -> ExtractingContent()
        CapturePhase.Result -> ResultContent(state, onSpeakAgain, onRetryExtraction, onAddManually)
    }
}

@Composable
private fun IdleContent(
    permission: MicPermissionStatus,
    error: SpeechError?,
    onMicClick: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.capture_title),
                style = MaterialTheme.typography.headlineLarge,
            )

            FilledIconButton(
                onClick = onMicClick,
                modifier = Modifier.size(76.dp),
                shape = CircleShape,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_mic),
                    contentDescription = stringResource(R.string.capture_mic_content_description),
                    modifier = Modifier.size(32.dp),
                )
            }

            val message = when {
                permission == MicPermissionStatus.NotGranted -> R.string.capture_mic_rationale
                permission == MicPermissionStatus.Blocked -> R.string.capture_mic_blocked
                error != null -> error.messageRes()
                else -> R.string.capture_hint
            }
            Text(
                text = stringResource(message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            if (permission == MicPermissionStatus.Blocked) {
                Button(onClick = onOpenSettings) {
                    Text(stringResource(R.string.capture_open_settings))
                }
            }
        }
    }
}

@Composable
private fun ListeningContent(
    state: CaptureUiState,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
) {
    val onDark = MaterialTheme.colorScheme.inverseOnSurface
    Surface(color = MaterialTheme.colorScheme.inverseSurface, contentColor = onDark) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(MaterialTheme.colorScheme.inversePrimary, CircleShape),
                )
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.capture_listening), style = MaterialTheme.typography.titleSmall)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.capture_said_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = onDark.copy(alpha = 0.7f),
                )
                Text(
                    text = state.transcript.ifEmpty { stringResource(R.string.capture_listening_placeholder) },
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (state.transcript.isEmpty()) onDark.copy(alpha = 0.5f) else onDark,
                )
            }

            Text(
                text = stringResource(R.string.capture_hint),
                style = MaterialTheme.typography.bodySmall,
                color = onDark.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton(text = stringResource(R.string.capture_finish), onClick = onFinish)
            TextButton(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text(stringResource(R.string.capture_cancel), color = onDark)
            }
        }
    }
}

@Composable
private fun NothingHeardContent(
    onTryAgain: () -> Unit,
    onAddManually: () -> Unit,
    onGiveUp: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_mic),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = stringResource(R.string.capture_nothing_heard_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.capture_nothing_heard_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            PrimaryButton(text = stringResource(R.string.capture_try_again), onClick = onTryAgain)
            SecondaryButton(text = stringResource(R.string.capture_add_manually), onClick = onAddManually)
            SecondaryButton(text = stringResource(R.string.capture_give_up), onClick = onGiveUp)
        }
    }
}

@Composable
private fun ExtractingContent() {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.capture_extracting),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ResultContent(
    state: CaptureUiState,
    onSpeakAgain: () -> Unit,
    onRetryExtraction: () -> Unit,
    onAddManually: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // A plan opens the review screen, so only the other outcomes are shown here.
                val message = when {
                    state.failure != null -> state.failure.messageRes()
                    state.result == ExtractionResult.OffTopic -> R.string.capture_off_topic
                    else -> R.string.capture_nothing_planned
                }
                MessageBlock(stringResource(message))
                TranscriptSection(state.committedText)
            }

            if (state.failure != null) {
                PrimaryButton(text = stringResource(R.string.capture_try_again), onClick = onRetryExtraction)
                SecondaryButton(text = stringResource(R.string.capture_add_manually), onClick = onAddManually)
                SecondaryButton(text = stringResource(R.string.capture_back_to_start), onClick = onSpeakAgain)
            } else {
                PrimaryButton(text = stringResource(R.string.capture_retry), onClick = onSpeakAgain)
                SecondaryButton(text = stringResource(R.string.capture_add_manually), onClick = onAddManually)
            }
        }
    }
}

@Composable
private fun MessageBlock(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(top = 24.dp),
    )
}

/** What was said, folded by default (kept visible in the prototype for checking the AI). */
@Composable
private fun TranscriptSection(transcript: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(
                stringResource(if (expanded) R.string.capture_hide_transcript else R.string.capture_show_transcript),
            )
        }
        if (expanded) {
            Text(
                text = transcript,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        Text(text)
    }
}

private fun SpeechError.messageRes(): Int = when (this) {
    SpeechError.NoSpeech -> R.string.capture_empty_result
    SpeechError.Interrupted, SpeechError.Network -> R.string.capture_error_network
    SpeechError.LanguageUnavailable -> R.string.capture_error_language
    SpeechError.NotAvailable -> R.string.capture_error_unavailable
    SpeechError.Busy -> R.string.capture_error_busy
    SpeechError.NoPermission -> R.string.capture_mic_rationale
    SpeechError.Other -> R.string.capture_error_generic
}

private fun ExtractionFailure.messageRes(): Int = when (this) {
    ExtractionFailure.Offline -> R.string.capture_failure_offline
    ExtractionFailure.RateLimited -> R.string.capture_failure_rate_limited
    ExtractionFailure.ServerError -> R.string.capture_failure_server
    ExtractionFailure.BadResponse -> R.string.capture_failure_bad_response
    ExtractionFailure.NotConfigured -> R.string.capture_failure_not_configured
}

private const val PREVIEW_TRANSCRIPT =
    "Yarın sabah onda maç var, öğleden sonra üç buçukta markete gideceğim, bir de annemi aramam lazım"

@Composable
private fun CapturePreview(state: CaptureUiState, permission: MicPermissionStatus = MicPermissionStatus.Granted) {
    SesliPlanlayiciTheme {
        CaptureContent(
            state = state,
            permission = permission,
            onMicClick = {},
            onOpenSettings = {},
            onFinish = {},
            onCancel = {},
            onSpeakAgain = {},
            onListenAgain = {},
            onRetryExtraction = {},
            onAddManually = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun IdleNotGrantedPreview() = CapturePreview(CaptureUiState(), MicPermissionStatus.NotGranted)

@Preview(showBackground = true)
@Composable
private fun IdleBlockedPreview() = CapturePreview(CaptureUiState(), MicPermissionStatus.Blocked)

@Preview(showBackground = true)
@Composable
private fun ListeningPreview() = CapturePreview(
    CaptureUiState(phase = CapturePhase.Listening, committedText = PREVIEW_TRANSCRIPT, partialText = "ve akşam"),
)

@Preview(showBackground = true)
@Composable
private fun NothingHeardPreview() = CapturePreview(CaptureUiState(phase = CapturePhase.NothingHeard))

@Preview(showBackground = true)
@Composable
private fun ExtractingPreview() = CapturePreview(CaptureUiState(phase = CapturePhase.Extracting))

@Preview(showBackground = true)
@Composable
private fun ResultOffTopicPreview() = CapturePreview(
    CaptureUiState(phase = CapturePhase.Result, committedText = "Bana bir fıkra anlat", result = ExtractionResult.OffTopic),
)

@Preview(showBackground = true)
@Composable
private fun ResultOfflinePreview() = CapturePreview(
    CaptureUiState(phase = CapturePhase.Result, committedText = PREVIEW_TRANSCRIPT, failure = ExtractionFailure.Offline),
)
