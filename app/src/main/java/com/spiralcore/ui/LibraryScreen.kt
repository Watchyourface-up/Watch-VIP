package com.spiralcore.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddLink
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spiralcore.data.LinkRepository
import com.spiralcore.data.LinkWithCategory
import com.spiralcore.data.SpiralDatabase
import com.spiralcore.domain.LinkValidation
import com.spiralcore.ui.theme.DeepSea
import com.spiralcore.ui.theme.DeepSeaDark
import com.spiralcore.ui.theme.Ink
import com.spiralcore.ui.theme.MutedInk
import com.spiralcore.ui.theme.Paper
import com.spiralcore.ui.theme.SoftMint
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val repository = remember(appContext) { LinkRepository(SpiralDatabase.getInstance(appContext).linkDao()) }
    val links by repository.observeLinks().collectAsState(initial = emptyList())
    val categories by repository.observeCategories().collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

    var pastedUrl by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var categoryFilterId by remember { mutableStateOf<Long?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<LinkWithCategory?>(null) }

    LaunchedEffect(repository) { repository.ensureStarterCategories() }
    LaunchedEffect(categories) {
        if (selectedCategoryId == null && categories.isNotEmpty()) {
            selectedCategoryId = categories.first().id
        }
    }

    val visibleLinks = remember(links, categoryFilterId) {
        if (categoryFilterId == null) links else links.filter { it.link.categoryId == categoryFilterId }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "SpiralCore",
                        style = MaterialTheme.typography.labelLarge,
                        color = DeepSea,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = "روابطك.",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Ink,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "كل رابط له قوقعته الخاصة.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MutedInk,
                    )
                }
                ShellMark(seed = 0x5A17, size = 64.dp)
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = Paper),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    OutlinedTextField(
                        value = pastedUrl,
                        onValueChange = {
                            pastedUrl = it
                            message = null
                            messageIsError = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("الصق رابطًا جديدًا هنا...") },
                        isError = messageIsError,
                        leadingIcon = {
                            Icon(Icons.Outlined.AddLink, contentDescription = "رابط جديد", tint = DeepSea)
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "التصنيف",
                        style = MaterialTheme.typography.labelLarge,
                        color = MutedInk,
                    )
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(categories, key = { it.id }) { category ->
                            FilterChip(
                                selected = selectedCategoryId == category.id,
                                onClick = { selectedCategoryId = category.id },
                                label = { Text(category.name) },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val categoryId = selectedCategoryId ?: return@Button
                            coroutineScope.launch {
                                isSaving = true
                                try {
                                    when (val result = repository.createLink(pastedUrl, categoryId)) {
                                        is LinkValidation.Valid -> {
                                            pastedUrl = ""
                                            message = "تم إنشاء قوقعتك وحفظ الرابط على هذا الجهاز."
                                            messageIsError = false
                                        }
                                        is LinkValidation.Invalid -> {
                                            message = validationMessage(result)
                                            messageIsError = true
                                        }
                                    }
                                } finally {
                                    isSaving = false
                                }
                            }
                        },
                        enabled = pastedUrl.isNotBlank() && selectedCategoryId != null && !isSaving,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DeepSea),
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("جارٍ الحفظ...", fontWeight = FontWeight.SemiBold)
                        } else {
                            Text("إنشاء قوقعة", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (!message.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { liveRegion = LiveRegionMode.Polite },
                            shape = RoundedCornerShape(16.dp),
                            color = if (messageIsError) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                SoftMint
                            },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = if (messageIsError) {
                                        Icons.Outlined.ErrorOutline
                                    } else {
                                        Icons.Outlined.CheckCircleOutline
                                    },
                                    contentDescription = null,
                                    tint = if (messageIsError) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        DeepSeaDark
                                    },
                                    modifier = Modifier.size(20.dp),
                                )
                                Column {
                                    Text(
                                        text = if (messageIsError) "تعذر حفظ الرابط" else "تم الحفظ بنجاح",
                                        color = if (messageIsError) {
                                            MaterialTheme.colorScheme.onErrorContainer
                                        } else {
                                            DeepSeaDark
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = message.orEmpty(),
                                        color = if (messageIsError) {
                                            MaterialTheme.colorScheme.onErrorContainer
                                        } else {
                                            DeepSeaDark
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "مجموعتك",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${visibleLinks.size} رابط",
                        style = MaterialTheme.typography.labelMedium,
                        color = MutedInk,
                    )
                    TextButton(onClick = { categoryFilterId = null }) {
                        Text("عرض الكل", color = DeepSea)
                    }
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "all") {
                    FilterChip(
                        selected = categoryFilterId == null,
                        onClick = { categoryFilterId = null },
                        label = { Text("الكل") },
                    )
                }
                items(categories, key = { "filter-${it.id}" }) { category ->
                    FilterChip(
                        selected = categoryFilterId == category.id,
                        onClick = { categoryFilterId = category.id },
                        label = { Text(category.name) },
                    )
                }
            }
        }

        if (visibleLinks.isEmpty()) {
            item {
                EmptyLibraryState(
                    hasAnyLinks = links.isNotEmpty(),
                    onResetFilter = { categoryFilterId = null },
                )
            }
        } else {
            items(visibleLinks, key = { it.link.id }) { item ->
                LinkCard(
                    item = item,
                    onOpen = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.link.url))
                            context.startActivity(intent)
                        } catch (_: ActivityNotFoundException) {
                            message = "لا يوجد تطبيق لفتح هذا الرابط."
                            messageIsError = true
                        }
                    },
                    onDelete = { pendingDelete = item },
                    onCopy = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("الرابط", item.link.url))
                        message = "تم نسخ الرابط."
                        messageIsError = false
                    },
                    onShare = {
                        try {
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, item.link.url)
                                    },
                                    "مشاركة الرابط",
                                ),
                            )
                        } catch (_: ActivityNotFoundException) {
                            message = "لا يوجد تطبيق لمشاركة الرابط."
                            messageIsError = true
                        }
                    },
                )
            }
        }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("حذف القوقعة؟") },
            text = { Text("سيُحذف الرابط من هذه المكتبة نهائيًا.") },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch {
                        try {
                            repository.deleteLink(item.link.id)
                            pendingDelete = null
                            message = "تم حذف الرابط."
                            messageIsError = false
                        } catch (_: kotlinx.coroutines.CancellationException) {
                            throw kotlinx.coroutines.CancellationException()
                        } catch (_: Exception) {
                            pendingDelete = null
                            message = "تعذر حذف الرابط. لم يتغير محتوى المكتبة."
                            messageIsError = true
                        }
                    }
                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("إلغاء") }
            },
        )
    }
}

@Composable
private fun LinkCard(
    item: LinkWithCategory,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onOpen),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Paper),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShellMark(seed = item.link.shellSeed, size = 58.dp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.link.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.link.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                )
                Spacer(Modifier.height(8.dp))
                Surface(color = SoftMint, shape = CircleShape) {
                    Text(
                        text = item.category?.name ?: "بلا تصنيف",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = DeepSeaDark,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(onClick = onCopy) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "نسخ الرابط", tint = DeepSea)
                    }
                    IconButton(onClick = onShare) {
                        Icon(Icons.Outlined.Share, contentDescription = "مشاركة الرابط", tint = DeepSea)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = "حذف الرابط",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            IconButton(onClick = onOpen) {
                Icon(Icons.Outlined.OpenInNew, contentDescription = "فتح الرابط", tint = DeepSea)
            }
        }
    }
}

@Composable
private fun EmptyLibraryState(hasAnyLinks: Boolean, onResetFilter: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = SoftMint,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ShellMark(seed = 0x1C0, size = 54.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (hasAnyLinks) "لا توجد روابط في هذا التصنيف." else "ابدأ بقوقعتك الأولى.",
                style = MaterialTheme.typography.bodyLarge,
                color = DeepSeaDark,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "كل رابط له قوقعته الخاصة.",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedInk,
                textAlign = TextAlign.Center,
            )
            if (hasAnyLinks) {
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onResetFilter) {
                    Text("عرض كل الروابط", color = DeepSea)
                }
            }
        }
    }
}

private fun validationMessage(validation: LinkValidation.Invalid): String = when (validation.reason) {
    LinkValidation.Reason.EMPTY -> "الصق رابطًا أولًا."
    LinkValidation.Reason.INVALID_FORMAT -> "صيغة الرابط غير صحيحة."
    LinkValidation.Reason.UNSUPPORTED_SCHEME -> "استخدم رابط HTTP أو HTTPS فقط."
    LinkValidation.Reason.CREDENTIALS_NOT_ALLOWED -> "لا يمكن حفظ رابط يحتوي بيانات دخول."
    LinkValidation.Reason.INVALID_HOST -> "تعذر التحقق من عنوان الموقع."
    LinkValidation.Reason.DUPLICATE -> "هذا الرابط محفوظ مسبقًا."
}
