package com.kino.puber

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.kino.puber.core.telemetry.KeyProbe
import com.kino.puber.core.telemetry.Telemetry
import com.kino.puber.core.telemetry.dispatchWithProbe
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kino.puber.core.system.AppLocale
import com.kino.puber.core.tvhome.TvHomePublisherFactory
import com.kino.puber.core.contentlink.ContentLaunchCoordinator
import com.kino.puber.core.ui.AppLanguageProvider
import com.kino.puber.ui.feature.root.component.App
import com.kino.puber.ui.feature.root.component.SplashContent
import org.koin.mp.KoinPlatform.getKoin

class MainActivity : ComponentActivity() {

    // Registered as a field because the launcher has to exist before the activity is started, and
    // there is nothing to do with the answer: the publisher reads the grant when it next runs, and
    // a refusal leaves the home row as empty as it already was.
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    // The chosen language is read from preferences rather than DI: this runs before the
    // application has finished starting Koin. It settles what the first frame is drawn in;
    // AppLanguageProvider takes over from there and follows the choice while the app is running.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    // Sees every key before the view tree. Off, this is one flag check and the original call; the
    // returned value is always exactly super's, because Compose focus depends on it. Lint marks
    // androidx.core's ComponentActivity.dispatchKeyEvent as restricted, but overriding it is the only
    // hook that sees every key, and calling super is its normal contract.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        dispatchWithProbe(Telemetry.isEnabled, { super.dispatchKeyEvent(event) }) { handled, dispatchNs ->
            KeyProbe.onKey(event.action, event.keyCode, event.repeatCount, event.eventTime, dispatchNs, handled)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
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
                    LaunchedEffect(Unit) { Telemetry.startupMarks?.markAppComposed() }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptContentIntent(intent)
    }

    /**
     * The Fire TV home row is published as notifications, and from API 33 posting one without
     * POST_NOTIFICATIONS granted does nothing and says nothing. There is no screen the row belongs
     * to, so there is no in-context moment to ask for it; startup is the only one available. Asked
     * only where it can matter - a Fire TV on API 33 or later that has not granted it - so no
     * other device ever sees the dialog.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (!TvHomePublisherFactory.publishesThroughNotifications(this)) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) return
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun acceptContentIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        getKoin().get<ContentLaunchCoordinator>().accept(intent.dataString)
    }
}
