package com.pik.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pik.app.R
import com.pik.app.ui.components.PikBackTopBar
import com.pik.app.ui.components.PikCard
import com.pik.app.ui.theme.PikColors

/** The fonts Pik ships with and their licence (SIL Open Font License 1.1). */
@Composable
fun LicencesScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PikBackTopBar(title = stringResource(R.string.licences_title), onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.licences_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = PikColors.TextSecondary,
            )
            FontLicenceCard(
                name = stringResource(R.string.licences_outfit),
                use = stringResource(R.string.licences_outfit_use),
                copyright = "Copyright 2021 The Outfit Project Authors (https://github.com/Outfitio/Outfit-Fonts)",
                rawRes = R.raw.ofl_outfit,
            )
            FontLicenceCard(
                name = stringResource(R.string.licences_inter),
                use = stringResource(R.string.licences_inter_use),
                copyright = "Copyright 2020 The Inter Project Authors (https://github.com/rsms/inter)",
                rawRes = R.raw.ofl_inter,
            )
            Text(
                text = stringResource(R.string.licences_ofl_note),
                style = MaterialTheme.typography.bodySmall,
                color = PikColors.TextSecondary,
                modifier = Modifier.padding(bottom = 32.dp),
            )
        }
    }
}

@Composable
private fun FontLicenceCard(name: String, use: String, copyright: String, rawRes: Int) {
    val context = LocalContext.current
    var expanded by rememberSaveable(rawRes) { mutableStateOf(false) }
    val fullText = remember(rawRes) {
        try {
            context.resources.openRawResource(rawRes).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            ""
        }
    }
    PikCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = name, style = MaterialTheme.typography.titleMedium, color = PikColors.TextPrimary)
            Text(text = use, style = MaterialTheme.typography.bodySmall, color = PikColors.TextSecondary)
            Text(text = copyright, style = MaterialTheme.typography.bodySmall, color = PikColors.TextSecondary)
            Text(
                text = stringResource(R.string.licences_ofl_name),
                style = MaterialTheme.typography.bodyMedium,
                color = PikColors.TextPrimary,
            )
            if (fullText.isNotEmpty()) {
                TextButton(
                    onClick = { expanded = !expanded },
                    colors = ButtonDefaults.textButtonColors(contentColor = PikColors.Lime),
                ) {
                    Text(
                        stringResource(if (expanded) R.string.licences_hide_text else R.string.licences_show_text),
                        maxLines = 2,
                    )
                }
                if (expanded) {
                    Text(text = fullText, style = MaterialTheme.typography.bodySmall, color = PikColors.TextSecondary)
                }
            }
        }
    }
}
