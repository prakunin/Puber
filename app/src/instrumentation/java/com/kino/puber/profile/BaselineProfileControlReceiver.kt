package com.kino.puber.profile

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Control channel of the baseline profile generator; runs in its own process. */
internal class BaselineProfileControlReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val journal = BaselineNetworkJournal(BaselineInstrumentationEnvironment.journalDirectory(context))
        when (intent.action) {
            ACTION_CLEAR -> {
                BaselineInstrumentationEnvironment.clearCaches(context)
                setResultCode(Activity.RESULT_OK)
                setResultData(RESULT_CLEARED)
            }

            ACTION_RESET_JOURNAL -> {
                journal.clear()
                setResultCode(Activity.RESULT_OK)
                setResultData(RESULT_JOURNAL_RESET)
            }

            ACTION_VERIFY -> {
                val lines = journal.snapshot().joinToString("\n")
                setResultCode(if (lines.isEmpty()) Activity.RESULT_OK else RESULT_VIOLATIONS)
                setResultData(lines)
            }

            else -> {
                setResultCode(RESULT_UNKNOWN_ACTION)
                setResultData(RESULT_UNKNOWN_ACTION_TEXT)
            }
        }
    }

    companion object {
        const val ACTION_CLEAR = "com.kino.puber.profile.CLEAR"
        const val ACTION_RESET_JOURNAL = "com.kino.puber.profile.RESET_JOURNAL"
        const val ACTION_VERIFY = "com.kino.puber.profile.VERIFY"
        const val RESULT_VIOLATIONS = 2

        private const val RESULT_CLEARED = "CLEARED"
        private const val RESULT_JOURNAL_RESET = "JOURNAL_RESET"
        private const val RESULT_UNKNOWN_ACTION = 3
        private const val RESULT_UNKNOWN_ACTION_TEXT = "UNKNOWN_ACTION"
    }
}
