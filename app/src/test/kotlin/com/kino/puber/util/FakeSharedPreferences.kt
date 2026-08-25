package com.kino.puber.util

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk

/**
 * A [SharedPreferences] that keeps its values in a map, so a repository can be tested for what it
 * stores as well as what it answers.
 *
 * [transactions] records one entry per `apply()`, which is what makes "read nothing, wrote
 * nothing" assertions possible — a repository that quietly rewrites a default on every read is a
 * different thing from one that leaves the file alone.
 */
class FakeSharedPreferences {
    val values: MutableMap<String, Any?> = mutableMapOf()
    val transactions: MutableList<Map<String, Any?>> = mutableListOf()
    val sharedPreferences: SharedPreferences = mockk()

    private val pending: MutableMap<String, Any?> = linkedMapOf()
    private val removals: MutableSet<String> = linkedSetOf()
    private val editor: SharedPreferences.Editor = mockk()

    init {
        every { sharedPreferences.getString(any(), any()) } answers {
            values[firstArg()] as? String ?: secondArg<String?>()
        }
        every { sharedPreferences.getInt(any(), any()) } answers {
            values[firstArg()] as? Int ?: secondArg()
        }
        every { sharedPreferences.getBoolean(any(), any()) } answers {
            values[firstArg()] as? Boolean ?: secondArg()
        }
        every { sharedPreferences.contains(any()) } answers { values.containsKey(firstArg()) }
        every { sharedPreferences.edit() } returns editor
        every { editor.putString(any(), any()) } answers {
            pending[firstArg()] = secondArg<String?>()
            editor
        }
        every { editor.putInt(any(), any()) } answers {
            pending[firstArg()] = secondArg<Int>()
            editor
        }
        every { editor.putBoolean(any(), any()) } answers {
            pending[firstArg()] = secondArg<Boolean>()
            editor
        }
        every { editor.remove(any()) } answers {
            removals += firstArg<String>()
            editor
        }
        every { editor.apply() } answers {
            val transaction = pending.toMap()
            values.putAll(transaction)
            values.keys.removeAll(removals)
            transactions += transaction
            pending.clear()
            removals.clear()
        }
    }
}
