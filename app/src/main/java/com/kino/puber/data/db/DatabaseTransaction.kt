package com.kino.puber.data.db

import androidx.room3.withWriteTransaction

/**
 * Runs a block as one database transaction.
 *
 * An indirection so the repositories above it stay testable against plain fakes instead of needing
 * a real Room instance to write anything.
 */
interface DatabaseTransaction {

    suspend fun <T> run(block: suspend () -> T): T

    /** Runs the block as it is. For tests and for callers that own no database. */
    object Direct : DatabaseTransaction {
        override suspend fun <T> run(block: suspend () -> T): T = block()
    }
}

fun PuberDatabase.transactions(): DatabaseTransaction = object : DatabaseTransaction {
    override suspend fun <T> run(block: suspend () -> T): T = withWriteTransaction { block() }
}
