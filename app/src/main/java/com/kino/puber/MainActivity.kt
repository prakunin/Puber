package com.kino.puber

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kino.puber.ui.feature.root.component.App
import com.kino.puber.ui.feature.root.component.SplashContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // The wordmark is composed on its own first, so it reaches the screen without waiting
            // for the theme, navigation and DI graph that App() builds behind it.
            SplashContent()

            var appComposed by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { appComposed = true }
            if (appComposed) {
                App()
            }
        }
    }
}
