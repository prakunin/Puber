package com.kino.puber.core.telemetry

import com.kino.puber.core.ui.navigation.PuberTab

/** Class to stable name. Writer-thread only: plain caches, no locks. */
internal class TelemetryNames {
    private val owners = HashMap<Class<*>, String>()
    private val plains = HashMap<Class<*>, String>()

    /** Screen and view model names: one trailing `Screen` or `VM` removed. */
    fun owner(cls: Class<*>?): String {
        if (cls == null) return ""
        return owners.getOrPut(cls) { stripSuffix(plain(cls)) }
    }

    /** State and action names: the bare simple name. */
    fun plain(cls: Class<*>?): String {
        if (cls == null) return ""
        return plains.getOrPut(cls) { simpleNameOf(cls.name) }
    }

    private fun stripSuffix(name: String): String = when {
        name.length > SCREEN.length && name.endsWith(SCREEN) -> name.removeSuffix(SCREEN)
        name.length > VM.length && name.endsWith(VM) -> name.removeSuffix(VM)
        else -> name
    }

    companion object {
        /**
         * Bare name from a binary name, identical in debug and minified builds: R8 may drop the
         * InnerClasses entry that `Class.simpleName` relies on, so it is not used.
         */
        fun simpleNameOf(binaryName: String): String =
            binaryName.substringAfterLast('.').substringAfterLast('$')


        private const val SCREEN = "Screen"
        private const val VM = "VM"
    }
}

/** The class a navigation probe names; a tab is replaced by the screen it wraps. No strings. */
internal fun telemetryClassOf(screen: Any?): Class<*>? = when (screen) {
    null -> null
    is PuberTab -> screen.contentScreenClass
    else -> screen.javaClass
}
