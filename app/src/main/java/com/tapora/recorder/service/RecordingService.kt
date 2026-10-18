package com.tapora.recorder.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.tapora.recorder.MainActivity
import com.tapora.recorder.data.Recording
import com.tapora.recorder.data.TaporaDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class RecordingService : Service() {
    private lateinit var engine: MediaRecorderEngine
    private lateinit var files: RecordingFileStore
    private val machine = RecordingStateMachine()
    private var pendingFile: File? = null
    private var startedAt = 0L
    private var foregroundStarted = false
    private var recordingWakeLock: PowerManager.WakeLock? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        engine = MediaRecorderEngine(this)
        files = RecordingFileStore(File(filesDir, "recordings"))
        files.removePendingFiles()
        state.value = RecordingPhase.Ready
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.i(TAG, "command_received action=$action startId=$startId armed=$isArmed recording=$isRecording phase=${state.value}")
        return try {
            if (action == ACTION_DISARM) {
                finishRecording(disarm = true)
                START_NOT_STICKY
            } else {
                ensureForeground()
                setArmed(true)
                when (action) {
                    ACTION_ARM -> updateNotification()
                    ACTION_STOP_RECORDING -> finishRecording(disarm = false)
                    ACTION_VOLUME_TOGGLE -> if (state.value == RecordingPhase.Recording) finishRecording(false) else startRecording()
                    else -> if (state.value == RecordingPhase.Ready || state.value == RecordingPhase.Error) startRecording()
                }
                START_NOT_STICKY
            }
        } catch (error: Exception) {
            Log.e(TAG, "command_failed action=$action", error)
            failAndCleanup(error)
            START_NOT_STICKY
        }
    }

    private fun ensureForeground() {
        createChannel()
        if (foregroundStarted) return
        val notification = notification()
        if (Build.VERSION.SDK_INT >= 29) ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, 0)
        foregroundStarted = true
        Log.i(TAG, "foreground_started type=microphone sdk=${Build.VERSION.SDK_INT}")
    }

    private fun startRecording() {
        if (machine.phase == RecordingPhase.Error) machine.transition(RecordingAction.Reset)
        if (!machine.transition(RecordingAction.Start)) {
            Log.w(TAG, "recording_start_ignored phase=${machine.phase}")
            return
        }
        try {
            val file = files.createPending()
            pendingFile = file
            engine.start(file).getOrThrow()
            acquireRecordingWakeLock()
            startedAt = System.currentTimeMillis()
            setRecording(true)
            state.value = RecordingPhase.Recording
            updateNotification()
            Log.i(TAG, "recording_started pending=${file.name} elapsedRealtime=${android.os.SystemClock.elapsedRealtime()}")
        } catch (error: Exception) {
            engine.abort()
            files.discard(pendingFile)
            pendingFile = null
            machine.transition(RecordingAction.Failed)
            state.value = RecordingPhase.Error
            releaseRecordingWakeLock()
            setRecording(false)
            throw error
        }
    }

    private fun finishRecording(disarm: Boolean) {
        if (!machine.transition(RecordingAction.Stop)) {
            Log.w(TAG, "recording_stop_ignored phase=${machine.phase}")
            if (disarm && machine.phase == RecordingPhase.Ready) disarmService()
            return
        }
        state.value = RecordingPhase.Saving
        if (disarm) setArmed(false)
        val pending = pendingFile
        pendingFile = null
        val stopResult = engine.stop()
        releaseRecordingWakeLock()
        setRecording(false)
        Log.i(TAG, "recording_stop_requested pending=${pending?.name} stopSuccess=${stopResult.isSuccess} disarm=$disarm")
        scope.launch {
            runCatching {
                val finalized = if (stopResult.isSuccess && pending != null) files.finalize(pending) else throw IllegalStateException("MediaRecorder stop failed")
                val createdAt = startedAt
                TaporaDatabase.get(this@RecordingService).recordings().insert(
                    Recording(
                        id = finalized.nameWithoutExtension,
                        title = "تسجيل ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(createdAt))}",
                        path = finalized.absolutePath,
                        createdAt = createdAt,
                        durationMs = stopResult.getOrThrow().durationMs,
                        fileSizeBytes = finalized.length(),
                        isComplete = true
                    )
                )
                finalized
            }.onSuccess { finalized ->
                Log.i(TAG, "recording_saved path=${finalized.name} bytes=${finalized.length()}")
                withContext(Dispatchers.Main) {
                    machine.transition(RecordingAction.SaveSucceeded)
                    state.value = RecordingPhase.Ready
                    if (disarm) disarmService() else updateNotification()
                }
            }.onFailure { error ->
                files.discard(pending)
                Log.e(TAG, "recording_save_failed", error)
                withContext(Dispatchers.Main) {
                    machine.transition(RecordingAction.Failed)
                    state.value = RecordingPhase.Error
                    if (disarm) disarmService() else updateNotification()
                }
            }
        }
    }

    private fun failAndCleanup(error: Throwable) {
        Log.e(TAG, "recording_cleanup_after_failure", error)
        engine.abort()
        files.discard(pendingFile)
        pendingFile = null
        releaseRecordingWakeLock()
        setRecording(false)
        if (machine.phase == RecordingPhase.Recording || machine.phase == RecordingPhase.Saving) machine.transition(RecordingAction.Failed)
        state.value = RecordingPhase.Error
        setArmed(false)
        if (foregroundStarted) stopForeground(STOP_FOREGROUND_REMOVE)
        foregroundStarted = false
        stopSelf()
    }

    private fun acquireRecordingWakeLock() {
        recordingWakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:TAPORA-recording").apply {
            setReferenceCounted(false); acquire()
        }
    }

    private fun releaseRecordingWakeLock() {
        recordingWakeLock?.let { if (it.isHeld) it.release() }
        recordingWakeLock = null
    }

    private fun setArmed(value: Boolean) { isArmed = value; publishState() }
    private fun setRecording(value: Boolean) { isRecording = value; publishState() }
    private fun publishState() { sendBroadcast(Intent(ACTION_STATE_CHANGED).setPackage(packageName)) }

    private fun disarmService() {
        setArmed(false)
        setRecording(false)
        getSharedPreferences(VolumeShortcutService.PREFS, MODE_PRIVATE).edit().putBoolean(VolumeShortcutService.KEY_VOLUME_SHORTCUT_ENABLED, false).apply()
        if (foregroundStarted) stopForeground(STOP_FOREGROUND_REMOVE)
        foregroundStarted = false
        stopSelf()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "تسجيل صوتي", NotificationManager.IMPORTANCE_LOW).apply { description = "إشعار ظاهر أثناء التسجيل" }
        )
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 2, Intent(this, RecordingService::class.java).setAction(ACTION_DISARM), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(if (isRecording) "TAPORA — التسجيل جارٍ" else "TAPORA — خدمة التسجيل مفعّلة")
            .setContentText(if (isRecording) "يُحفظ التسجيل على هذا الجهاز. اضغط لإيقافه." else "لا يجري تسجيل الصوت.")
            .setContentIntent(open).setOngoing(true).setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_media_pause, "إيقاف التسجيل", stop).build()
    }

    private fun updateNotification() { if (foregroundStarted) getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification()) }

    override fun onDestroy() {
        Log.w(TAG, "service_destroyed recording=$isRecording phase=${state.value}")
        engine.abort()
        files.discard(pendingFile)
        pendingFile = null
        releaseRecordingWakeLock()
        setRecording(false)
        setArmed(false)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        @Volatile var isArmed: Boolean = false
            private set
        @Volatile var isRecording: Boolean = false
            private set
        val state = MutableStateFlow(RecordingPhase.Ready)
        const val ACTION_ARM = "com.tapora.recorder.ARM"
        const val ACTION_START_RECORDING = "com.tapora.recorder.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.tapora.recorder.STOP_RECORDING"
        const val ACTION_VOLUME_TOGGLE = "com.tapora.recorder.VOLUME_TOGGLE"
        const val ACTION_DISARM = "com.tapora.recorder.DISARM"
        const val ACTION_STATE_CHANGED = "com.tapora.recorder.STATE_CHANGED"
        private const val TAG = "TAPORA-Recorder"
        const val CHANNEL_ID = "tapora_recording"
        const val NOTIFICATION_ID = 101
        fun start(context: Context) { context.startForegroundService(Intent(context, RecordingService::class.java).setAction(ACTION_START_RECORDING)) }
        fun arm(context: Context) { context.startForegroundService(Intent(context, RecordingService::class.java).setAction(ACTION_ARM)) }
        fun stopRecording(context: Context) { context.startService(Intent(context, RecordingService::class.java).setAction(ACTION_STOP_RECORDING)) }
        fun stop(context: Context) { context.startService(Intent(context, RecordingService::class.java).setAction(ACTION_DISARM)) }
        fun toggleFromShortcut(context: Context) { if (isArmed) context.startService(Intent(context, RecordingService::class.java).setAction(ACTION_VOLUME_TOGGLE)) }
    }
}
