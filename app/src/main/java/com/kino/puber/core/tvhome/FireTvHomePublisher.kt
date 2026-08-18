package com.kino.puber.core.tvhome

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.kino.puber.MainActivity
import com.kino.puber.R
import com.kino.puber.core.contentlink.ContentUriCodec

internal class FireTvHomePublisher(
    private val context: Context,
    private val uriCodec: ContentUriCodec,
) : TvHomePublisher {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override suspend fun reconcile(programs: List<PublishedProgram>) {
        ensureChannel()
        val previousIds = storedIds()
        val usedIds = mutableSetOf<Int>()
        val nextIds = programs.associate { program ->
            val id = previousIds[program.stableKey]
                ?.takeIf(usedIds::add)
                ?: allocateNotificationId(program.stableKey, usedIds)
            notificationManager.notify(id, buildNotification(program, id))
            program.stableKey to id
        }
        (previousIds.values - nextIds.values.toSet()).forEach(notificationManager::cancel)
        storeIds(nextIds)
    }

    override suspend fun clearAccountPrograms() {
        storedIds().values.forEach(notificationManager::cancel)
        preferences.edit().remove(KEY_NOTIFICATION_IDS).apply()
    }

    private fun buildNotification(program: PublishedProgram, notificationId: Int): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(uriCodec.internalUri(program.target))
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        return builder
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(program.title)
            .setContentText(context.getString(R.string.tv_home_continue_watching))
            .setContentIntent(pendingIntent)
            .setCategory(Notification.CATEGORY_RECOMMENDATION)
            .setGroup(RECOMMENDATION_GROUP)
            .setSortKey(program.lastEngagementTimeMs.toString())
            .setProgress(program.durationMs.toInt(), program.positionMs.toInt(), false)
            .setAutoCancel(true)
            .setExtras(android.os.Bundle().apply {
                putString(Notification.EXTRA_BACKGROUND_IMAGE_URI, program.artworkUri)
            })
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.tv_home_recommendation_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        notificationManager.createNotificationChannel(channel)
    }

    private fun notificationId(key: String): Int = NOTIFICATION_ID_BASE + (key.hashCode() and ID_MASK)

    private fun allocateNotificationId(key: String, usedIds: MutableSet<Int>): Int {
        var id = notificationId(key)
        while (!usedIds.add(id)) {
            id = NOTIFICATION_ID_BASE + ((id - NOTIFICATION_ID_BASE + 1) and ID_MASK)
        }
        return id
    }

    private fun storedIds(): Map<String, Int> = preferences
        .getStringSet(KEY_NOTIFICATION_IDS, emptySet())
        .orEmpty()
        .mapNotNull { value ->
            val separator = value.lastIndexOf(':')
            if (separator <= 0) return@mapNotNull null
            value.substring(0, separator) to
                (value.substring(separator + 1).toIntOrNull() ?: return@mapNotNull null)
        }
        .toMap()

    private fun storeIds(ids: Map<String, Int>) {
        preferences.edit()
            .putStringSet(KEY_NOTIFICATION_IDS, ids.mapTo(mutableSetOf()) { "${it.key}:${it.value}" })
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "tv_home_fire"
        const val KEY_NOTIFICATION_IDS = "notification_ids"
        const val CHANNEL_ID = "continue_watching_recommendations"
        const val RECOMMENDATION_GROUP = "continue_watching"
        const val NOTIFICATION_ID_BASE = 20_000
        const val ID_MASK = 0x3FFF
    }
}
