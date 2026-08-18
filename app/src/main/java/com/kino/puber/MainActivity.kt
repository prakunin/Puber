package com.kino.puber

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kino.puber.core.system.AppLocale
import com.kino.puber.core.contentlink.ContentLaunchCoordinator
import com.kino.puber.core.ui.AppLanguageProvider
import com.kino.puber.ui.feature.root.component.App
import com.kino.puber.ui.feature.root.component.SplashContent
import org.koin.mp.KoinPlatform.getKoin

class MainActivity : ComponentActivity() {

    // The chosen language is read from preferences rather than DI: this runs before the
    // application has finished starting Koin. It settles what the first frame is drawn in;
    // AppLanguageProvider takes over from there and follows the choice while the app is running.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        acceptContentIntent(intent)
        setContent {
            // Outermost, so the wordmark is in the chosen language too.
            AppLanguageProvider {
                // The wordmark is composed on its own first, so it reaches the screen without
                // waiting for the theme, navigation and DI graph that App() builds behind it.
                SplashContent()

                var appComposed by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { appComposed = true }
                if (appComposed) {
                    App()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptContentIntent(intent)
    }

    private fun acceptContentIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        getKoin().get<ContentLaunchCoordinator>().accept(intent.dataString)
    }
}
