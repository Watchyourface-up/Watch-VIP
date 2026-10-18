package com.tapora.recorder.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.tapora.recorder.MainActivity
import com.tapora.recorder.R
import com.tapora.recorder.data.Recording
import com.tapora.recorder.data.TaporaDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.text.SimpleDateFormat
import java.util.*

class RecordingService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var engine: MediaRecorderEngine
    private lateinit var files: RecordingFileStore
    private val machine = RecordingStateMachine()
    private var pendingFile: java.io.File? = null
    private var startedAt = 0L

    override fun onCreate() {
        super.onCreate()
        engine = MediaRecorderEngine(this)
        files = RecordingFileStore(java.io.File(filesDir, "recordings"))
        state.value = RecordingPhase.Ready
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopRecording() else startRecording()
        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (machine.phase == RecordingPhase.Error) machine.transition(RecordingAction.Reset)
        if (!machine.transition(RecordingAction.Start)) return
        try {
            createChannel()
            val notification = notification("جارٍ التسجيل…")
            if (Build.VERSION.SDK_INT >= 29) ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, 0)
            val file = files.createPending()
            pendingFile = file
            engine.start(file).getOrThrow()
            startedAt = System.currentTimeMillis()
            state.value = RecordingPhase.Recording
        } catch (failure: Exception) {
            engine.abort()
            files.discard(pendingFile)
            pendingFile = null
            machine.transition(RecordingAction.Failed)
            state.value = RecordingPhase.Error
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopRecording() {
        if (!machine.transition(RecordingAction.Stop)) return
        state.value = RecordingPhase.Saving
        val result = engine.stop()
        val pending = pendingFile
        pendingFile = null
        if (result.isFailure || pending == null) {
            files.discard(pending)
            completeSaveFailure()
            return
        }
        serviceScope.launch {
            runCatching {
                val finalized = files.finalize(pending)
                val createdAt = startedAt
                TaporaDatabase.get(this@RecordingService).recordings().insert(
                    Recording(
                        id = finalized.nameWithoutExtension,
                        title = "تسجيل ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(createdAt))}",
                        path = finalized.absolutePath,
                        createdAt = createdAt,
                        durationMs = result.getOrThrow().durationMs,
                        fileSizeBytes = finalized.length(),
                        isComplete = true
                    )
                )
            }.onSuccess {
                withContext(Dispatchers.Main) {
                    machine.transition(RecordingAction.SaveSucceeded)
                    state.value = RecordingPhase.Ready
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }.onFailure {
                files.discard(pending)
                withContext(Dispatchers.Main) { completeSaveFailure() }
            }
        }
    }

    private fun completeSaveFailure() {
        machine.transition(RecordingAction.Failed)
        state.value = RecordingPhase.Error
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "تسجيل صوتي", NotificationManager.IMPORTANCE_LOW).apply { description = "إشعار ظاهر أثناء التسجيل" }
        )
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 2, Intent(this, RecordingService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("TAPORA").setContentText(text).setContentIntent(open).setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE).addAction(android.R.drawable.ic_media_pause, "إيقاف التسجيل", stop).build()
    }

    override fun onDestroy() {
        if (machine.phase == RecordingPhase.Recording) engine.abort()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.tapora.recorder.STOP"
        const val CHANNEL_ID = "tapora_recording"
        const val NOTIFICATION_ID = 101
        val state = MutableStateFlow(RecordingPhase.Ready)
        fun start(context: Context) { context.startForegroundService(Intent(context, RecordingService::class.java)) }
        fun stop(context: Context) { context.startService(Intent(context, RecordingService::class.java).setAction(ACTION_STOP)) }
    }
}
