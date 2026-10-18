package com.tapora.recorder.service

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class MediaRecorderEngine(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var output: File? = null
    private var startedAt: Long = 0L

    fun start(file: File): Result<Long> {
        check(recorder == null) { "MediaRecorder is already active" }
        return runCatching {
            file.parentFile?.mkdirs()
            val instance = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
            instance.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = instance
            output = file
            startedAt = System.currentTimeMillis()
            startedAt
        }.onFailure {
            releaseAndDelete()
        }
    }

    fun stop(): Result<MediaRecorderResult> {
        val active = recorder ?: return Result.failure(IllegalStateException("MediaRecorder is not active"))
        val file = output
        val started = startedAt
        return try {
            active.stop()
            active.release()
            recorder = null
            output = null
            Result.success(MediaRecorderResult(file, (System.currentTimeMillis() - started).coerceAtLeast(0)))
        } catch (failure: RuntimeException) {
            releaseAndDelete()
            Result.failure(failure)
        }
    }

    fun abort() {
        releaseAndDelete()
    }

    private fun releaseAndDelete() {
        try { recorder?.reset() } catch (_: Exception) { }
        try { recorder?.release() } catch (_: Exception) { }
        recorder = null
        output?.delete()
        output = null
    }
}

data class MediaRecorderResult(val file: File?, val durationMs: Long)
