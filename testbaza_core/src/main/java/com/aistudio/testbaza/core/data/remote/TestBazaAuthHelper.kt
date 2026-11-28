package com.aistudio.testbaza.core.data.remote

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
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
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

fun FirebaseAuth.testBazaAuthStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

private suspend fun signInWithCredentialRequest(
    activityContext: Context,
    credentialManager: CredentialManager,
    request: GetCredentialRequest,
): Result<FirebaseUser> {
    val result = credentialManager.getCredential(activityContext, request)
    val credential = result.credential
    return if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
        val authResult = Firebase.auth.signInWithCredential(authCredential).await()
        val user = authResult.user ?: Firebase.auth.currentUser
            ?: return Result.failure(IllegalStateException("Google hesabı təsdiqlənmədi."))
        Result.success(user)
    } else {
        Result.failure(IllegalStateException("Gözlənilməz kimlik növü: ${credential.type}"))
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

object TestBazaAuthHelper {

    fun isFirebaseReady(context: Context): Boolean {
        return try {
            FirebaseApp.getApps(context).isNotEmpty() ||
                FirebaseApp.initializeApp(context) != null
        } catch (e: Exception) {
            Log.w(AUTH_TAG, "Firebase initialization unavailable: ${e.message}")
            false
        }
    }

    fun attemptAutoSignIn(
        context: Context,
        webClientId: String,
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onSignedIn: (FirebaseUser) -> Unit,
        onUnauthenticated: () -> Unit,
    ) {
        if (!isFirebaseReady(context) || webClientId.isBlank()) {
            onUnauthenticated()
            return
        }
        Firebase.auth.currentUser?.let { existing ->
            onSignedIn(existing)
            return
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val activityContext: Context = context.findActivity() ?: context
        scope.launch {
            try {
                val res = signInWithCredentialRequest(activityContext, credentialManager, request)
                res.fold(
                    onSuccess = onSignedIn,
                    onFailure = { onUnauthenticated() },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                onUnauthenticated()
            }
        }
    }

    fun signInWithGoogle(
        context: Context,
        webClientId: String,
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onSuccess: (FirebaseUser) -> Unit,
        onCancelled: () -> Unit,
        onError: (String) -> Unit,
    ) {
        if (!isFirebaseReady(context)) {
            onError("Bulud xidməti hazır deyil (google-services.json tapılmadı).")
            return
        }
        if (webClientId.isBlank()) {
            onError("Google Giriş açarı (default_web_client_id) konfiqurasiya edilməyib.")
            return
        }

        val activityContext: Context = context.findActivity() ?: context
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = webClientId).build()
        val primaryRequest = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        scope.launch {
            try {
                val primaryResult = signInWithCredentialRequest(activityContext, credentialManager, primaryRequest)
                primaryResult.fold(
                    onSuccess = onSuccess,
                    onFailure = { err -> onError(err.localizedMessage ?: "Google girişi alınmadı.") },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: GetCredentialCancellationException) {
                val detail = e.message?.takeIf { it.isNotBlank() }
                Log.w(AUTH_TAG, "Google Sign-In cancelled or dismissed: $detail", e)
                if (isSystemOrConfigCancellation(detail)) {
                    onError(detail ?: "Google Play Services konfiqurasiya xətası.")
                } else {
                    onCancelled()
                }
            } catch (e: NoCredentialException) {
                Log.w(AUTH_TAG, "NoCredentialException from GetSignInWithGoogleOption; trying GetGoogleIdOption fallback", e)
                try {
                    val fallbackOption = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setAutoSelectEnabled(false)
                        .setServerClientId(webClientId)
                        .build()
                    val fallbackRequest = GetCredentialRequest.Builder()
                        .addCredentialOption(fallbackOption)
                        .build()
                    val fallbackResult = signInWithCredentialRequest(activityContext, credentialManager, fallbackRequest)
                    fallbackResult.fold(
                        onSuccess = onSuccess,
                        onFailure = { err -> onError(err.localizedMessage ?: "Google girişi alınmadı.") },
                    )
                } catch (fc: CancellationException) {
                    throw fc
                } catch (fc: GetCredentialCancellationException) {
                    val detail = fc.message?.takeIf { it.isNotBlank() }
                    Log.w(AUTH_TAG, "Fallback Google Sign-In cancelled: $detail", fc)
                    if (isSystemOrConfigCancellation(detail)) {
                        onError(detail ?: "Google Play Services konfiqurasiya xətası.")
                    } else {
                        onCancelled()
                    }
                } catch (fe: Exception) {
                    Log.e(AUTH_TAG, "Fallback GoogleIdOption failed", fe)
                    onError(fe.localizedMessage ?: "Cihazda aktiv Google hesabı tapılmadı.")
                }
            } catch (e: FirebaseAuthException) {
                Log.e(AUTH_TAG, "FirebaseAuth error [${e.errorCode}]: ${e.message}", e)
                onError(e.localizedMessage ?: "Google hesabı ilə giriş xətası.")
            } catch (e: GetCredentialException) {
                Log.e(AUTH_TAG, "CredentialManager error [${e.type}]: ${e.message}", e)
                onError(e.localizedMessage ?: "Google girişi alınmadı.")
            } catch (e: Exception) {
                Log.e(AUTH_TAG, "Google Sign-In failed", e)
                onError(e.localizedMessage ?: "Google girişi zamanı xəta baş verdi.")
            }
        }
    }

    fun signInWithBrowserFallback(
        context: Context,
        scope: CoroutineScope,
        onSuccess: (FirebaseUser) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (!isFirebaseReady(context)) {
            onError("Bulud xidməti hazır deyil.")
            return
        }
        val activity = context.findActivity()
        if (activity == null) {
            onError("Brauzer girişi üçün Activity konteksti tapılmadı.")
            return
        }
        val provider = OAuthProvider.newBuilder("google.com")
            .addCustomParameter("prompt", "select_account")
            .build()
        scope.launch {
            try {
                val res = Firebase.auth.startActivityForSignInWithProvider(activity, provider).await()
                val user = res.user ?: Firebase.auth.currentUser
                    ?: error("Google hesabı təsdiqlənmədi.")
                onSuccess(user)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(AUTH_TAG, "Browser Google OAuth failed", e)
                onError(e.localizedMessage ?: "Brauzer ilə giriş ləğv edildi və ya xəta verdi.")
            }
        }
    }

    fun signOutUser(
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onComplete: () -> Unit = {},
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
                        Log.w(AUTH_TAG, "Firebase signOut skipped: ${e.message}")
                    }
                    onComplete()
                }
            }
        }
    }
}
