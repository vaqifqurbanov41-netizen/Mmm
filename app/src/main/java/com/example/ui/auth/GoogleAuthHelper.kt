package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.example.data.firebase.getRuntimeWebClientId
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val AUTH_TAG = "Auth"

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

fun resolveWebClientId(context: Context): String? {
    getRuntimeWebClientId(context)?.let { return it }
    val resId = context.resources.getIdentifier(
        "default_web_client_id",
        "string",
        context.packageName,
    )
    if (resId != 0) {
        val fromRes = try {
            context.getString(resId).takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
        if (fromRes != null) return fromRes
    }
    return null
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope,
) {
    if (FirebaseApp.getApps(context).isEmpty()) {
        onUnauthenticated()
        return
    }
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = resolveWebClientId(context)
    if (clientId == null) {
        onUnauthenticated()
        return
    }

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    val activityContext: Context = context.findActivity() ?: context
    scope.launch {
        try {
            val result = credentialManager.getCredential(activityContext, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (_: Exception) {
            onUnauthenticated()
        }
    }
}

private suspend fun signInWithRequest(
    activityContext: Context,
    credentialManager: CredentialManager,
    request: GetCredentialRequest,
): Result<Unit> {
    val result = credentialManager.getCredential(activityContext, request)
    val credential = result.credential
    return if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
        Firebase.auth.signInWithCredential(authCredential).await()
        Result.success(Unit)
    } else {
        Result.failure(IllegalStateException("Unexpected credential type: ${credential.type}"))
    }
}

private fun isSystemOrConfigCancellation(message: String?): Boolean {
    val lower = message?.lowercase().orEmpty()
    return lower.contains("28436") ||
        lower.contains("28444") ||
        lower.contains("developer") ||
        lower.contains("blocked") ||
        lower.contains("reauth") ||
        lower.contains("configuration") ||
        lower.contains("sha") ||
        lower.contains("10:") ||
        lower.contains("16:")
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: (String?) -> Unit = {},
) {
    if (FirebaseApp.getApps(context).isEmpty()) {
        onAuthError("Firebase is not initialized: app/google-services.json missing")
        return
    }
    val clientId = resolveWebClientId(context)
    if (clientId == null) {
        onAuthError("Web Client ID (oauth_client client_type=3) missing in google-services.json. Enable Google Sign-In & add SHA-1 in Firebase Console.")
        return
    }

    val activityContext: Context = context.findActivity() ?: context
    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val primaryRequest = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    scope.launch {
        try {
            val primaryResult = signInWithRequest(activityContext, credentialManager, primaryRequest)
            if (primaryResult.isSuccess) {
                onAuthSuccess()
            } else {
                onAuthError(primaryResult.exceptionOrNull()?.localizedMessage ?: "Unexpected credential")
            }
        } catch (e: GetCredentialCancellationException) {
            val detail = e.message?.takeIf { it.isNotBlank() }
            Log.w(AUTH_TAG, "Google Sign-In flow cancelled or dismissed: $detail", e)
            if (isSystemOrConfigCancellation(detail)) {
                onAuthError(detail ?: "Google Play Services blocked or misconfigured")
            } else {
                onAuthCancelled(detail)
            }
        } catch (e: NoCredentialException) {
            Log.w(AUTH_TAG, "GetSignInWithGoogleOption returned NoCredentialException; trying GetGoogleIdOption fallback", e)
            try {
                val fallbackOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setAutoSelectEnabled(false)
                    .setServerClientId(clientId)
                    .build()
                val fallbackRequest = GetCredentialRequest.Builder()
                    .addCredentialOption(fallbackOption)
                    .build()
                val fallbackResult = signInWithRequest(activityContext, credentialManager, fallbackRequest)
                if (fallbackResult.isSuccess) {
                    onAuthSuccess()
                } else {
                    onAuthError(fallbackResult.exceptionOrNull()?.localizedMessage ?: "Sign in failed")
                }
            } catch (fallbackCancel: GetCredentialCancellationException) {
                val detail = fallbackCancel.message?.takeIf { it.isNotBlank() }
                if (isSystemOrConfigCancellation(detail)) {
                    onAuthError(detail ?: "Google Play Services blocked or misconfigured")
                } else {
                    onAuthCancelled(detail)
                }
            } catch (fallbackErr: Exception) {
                Log.e(AUTH_TAG, "Fallback GoogleIdOption failed", fallbackErr)
                onAuthError(fallbackErr.localizedMessage ?: e.localizedMessage ?: "No Google account available")
            }
        } catch (e: FirebaseAuthException) {
            Log.e(AUTH_TAG, "FirebaseAuth error [${e.errorCode}]: ${e.message}", e)
            onAuthError("[${e.errorCode}] ${e.localizedMessage ?: "Firebase Auth failed"}")
        } catch (e: GetCredentialException) {
            Log.e(AUTH_TAG, "CredentialManager error [${e.type}]: ${e.message}", e)
            onAuthError("[${e.type}] ${e.localizedMessage ?: "Sign in failed"}")
        } catch (e: Exception) {
            Log.e(AUTH_TAG, "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "Sign in failed")
        }
    }
}

fun signOutUser(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {},
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(AUTH_TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                try {
                    Firebase.auth.signOut()
                } catch (e: Exception) {
                    Log.w(AUTH_TAG, "FirebaseAuth signOut skipped: ${e.message}")
                }
                onSignOutComplete()
            }
        }
    }
}
