package com.kino.puber.core.telemetry

import com.kino.puber.core.logger.SanitizedLogThrowable
import com.kino.puber.core.logger.SensitiveRequestLogPolicy

/** The only free-text field of the log is `err.msg`; this is what stands between it and a payload. */
internal object ErrorScrubber {
    private const val MAX_MESSAGE = 160
    private const val MAX_FRAMES = 5
    private const val URL_PLACEHOLDER = "<url>"
    private const val JSON_MARKER = "JSON input"

    private val urlRegex = Regex("""(?i)\b[a-z][a-z0-9+.-]*://\S+""")
    private val whitespaceRegex = Regex("""\s+""")
    private val libraryPrefixes = listOf(
        "java.", "javax.", "kotlin.", "kotlinx.", "android.", "androidx.", "dalvik.",
        "com.android.", "okhttp3.", "io.ktor.", "timber.",
    )
    private val policy = SensitiveRequestLogPolicy()

    fun scrub(message: String?): String {
        if (message == null) return ""
        val replaced = urlRegex.replace(message, URL_PLACEHOLDER)
        val cut = replaced.substringBefore(JSON_MARKER)
        return whitespaceRegex.replace(cut, " ").trim().take(MAX_MESSAGE)
    }

    fun frames(t: Throwable): List<String> = t.stackTrace.asSequence()
        .filter { element -> libraryPrefixes.none(element.className::startsWith) }
        .take(MAX_FRAMES)
        .map { "${it.className}.${it.methodName}:${it.lineNumber}" }
        .toList()

    /** The full binary class name on every path, so the R8 mapping file can retrace it. */
    fun type(t: Throwable): String = (t as? SanitizedLogThrowable)?.sourceType ?: t.javaClass.name

    fun message(t: Throwable): String {
        if (t is SanitizedLogThrowable) return scrub(t.sourceMessage)
        return scrub(policy.sanitizeText(t.message.orEmpty()))
    }
}
