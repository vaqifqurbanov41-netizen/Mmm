package com.aistudio.testbaza.core.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.aistudio.testbaza.core.domain.QuizEngine
import com.aistudio.testbaza.core.domain.SharedExam
import com.aistudio.testbaza.core.domain.SharedExamAttempt
import com.aistudio.testbaza.core.ui.theme.LocalVipColors
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun GoogleAuthPromptDialog(
    isBusy: Boolean,
    errorMessage: String?,
    onGoogleSignInClick: () -> Unit,
    onBrowserSignInClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val vip = LocalVipColors.current
    Dialog(onDismissRequest = { if (!isBusy) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = vip.surface,
            border = BorderStroke(1.2.dp, vip.goldBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(vip.goldSoft),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Google Hesabı",
                                tint = vip.gold,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Column {
                            Text(
                                text = "Google ilə Daxil Olun",
                                style = MaterialTheme.typography.titleLarge,
                                color = vip.textPrimary,
                            )
                            Text(
                                text = "İmtahan paylaşmaq və qoşulmaq üçün",
                                style = MaterialTheme.typography.bodySmall,
                                color = vip.textSecondary,
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isBusy,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Bağla",
                            tint = vip.textSecondary,
                        )
                    }
                }

                Text(
                    text = "Oflayn test və sınaq imtahanı giriş tələb etmir. İmtahanı digər telefonlarla paylaşmaq və iştirakçı nəticələrini görmək üçün Google hesabınızla daxil olun.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = vip.textSecondary,
                )

                if (!errorMessage.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = vip.roseSoft,
                        border = BorderStroke(1.dp, vip.roseBorder),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = vip.textPrimary,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                Button(
                    onClick = onGoogleSignInClick,
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("google_sign_in_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = vip.emerald,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Google Hesabı ilə Daxil Ol")
                    }
                }

                OutlinedButton(
                    onClick = onBrowserSignInClick,
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("google_browser_sign_in_button"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, vip.borderStrong),
                ) {
                    Text("Brauzer vasitəsilə daxil ol")
                }
            }
        }
    }
}

@Composable
fun PublishExamDialog(
    defaultTitle: String,
    defaultOwnerName: String,
    questionCount: Int,
    durationMinutes: Int,
    isBusy: Boolean,
    publishedExam: SharedExam?,
    onPublish: (title: String, ownerName: String) -> Unit,
    onCopyCode: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val vip = LocalVipColors.current
    val context = LocalContext.current
    var title by remember(defaultTitle) { mutableStateOf(defaultTitle.ifBlank { "Testbaza İmtahanı" }) }
    var ownerName by remember(defaultOwnerName) { mutableStateOf(defaultOwnerName) }

    Dialog(onDismissRequest = { if (!isBusy) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = vip.surface,
            border = BorderStroke(1.2.dp, vip.goldBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (publishedExam == null) "İmtahanı Paylaş" else "İmtahan Hazırdır",
                        style = MaterialTheme.typography.titleLarge,
                        color = vip.gold,
                    )
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isBusy,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Bağla",
                            tint = vip.textSecondary,
                        )
                    }
                }

                if (publishedExam == null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = vip.surfaceElevated,
                        border = BorderStroke(1.dp, vip.border),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Bu paylaşıma $questionCount sual və $durationMinutes dəqiqə vaxt limiti daxil ediləcək. İmtahanı alan digər Testbaza istifadəçisi icazə verərsə, nəticəsi, səhvləri və hər sual üzrə vaxtı sizə görünəcək.",
                            style = MaterialTheme.typography.bodySmall,
                            color = vip.textSecondary,
                            modifier = Modifier.padding(12.dp),
                        )
                    }

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("İmtahanın adı") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("publish_exam_title_input"),
                    )

                    OutlinedTextField(
                        value = ownerName,
                        onValueChange = { ownerName = it },
                        label = { Text("Paylaşanın adı") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("publish_exam_owner_input"),
                    )

                    Button(
                        onClick = { onPublish(title, ownerName) },
                        enabled = !isBusy && title.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .testTag("publish_exam_confirm_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = vip.emerald,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Paylaşım Kodu Yarat")
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = vip.goldSoft,
                        border = BorderStroke(1.2.dp, vip.goldBorder),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "İmtahan Kodu",
                                style = MaterialTheme.typography.labelMedium,
                                color = vip.textSecondary,
                            )
                            Text(
                                text = publishedExam.id,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = vip.gold,
                                modifier = Modifier.testTag("published_exam_code_text"),
                            )
                            Text(
                                text = "${publishedExam.questionCount} sual • ${publishedExam.durationMinutes} dəqiqə",
                                style = MaterialTheme.typography.bodySmall,
                                color = vip.textPrimary,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                copyCodeToClipboard(context, publishedExam.id)
                                onCopyCode(publishedExam.id)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 50.dp)
                                .testTag("copy_exam_code_button"),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Kopyala")
                        }

                        Button(
                            onClick = { shareExamViaIntent(context, publishedExam) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 50.dp)
                                .testTag("android_share_exam_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = vip.emerald,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Paylaş")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun JoinSharedExamDialog(
    defaultParticipantName: String,
    isBusy: Boolean,
    previewExam: SharedExam?,
    onLookupCode: (String) -> Unit,
    onStartExam: (exam: SharedExam, participantName: String, consentGranted: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val vip = LocalVipColors.current
    var codeInput by remember { mutableStateOf("") }
    var participantName by remember(defaultParticipantName) { mutableStateOf(defaultParticipantName) }
    var consentGranted by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { if (!isBusy) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = vip.surface,
            border = BorderStroke(1.2.dp, vip.emeraldBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Paylaşılan İmtahana Qoşul",
                        style = MaterialTheme.typography.titleLarge,
                        color = vip.textPrimary,
                    )
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isBusy,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Bağla", tint = vip.textSecondary)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { codeInput = it.uppercase().take(32) },
                        label = { Text("İmtahan kodu (məs: TB4K9M2A)") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("join_exam_code_input"),
                    )
                    Button(
                        onClick = { onLookupCode(codeInput) },
                        enabled = !isBusy && codeInput.trim().length >= 4,
                        modifier = Modifier
                            .heightIn(min = 52.dp)
                            .testTag("join_exam_lookup_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = vip.gold),
                    ) {
                        Text("Tap", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }

                if (isBusy) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(color = vip.gold, modifier = Modifier.size(28.dp))
                    }
                }

                if (previewExam != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = vip.surfaceElevated,
                        border = BorderStroke(1.dp, vip.goldBorder),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = previewExam.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = vip.gold,
                            )
                            Text(
                                text = "Paylaşan: ${previewExam.ownerName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = vip.textSecondary,
                            )
                            Text(
                                text = "${previewExam.questionCount} sual • ${previewExam.durationMinutes} dəqiqə",
                                style = MaterialTheme.typography.labelLarge,
                                color = vip.emerald,
                            )
                        }
                    }

                    OutlinedTextField(
                        value = participantName,
                        onValueChange = { participantName = it },
                        label = { Text("Adınız və soyadınız") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("join_participant_name_input"),
                    )

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (consentGranted) vip.emeraldSoft else vip.surfaceElevated,
                        border = BorderStroke(
                            1.dp,
                            if (consentGranted) vip.emeraldBorder else vip.border,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Məxfilik icazəsi",
                                tint = if (consentGranted) vip.emerald else vip.gold,
                                modifier = Modifier.size(20.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Nəticə paylaşma icazəsi",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = vip.textPrimary,
                                )
                                Text(
                                    text = "İcazə versəniz, imtahan nəticəniz, səhv etdiyiniz suallar və hər suala sərf etdiyiniz vaxt (${previewExam.ownerName}) ilə paylaşılacaq.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = vip.textSecondary,
                                )
                            }
                            Switch(
                                checked = consentGranted,
                                onCheckedChange = { consentGranted = it },
                                modifier = Modifier.testTag("join_exam_consent_switch"),
                            )
                        }
                    }

                    Button(
                        onClick = {
                            onStartExam(
                                previewExam,
                                participantName.ifBlank { "İştirakçı" },
                                consentGranted,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .testTag("join_exam_start_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = vip.emerald,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("İmtahana Başla")
                    }
                }
            }
        }
    }
}

@Composable
fun MySharedExamsDialog(
    sharedExams: List<SharedExam>,
    inspectedExamId: String?,
    attempts: List<SharedExamAttempt>,
    attemptsError: String?,
    onSelectExam: (String?) -> Unit,
    onDeleteExam: (String) -> Unit,
    onCopyCode: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val vip = LocalVipColors.current
    val context = LocalContext.current
    val inspectedExam = sharedExams.firstOrNull { it.id == inspectedExamId }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = vip.surface,
            border = BorderStroke(1.2.dp, vip.goldBorder),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (inspectedExam != null) {
                            IconButton(
                                onClick = { onSelectExam(null) },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Geri",
                                    tint = vip.gold,
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = inspectedExam?.title ?: "Paylaşdığım İmtahanlar",
                                style = MaterialTheme.typography.titleLarge,
                                color = vip.gold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (inspectedExam != null) {
                                    "Kod: ${inspectedExam.id} • İcazəli iştirakçılar"
                                } else {
                                    "Digər telefonlarla paylaşılan imtahanlar"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = vip.textSecondary,
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Bağla", tint = vip.textSecondary)
                    }
                }

                if (inspectedExam == null) {
                    if (sharedExams.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = vip.surfaceElevated,
                            border = BorderStroke(1.dp, vip.border),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "Hələ paylaşdığınız imtahan yoxdur. Tənzimləmələr -> İmtahan Bölməsindən imtahan yaradıb paylaşa bilərsiniz.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = vip.textSecondary,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f, fill = false),
                        ) {
                            items(sharedExams, key = { it.id }) { exam ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { onSelectExam(exam.id) },
                                    shape = RoundedCornerShape(16.dp),
                                    color = vip.surfaceElevated,
                                    border = BorderStroke(1.dp, vip.borderStrong),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = exam.title,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = vip.textPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    text = "Kod: ${exam.id} • ${exam.questionCount} sual • ${exam.durationMinutes} dəq",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = vip.gold,
                                                )
                                            }
                                            IconButton(
                                                onClick = { onDeleteExam(exam.id) },
                                                modifier = Modifier.size(48.dp),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = "İmtahanı sil",
                                                    tint = vip.rose,
                                                )
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    copyCodeToClipboard(context, exam.id)
                                                    onCopyCode(exam.id)
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .heightIn(min = 48.dp),
                                                shape = RoundedCornerShape(12.dp),
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.size(4.dp))
                                                Text("Kopyala")
                                            }
                                            Button(
                                                onClick = { onSelectExam(exam.id) },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .heightIn(min = 48.dp),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = vip.emerald,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                                ),
                                            ) {
                                                Icon(Icons.Default.Assessment, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.size(4.dp))
                                                Text("Nəticələr")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (!attemptsError.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = vip.roseSoft,
                            border = BorderStroke(1.dp, vip.roseBorder),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = attemptsError,
                                style = MaterialTheme.typography.bodySmall,
                                color = vip.textPrimary,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                    if (attempts.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = vip.surfaceElevated,
                            border = BorderStroke(1.dp, vip.border),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "Hələ icazəli nəticə yoxdur. İmtahanda iştirak edən şəxs nəticəsini paylaşmağa icazə verdikdə burada görünəcək.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = vip.textSecondary,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f, fill = false),
                        ) {
                            items(attempts, key = { it.id }) { attempt ->
                                ParticipantAttemptCard(attempt = attempt)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParticipantAttemptCard(attempt: SharedExamAttempt) {
    val vip = LocalVipColors.current
    var showTimings by remember { mutableStateOf(false) }
    val total = attempt.totalQuestions.coerceAtLeast(1L)
    val pct = ((attempt.correctCount * 100L) / total).toInt()
    val dateLabel = remember(attempt.createdAt) {
        val date = attempt.createdAt?.toDate()
        if (date != null) SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(date) else ""
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = vip.surfaceElevated,
        border = BorderStroke(1.dp, vip.borderStrong),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = attempt.participantName.ifBlank { "İştirakçı" },
                        style = MaterialTheme.typography.titleMedium,
                        color = vip.textPrimary,
                    )
                    if (dateLabel.isNotBlank()) {
                        Text(
                            text = dateLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = vip.textSecondary,
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = vip.emeraldSoft,
                    border = BorderStroke(1.dp, vip.emeraldBorder),
                ) {
                    Text(
                        text = "$pct% (${attempt.correctCount}/${attempt.totalQuestions})",
                        style = MaterialTheme.typography.labelLarge,
                        color = vip.emerald,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Düzgün: ${attempt.correctCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = vip.emerald,
                )
                Text(
                    text = "• Səhv: ${attempt.wrongCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = vip.rose,
                )
                Text(
                    text = "• Boş: ${attempt.unansweredCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = vip.textSecondary,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = vip.gold,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "Ümumi vaxt: ${QuizEngine.formatDurationHumanAz(attempt.totalElapsedSeconds)}" +
                        if (attempt.timedOut) " (Vaxt bitdi)" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = vip.gold,
                )
            }

            if (attempt.wrongDetails.isNotEmpty()) {
                HorizontalDivider(color = vip.border)
                Text(
                    text = "Səhv Edilən Suallar (${attempt.wrongDetails.size}):",
                    style = MaterialTheme.typography.labelLarge,
                    color = vip.rose,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    attempt.wrongDetails.forEach { wrong ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = vip.roseSoft.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, vip.roseBorder),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = "Sual #${wrong.questionNumber} (${QuizEngine.formatDurationHumanAz(wrong.timeSpentSeconds)}): ${wrong.questionText}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = vip.textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "Verilən cavab: ${wrong.selectedAnswer}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = vip.rose,
                                )
                                Text(
                                    text = "Doğru cavab: ${wrong.correctAnswer}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = vip.emerald,
                                )
                            }
                        }
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = vip.emerald,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Səhv cavab yoxdur.",
                        style = MaterialTheme.typography.bodySmall,
                        color = vip.emerald,
                    )
                }
            }

            TextButton(
                onClick = { showTimings = !showTimings },
                modifier = Modifier.align(Alignment.Start),
            ) {
                Text(
                    text = if (showTimings) {
                        "Sual üzrə vaxtları gizlət"
                    } else {
                        "Hər suala sərf olunan vaxtı göstər (${attempt.questionTimings.size})"
                    },
                    color = vip.gold,
                )
            }

            if (showTimings && attempt.questionTimings.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    attempt.questionTimings.forEach { timing ->
                        val bg = when (timing.status) {
                            "CORRECT" -> vip.emeraldSoft
                            "WRONG" -> vip.roseSoft
                            else -> vip.surface
                        }
                        val fg = when (timing.status) {
                            "CORRECT" -> vip.emerald
                            "WRONG" -> vip.rose
                            else -> vip.textSecondary
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = bg,
                            border = BorderStroke(1.dp, fg.copy(alpha = 0.5f)),
                        ) {
                            Text(
                                text = "#${timing.questionNumber} • ${timing.timeSpentSeconds} san",
                                style = MaterialTheme.typography.labelSmall,
                                color = fg,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun copyCodeToClipboard(context: Context, code: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Testbaza İmtahan Kodu", code))
}

private fun shareExamViaIntent(context: Context, exam: SharedExam) {
    val shareText = buildString {
        appendLine("Testbaza imtahanına dəvət!")
        appendLine("İmtahan: ${exam.title}")
        appendLine("Suallar: ${exam.questionCount} sual | Vaxt: ${exam.durationMinutes} dəqiqə")
        appendLine("İmtahan kodu: ${exam.id}")
        append("Testbaza tətbiqini açın -> Tənzimləmələr -> İmtahan Bölməsi -> 'Kodu ilə Qoşul' seçib kodu daxil edin.")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Testbaza İmtahanı: ${exam.title}")
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    context.startActivity(Intent.createChooser(intent, "İmtahanı paylaş"))
}
