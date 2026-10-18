package com.tapora.recorder

import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.tapora.recorder.data.Recording
import com.tapora.recorder.data.TaporaDatabase
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val LibraryInk = Color(0xFF172B29)
private val LibraryTeal = Color(0xFF087E70)
private val LibraryMuted = Color(0xFF73817D)

class LibraryActivity : ComponentActivity() {
    private val db by lazy { TaporaDatabase.get(this) }
    private var player: MediaPlayer? = null
    private var playingId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            db.recordings().all().filterNot { File(it.path).isFile && File(it.path).length() > 0L }
                .forEach { db.recordings().delete(it.id) }
        }
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
                val recordings by db.recordings().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
                var renameTarget by remember { mutableStateOf<Recording?>(null) }
                var deleteTarget by remember { mutableStateOf<Recording?>(null) }
                var title by remember { mutableStateOf("") }
                var message by remember { mutableStateOf<String?>(null) }
                MaterialTheme(colorScheme = lightColorScheme(primary = LibraryTeal, surface = Color.White)) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("مكتبة TAPORA", color = LibraryInk, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                Text("تسجيلاتك محفوظة على هذا الجهاز", color = LibraryMuted, fontSize = 12.sp)
                            }
                            TextButton(onClick = { finish() }) { Text("العودة إلى الإعدادات") }
                        }
                        Spacer(Modifier.height(12.dp))
                        if (recordings.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("لا توجد تسجيلات محفوظة حتى الآن", color = LibraryMuted) }
                        } else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                            items(recordings, key = { it.id }) { item ->
                                LibraryRecordingCard(
                                    item = item,
                                    playing = playingId == item.id,
                                    onPlay = { playOrPause(item) { message = it } },
                                    onRename = { renameTarget = item; title = item.title },
                                    onDelete = { deleteTarget = item }
                                )
                            }
                        }
                    }
                    if (renameTarget != null) AlertDialog(
                        onDismissRequest = { renameTarget = null },
                        title = { Text("تغيير اسم التسجيل") },
                        text = { OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true, label = { Text("اسم التسجيل") }) },
                        confirmButton = { TextButton(onClick = {
                            val target = renameTarget
                            if (target != null && title.isNotBlank()) lifecycleScope.launch { db.recordings().rename(target.id, title.trim()) }
                            renameTarget = null
                        }) { Text("حفظ") } },
                        dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("إلغاء") } }
                    )
                    if (deleteTarget != null) AlertDialog(
                        onDismissRequest = { deleteTarget = null },
                        title = { Text("حذف التسجيل؟") },
                        text = { Text("سيُحذف التسجيل نهائيًا من هذا الجهاز. لا يمكن التراجع عن هذا الإجراء.") },
                        confirmButton = { TextButton(onClick = {
                            val target = deleteTarget
                            if (target != null) {
                                stopPlayback(target.id)
                                lifecycleScope.launch { db.recordings().delete(target.id); File(target.path).delete() }
                            }
                            deleteTarget = null
                        }) { Text("حذف", color = Color(0xFFB84D42)) } },
                        dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("إلغاء") } }
                    )
                    if (message != null) AlertDialog(onDismissRequest = { message = null }, text = { Text(message.orEmpty()) }, confirmButton = { TextButton(onClick = { message = null }) { Text("حسنًا") } })
                }
            }
        }
    }

    private fun playOrPause(item: Recording, error: (String) -> Unit) {
        if (playingId == item.id) { stopPlayback(item.id); return }
        stopPlayback(null)
        try {
            player = MediaPlayer().apply {
                setDataSource(item.path)
                setOnCompletionListener { it.release(); player = null; playingId = null }
                prepare()
                start()
            }
            playingId = item.id
        } catch (_: Exception) {
            stopPlayback(null)
                        error("تعذّر تشغيل هذا التسجيل.")
        }
    }

    private fun stopPlayback(id: String?) {
        if (id == null || playingId == id) {
            try { player?.stop() } catch (_: Exception) { }
            player?.release()
            player = null
            playingId = null
        }
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}

@Composable
private fun LibraryRecordingCard(item: Recording, playing: Boolean, onPlay: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (playing) "Ⅱ" else "▶", color = LibraryTeal, fontSize = 18.sp, modifier = Modifier.clickable(onClick = onPlay).padding(8.dp))
            Column(Modifier.weight(1f).clickable(onClick = onPlay)) {
                Text(item.title, color = LibraryInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("${formatLibraryDuration(item.durationMs)} • ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(item.createdAt))}", color = LibraryMuted, fontSize = 11.sp)
            }
            Box {
                Text("⋮", color = LibraryMuted, fontSize = 22.sp, modifier = Modifier.clickable { menuOpen = true }.padding(horizontal = 8.dp))
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("إعادة تسمية") }, onClick = { menuOpen = false; onRename() })
                    DropdownMenuItem(text = { Text("حذف", color = Color(0xFFB84D42)) }, onClick = { menuOpen = false; onDelete() })
                }
            }
        }
    }
}

private fun formatLibraryDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(total / 60, total % 60)
}
