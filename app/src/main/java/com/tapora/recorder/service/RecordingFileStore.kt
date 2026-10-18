package com.tapora.recorder.service

import android.os.StatFs
import java.io.File
import java.io.IOException
import java.util.UUID

class RecordingFileStore(private val root: File) {
    companion object { private const val MIN_FREE_BYTES = 1L * 1024 * 1024 }

    fun createPending(): File {
        if (!root.exists() && !root.mkdirs()) throw IOException("Unable to create recordings directory")
        val free = StatFs(root.absolutePath).availableBytes
        if (free < MIN_FREE_BYTES) throw IOException("Insufficient storage space")
        return File(root, "${UUID.randomUUID()}.m4a.part")
    }

    fun finalize(pending: File): File {
        if (!pending.exists() || pending.length() <= 0L) throw IOException("Recording file is empty or missing")
        val target = File(pending.parentFile, pending.name.removeSuffix(".part"))
        if (!pending.renameTo(target)) throw IOException("Unable to finalize recording file")
        return target
    }

    fun removePendingFiles() {
        if (!root.exists()) return
        root.listFiles { file -> file.name.endsWith(".m4a.part") }?.forEach { it.delete() }
    }

    fun discard(file: File?) { if (file != null) file.delete() }
}
