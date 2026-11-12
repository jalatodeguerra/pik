package com.pik.app.lock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pik.app.R
import com.pik.app.ui.components.PikPrimaryButton
import com.pik.app.ui.theme.PikColors

/**
 * Shows [content] and, while the app is locked, a full-screen "locked" state on top of it. The app
 * below stays composed (so the screen and the back stack are still there after unlocking) but is
 * hidden from touch and from screen readers. Because the overlay is opaque, a quick-add shortcut
 * that is opened while locked also waits behind it.
 */
@Composable
fun LockHost(appLock: AppLock, content: @Composable () -> Unit) {
    val locked by appLock.locked.collectAsStateWithLifecycle()
    val covered = locked != false
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (covered) Modifier.clearAndSetSemantics { } else Modifier),
        ) {
            content()
        }
        if (locked == null) {
            // Still reading the setting: cover the app instead of flashing it.
            Box(Modifier.fillMaxSize().background(PikColors.Background))
        } else if (locked == true) {
            LockScreen(appLock)
        }
    }
}

@Composable
private fun LockScreen(appLock: AppLock) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val promptTitle = stringResource(R.string.lock_unlock_title)
    // The back button leaves the app instead of going through the screens hidden below.
    BackHandler { activity?.moveTaskToBack(true) }

    fun promptNow() {
        if (activity == null) return
        BiometricAuth.authenticate(
            activity = activity,
            title = promptTitle,
            subtitle = null,
            onSuccess = { appLock.markUnlocked() },
            onFailure = { },
        )
    }

    // If the phone lost its screen lock and fingerprints, nothing could ever unlock the app:
    // switch the lock off (the data file is not encrypted anyway).
    LaunchedEffect(Unit) {
        if (BiometricAuth.isPermanentlyUnavailable(context)) appLock.disable()
    }

    // Ask once when the lock screen appears (this composable only exists while locked).
    var asked by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        if (!asked) {
            asked = true
            promptNow()
        }
        onPauseOrDispose { }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PikColors.Background)
            .pointerInput(Unit) { detectTapGestures { } }
            .systemBarsPadding()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(160.dp),
            )
            Text(
                text = stringResource(R.string.lock_locked_title),
                style = MaterialTheme.typography.headlineLarge,
                color = PikColors.Lime,
            )
            Text(
                text = stringResource(R.string.lock_locked_message),
                style = MaterialTheme.typography.bodyMedium,
                color = PikColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            PikPrimaryButton(
                text = stringResource(R.string.lock_unlock),
                onClick = { promptNow() },
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
