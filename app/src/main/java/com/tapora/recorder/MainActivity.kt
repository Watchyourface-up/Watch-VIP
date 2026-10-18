package com.tapora.recorder

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tapora.recorder.service.RecordingService
import com.tapora.recorder.service.VolumeShortcutService

private val Ink = Color(0xFF172B29)
private val Teal = Color(0xFF087E70)
private val Paper = Color(0xFFF6F4EF)
private val Muted = Color(0xFF73817D)
private val Danger = Color(0xFFB84D42)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
                var microphoneGranted by remember { mutableStateOf(hasMicrophonePermission()) }
                var notificationsGranted by remember { mutableStateOf(hasNotificationPermission()) }
                var accessibilityEnabled by remember { mutableStateOf(isVolumeShortcutServiceEnabled()) }
                var shortcutEnabled by remember { mutableStateOf(shortcutPreference()) }
                var serviceArmed by remember { mutableStateOf(RecordingService.isArmed) }
                var recording by remember { mutableStateOf(RecordingService.isRecording) }
                var showDisclosure by remember { mutableStateOf(false) }
                var pendingShortcutSetup by remember { mutableStateOf(false) }
                var message by remember { mutableStateOf<String?>(null) }
                val lifecycleOwner = LocalLifecycleOwner.current

                val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                    microphoneGranted = hasMicrophonePermission()
                    notificationsGranted = hasNotificationPermission()
                    if (microphoneGranted && notificationsGranted && pendingShortcutSetup) {
                        pendingShortcutSetup = false
                        activateShortcutAndOpenSettings { message = it }
                        shortcutEnabled = shortcutPreference()
                    } else if (!microphoneGranted || !notificationsGranted) {
                        pendingShortcutSetup = false
                        message = "لم تكتمل الموافقات المطلوبة. يمكنك منح إذن الميكروفون وإذن الإشعارات من هذه الشاشة ثم متابعة الإعداد."
                    }
                }

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            microphoneGranted = hasMicrophonePermission()
                            notificationsGranted = hasNotificationPermission()
                            accessibilityEnabled = isVolumeShortcutServiceEnabled()
                            shortcutEnabled = shortcutPreference()
                            recording = RecordingService.isRecording
                            serviceArmed = RecordingService.isArmed
                            if (shortcutEnabled && accessibilityEnabled && microphoneGranted && notificationsGranted && !serviceArmed) {
                                try {
                                    RecordingService.arm(this@MainActivity)
                                } catch (_: Exception) {
                                    message = "تعذر إعداد خدمة التسجيل. يمكنك استخدام بلاطة TAPORA في الإعدادات السريعة أو إعادة فتح التطبيق."
                                }
                            } else if (shortcutEnabled && !accessibilityEnabled && serviceArmed && !RecordingService.isRecording) {
                                RecordingService.stop(this@MainActivity)
                                shortcutEnabled = false
                            }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                DisposableEffect(this@MainActivity) {
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(context: android.content.Context?, intent: Intent?) {
                            serviceArmed = RecordingService.isArmed
                            recording = RecordingService.isRecording
                        }
                    }
                    ContextCompat.registerReceiver(this@MainActivity, receiver, IntentFilter(RecordingService.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
                    onDispose { unregisterReceiver(receiver) }
                }

                MaterialTheme(colorScheme = lightColorScheme(primary = Teal, background = Paper, surface = Color.White)) {
                    Surface(Modifier.fillMaxSize(), color = Paper) {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("TAPORA", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                                    Text("الإعدادات وحالة الاستخدام", color = Muted, fontSize = 14.sp)
                                }
                                Box(Modifier.size(42.dp).background(Color(0xFFE1EEE9), CircleShape), contentAlignment = Alignment.Center) {
                                    Text("●", color = Teal, fontSize = 20.sp)
                                }
                            }

                            Card(colors = CardDefaults.cardColors(containerColor = if (recording) Color(0xFFFFE8E2) else Color(0xFFE4EFEB)), shape = RoundedCornerShape(22.dp)) {
                                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                                    Text(if (recording) "التسجيل جارٍ الآن" else "حالة التسجيل", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        readinessText(recording, microphoneGranted, notificationsGranted, shortcutEnabled, accessibilityEnabled, serviceArmed),
                                        color = if (!microphoneGranted || !notificationsGranted || (shortcutEnabled && (!accessibilityEnabled || !serviceArmed))) Danger else Muted,
                                        fontSize = 13.sp
                                    )
                                    if (recording) Text("لإيقاف التسجيل، استخدم زر الإيقاف في إشعار TAPORA أو بلاطة TAPORA في الإعدادات السريعة.", color = Danger, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                                    else Text("لم يُتحقق من عمل اختصار الزر عند إطفاء الشاشة على هذا الجهاز. منح الأذونات وحده لا يعني أن الاختصار يعمل.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                                }
                            }

                            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                    Text("الأذونات والخصوصية", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text("يُستخدم الميكروفون عند بدء التسجيل فقط. وتعرض الإشعارات حالة التسجيل وتتيح إيقافه. تُحفظ التسجيلات على هذا الجهاز ولا تُرفع عبر TAPORA.", color = Muted, fontSize = 11.sp)
                                    StatusRow("الميكروفون", microphoneGranted, "ممنوح", "غير ممنوح")
                                    StatusRow("الإشعارات", notificationsGranted, "ممنوحة", "غير ممنوحة")
                                    if (!microphoneGranted || !notificationsGranted) {
                                        Button(onClick = { requestMissingPermissions(permissionLauncher) }, shape = RoundedCornerShape(14.dp)) {
                                            Text("منح الأذونات")
                                        }
                                    } else Text("الأذونات متاحة. تحقق من حالة خدمة الاختصار أدناه؛ فالإذن وحده لا يؤكد عمله.", color = Muted, fontSize = 11.sp)
                                }
                            }

                            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text("اختصار زر رفع الصوت", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                            Text("اضغط مرتين متتاليتين لبدء التسجيل أو إيقافه", color = Muted, fontSize = 12.sp)
                                        }
                                        Switch(checked = shortcutEnabled, onCheckedChange = { enabled ->
                                            if (enabled) showDisclosure = true
                                            else if (recording || RecordingService.isRecording) {
                                                message = "التسجيل جارٍ. أوقفه من إشعار TAPORA أو من بلاطة TAPORA في الإعدادات السريعة قبل تعطيل الاختصار."
                                            } else {
                                                getSharedPreferences(VolumeShortcutService.PREFS, MODE_PRIVATE).edit().putBoolean(VolumeShortcutService.KEY_VOLUME_SHORTCUT_ENABLED, false).apply()
                                                shortcutEnabled = false
                                                RecordingService.stop(this@MainActivity)
                                            }
                                        })
                                    }
                                    StatusRow("خدمة اختصار الزر", accessibilityEnabled, "مفعّلة", "غير مفعّلة")
                                    StatusRow("خدمة التسجيل", serviceArmed, "مهيّأة", "غير مهيّأة")
                                    if (shortcutEnabled && !accessibilityEnabled) {
                                        Text("للاستفادة من الاختصار، فعّل خدمة TAPORA من إعدادات إمكانية الوصول في Android. ستظهر باسم «اختصار زر رفع الصوت في TAPORA».", color = Danger, fontSize = 12.sp)
                                        OutlinedButton(onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("فتح إعدادات إمكانية الوصول") }
                                    }
                                    Text("يستخدم الاختصار ضغطات زر رفع الصوت فقط، ولا يقرأ محتوى الشاشة. تبقى الضغطة المنفردة وسائر أزرار الصوت للاستخدام المعتاد.", color = Muted, fontSize = 11.sp)
                                    Text("قد تمنع إعدادات الجهاز أو إدارة البطارية عمل الاختصار في الخلفية. قد تختلف الخيارات المتاحة بحسب طراز الهاتف وإصدار النظام.", color = Muted, fontSize = 11.sp)
                                    OutlinedButton(onClick = {
                                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                                    }) { Text("إعدادات TAPORA والبطارية") }
                                }
                            }

                            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("طريقة الاستخدام", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text("امنح إذني الميكروفون والإشعارات، ثم راجع الإفصاح وفعّل اختصار زر رفع الصوت وخدمة TAPORA من إعدادات إمكانية الوصول. تظهر حالة الجاهزية في الأعلى؛ اختبر الاختصار على جهازك قبل الاعتماد عليه، إذ قد لا يعمل عند إطفاء الشاشة.", color = Muted, fontSize = 12.sp)
                                    Text("يمكنك بدء التسجيل أو إيقافه من بلاطة TAPORA في الإعدادات السريعة. أثناء التسجيل يبقى إشعار واضح ظاهرًا مع زر للإيقاف.", color = Muted, fontSize = 12.sp)
                                }
                            }

                            OutlinedButton(onClick = { startActivity(Intent(this@MainActivity, LibraryActivity::class.java)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                                Text("مكتبة التسجيلات")
                            }
                            Text("تُحفظ التسجيلات على هذا الجهاز، ولا تُرسل إلى الإنترنت.", color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    if (showDisclosure) AlertDialog(
                        onDismissRequest = { showDisclosure = false },
                        title = { Text("إفصاح اختصار زر رفع الصوت") },
                        text = { Text("إذا فعّلت هذه الميزة، ستستخدم TAPORA خدمة إمكانية الوصول لاكتشاف ضغطتين متتاليتين على زر رفع الصوت لبدء التسجيل أو إيقافه. تلتقط الخدمة ضغطات الزر فقط؛ ولا تقرأ محتوى الشاشة أو تسجل الصوت قبل بدء التسجيل. يُستخدم الميكروفون للتسجيل بعد تفعيل الاختصار أو من بلاطة TAPORA، وتظل علامة الميكروفون وإشعار واضح مع زر الإيقاف ظاهرين أثناء التسجيل. تُحفظ الملفات على جهازك ولا ترسلها TAPORA إلى الإنترنت. قد لا يعمل الاختصار عند إطفاء الشاشة على بعض الأجهزة، ولا يعني منح الأذونات أن الميزة تعمل. سيطلب Android تفعيل خدمة TAPORA من إعدادات إمكانية الوصول؛ لا تتابع إلا إذا كنت توافق على ذلك.") },
                        confirmButton = { TextButton(onClick = {
                            showDisclosure = false
                            if (hasMicrophonePermission() && hasNotificationPermission()) {
                                activateShortcutAndOpenSettings { message = it }
                                shortcutEnabled = shortcutPreference()
                            } else {
                                pendingShortcutSetup = true
                                requestMissingPermissions(permissionLauncher)
                            }
                        }) { Text("أوافق والمتابعة") } },
                        dismissButton = { TextButton(onClick = { showDisclosure = false }) { Text("ليس الآن") } }
                    )
                    if (message != null) AlertDialog(onDismissRequest = { message = null }, confirmButton = { TextButton(onClick = { message = null }) { Text("حسنًا") } }, text = { Text(message.orEmpty()) })
                }
            }
        }
    }

    private fun requestMissingPermissions(launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>) {
        val needed = mutableListOf<String>()
        if (!hasMicrophonePermission()) needed += Manifest.permission.RECORD_AUDIO
        if (Build.VERSION.SDK_INT >= 33 && !hasNotificationPermission()) needed += Manifest.permission.POST_NOTIFICATIONS
        if (needed.isNotEmpty()) launcher.launch(needed.toTypedArray())
    }

    private fun activateShortcutAndOpenSettings(onError: (String) -> Unit) {
        getSharedPreferences(VolumeShortcutService.PREFS, MODE_PRIVATE).edit().putBoolean(VolumeShortcutService.KEY_VOLUME_SHORTCUT_ENABLED, true).apply()
        try {
            // Start the microphone-type FGS from a visible Activity before handing control to system Settings.
            RecordingService.arm(this)
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: Exception) {
            getSharedPreferences(VolumeShortcutService.PREFS, MODE_PRIVATE).edit().putBoolean(VolumeShortcutService.KEY_VOLUME_SHORTCUT_ENABLED, false).apply()
            onError("تعذر إعداد خدمة التسجيل، ولم يبدأ التسجيل. يمكنك استخدام بلاطة TAPORA في الإعدادات السريعة كبديل.")
        }
    }

    private fun hasMicrophonePermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    private fun hasNotificationPermission() = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    private fun shortcutPreference() = getSharedPreferences(VolumeShortcutService.PREFS, MODE_PRIVATE).getBoolean(VolumeShortcutService.KEY_VOLUME_SHORTCUT_ENABLED, false)

    private fun isVolumeShortcutServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it)?.className == VolumeShortcutService::class.java.name }
    }

    private fun readinessText(recording: Boolean, mic: Boolean, notifications: Boolean, shortcut: Boolean, accessibility: Boolean, armed: Boolean): String = when {
        recording -> "التسجيل جارٍ. استخدم إشعار TAPORA لإيقافه بأمان."
        !mic -> "غير جاهز: امنح TAPORA إذن الميكروفون لبدء التسجيل."
        !notifications -> "غير جاهز: اسمح بالإشعارات لعرض حالة التسجيل وإتاحة إيقافه."
        !shortcut -> "غير جاهز: فعّل اختصار زر رفع الصوت."
        !accessibility -> "غير جاهز: فعّل خدمة اختصار TAPORA من إعدادات إمكانية الوصول."
        !armed -> "غير جاهز: أعد فتح TAPORA لإكمال إعداد خدمة التسجيل."
        else -> "اكتملت متطلبات الإعداد. لم يُتحقق من عمل الاختصار عند إطفاء الشاشة؛ اختبره على جهازك قبل الاعتماد عليه."
    }
}

@Composable
private fun StatusRow(label: String, granted: Boolean, grantedText: String, missingText: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(if (granted) "●" else "○", color = if (granted) Teal else Danger, fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Text(label, color = Ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(if (granted) grantedText else missingText, color = if (granted) Teal else Danger, fontSize = 11.sp)
    }
}

internal fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(total / 60, total % 60)
}
