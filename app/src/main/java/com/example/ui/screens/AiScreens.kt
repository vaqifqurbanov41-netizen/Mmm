package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.firebase.applyCustomAppCheckDebugToken
import com.example.data.firebase.getOrCreateAppCheckDebugUuid
import com.example.data.firebase.isAppCheckTokenError
import com.example.i18n.AppLanguage
import com.example.i18n.LocalStrings
import com.example.ui.components.AqroCard
import com.example.ui.components.DangerBannerBox
import com.example.ui.components.ScreenTopBar
import com.example.ui.theme.LocalAqroColors
import com.example.ui.viewmodel.ChatMessageUi

@Composable
fun ChatScreen(
    language: AppLanguage,
    messages: List<ChatMessageUi>,
    isStreaming: Boolean,
    onSendQuestion: (String, AppLanguage) -> Unit,
    onClearChat: () -> Unit,
    onBack: () -> Unit,
    isOfflineOnly: Boolean = false,
) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val hasAppCheckIssue = messages.any { !it.isUser && isAppCheckTokenError(it.text) }

    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size + 2)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        ScreenTopBar(title = strings.aiTitle, onBack = onBack)

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
            modifier = Modifier
                .weight(1f)
                .testTag("ai_chat_list"),
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = extra.pale,
                    border = BorderStroke(1.dp, extra.line),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = if (isOfflineOnly) strings.aiOfflineNotice else strings.aiSampleNotice,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                ) {
                    strings.aiQuickPrompts.forEach { prompt ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, extra.line),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { input = prompt },
                        ) {
                            Text(
                                text = prompt,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            )
                        }
                    }
                }
            }
            item {
                ChatBubble(
                    isUser = false,
                    badge = strings.aiSampleLabel,
                    text = strings.aiWelcomeMessage,
                    isStreaming = false,
                )
            }
            items(messages, key = { it.id }) { msg ->
                ChatBubble(
                    isUser = msg.isUser,
                    badge = if (msg.isUser) null else strings.aiSampleLabel,
                    text = msg.text.ifBlank { strings.loading },
                    isStreaming = msg.isStreaming,
                )
            }
            if (hasAppCheckIssue) {
                item {
                    AppCheckTroubleCard()
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text(strings.aiQuestionLabel) },
            placeholder = { Text(strings.aiQuestionPlaceholder) },
            maxLines = 3,
            modifier = Modifier.fillMaxWidth().testTag("ai_chat_input"),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            Button(
                onClick = {
                    val q = input.trim()
                    if (q.isNotBlank()) {
                        input = ""
                        onSendQuestion(q, language)
                    }
                },
                enabled = !isStreaming && input.isNotBlank(),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .heightIn(min = 48.dp)
                    .testTag("ai_send_button"),
            ) {
                if (isStreaming) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = strings.btnSendAi,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            OutlinedButton(
                onClick = onClearChat,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .heightIn(min = 48.dp)
                    .testTag("ai_clear_button"),
            ) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = strings.btnClearChat,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(
    isUser: Boolean,
    badge: String?,
    text: String,
    isStreaming: Boolean,
) {
    val extra = LocalAqroColors.current
    Box(
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp,
            ),
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            border = if (isUser) null else BorderStroke(1.dp, extra.line),
            modifier = Modifier.widthIn(max = 520.dp),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (!badge.isNullOrBlank()) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(
                    text = if (isStreaming) "$text ●" else text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun AppCheckTroubleCard() {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current
    val context = LocalContext.current
    val deviceUuid = remember(context) { getOrCreateAppCheckDebugUuid(context) }
    var customTokenInput by remember { mutableStateOf("") }
    var appliedNotice by remember { mutableStateOf<String?>(null) }

    AqroCard(containerColor = extra.warningContainer) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Key,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = strings.aiAppCheckHelpTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = strings.aiAppCheckHelpBody,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, extra.line),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.aiDeviceDebugUuidLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = deviceUuid,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("AppCheckUUID", deviceUuid))
                        Toast.makeText(context, strings.aiDebugTokenCopiedMsg, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("copy_appcheck_uuid_btn"),
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(strings.aiCopyDebugTokenBtn)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = customTokenInput,
            onValueChange = { customTokenInput = it; appliedNotice = null },
            label = { Text(strings.aiCustomDebugTokenLabel) },
            placeholder = { Text(deviceUuid) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("appcheck_custom_token_input"),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                val target = customTokenInput.trim().ifBlank { deviceUuid }
                if (applyCustomAppCheckDebugToken(context, target)) {
                    appliedNotice = strings.aiDebugTokenAppliedMsg
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("apply_appcheck_token_btn"),
        ) {
            Text(strings.aiApplyDebugTokenBtn)
        }
        if (appliedNotice != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = appliedNotice!!,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun ScannerScreen(
    language: AppLanguage,
    selectedBitmap: Bitmap?,
    diagnosisResult: String,
    isLoading: Boolean,
    errorMessage: String?,
    onSelectBitmap: (Bitmap?) -> Unit,
    onStartDiagnosis: (String, AppLanguage) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current
    val context = LocalContext.current

    var noteText by remember { mutableStateOf("") }
    var emptyWarn by remember { mutableStateOf(false) }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onSelectBitmap(decodeUriToBitmap(context, uri))
            emptyWarn = false
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            ScreenTopBar(title = strings.scannerTitle, onBack = onBack)
        }

        item {
            AqroCard {
                Text(strings.scannerPhotoCardTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(strings.scannerPhotoHint, style = MaterialTheme.typography.bodySmall, color = extra.muted)
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        pickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.fillMaxWidth().testTag("pick_plant_photo_btn"),
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(strings.btnPickPlantPhoto)
                }

                selectedBitmap?.let { bmp ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = strings.scannerTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(14.dp)),
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it; emptyWarn = false },
                    label = { Text(strings.scannerNoteLabel) },
                    placeholder = { Text(strings.scannerNotePlaceholder) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("scanner_note_input"),
                )

                if (emptyWarn) {
                    Spacer(modifier = Modifier.height(8.dp))
                    DangerBannerBox(text = strings.scannerEmptyPhotoWarning)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (selectedBitmap == null && noteText.isBlank()) {
                            emptyWarn = true
                        } else {
                            emptyWarn = false
                            onStartDiagnosis(noteText, language)
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth().testTag("start_diagnosis_btn"),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        Icon(Icons.Filled.Science, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(strings.btnStartDiagnosis)
                    }
                }
            }
        }

        if (errorMessage != null) {
            item {
                DangerBannerBox(text = errorMessage)
            }
            if (isAppCheckTokenError(errorMessage)) {
                item {
                    AppCheckTroubleCard()
                }
            }
        }

        if (diagnosisResult.isNotBlank() || isLoading) {
            item {
                AqroCard(containerColor = extra.pale) {
                    Text(
                        text = strings.aiSampleLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = diagnosisResult.ifBlank { strings.loading },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("scanner_diagnosis_result"),
                    )
                }
            }
        }
    }
}
