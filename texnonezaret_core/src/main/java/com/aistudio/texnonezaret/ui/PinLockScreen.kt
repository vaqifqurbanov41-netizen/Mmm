package com.aistudio.texnonezaret.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.texnonezaret.ui.theme.LocalTexnoColors
import kotlin.math.roundToInt

@Composable
fun PinLockScreen(
    state: PinUiState,
    masterErrorMessage: String?,
    masterCooldownSecondsLeft: Int,
    onDigitPress: (Char) -> Unit,
    onBackspacePress: () -> Unit,
    onClearPress: () -> Unit,
    onBackToSetup: () -> Unit,
    onCancelChange: () -> Unit,
    onConfirmResetWithPassword: (String, Boolean) -> Boolean,
) {
    val extra = LocalTexnoColors.current
    var showResetDialog by remember { mutableStateOf(false) }

    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(state.shakeTrigger) {
        if (state.shakeTrigger > 0) {
            val sequence = listOf(-14f, 14f, -10f, 10f, -6f, 0f)
            for (target in sequence) {
                shakeOffset.animateTo(target, animationSpec = tween(45))
            }
        }
    }

    if (showResetDialog) {
        var masterPassword by remember { mutableStateOf("") }
        var showPassword by remember { mutableStateOf(false) }
        var wipeData by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("PIN-i sıfırla") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "PIN-i sıfırlamaq üçün müdaxilə parolunu daxil edin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = extra.muted,
                    )
                    OutlinedTextField(
                        value = masterPassword,
                        onValueChange = { masterPassword = it },
                        singleLine = true,
                        enabled = masterCooldownSecondsLeft == 0,
                        label = { Text("Müdaxilə parolu") },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (showPassword) "Parolu gizlət" else "Parolu göstər",
                                )
                            }
                        },
                        isError = !masterErrorMessage.isNullOrBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reset_pin_master_input"),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { wipeData = !wipeData },
                    ) {
                        Checkbox(
                            checked = wipeData,
                            onCheckedChange = { wipeData = it },
                        )
                        Text(
                            text = "Yaddaşdakı Excel məlumatlarını da sil",
                            style = MaterialTheme.typography.bodySmall,
                            color = extra.ink,
                        )
                    }
                    if (!masterErrorMessage.isNullOrBlank()) {
                        Text(
                            text = masterErrorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = extra.badText,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.testTag("reset_pin_master_error"),
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val ok = onConfirmResetWithPassword(masterPassword, wipeData)
                        if (ok) {
                            showResetDialog = false
                        }
                    },
                    enabled = masterCooldownSecondsLeft == 0 && masterPassword.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = extra.brand),
                    modifier = Modifier.testTag("confirm_pin_reset_btn"),
                ) {
                    Text(if (masterCooldownSecondsLeft > 0) "Gözləyin (${masterCooldownSecondsLeft}s)" else "Təsdiqlə və sıfırla")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Ləğv et")
                }
            },
        )
    }

    val (title, subtitle) = when (state.mode) {
        PinFlowMode.SETUP -> "Yeni PIN təyin edin" to "Məlumatları qorumaq üçün 4 rəqəmli PIN yaradın"
        PinFlowMode.CONFIRM -> "PIN-i təsdiqləyin" to "Təyin etdiyiniz 4 rəqəmli PIN-i təkrar daxil edin"
        PinFlowMode.CHANGE_OLD -> "Cari PIN-i daxil edin" to "PIN-i dəyişmək üçün əvvəlcə köhnə şifrəni yazın"
        PinFlowMode.ENTER -> "Təhlükəsizlik PIN-i" to "Davam etmək üçün 4 rəqəmli şifrəni daxil edin"
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(extra.bg)
            .safeDrawingPadding()
            .padding(20.dp)
            .testTag("pin_lock_screen"),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = extra.surface,
            border = BorderStroke(1.dp, extra.line),
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = extra.brand,
                ) {
                    Text(
                        text = "TNT",
                        color = extra.brandInk,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        letterSpacing = 1.5.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(extra.okBg),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = title,
                        tint = extra.brand,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = extra.ink,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = extra.muted,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.offset { IntOffset(shakeOffset.value.roundToInt(), 0) },
                ) {
                    for (i in 0 until 4) {
                        val filled = i < state.enteredDigits
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .scale(if (filled) 1.12f else 1f)
                                .clip(CircleShape)
                                .background(if (filled) extra.brand else Color.Transparent)
                                .border(2.dp, extra.brand, CircleShape),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.height(22.dp),
                ) {
                    if (!state.message.isNullOrBlank()) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.labelMedium,
                            color = extra.badText,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("pin_error_msg"),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                val keypadRows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("CLR", "0", "DEL"),
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.widthIn(max = 276.dp),
                ) {
                    for (row in keypadRows) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            for (key in row) {
                                Surface(
                                    onClick = {
                                        when (key) {
                                            "CLR" -> onClearPress()
                                            "DEL" -> onBackspacePress()
                                            else -> onDigitPress(key.first())
                                        }
                                    },
                                    enabled = state.cooldownSecondsLeft == 0,
                                    shape = RoundedCornerShape(14.dp),
                                    color = extra.bg,
                                    border = BorderStroke(1.dp, extra.line),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(54.dp)
                                        .testTag(
                                            when (key) {
                                                "CLR" -> "pin_clear_btn"
                                                "DEL" -> "pin_backspace_btn"
                                                else -> "pin_key_$key"
                                            }
                                        ),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        when (key) {
                                            "CLR" -> Text(
                                                text = "Sil",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = extra.muted,
                                            )
                                            "DEL" -> Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                                contentDescription = "Poz",
                                                tint = extra.muted,
                                                modifier = Modifier.size(20.dp),
                                            )
                                            else -> Text(
                                                text = key,
                                                fontSize = 21.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = extra.ink,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                when (state.mode) {
                    PinFlowMode.CONFIRM -> {
                        TextButton(
                            onClick = onBackToSetup,
                            modifier = Modifier.testTag("pin_back_btn"),
                        ) {
                            Text(
                                text = "Geri qayıt",
                                color = extra.muted,
                                textDecoration = TextDecoration.Underline,
                            )
                        }
                    }
                    PinFlowMode.CHANGE_OLD -> {
                        TextButton(
                            onClick = onCancelChange,
                            modifier = Modifier.testTag("pin_cancel_btn"),
                        ) {
                            Text(
                                text = "Ləğv et",
                                color = extra.muted,
                                textDecoration = TextDecoration.Underline,
                            )
                        }
                    }
                    PinFlowMode.ENTER -> {
                        TextButton(
                            onClick = { showResetDialog = true },
                            modifier = Modifier.testTag("pin_reset_btn"),
                        ) {
                            Text(
                                text = "PIN-i unutmusunuz? (Müdaxilə parolu ilə sıfırla)",
                                color = extra.muted,
                                style = MaterialTheme.typography.bodySmall,
                                textDecoration = TextDecoration.Underline,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    PinFlowMode.SETUP -> Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}
