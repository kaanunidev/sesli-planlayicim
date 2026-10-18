package com.sesliplanlayici.app.capture.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.sesliplanlayici.app.R
import com.sesliplanlayici.app.core.ui.findActivity
import com.sesliplanlayici.app.core.ui.openAppSettings
import com.sesliplanlayici.app.core.ui.theme.SesliPlanlayiciTheme

private const val MIC_PERMISSION = Manifest.permission.RECORD_AUDIO

/**
 * Prototype capture screen. For now it only handles the microphone permission; speech
 * recognition is the next step. The permission is asked when the user taps the mic, not on launch.
 */
@Composable
fun CaptureScreen() {
    val context = LocalContext.current
    fun hasPermission() =
        ContextCompat.checkSelfPermission(context, MIC_PERMISSION) == PackageManager.PERMISSION_GRANTED

    // Mirrors system state, so it is re-read on every resume instead of living in a ViewModel.
    var status by rememberSaveable {
        mutableStateOf(if (hasPermission()) MicPermissionStatus.Granted else MicPermissionStatus.NotGranted)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val canAskAgain = context.findActivity()?.shouldShowRequestPermissionRationale(MIC_PERMISSION) == true
        status = MicPermissionStatus.afterRequest(granted, canAskAgain)
    }

    LifecycleResumeEffect(Unit) {
        status = MicPermissionStatus.onResume(status, hasPermission())
        onPauseOrDispose { }
    }

    CaptureContent(
        status = status,
        onMicClick = {
            when (status) {
                MicPermissionStatus.Granted -> Unit // Speech recognition comes in the next step.
                MicPermissionStatus.NotGranted -> launcher.launch(MIC_PERMISSION)
                MicPermissionStatus.Blocked -> context.openAppSettings()
            }
        },
        onOpenSettings = { context.openAppSettings() },
    )
}

@Composable
fun CaptureContent(
    status: MicPermissionStatus,
    onMicClick: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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

            val message = when (status) {
                MicPermissionStatus.Granted -> R.string.capture_hint
                MicPermissionStatus.NotGranted -> R.string.capture_mic_rationale
                MicPermissionStatus.Blocked -> R.string.capture_mic_blocked
            }
            Text(
                text = stringResource(message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            if (status == MicPermissionStatus.Blocked) {
                Button(onClick = onOpenSettings) {
                    Text(stringResource(R.string.capture_open_settings))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CaptureContentNotGrantedPreview() {
    SesliPlanlayiciTheme {
        CaptureContent(status = MicPermissionStatus.NotGranted, onMicClick = {}, onOpenSettings = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun CaptureContentGrantedPreview() {
    SesliPlanlayiciTheme {
        CaptureContent(status = MicPermissionStatus.Granted, onMicClick = {}, onOpenSettings = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun CaptureContentBlockedPreview() {
    SesliPlanlayiciTheme {
        CaptureContent(status = MicPermissionStatus.Blocked, onMicClick = {}, onOpenSettings = {})
    }
}
