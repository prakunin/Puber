package com.kino.puber.core.ui.navigation

import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator

internal fun Navigator.puberPush(screen: Screen) {
    push(screen)
}

internal fun Navigator.puberReplace(screen: Screen) {
    replace(screen)
}

internal fun Navigator.puberReplaceAll(vararg screen: Screen) {
    replaceAll(screen.toList())
}

internal fun Navigator.puberPop() {
    pop()
}

internal fun Navigator.puberPopUntil(predicate: (Screen) -> Boolean) {
    val poppedScreens = mutableListOf<Screen>()
    popUntil {
        val popped = predicate(it)
        if (popped) {
            poppedScreens.add(it)
        }
        return@popUntil popped
    }
}