package ai.bowen.phoneagent

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.io.File
import java.util.UUID

/**
 * Explicit, video-only MediaProjection tutorial capture. No microphone, accessibility-event
 * collection, cloud upload, transcription, automatic playback, or headless start.
 * Recordings stay in app-private files; users can stop from a persistent notification.
 */
class TutorialRecordingService : Service() {
    companion object {
        const val ACTION_START = "ai.bowen.phoneagent.action.TUTORIAL_START"
        const val ACTION_STOP = "ai.bowen.phoneagent.action.TUTORIAL_STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "phoneagent_tutorial_recording"
        private const val NOTIFICATION_ID = 3721
        private const val PREFS = "tutorial_capture"
        private const val LAST_FILE = "last_recording_file"
        private const val DIR = "tutorial_recordings"

        @Volatile var isCapturing: Boolean = false
            private set

        fun latestRecording(context: Context): File? {
            val name = context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(LAST_FILE, null) ?: return null
            // Never trust a filename from preferences to escape our private directory.
            if (!name.matches(Regex("teach_[A-Za-z0-9_-]+\\.mp4"))) return null
            val root = File(context.filesDir, DIR)
            return File(root, name).takeIf { it.isFile }
        }

        fun deleteLatestRecording(context: Context): Boolean {
            if (isCapturing) return false
            val f = latestRecording(context) ?: return false
            val deleted = f.delete()
            if (deleted) context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit().remove(LAST_FILE).apply()
            return deleted
        }
    }

    private var recorder: MediaRecorder? = null
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var outputFile: File? = null
    private var recordingStarted = false
    private var stopping = false

    private val callback = object : MediaProjection.Callback() {
        override fun onStop() {
            // Also stop if the user revokes projection from Android's capture indicator.
            Handler(Looper.getMainLooper()).post { finishCapture() }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (isCapturing || recordingStarted) return START_NOT_STICKY
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                @Suppress("DEPRECATION")
                val data: Intent? = if (Build.VERSION.SDK_INT >= 33)
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                else intent.getParcelableExtra(EXTRA_RESULT_DATA)
                if (resultCode != Activity.RESULT_OK || data == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                createNotificationChannel()
                startForeground(
                    NOTIFICATION_ID, captureNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
                try {
                    beginCapture(resultCode, data)
                } catch (_: Exception) {
                    finishCapture()
                }
            }
            ACTION_STOP -> finishCapture()
            else -> stopSelf()
        }
        // Never restart an old projection session with a reused one-time token.
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun beginCapture(resultCode: Int, resultData: Intent) {
        val metrics = resources.displayMetrics
        val longSide = maxOf(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(1)
        val scale = minOf(1.0, 1280.0 / longSide)
        fun align16(pixels: Int) = maxOf(16, ((pixels * scale).toInt() / 16) * 16)
        val width = align16(metrics.widthPixels)
        val height = align16(metrics.heightPixels)
        val folder = File(filesDir, DIR).also { check(it.isDirectory || it.mkdirs()) }
        val file = File(folder, "teach_${System.currentTimeMillis()}_${UUID.randomUUID()}.mp4")
        outputFile = file

        recorder = MediaRecorder().apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoSize(width, height)
            setVideoFrameRate(24)
            setVideoEncodingBitRate(2_500_000)
            setOutputFile(file.absolutePath)
            prepare()
        }

        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(resultCode, resultData)
        val activeProjection = checkNotNull(projection)
        activeProjection.registerCallback(callback, Handler(Looper.getMainLooper()))
        display = activeProjection.createVirtualDisplay(
            "PhoneAgent guided tutorial", width, height, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            checkNotNull(recorder).surface, null, Handler(Looper.getMainLooper())
        )
        checkNotNull(recorder).start()
        recordingStarted = true
        isCapturing = true
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Tutorial screen recording", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun captureNotification(): Notification {
        val stop = Intent(this, TutorialRecordingService::class.java).apply { action = ACTION_STOP }
        val pendingStop = PendingIntent.getService(
            this, 0, stop, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("PhoneAgent tutorial recording")
            .setContentText("Video-only screen capture is active. Tap Stop to finish.")
            .setOngoing(true)
            .addAction(Notification.Action.Builder(
                android.R.drawable.ic_media_pause, "Stop", pendingStop
            ).build())
            .build()
    }

    private fun finishCapture() {
        if (stopping) return
        stopping = true
        isCapturing = false
        val wasRecording = recordingStarted
        recordingStarted = false
        var saved = false
        try {
            if (wasRecording) {
                try {
                    recorder?.stop()
                    saved = outputFile?.length()?.let { it > 0L } == true
                } catch (_: RuntimeException) {
                    // Very short or interrupted recordings may have no valid MP4 container.
                }
            }
        } finally {
            display?.release()
            display = null
            try { recorder?.reset() } catch (_: Exception) {}
            recorder?.release()
            recorder = null
            try { projection?.unregisterCallback(callback) } catch (_: Exception) {}
            projection?.stop()
            projection = null
            val f = outputFile
            if (saved && f != null) {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString(LAST_FILE, f.name).apply()
            } else {
                f?.delete()
            }
            outputFile = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        if (!stopping) finishCapture()
        super.onDestroy()
    }
}
