package com.tapora.recorder

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tapora.recorder.service.RecordingFileStore
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordingFileStoreInstrumentedTest {
    @Test fun pendingFileIsFinalizedOnlyAfterNonEmptyContent() {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "tapora-file-store-test")
        root.deleteRecursively()
        val store = RecordingFileStore(root)
        val pending = store.createPending()
        assertTrue(pending.name.endsWith(".m4a.part"))
        pending.writeBytes(byteArrayOf(1, 2, 3))
        val finalized = store.finalize(pending)
        assertTrue(finalized.name.endsWith(".m4a"))
        assertTrue(finalized.isFile)
        assertFalse(pending.exists())
        root.deleteRecursively()
    }
}
