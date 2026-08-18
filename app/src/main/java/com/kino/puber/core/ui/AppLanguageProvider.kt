package com.kino.puber.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import com.kino.puber.core.system.AppLocale

/**
 * Draws [content] in the chosen interface language, and redraws it the moment the choice changes.
 *
 * `attachBaseContext` still applies the language to the process, but it runs once per start, so on
 * its own it would hold the interface in whichever language the app came up in. `stringResource`
 * and its siblings read [LocalResources], so overriding that here is what turns a change of the
 * setting into a change on screen without a restart the Fire TV boxes cannot perform anyway.
 *
 * [LocalContext] is deliberately left as it is. `createConfigurationContext` does not return a
 * [android.content.ContextWrapper] around the activity, so anything walking back up to the
 * activity — the exit handler, the activity navigator — would stop finding it. Nothing reads
 * strings off that context; they all go through the resources above.
 */
@Composable
fun AppLanguageProvider(content: @Composable () -> Unit) {
    val language by AppLocale.current.collectAsState()
    val context = LocalContext.current
    // Also keyed on the device configuration so that a change of density, night mode or screen
    // size still reaches the composition through the copy made here.
    val configuration = LocalConfiguration.current
    val resources = remember(context, configuration, language) {
        AppLocale.wrap(context, language).resources
    }
    CompositionLocalProvider(
        LocalConfiguration provides resources.configuration,
        LocalResources provides resources,
        content = content,
    )
}
