package com.kino.puber.core.error

data class ErrorEntity(
    val message: String,
    val code: String,
) {
    companion object {
        const val CODE_UNAUTHORIZED = "Unauthorized"
    }
}
