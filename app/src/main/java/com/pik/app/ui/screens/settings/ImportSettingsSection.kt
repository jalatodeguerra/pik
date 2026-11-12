package com.pik.app.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pik.app.R
import com.pik.app.ui.components.IconCircle
import com.pik.app.ui.components.PikCard
import com.pik.app.ui.components.PikFilterChip
import com.pik.app.ui.components.PikPrimaryButton
import com.pik.app.ui.components.PikSecondaryButton
import com.pik.app.ui.components.PikSwitchRow
import com.pik.app.ui.components.SectionTitle
import com.pik.app.ui.screens.importing.ImportFileDialogs
import com.pik.app.ui.screens.importing.ImportFileViewModel
import com.pik.app.ui.screens.importing.rememberImportFileLauncher
import com.pik.app.ui.theme.PikColors

/**
 * The "Automatic import" part of Settings: notification access, which apps to read, importing a file, and the
 * way to the Inbox. It adds its cards straight into the caller's column.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportSettingsSection(
    snackbarHostState: SnackbarHostState,
    onOpenInbox: () -> Unit,
    viewModel: ImportSettingsViewModel = hiltViewModel(),
    fileViewModel: ImportFileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showAll by remember { mutableStateOf(false) }
    val openFilePicker = rememberImportFileLauncher(fileViewModel)
    ImportFileDialogs(fileViewModel, snackbarHostState)

    // The user turns the notification access on in the system settings: look again when coming back.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshAccess()
        onPauseOrDispose { }
    }

    SectionTitle(stringResource(R.string.settings_import))

    // ---- notification access ----
    PikCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.import_access_title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = PikColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(if (state.accessGranted) R.string.import_access_on else R.string.import_access_off),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (state.accessGranted) PikColors.Lime else PikColors.Amber,
                )
            }
            if (!state.accessGranted) {
                PikPrimaryButton(
                    text = stringResource(R.string.import_access_turn_on),
                    onClick = {
                        startSettings(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.import_access_restricted_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = PikColors.TextSecondary,
                )
                PikSecondaryButton(
                    text = stringResource(R.string.import_open_app_info),
                    onClick = {
                        startSettings(
                            context,
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.fromParts("package", context.packageName, null)),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                text = stringResource(R.string.import_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = PikColors.TextSecondary,
            )
        }
    }

    // ---- inbox ----
    PikCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenInbox) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconCircle(Icons.Filled.Inbox, PikColors.Lime, null)
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.inbox_title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = PikColors.TextPrimary,
                )
                Text(
                    text = if (state.pendingCount > 0) {
                        stringResource(R.string.home_inbox_title, state.pendingCount)
                    } else {
                        stringResource(R.string.inbox_settings_hint)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = PikColors.TextSecondary,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = PikColors.TextSecondary,
            )
        }
    }

    // ---- apps to read ----
    val visibleApps = if (showAll) state.apps else state.apps.filter { it.app.source != null || it.enabled }
    PikCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.import_apps_title),
                style = MaterialTheme.typography.bodyLarge,
                color = PikColors.TextPrimary,
            )
            Text(
                text = stringResource(R.string.import_apps_hint),
                style = MaterialTheme.typography.bodySmall,
                color = PikColors.TextSecondary,
            )
            if (!state.appsLoaded) {
                Text(
                    text = stringResource(R.string.import_apps_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PikColors.TextSecondary,
                )
            } else if (visibleApps.isEmpty()) {
                Text(
                    text = stringResource(R.string.import_apps_none_known),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PikColors.TextSecondary,
                )
            }
            visibleApps.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider(color = PikColors.Outline)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PikSwitchRow(
                        label = item.app.label,
                        checked = item.enabled,
                        onCheckedChange = { viewModel.setEnabled(item.app, it) },
                        supporting = if (item.app.source != null) stringResource(R.string.import_apps_suggested) else null,
                    )
                    if (item.enabled) {
                        Text(
                            text = stringResource(R.string.import_apps_account),
                            style = MaterialTheme.typography.labelLarge,
                            color = PikColors.TextSecondary,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            state.accounts.forEach { account ->
                                PikFilterChip(
                                    label = account.name,
                                    selected = account.id == item.accountId,
                                    onClick = { viewModel.setAccount(item.app, account.id) },
                                )
                            }
                        }
                    }
                }
            }
            if (state.appsLoaded && state.apps.size > visibleApps.size || showAll) {
                TextButton(
                    onClick = { showAll = !showAll },
                    colors = ButtonDefaults.textButtonColors(contentColor = PikColors.Lime),
                ) {
                    Text(stringResource(if (showAll) R.string.import_apps_show_fewer else R.string.import_apps_show_all))
                }
            }
        }
    }

    // ---- file ----
    PikCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.import_file_title),
                style = MaterialTheme.typography.bodyLarge,
                color = PikColors.TextPrimary,
            )
            Text(
                text = stringResource(R.string.import_file_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = PikColors.TextSecondary,
            )
            PikSecondaryButton(
                text = stringResource(R.string.import_file_button),
                onClick = openFilePicker,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun startSettings(context: android.content.Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // no such settings screen on this phone: nothing to do
    }
}
