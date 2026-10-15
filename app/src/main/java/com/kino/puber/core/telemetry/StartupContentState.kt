package com.kino.puber.core.telemetry

/**
 * Marks a view state that shows the screen's real content (or its settled empty answer). Only
 * such a state can be the cold start's `first_content`; loading and error states, and root overlay
 * view models such as the update prompt, never implement it.
 */
internal interface StartupContentState
