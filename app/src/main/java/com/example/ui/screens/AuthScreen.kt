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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.example.i18n.AppLanguage
import com.example.i18n.LocalStrings
import com.example.ui.auth.onGoogleSignInClicked
import com.example.ui.components.DangerBannerBox
import com.example.ui.theme.LocalAqroColors

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
        // Language Selector Pill Row at Top
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.align(Alignment.TopEnd),
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
