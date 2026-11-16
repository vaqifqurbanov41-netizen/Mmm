package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.data.firebase.clearRuntimeFirebaseConfig
import com.example.data.firebase.configureAppCheck
import com.example.data.firebase.ensureFirebaseInitialized
import com.example.data.firebase.getActiveFirebaseProjectId
import com.example.data.firebase.getSigningSha1
import com.example.data.firebase.getSigningSha256
import com.example.data.firebase.hasRuntimeFirebaseOverride
import com.example.data.firebase.inspectFirebaseJson
import com.example.data.firebase.saveAndInitializeFirebaseFromJson
import com.example.i18n.AppLanguage
import com.example.i18n.LocalStrings
import com.example.ui.auth.consumePendingWebSignInResult
import com.example.ui.auth.isShaOrCertMismatchError
import com.example.ui.auth.onGoogleSignInClicked
import com.example.ui.auth.signInWithGoogleWebBrowser
import com.example.ui.components.DangerBannerBox
import com.example.ui.components.KTN_TELEGRAM_BOT_URL
import com.example.ui.components.openExternalUrl
import com.example.ui.theme.LocalAqroColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AuthScreen(
    currentLanguage: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
    onAuthSuccess: () -> Unit,
    onContinueLocalMode: () -> Unit = {},
) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showTroubleshoot by remember { mutableStateOf(false) }
    var configNotice by remember { mutableStateOf<String?>(null) }
    var showPasteDialog by remember { mutableStateOf(false) }
    var pastedJson by remember { mutableStateOf("") }
    var hasRuntimeConfig by remember(context) { mutableStateOf(hasRuntimeFirebaseOverride(context)) }
    var activeProjectId by remember(context) { mutableStateOf(getActiveFirebaseProjectId(context)) }
    val sha1Fingerprint = remember(context) { getSigningSha1(context) }
    val sha256Fingerprint = remember(context) { getSigningSha256(context) }

    fun applyImportedJson(rawJson: String) {
        val diag = inspectFirebaseJson(context, rawJson)
        val res = saveAndInitializeFirebaseFromJson(context, rawJson)
        res.onSuccess { projectId ->
            configureAppCheck(context, null)
            hasRuntimeConfig = hasRuntimeFirebaseOverride(context)
            activeProjectId = getActiveFirebaseProjectId(context) ?: projectId
            if (diag != null && !diag.hasMatchingSha1) {
                configNotice = null
                errorMessage = String.format(
                    currentLanguage.locale,
                    strings.googleServicesMissingShaWarning,
                    projectId,
                )
                showTroubleshoot = true
            } else if (diag != null && !diag.hasWebClientId) {
                configNotice = null
                errorMessage = String.format(
                    currentLanguage.locale,
                    strings.googleServicesMissingWebClientWarning,
                    projectId,
                )
                showTroubleshoot = true
            } else {
                errorMessage = null
                configNotice = String.format(
                    currentLanguage.locale,
                    strings.googleServicesImportSuccess,
                    projectId,
                )
            }
        }.onFailure {
            configNotice = null
            errorMessage = strings.googleServicesImportError
        }
    }

    if (showPasteDialog) {
        AlertDialog(
            onDismissRequest = { showPasteDialog = false },
            title = { Text(strings.googleServicesPasteTitle, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = pastedJson,
                    onValueChange = { pastedJson = it },
                    placeholder = { Text(strings.googleServicesPasteHint) },
                    minLines = 5,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth().testTag("auth_paste_google_services_input"),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPasteDialog = false
                        applyImportedJson(pastedJson)
                    },
                    modifier = Modifier.testTag("auth_confirm_paste_google_services_btn"),
                ) {
                    Text(strings.save, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasteDialog = false }) {
                    Text(strings.cancel)
                }
            },
        )
    }

    LaunchedEffect(Unit) {
        consumePendingWebSignInResult(
            context = context,
            onAuthSuccess = {
                isLoading = false
                onAuthSuccess()
            },
            onAuthError = { msg ->
                isLoading = false
                errorMessage = msg
                showTroubleshoot = true
            },
        )
    }

    val updateJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val content = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use {
                            it.bufferedReader().readText()
                        }.orEmpty()
                    }.getOrDefault("")
                }
                if (content.isBlank()) {
                    configNotice = null
                    errorMessage = strings.googleServicesImportError
                } else {
                    applyImportedJson(content)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp)
            .testTag("auth_screen"),
    ) {
        LanguageSwitcherPills(
            currentLanguage = currentLanguage,
            onSelectLanguage = onSelectLanguage,
            modifier = Modifier.align(Alignment.TopEnd),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 48.dp)
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(extra.heroStart, extra.heroEnd))
                    ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Eco,
                    contentDescription = strings.appName,
                    tint = Color.White,
                    modifier = Modifier.size(46.dp),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = strings.signInTitle,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${strings.creatorLabel}: ${strings.creatorName}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = strings.signInSubtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = extra.muted,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (errorMessage != null) {
                DangerBannerBox(text = errorMessage!!)
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (configNotice != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = extra.pale,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = configNotice!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Button(
                onClick = {
                    errorMessage = null
                    configNotice = null
                    isLoading = true
                    onGoogleSignInClicked(
                        context = context,
                        credentialManager = credentialManager,
                        onAuthSuccess = {
                            isLoading = false
                            onAuthSuccess()
                        },
                        onAuthError = { msg ->
                            isLoading = false
                            errorMessage = msg
                            showTroubleshoot = true
                        },
                        scope = scope,
                        onAuthCancelled = { detail ->
                            isLoading = false
                            errorMessage = if (!detail.isNullOrBlank() &&
                                !detail.equals("activity is cancelled by the user.", ignoreCase = true)
                            ) {
                                "${strings.signInCancelledMsg}\n($detail)"
                            } else {
                                strings.signInCancelledMsg
                            }
                            showTroubleshoot = true
                        },
                    )
                },
                enabled = !isLoading,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("google_sign_in_button"),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(22.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.VerifiedUser,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 10.dp),
                    )
                    Text(
                        text = strings.signInWithGoogle,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = {
                    errorMessage = null
                    configNotice = null
                    isLoading = true
                    signInWithGoogleWebBrowser(
                        context = context,
                        onAuthSuccess = {
                            isLoading = false
                            onAuthSuccess()
                        },
                        onAuthError = { msg ->
                            isLoading = false
                            errorMessage = msg
                            showTroubleshoot = true
                        },
                        scope = scope,
                    )
                },
                enabled = !isLoading,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    1.dp,
                    if (errorMessage != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("web_google_sign_in_button"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Public,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp).size(18.dp),
                )
                Text(
                    text = strings.btnGoogleWebFallback,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = strings.googleWebFallbackHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = extra.muted,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onContinueLocalMode,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, extra.line),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("auth_continue_local_btn"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Build,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp).size(18.dp),
                )
                Text(
                    text = strings.openLocalToolsBtn,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            TextButton(
                onClick = { showTroubleshoot = !showTroubleshoot },
                modifier = Modifier.testTag("auth_troubleshoot_toggle_btn"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 6.dp).size(18.dp),
                )
                Text(
                    text = strings.firebaseQuickHelpLink,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (showTroubleshoot) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, extra.line),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_troubleshoot_card"),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(14.dp),
                    ) {
                        Text(
                            text = strings.signInTroubleTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )

                        if (isShaOrCertMismatchError(errorMessage)) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = extra.warningContainer,
                                modifier = Modifier.fillMaxWidth().testTag("sha_mismatch_diagnosis_box"),
                            ) {
                                Text(
                                    text = strings.shaMismatchDiagnosisMsg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(10.dp),
                                )
                            }
                        }

                        if (!activeProjectId.isNullOrBlank()) {
                            Text(
                                text = "${strings.activeFirebaseProjectLabel}: $activeProjectId",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Text(
                            text = strings.signInTroubleBody,
                            style = MaterialTheme.typography.bodySmall,
                            color = extra.muted,
                        )

                        FingerprintCopyRow(
                            label = strings.packageNameLabel,
                            value = context.packageName,
                            copyLabel = strings.btnCopyPkg,
                            testTag = "copy_pkg_button",
                            onCopy = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("Package", context.packageName))
                                Toast.makeText(context, strings.pkgCopiedMsg, Toast.LENGTH_SHORT).show()
                            },
                        )

                        FingerprintCopyRow(
                            label = strings.sha1Label,
                            value = sha1Fingerprint,
                            copyLabel = strings.btnCopySha1,
                            testTag = "copy_sha1_button",
                            onCopy = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("SHA-1", sha1Fingerprint))
                                Toast.makeText(context, strings.sha1CopiedMsg, Toast.LENGTH_SHORT).show()
                            },
                        )

                        FingerprintCopyRow(
                            label = strings.sha256Label,
                            value = sha256Fingerprint,
                            copyLabel = strings.btnCopySha256,
                            testTag = "copy_sha256_button",
                            onCopy = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("SHA-256", sha256Fingerprint))
                                Toast.makeText(context, strings.sha256CopiedMsg, Toast.LENGTH_SHORT).show()
                            },
                        )

                        OutlinedButton(
                            onClick = { updateJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, extra.line),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_update_google_services_btn"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.UploadFile,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp).size(16.dp),
                            )
                            Text(
                                text = strings.btnUpdateGoogleServices,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        OutlinedButton(
                            onClick = { showPasteDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, extra.line),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_paste_google_services_btn"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Code,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp).size(16.dp),
                            )
                            Text(
                                text = strings.btnPasteGoogleServices,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        if (hasRuntimeConfig) {
                            TextButton(
                                onClick = {
                                    clearRuntimeFirebaseConfig(context)
                                    hasRuntimeConfig = false
                                    ensureFirebaseInitialized(context)
                                    activeProjectId = getActiveFirebaseProjectId(context)
                                    errorMessage = null
                                    configNotice = strings.firebaseConfigResetMsg
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_reset_firebase_btn"),
                            ) {
                                Text(
                                    text = strings.btnResetFirebaseConfig,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = extra.muted,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FingerprintCopyRow(
    label: String,
    value: String,
    copyLabel: String,
    testTag: String,
    onCopy: () -> Unit,
) {
    val extra = LocalAqroColors.current
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = extra.pale,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            TextButton(
                onClick = onCopy,
                modifier = Modifier.testTag(testTag),
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = copyLabel,
                    modifier = Modifier.size(16.dp).padding(end = 4.dp),
                )
                Text(
                    text = copyLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun LanguageSwitcherPills(
    currentLanguage: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val extra = LocalAqroColors.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        AppLanguage.entries.forEach { lang ->
            val selected = lang == currentLanguage
            Surface(
                shape = RoundedCornerShape(50),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else extra.line),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onSelectLanguage(lang) }
                    .testTag("auth_lang_${lang.code}"),
            ) {
                Text(
                    text = lang.code.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
fun FirebaseConfigMissingScreen(
    currentLanguage: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
    onContinueLocalMode: () -> Unit = {},
    onFirebaseActivated: () -> Unit = {},
) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var configError by remember { mutableStateOf<String?>(null) }
    var configSuccess by remember { mutableStateOf<String?>(null) }
    var showPasteDialog by remember { mutableStateOf(false) }
    var pastedJson by remember { mutableStateOf("") }

    fun applyConfigJson(rawJson: String) {
        val result = saveAndInitializeFirebaseFromJson(context, rawJson)
        result.onSuccess { projectId ->
            configError = null
            configSuccess = String.format(currentLanguage.locale, strings.googleServicesImportSuccess, projectId)
            onFirebaseActivated()
        }.onFailure {
            configSuccess = null
            configError = strings.googleServicesImportError
        }
    }

    val jsonFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val content = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use {
                            it.bufferedReader().readText()
                        }.orEmpty()
                    }.getOrDefault("")
                }
                if (content.isBlank()) {
                    configError = strings.googleServicesImportError
                } else {
                    applyConfigJson(content)
                }
            }
        }
    }

    if (showPasteDialog) {
        AlertDialog(
            onDismissRequest = { showPasteDialog = false },
            title = { Text(strings.googleServicesPasteTitle, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = pastedJson,
                    onValueChange = { pastedJson = it },
                    placeholder = { Text(strings.googleServicesPasteHint) },
                    minLines = 5,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth().testTag("paste_google_services_input"),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPasteDialog = false
                        applyConfigJson(pastedJson)
                    },
                    modifier = Modifier.testTag("confirm_paste_google_services_btn"),
                ) {
                    Text(strings.save, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasteDialog = false }) {
                    Text(strings.cancel)
                }
            },
        )
    }

    val (title, subtitle, stepsTitle) = when (currentLanguage) {
        AppLanguage.AZ -> Triple(
            "Firebase konfiqurasiyası tapılmadı",
            "Bu APK faylı \"app/google-services.json\" olmadan qurulub. Google Giriş, Bulud sinxronizasiyası və AI Aqronom üçün bu konfiqurasiya tələb olunur — lakin yerli alətlərdən indi istifadə edə bilərsiniz.",
            "Tam versiya üçün (GitHub Actions):"
        )
        AppLanguage.EN -> Triple(
            "Firebase Configuration Missing",
            "This APK was built without \"app/google-services.json\". Cloud Sync, Google Sign-In, and AI Agronomist require Firebase — or you can continue using local farm tools right now.",
            "To enable full cloud features (GitHub Actions):"
        )
        AppLanguage.RU -> Triple(
            "Конфигурация Firebase не найдена",
            "Этот APK собран без \"app/google-services.json\". Для входа через Google, облака и ИИ-агронома требуется Firebase — либо вы можете использовать локальные инструменты прямо сейчас.",
            "Для полной облачной версии (GitHub Actions):"
        )
    }

    val steps = when (currentLanguage) {
        AppLanguage.AZ -> listOf(
            "Layihədən \"app/google-services.json\" faylını götürün.",
            "GitHub-da Settings → Secrets and variables → Actions bölməsində GOOGLE_SERVICES_JSON adlı secret əlavə edin (və ya faylı birbaşa app/ qovluğuna yükləyin).",
            "GitHub Actions-da APK qurulmasını yenidən başladın və yeni APK-nı quraşdırın."
        )
        AppLanguage.EN -> listOf(
            "Copy \"app/google-services.json\" from the project.",
            "Add it as a repository secret named GOOGLE_SERVICES_JSON under Settings → Secrets and variables → Actions (or upload it to app/).",
            "Re-run the Build APK workflow and install the updated APK."
        )
        AppLanguage.RU -> listOf(
            "Скопируйте файл \"app/google-services.json\" из проекта.",
            "Добавьте его в GitHub: Settings → Secrets and variables → Actions с именем GOOGLE_SERVICES_JSON (или загрузите в папку app/).",
            "Перезапустите сборку APK в GitHub Actions и установите обновлённый APK."
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp)
            .testTag("firebase_missing_screen"),
    ) {
        LanguageSwitcherPills(
            currentLanguage = currentLanguage,
            onSelectLanguage = onSelectLanguage,
            modifier = Modifier.align(Alignment.TopEnd),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(74.dp)
                    .clip(CircleShape)
                    .background(extra.warningContainer),
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(38.dp),
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = strings.appName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = extra.muted,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = onContinueLocalMode,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("open_local_tools_btn"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Build,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp).size(20.dp),
                )
                Text(
                    text = strings.openLocalToolsBtn,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = { jsonFileLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("import_google_services_file_btn"),
            ) {
                Icon(
                    imageVector = Icons.Filled.UploadFile,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp).size(18.dp),
                )
                Text(
                    text = strings.btnImportGoogleServices,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { showPasteDialog = true },
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, extra.line),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("paste_google_services_btn"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Code,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp).size(18.dp),
                )
                Text(
                    text = strings.btnPasteGoogleServices,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (configError != null) {
                Spacer(modifier = Modifier.height(10.dp))
                DangerBannerBox(text = configError!!)
            }

            if (configSuccess != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = extra.pale,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = configSuccess!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val telegramBlue = Color(0xFF0088CC)
            OutlinedButton(
                onClick = { openExternalUrl(context, KTN_TELEGRAM_BOT_URL) },
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, telegramBlue.copy(alpha = 0.55f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = telegramBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("missing_firebase_telegram_btn"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp).size(18.dp),
                )
                Text(
                    text = "${strings.telegramBotTitle} (@ktn_asi_bot)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                border = BorderStroke(1.dp, extra.line),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(18.dp),
                ) {
                    Text(
                        text = stepsTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    steps.forEachIndexed { index, stepText ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(extra.pale),
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                            }
                            Text(
                                text = stepText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}
