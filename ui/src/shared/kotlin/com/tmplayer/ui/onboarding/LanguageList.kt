package com.tmplayer.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.tmplayer.data.SettingsStore
import com.tmplayer.i18n.Languages
import com.tmplayer.i18n.Translator
import com.tmplayer.ui.components.ChoiceRow
import com.tmplayer.ui.i18n.LocalStrings
import com.tmplayer.ui.theme.Tone
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The system's language as it names itself, for the "System default" row: the device's languages
 * the app resolves against, never the language picked in the app. The process locale is only the
 * fallback before the app has resolved one, because Android rewrites it to the per-app language.
 */
fun systemLanguageName(): String? {
    val tag = Translator.systemDefault(fallback = listOf(Locale.getDefault().toLanguageTag()))
    return if (tag == Languages.PSEUDO) Onboarding.PSEUDO_NAME else Languages.find(tag)?.name
}

/**
 * Every language, each named in itself, with "System default" first and picked until somebody
 * picks another. The choice is the Settings one, so the app switches the moment a row is chosen,
 * and [onPicked] hears of it. The Settings picker on a phone and a computer
 * draws this. [helpTranslate] adds the "Help translate" link under the list (Settings has it).
 *
 * With [rows] (a height limit or a weight) the rows scroll inside that, with a hairline over and
 * under them while there is more to see, so a page with buttons under the list keeps them in sight.
 * Without it the rows take their full height, for a picker that already scrolls them in its panel.
 */
@Composable
fun LanguageList(
    settings: SettingsStore,
    columns: Int,
    helpTranslate: Boolean = true,
    rows: Modifier? = null,
    onPicked: () -> Unit = {},
) {
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    val saved by settings.language.collectAsState(initial = "")
    val system = remember { systemLanguageName() }
    val choices = remember { Onboarding.languages() }
    val scroll = rememberScrollState()
    val more = rows != null && scroll.maxValue > 0
    if (more) HorizontalDivider(color = Tone.outline)
    // Edge to edge, as a Material list's rows are: each is already 56 dp of its own.
    Column(if (rows != null) rows.verticalScroll(scroll) else Modifier) {
        for (row in choices.chunked(columns)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (choice in row) {
                    ChoiceRow(
                        title = choice.name ?: s.onboardingLanguageSystem,
                        detail = if (choice.name == null) system else null,
                        selected = saved.equals(choice.tag, ignoreCase = true),
                        modifier = Modifier.weight(1f),
                    ) {
                        scope.launch { settings.setLanguage(choice.tag) }
                        onPicked()
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    if (more) HorizontalDivider(color = Tone.outline)
    if (helpTranslate) Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        TextButton(onClick = { runCatching { uri.openUri(Onboarding.TRANSLATE_URL) } }) { Text(s.onboardingHelpTranslate) }
        Text(s.onboardingHelpTranslateBody, style = MaterialTheme.typography.bodySmall, color = Tone.muted)
    }
}
