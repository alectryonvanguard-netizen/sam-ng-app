package com.cinemotion.editor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExportService : Service() {

    companion object {
        const val ACTION_EXPORT = "com.cinemotion.editor.ACTION_EXPORT"
        const val ACTION_EXPORT_COMPLETE =
            "com.cinemotion.editor.ACTION_EXPORT_COMPLETE"
        const val ACTION_EXPORT_FAILED =
            "com.cinemotion.editor.ACTION_EXPORT_FAILED"

        const val EXTRA_URIS = "export_uris"
        const val EXTRA_NAMES = "export_names"
        const val EXTRA_IMAGE_FLAGS = "export_image_flags"
        const val EXTRA_MIME_TYPES = "export_mime_types"
        const val EXTRA_MUSIC_URI = "export_music_uri"
        const val EXTRA_IMAGE_DURATION = "export_image_duration"
        const val EXTRA_RESULT_URI = "export_result_uri"
        const val EXTRA_ERROR = "export_error"

        private const val CHANNEL_ID = "cinemotion_export"
        private const val NOTIFICATION_ID = 4102
    }

    private lateinit var notificationManager: NotificationManager
    private var exporting = false

    override fun onCreate() {
        super.onCreate()

        notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Video exports",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows CineMotion video export progress"
                setShowBadge(false)
            }

            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        if (exporting) {
            return START_NOT_STICKY
        }

        if (intent?.action != ACTION_EXPORT) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        // Show the foreground notification before starting the export.
        startForegroundSafely(buildNotification("Preparing video…", 0))

        val uriStrings = intent.getStringArrayListExtra(EXTRA_URIS)
        val imageFlags = intent.getBooleanArrayExtra(EXTRA_IMAGE_FLAGS)

        if (
            uriStrings.isNullOrEmpty() ||
            imageFlags == null ||
            uriStrings.size != imageFlags.size
        ) {
            failExport("No valid media clips were supplied.")
            return START_NOT_STICKY
        }

        val imageDuration = intent
            .getIntExtra(EXTRA_IMAGE_DURATION, 3)
            .coerceIn(1, 60)

        val edits = MediaEditor.editsFrom(
            uris = uriStrings,
            imageFlags = imageFlags,
            defaultImageSeconds = imageDuration,
            starts = intent.getLongArrayExtra(MediaEditor.EXTRA_CLIP_STARTS),
            ends = intent.getLongArrayExtra(MediaEditor.EXTRA_CLIP_ENDS),
            speeds = intent.getFloatArrayExtra(MediaEditor.EXTRA_CLIP_SPEEDS),
            transitions =
                intent.getStringArrayListExtra(MediaEditor.EXTRA_CLIP_TRANSITIONS),
            mutes = intent.getBooleanArrayExtra(MediaEditor.EXTRA_CLIP_MUTES)
        )

        val heightPx = intent.getIntExtra(MediaEditor.EXTRA_HEIGHT, 0)
        val fps = intent.getIntExtra(MediaEditor.EXTRA_FPS, 30)
        val aspect = intent.getFloatExtra(MediaEditor.EXTRA_ASPECT, 0f)
        val transitionMs =
            intent.getIntExtra(MediaEditor.EXTRA_TRANSITION_MS, 600).toLong()

        val musicUri = intent.getStringExtra(EXTRA_MUSIC_URI)
            ?.takeIf { it.isNotBlank() }
            ?.let(Uri::parse)

        exporting = true

        try {
            MediaEditor(applicationContext).exportEdited(
                edits = edits,
                musicUri = musicUri,
                heightPx = heightPx,
                frameRate = fps,
                transitionMs = transitionMs,
                aspect = aspect,
                onProgress = { progress ->
                    val safeProgress = progress.coerceIn(0, 100)
                    notificationManager.notify(
                        NOTIFICATION_ID,
                        buildNotification(
                            "Exporting video… $safeProgress%",
                            safeProgress
                        )
                    )
                },
                onComplete = { outputUri ->
                    saveToHistory(outputUri)

                    notificationManager.notify(
                        NOTIFICATION_ID,
                        buildNotification("Export complete", 100)
                    )

                    sendBroadcast(
                        Intent(ACTION_EXPORT_COMPLETE)
                            .setPackage(packageName)
                            .putExtra(EXTRA_RESULT_URI, outputUri.toString())
                    )

                    exporting = false
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                },
                onError = { message ->
                    failExport(message)
                }
            )
        } catch (exception: Exception) {
            failExport(
                exception.localizedMessage ?: "Unable to export this video."
            )
        }

        return START_NOT_STICKY
    }

    /** Keeps a list of finished exports so the Projects > Exports tab can show them. */
    private fun saveToHistory(outputUri: Uri) {
        try {
            val prefs = getSharedPreferences("cinemotion_main", Context.MODE_PRIVATE)
            val title = "CineMotion " +
                SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date())
            val old = prefs.getString("exports", "") ?: ""
            val line = "$outputUri\t$title"
            prefs.edit()
                .putString("exports", if (old.isBlank()) line else "$line\n$old")
                .apply()
        } catch (_: Exception) {
            // History is a convenience; ignore failures.
        }
    }

    private fun startForegroundSafely(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 35) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(
        message: String,
        progress: Int
    ): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        builder
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("CineMotion Editor")
            .setContentText(message)
            .setOngoing(progress < 100)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)

        if (progress in 1..99) {
            builder.setProgress(100, progress, false)
        } else if (progress == 0) {
            builder.setProgress(0, 0, true)
        } else {
            builder.setProgress(0, 0, false)
        }

        return builder.build()
    }

    private fun failExport(message: String) {
        exporting = false

        notificationManager.notify(
            NOTIFICATION_ID,
            buildNotification("Export failed: $message", 0)
        )

        sendBroadcast(
            Intent(ACTION_EXPORT_FAILED)
                .setPackage(packageName)
                .putExtra(EXTRA_ERROR, message)
        )

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}
