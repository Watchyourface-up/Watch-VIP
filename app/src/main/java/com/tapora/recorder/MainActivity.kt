package com.tapora.recorder

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.tapora.recorder.data.Recording
import com.tapora.recorder.data.TaporaDatabase
import com.tapora.recorder.service.RecordingService
import com.tapora.recorder.service.RecordingPhase
import kotlinx.coroutines.launch
import java.io.File

private val Ink = Color(0xFF172B29)
private val Teal = Color(0xFF087E70)
private val Paper = Color(0xFFF6F4EF)
private val Muted = Color(0xFF73817D)

class MainActivity : ComponentActivity() {
    private var player: MediaPlayer? = null
    private var playingId by mutableStateOf<String?>(null)
    private val db by lazy { TaporaDatabase.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val recordings by db.recordings().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
            var message by remember { mutableStateOf<String?>(null) }
            var renameTarget by remember { mutableStateOf<Recording?>(null) }
            var newTitle by remember { mutableStateOf("") }
            val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
                val notificationsGranted = Build.VERSION.SDK_INT < 33 || grants[Manifest.permission.POST_NOTIFICATIONS] == true
                if (grants[Manifest.permission.RECORD_AUDIO] == true && notificationsGranted) {
                    try { RecordingService.start(this) } catch (_: Exception) { message = "تعذر بدء التسجيل. افتح التطبيق وراجع الأذونات." }
                } else message = "يلزم السماح بالميكروفون والإشعارات لإظهار حالة التسجيل وزر إيقافه."
            }
            val phase by RecordingService.state.collectAsStateWithLifecycle()
            val recording = phase == RecordingPhase.Recording
            MaterialTheme(colorScheme = lightColorScheme(primary = Teal, background = Paper, surface = Color.White)) {
                Surface(Modifier.fillMaxSize(), color = Paper) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
                        Spacer(Modifier.height(28.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("TAPORA", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                                Text("مسجل صوتي خاص، على جهازك فقط", color = Muted, fontSize = 13.sp)
                            }
                            Box(Modifier.size(42.dp).background(Color(0xFFE1EEE9), CircleShape), contentAlignment = Alignment.Center) {
                                Text("●", color = Teal, fontSize = 20.sp)
                            }
                        }
                        Spacer(Modifier.height(26.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE4EFEB)), shape = RoundedCornerShape(24.dp)) {
                            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(when (phase) { RecordingPhase.Recording -> "التسجيل جارٍ"; RecordingPhase.Saving -> "جارٍ حفظ التسجيل…"; RecordingPhase.Error -> "حدث خطأ في التسجيل"; RecordingPhase.Ready -> "جاهز للتسجيل" }, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text(when (phase) { RecordingPhase.Recording -> "سيستمر التسجيل عند إطفاء الشاشة"; RecordingPhase.Saving -> "لن يظهر التسجيل حتى يكتمل حفظ الملف وفهرسته"; RecordingPhase.Error -> "تحقق من الأذونات والمساحة ثم أعد المحاولة"; RecordingPhase.Ready -> "ابدأ من هنا، ثم أطفئ الشاشة إن رغبت" }, color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
                                Spacer(Modifier.height(16.dp))
                                Button(onClick = {
                                    if (phase == RecordingPhase.Recording) RecordingService.stop(this@MainActivity)
                                    else if (phase == RecordingPhase.Saving) { /* Ignore duplicate stop while finalizing. */ }
                                    else {
                                        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
                                        if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                                        if (permissions.all { ContextCompat.checkSelfPermission(this@MainActivity, it) == PackageManager.PERMISSION_GRANTED }) {
                                            try { RecordingService.start(this@MainActivity) } catch (_: Exception) { message = "تعذر بدء التسجيل. تحقق من الإذن وإعدادات الجهاز." }
                                        } else permissionLauncher.launch(permissions.toTypedArray())
                                    }
                                }, colors = ButtonDefaults.buttonColors(containerColor = if (recording) Color(0xFFB84D42) else Teal), modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp)) {
                                    Text(if (recording) "إيقاف التسجيل" else if (phase == RecordingPhase.Saving) "جارٍ الحفظ…" else "●  بدء التسجيل", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                }
                                Text("مؤشر إشعار دائم ظاهر طوال التسجيل", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
                            }
                        }
                        Spacer(Modifier.height(22.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("تسجيلاتي", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("${recordings.size} ملف", color = Muted, fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                        if (recordings.isEmpty()) {
                            Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("لا توجد تسجيلات بعد", color = Muted, fontSize = 15.sp)
                                Text("ستظهر التسجيلات هنا بعد إيقافها", color = Muted, fontSize = 12.sp)
                            }
                        } else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                            items(recordings, key = { it.id }) { item ->
                                RecordingCard(item, playingId == item.id,
                                    onPlay = { togglePlayback(item, { playingId = it }, { message = it }) },
                                    onRename = { renameTarget = item; newTitle = item.title },
                                    onDelete = { stopPlayback(item.id); lifecycleScope.launch { db.recordings().delete(item.id); File(item.path).delete() } })
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Text("خصوصي ومحلي • لا يُرفع أي صوت إلى الإنترنت", color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp))
                    }
                }
                if (renameTarget != null) AlertDialog(
                    onDismissRequest = { renameTarget = null }, title = { Text("إعادة تسمية التسجيل") },
                    text = { OutlinedTextField(value = newTitle, onValueChange = { newTitle = it }, singleLine = true, label = { Text("الاسم") }) },
                    confirmButton = { TextButton(onClick = { val r = renameTarget; if (r != null && newTitle.isNotBlank()) lifecycleScope.launch { db.recordings().rename(r.id, newTitle.trim()) }; renameTarget = null }) { Text("حفظ") } },
                    dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("إلغاء") } }
                )
                if (message != null) AlertDialog(onDismissRequest = { message = null }, confirmButton = { TextButton(onClick = { message = null }) { Text("حسنًا") } }, text = { Text(message.orEmpty()) })
            }
        }
        lifecycleScope.launch {
            db.recordings().all().filterNot { File(it.path).isFile && File(it.path).length() > 0L }
                .forEach { db.recordings().deleteMissing(it.id) }
        }
    }

    private fun togglePlayback(item: Recording, set: (String?) -> Unit, error: (String) -> Unit) {
        if (playingId == item.id) { stopPlayback(item.id); set(null); return }
        stopPlayback(playingId)
        try {
            player = MediaPlayer().apply {
                setDataSource(item.path)
                setOnCompletionListener { it.release(); player = null; playingId = null; set(null) }
                prepare(); start()
            }
            playingId = item.id; set(item.id)
        } catch (_: Exception) { stopPlayback(null); error("تعذر تشغيل الملف الصوتي.") }
    }
    private fun stopPlayback(id: String?) { if (id == null || playingId == id) { try { player?.stop() } catch (_: Exception) {}; player?.release(); player = null; playingId = null } }
    override fun onDestroy() { player?.release(); player = null; super.onDestroy() }
}

@Composable
private fun RecordingCard(item: Recording, playing: Boolean, onPlay: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (playing) "Ⅱ" else "▶", color = Teal, fontSize = 18.sp, modifier = Modifier.clickable(onClick = onPlay).padding(8.dp))
            Column(Modifier.weight(1f).clickable(onClick = onPlay)) {
                Text(item.title, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("${formatDuration(item.durationMs)}  •  ${java.text.SimpleDateFormat("dd/MM/yyyy  HH:mm", java.util.Locale.getDefault()).format(java.util.Date(item.createdAt))}", color = Muted, fontSize = 11.sp)
            }
            Text("⋮", color = Muted, fontSize = 22.sp, modifier = Modifier.clickable { onRename() }.padding(horizontal = 8.dp))
            Text("×", color = Color(0xFFB84D42), fontSize = 22.sp, modifier = Modifier.clickable { onDelete() }.padding(horizontal = 6.dp))
        }
    }
}
internal fun formatDuration(ms: Long): String { val total = (ms / 1000).coerceAtLeast(0); return "%02d:%02d".format(total / 60, total % 60) }
