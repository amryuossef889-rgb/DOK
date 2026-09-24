package com.dok.editor.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.dok.editor.MainActivity
import com.dok.editor.R
import com.dok.editor.engine.export.ExportPipeline
import com.dok.editor.engine.export.ExportPreset
import com.dok.editor.persistence.ProjectSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

class ExportForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "dok_export_channel"
        const val NOTIFICATION_ID = 4040
        const val ACTION_START_EXPORT = "com.dok.editor.START_EXPORT"
        const val ACTION_CANCEL_EXPORT = "com.dok.editor.CANCEL_EXPORT"
        const val EXTRA_PROJECT_FILE_PATH = "extra_project_file_path"
        const val EXTRA_PRESET_ID = "extra_preset_id"

        var isExporting: Boolean = false
            private set
        var currentProgress: Float = 0f
            private set
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var exportJob: Job? = null
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL_EXPORT -> {
                cancelExport()
            }
            ACTION_START_EXPORT -> {
                val filePath = intent.getStringExtra(EXTRA_PROJECT_FILE_PATH)
                val presetId = intent.getStringExtra(EXTRA_PRESET_ID) ?: ExportPreset.YOUTUBE_1080P_60.id
                if (filePath != null) {
                    startExportProcess(File(filePath), presetId)
                } else {
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Dok Video Export",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of ongoing video render and export"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(progress: Float, isIndeterminate: Boolean): NotificationCompat.Builder {
        val cancelIntent = Intent(this, ExportForegroundService::class.java).apply {
            action = ACTION_CANCEL_EXPORT
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val progressPercent = (progress * 100f).toInt()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("DOK Video Export")
            .setContentText(if (isIndeterminate) "Preparing media pipeline..." else "Exporting: $progressPercent%")
            .setProgress(100, progressPercent, isIndeterminate)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
    }

    private fun startExportProcess(projectFile: File, presetId: String) {
        val initialNotification = buildNotification(0f, true).build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        isExporting = true
        currentProgress = 0f

        exportJob = serviceScope.launch {
            try {
                val project = ProjectSerializer.loadProject(projectFile)
                val preset = ExportPreset.ALL_PRESETS.find { it.id == presetId } ?: ExportPreset.YOUTUBE_1080P_60

                val pipeline = ExportPipeline(applicationContext, project, preset)
                pipeline.execute(
                    onProgress = { p ->
                        currentProgress = p
                        val notif = buildNotification(p, false).build()
                        notificationManager.notify(NOTIFICATION_ID, notif)
                    },
                    onComplete = { uri ->
                        showCompletedNotification(uri)
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        isExporting = false
                        stopSelf()
                    },
                    onError = {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        isExporting = false
                        stopSelf()
                    }
                )
            } catch (_: Exception) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                isExporting = false
                stopSelf()
            }
        }
    }

    private fun cancelExport() {
        exportJob?.cancel()
        isExporting = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun showCompletedNotification(mediaUri: Uri) {
        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(mediaUri, "video/mp4")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            2,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val completedNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Export Complete!")
            .setContentText("Your video has been saved to Movies/DokEditor")
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, completedNotification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        isExporting = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
