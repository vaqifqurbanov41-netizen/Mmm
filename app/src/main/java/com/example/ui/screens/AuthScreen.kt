package com.example.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import com.example.data.firebase.saveAndInitializeFirebaseFromJson
import com.example.i18n.AppLanguage
import com.example.i18n.LocalStrings
import com.example.ui.auth.onGoogleSignInClicked
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
) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp),
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
                .widthIn(max = 460.dp)
                .fillMaxWidth(),
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

            Spacer(modifier = Modifier.height(28.dp))

            if (errorMessage != null) {
                DangerBannerBox(text = errorMessage!!)
                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(
                onClick = {
                    errorMessage = null
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
                        },
                        scope = scope,
                        onAuthCancelled = {
                            isLoading = false
                            errorMessage = strings.signInCancelledMsg
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
