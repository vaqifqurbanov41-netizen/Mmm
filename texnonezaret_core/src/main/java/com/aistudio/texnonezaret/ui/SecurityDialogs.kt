package com.aistudio.texnonezaret.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aistudio.texnonezaret.security.SecurityIntegrityReport
import com.aistudio.texnonezaret.ui.theme.LocalTexnoColors

@Composable
fun InterventionPasswordDialog(
    actionLabel: String?,
    errorMessage: String?,
    cooldownSecondsLeft: Int,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val extra = LocalTexnoColors.current
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = null,
                tint = extra.brand,
                modifier = Modifier.size(28.dp),
            )
        },
        title = {
            Text(
                text = "Müdaxilə icazəsi tələb olunur",
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!actionLabel.isNullOrBlank()) {
                    Text(
                        text = "Əməliyyat: $actionLabel. Dəyişiklik və ya müdaxilə etmək üçün icazə parolunu daxil edin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = extra.muted,
                    )
                } else {
                    Text(
                        text = "Sistemə müdaxilə etmək üçün icazə parolunu daxil edin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = extra.muted,
                    )
                }

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    enabled = cooldownSecondsLeft == 0,
                    label = { Text("Müdaxilə parolu") },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (cooldownSecondsLeft == 0 && password.isNotEmpty()) {
                                onSubmit(password)
                            }
                        }
                    ),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showPassword) "Parolu gizlət" else "Parolu göstər",
                            )
                        }
                    },
                    isError = !errorMessage.isNullOrBlank(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = extra.brand,
                        unfocusedBorderColor = extra.line,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("intervention_password_input"),
                )

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = extra.badText,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("intervention_password_error"),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(password) },
                enabled = cooldownSecondsLeft == 0 && password.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = extra.brand),
                modifier = Modifier.testTag("intervention_submit_btn"),
            ) {
                Text(if (cooldownSecondsLeft > 0) "Gözləyin (${cooldownSecondsLeft}s)" else "Təsdiqlə")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Ləğv et")
            }
        },
    )
}

@Composable
fun SecurityCenterDialog(
    report: SecurityIntegrityReport?,
    isInterventionUnlocked: Boolean,
    immediateBgLock: Boolean = false,
    onToggleImmediateBgLock: (Boolean) -> Unit = {},
    onUnlockIntervention: () -> Unit,
    onLockIntervention: () -> Unit,
    onDismiss: () -> Unit,
) {
    val extra = LocalTexnoColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = null,
                tint = if (report?.hasCriticalTamper == true) extra.badText else extra.brand,
                modifier = Modifier.size(30.dp),
            )
        },
        title = {
            Text(
                text = "TNT Təhlükəsizlik Qalxanı",
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isInterventionUnlocked) extra.warnBg else extra.okBg,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(10.dp),
                    ) {
                        Icon(
                            imageVector = if (isInterventionUnlocked) Icons.Filled.LockOpen else Icons.Filled.Lock,
                            contentDescription = null,
                            tint = if (isInterventionUnlocked) extra.warnText else extra.okText,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = if (isInterventionUnlocked) {
                                "Müdaxilə rejimi açıqdır (Dəyişiklik etmək olar)"
                            } else {
                                "Müdaxilə kilidi bağlıdır (Parol qoruması aktivdir)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isInterventionUnlocked) extra.warnText else extra.okText,
                        )
                    }
                }

                if (!isInterventionUnlocked) {
                    Text(
                        text = "Təhlükəsizlik qalxanının daxili məlumatları gizlədilib. Məlumatları görmək üçün müdaxilə icazəsi tələb olunur.",
                        style = MaterialTheme.typography.bodySmall,
                        color = extra.muted,
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .testTag("security_center_locked_notice"),
                    )
                } else if (report != null) {
                    SecurityCheckRow(
                        title = "APK İmza Yoxlaması",
                        ok = report.signatureVerified && report.signatureMatchesKnownOrSealed,
                        detail = if (report.signatureSha256.isNotBlank()) {
                            "SHA-256: ${report.signatureSha256.take(23)}…"
                        } else {
                            "İmza tapılmadı"
                        },
                    )
                    SecurityCheckRow(
                        title = "Paket İdentifikasiyası",
                        ok = report.packageNameValid,
                        detail = report.packageName,
                    )
                    SecurityCheckRow(
                        title = "Ekran və Tapjacking Qoruması",
                        ok = report.screenProtectionEnabled && report.tapjackingProtected,
                        detail = "FLAG_SECURE və pərdəyə-qarşı toxunuş filtri aktivdir",
                    )
                    SecurityCheckRow(
                        title = "100% Oflayn İzolyasiya",
                        ok = report.offlineIsolated,
                        detail = if (report.offlineIsolated) "Şəbəkə icazəsi yoxdur" else "Şəbəkə icazəsi aşkarlandı",
                    )
                    SecurityCheckRow(
                        title = "Android KeyStore & AES-256-GCM",
                        ok = report.storageIntegrityOk && report.photoVaultIntegrityOk,
                        detail = buildString {
                            append(if (report.keyStoreActive) "Aparat KeyStore + " else "")
                            append(if (report.storageIntegrityOk && report.photoVaultIntegrityOk) {
                                "Cədvəl, Foto Qalereya və HMAC tamdır"
                            } else {
                                "Yaddaş və ya foto bütövlüyü pozulub"
                            })
                        },
                    )
                    SecurityCheckRow(
                        title = "PIN Şifrələməsi (PBKDF2-SHA256)",
                        ok = true,
                        detail = "12,000 iterasiya duzlu heş + 30 san. cəhd kilidi",
                    )
                    SecurityCheckRow(
                        title = "Anti-Debug / Anti-Hook / Root",
                        ok = report.noDebuggerAttached && report.noHookFramework && report.noRootDetected,
                        detail = when {
                            !report.noDebuggerAttached -> "Aktiv debugger qoşulması aşkarlandı"
                            !report.noHookFramework -> "Kənar müdaxilə (Frida/Xposed) aşkarlandı"
                            !report.noRootDetected -> "Root / test-keys mühiti müşahidə edildi"
                            else -> "Debugger, hook və root müdaxiləsi yoxdur"
                        },
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = extra.bg,
                        border = BorderStroke(1.dp, extra.line),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Arxa plana keçəndə dərhal PIN kilidi",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = extra.ink,
                                )
                                Text(
                                    text = "Tətbiqdən çıxan kimi PIN soruşulsun",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = extra.muted,
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = immediateBgLock,
                                onCheckedChange = onToggleImmediateBgLock,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isInterventionUnlocked) {
                Button(
                    onClick = {
                        onLockIntervention()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = extra.brand),
                    modifier = Modifier.testTag("lock_intervention_btn"),
                ) {
                    Text("Müdaxiləni bağla")
                }
            } else {
                Button(
                    onClick = {
                        onDismiss()
                        onUnlockIntervention()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = extra.brand),
                    modifier = Modifier.testTag("unlock_intervention_btn"),
                ) {
                    Text("Müdaxilə icazəsini aç")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Bağla")
            }
        },
    )
}

@Composable
private fun SecurityCheckRow(
    title: String,
    ok: Boolean,
    detail: String,
) {
    val extra = LocalTexnoColors.current
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = extra.bg,
        border = BorderStroke(1.dp, extra.line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector = if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
                tint = if (ok) extra.okText else extra.badText,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = extra.ink,
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = if (detail.startsWith("SHA-256")) FontFamily.Monospace else FontFamily.Default,
                    color = extra.muted,
                )
            }
        }
    }
}

@Composable
fun TamperLockScreen(
    reasons: List<String>,
    errorMessage: String?,
    cooldownSecondsLeft: Int,
    onUnlockWithPassword: (String) -> Unit,
) {
    val extra = LocalTexnoColors.current
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(extra.bg)
            .safeDrawingPadding()
            .padding(20.dp)
            .testTag("tamper_lock_screen"),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = extra.surface,
            border = BorderStroke(1.dp, extra.badText),
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 380.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(20.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(extra.badBg),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Error,
                        contentDescription = "Təhlükəsizlik xəbərdarlığı",
                        tint = extra.badText,
                        modifier = Modifier.size(30.dp),
                    )
                }

                Text(
                    text = "TNT · Müdaxilə Aşkarlandı",
                    style = MaterialTheme.typography.titleMedium,
                    color = extra.badText,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Tətbiq fayllarına və ya iş mühitinə icazəsiz müdaxilə aşkarlandığı üçün məlumatlar kilidlənib.",
                    style = MaterialTheme.typography.bodySmall,
                    color = extra.muted,
                    textAlign = TextAlign.Center,
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    reasons.forEach { reason ->
                        Text(
                            text = "• $reason",
                            style = MaterialTheme.typography.bodySmall,
                            color = extra.badText,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    enabled = cooldownSecondsLeft == 0,
                    label = { Text("Müdaxilə parolu") },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showPassword) "Parolu gizlət" else "Parolu göstər",
                            )
                        }
                    },
                    isError = !errorMessage.isNullOrBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = extra.badText,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))
                Button(
                    onClick = { onUnlockWithPassword(password) },
                    enabled = cooldownSecondsLeft == 0 && password.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = extra.brand),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text(
                        if (cooldownSecondsLeft > 0) {
                            "Gözləyin (${cooldownSecondsLeft}s)"
                        } else {
                            "Parol ilə təsdiqlə və aç"
                        }
                    )
                }
                OutlinedButton(
                    onClick = { password = "" },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Təmizlə")
                }
            }
        }
    }
}
