package com.kino.puber.domain.model

sealed interface AuthState {
    data object Success : AuthState
    data class Code(val code: String, val url: String, val expireTimeSeconds: Int) : AuthState
}
