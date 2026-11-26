package com.kino.puber.core.session

/** Why the account's session ended; Auth words its explanation from it. */
enum class SessionEndReason {
    /** The tokens were refused or could not be refreshed. */
    Expired,

    /** The server answered that the account is blocked. */
    Blocked,
}
