package com.example.data.firebase

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONObject

private const val TAG = "AppCheckInit"
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"
private const val APPCHECK_CUSTOM_PREFS = "aqro_appcheck_custom_prefs"
private const val KEY_CUSTOM_DEBUG_TOKEN = "custom_debug_token"
private const val KEY_GENERATED_DEBUG_UUID = "generated_debug_uuid"

private const val RUNTIME_FIREBASE_PREFS = "aqro_firebase_runtime"
private const val KEY_APP_ID = "mobilesdk_app_id"
private const val KEY_API_KEY = "api_key"
private const val KEY_PROJECT_ID = "project_id"
private const val KEY_PROJECT_NUMBER = "project_number"
private const val KEY_STORAGE_BUCKET = "storage_bucket"
private const val KEY_WEB_CLIENT_ID = "web_client_id"
private const val KEY_HAS_MATCHING_SHA1 = "has_matching_sha1"

private const val DEFAULT_DEBUG_SHA1 = "EC:A9:CD:7B:88:34:CF:8F:37:4C:5E:D6:6E:C3:BA:F5:06:18:72:31"
private const val DEFAULT_DEBUG_SHA256 = "74:19:5D:F7:59:5A:13:2B:7D:68:74:3E:59:23:8D:46:14:1F:D3:5E:8E:AF:54:AB:C3:F8:ED:E8:EC:6E:A4:6A"
private const val AI_STUDIO_FIREBASE_PROJECT_ID = "gen-lang-client-0387086288"
private const val AI_STUDIO_FIRESTORE_DB_ID = "ai-studio-android-aqromslh-892d166e-8482-4e99-87aa-793ae6666b78"

data class FirebaseJsonDiagnostics(
    val projectId: String,
    val hasWebClientId: Boolean,
    val hasMatchingSha1: Boolean,
    val hasMatchingPackage: Boolean,
)

@Suppress("DEPRECATION")
private fun getSigningCertBytes(context: Context): ByteArray? {
    return try {
        val pm = context.packageManager
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )
            info.signingInfo?.apkContentsSigners ?: info.signingInfo?.signingCertificateHistory
        } else {
            pm.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES,
            ).signatures
        }
        signatures?.firstOrNull()?.toByteArray()
    } catch (_: Exception) {
        null
    }
}

fun getSigningSha1(context: Context): String {
    return try {
        val certBytes = getSigningCertBytes(context) ?: return DEFAULT_DEBUG_SHA1
        val digest = MessageDigest.getInstance("SHA-1").digest(certBytes)
        digest.joinToString(":") { byte -> "%02X".format(byte) }
    } catch (_: Exception) {
        DEFAULT_DEBUG_SHA1
    }
}

fun getSigningSha256(context: Context): String {
    return try {
        val certBytes = getSigningCertBytes(context) ?: return DEFAULT_DEBUG_SHA256
        val digest = MessageDigest.getInstance("SHA-256").digest(certBytes)
        digest.joinToString(":") { byte -> "%02X".format(byte) }
    } catch (_: Exception) {
        DEFAULT_DEBUG_SHA256
    }
}

fun getRuntimeWebClientId(context: Context): String? {
    val prefs = context.getSharedPreferences(RUNTIME_FIREBASE_PREFS, Context.MODE_PRIVATE)
    return prefs.getString(KEY_WEB_CLIENT_ID, null)?.takeIf { it.isNotBlank() }
}

fun getRuntimeFirebaseProjectId(context: Context): String? {
    val prefs = context.getSharedPreferences(RUNTIME_FIREBASE_PREFS, Context.MODE_PRIVATE)
    return prefs.getString(KEY_PROJECT_ID, null)?.takeIf { it.isNotBlank() }
}

fun hasRuntimeFirebaseOverride(context: Context): Boolean {
    val prefs = context.getSharedPreferences(RUNTIME_FIREBASE_PREFS, Context.MODE_PRIVATE)
    return !prefs.getString(KEY_APP_ID, null).isNullOrBlank()
}

fun getRuntimeHasMatchingSha1(context: Context): Boolean? {
    val prefs = context.getSharedPreferences(RUNTIME_FIREBASE_PREFS, Context.MODE_PRIVATE)
    return if (prefs.contains(KEY_HAS_MATCHING_SHA1)) {
        prefs.getBoolean(KEY_HAS_MATCHING_SHA1, false)
    } else {
        null
    }
}

fun getActiveFirebaseProjectId(context: Context): String? {
    val fromApp = runCatching { FirebaseApp.getApps(context).firstOrNull()?.options?.projectId }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
    if (fromApp != null) return fromApp
    return getRuntimeFirebaseProjectId(context)
}

fun resolveFirestoreDatabaseId(context: Context): String {
    val projectId = getActiveFirebaseProjectId(context)
    if (!projectId.isNullOrBlank() && projectId != AI_STUDIO_FIREBASE_PROJECT_ID) {
        return "(default)"
    }
    val resId = context.resources.getIdentifier("firestore_database_id", "string", context.packageName)
    return if (resId != 0) {
        context.getString(resId)
    } else {
        AI_STUDIO_FIRESTORE_DB_ID
    }
}

fun clearRuntimeFirebaseConfig(context: Context) {
    context.getSharedPreferences(RUNTIME_FIREBASE_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
}

fun sanitizeGoogleServicesJson(rawText: String): String {
    return rawText
        .trim()
        .removePrefix("\uFEFF")
        .replace('\u201C', '"')
        .replace('\u201D', '"')
        .replace('\u2018', '\'')
        .replace('\u2019', '\'')
        .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), "")
}

fun inspectFirebaseJson(context: Context, jsonText: String): FirebaseJsonDiagnostics? {
    return try {
        val root = JSONObject(sanitizeGoogleServicesJson(jsonText))
        val projectId = root.getJSONObject("project_info").getString("project_id").trim()
        val clients = root.getJSONArray("client")
        var selectedClient: JSONObject? = null
        var matchingPkg = false
        for (i in 0 until clients.length()) {
            val c = clients.getJSONObject(i)
            val pkg = c.optJSONObject("client_info")
                ?.optJSONObject("android_client_info")
                ?.optString("package_name")
            if (pkg == context.packageName) {
                selectedClient = c
                matchingPkg = true
                break
            }
        }
        val client = selectedClient ?: clients.getJSONObject(0)
        val oauthClients = client.optJSONArray("oauth_client")
        val expectedHash = getSigningSha1(context).replace(":", "").lowercase()
        var hasWebClient = false
        var hasSha1Match = false
        if (oauthClients != null) {
            for (i in 0 until oauthClients.length()) {
                val oc = oauthClients.getJSONObject(i)
                when (oc.optInt("client_type")) {
                    3 -> if (oc.optString("client_id", "").isNotBlank()) hasWebClient = true
                    1 -> {
                        val hash = oc.optJSONObject("android_info")
                            ?.optString("certificate_hash", "")
                            ?.replace(":", "")
                            ?.lowercase()
                        if (hash == expectedHash) hasSha1Match = true
                    }
                }
            }
        }
        FirebaseJsonDiagnostics(
            projectId = projectId,
            hasWebClientId = hasWebClient,
            hasMatchingSha1 = hasSha1Match,
            hasMatchingPackage = matchingPkg,
        )
    } catch (_: Exception) {
        null
    }
}

fun saveAndInitializeFirebaseFromJson(context: Context, jsonText: String): Result<String> {
    return try {
        val cleaned = sanitizeGoogleServicesJson(jsonText)
        val root = JSONObject(cleaned)
        val projectInfo = root.getJSONObject("project_info")
        val projectId = projectInfo.getString("project_id").trim()
        val projectNumber = projectInfo.optString("project_number", "").trim()
        val storageBucket = projectInfo.optString("storage_bucket", "").trim()

        val clients = root.getJSONArray("client")
        var selectedClient: JSONObject? = null
        for (i in 0 until clients.length()) {
            val c = clients.getJSONObject(i)
            val pkg = c.optJSONObject("client_info")
                ?.optJSONObject("android_client_info")
                ?.optString("package_name")
            if (pkg == context.packageName) {
                selectedClient = c
                break
            }
        }
        val client = selectedClient ?: clients.getJSONObject(0)
        val appId = client.getJSONObject("client_info").getString("mobilesdk_app_id").trim()
        val apiKey = client.getJSONArray("api_key").getJSONObject(0).getString("current_key").trim()

        var webClientId = ""
        var hasMatchingSha1 = false
        val expectedHash = getSigningSha1(context).replace(":", "").lowercase()
        val oauthClients = client.optJSONArray("oauth_client")
        if (oauthClients != null) {
            for (i in 0 until oauthClients.length()) {
                val oc = oauthClients.getJSONObject(i)
                when (oc.optInt("client_type")) {
                    3 -> if (webClientId.isEmpty()) webClientId = oc.optString("client_id", "").trim()
                    1 -> {
                        val hash = oc.optJSONObject("android_info")
                            ?.optString("certificate_hash", "")
                            ?.replace(":", "")
                            ?.lowercase()
                        if (hash == expectedHash) hasMatchingSha1 = true
                    }
                }
            }
        }

        if (appId.isBlank() || apiKey.isBlank() || projectId.isBlank()) {
            return Result.failure(IllegalArgumentException("google-services.json missing required Firebase fields"))
        }

        val optionsBuilder = FirebaseOptions.Builder()
            .setApplicationId(appId)
            .setApiKey(apiKey)
            .setProjectId(projectId)
        if (projectNumber.isNotBlank()) optionsBuilder.setGcmSenderId(projectNumber)
        if (storageBucket.isNotBlank()) optionsBuilder.setStorageBucket(storageBucket)

        val existing = FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
        if (existing != null) {
            runCatching { existing.delete() }
        }
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context.applicationContext, optionsBuilder.build())
        }

        context.getSharedPreferences(RUNTIME_FIREBASE_PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_APP_ID, appId)
            .putString(KEY_API_KEY, apiKey)
            .putString(KEY_PROJECT_ID, projectId)
            .putString(KEY_PROJECT_NUMBER, projectNumber)
            .putString(KEY_STORAGE_BUCKET, storageBucket)
            .putString(KEY_WEB_CLIENT_ID, webClientId)
            .putBoolean(KEY_HAS_MATCHING_SHA1, hasMatchingSha1)
            .apply()

        Result.success(projectId)
    } catch (e: Exception) {
        Log.w(TAG, "Failed to parse/initialize Firebase from JSON", e)
        Result.failure(e)
    }
}

private fun initializeFromSavedPrefs(context: Context): Boolean {
    return try {
        val prefs = context.getSharedPreferences(RUNTIME_FIREBASE_PREFS, Context.MODE_PRIVATE)
        val appId = prefs.getString(KEY_APP_ID, null) ?: return false
        val apiKey = prefs.getString(KEY_API_KEY, null) ?: return false
        val projectId = prefs.getString(KEY_PROJECT_ID, null) ?: return false
        val projectNumber = prefs.getString(KEY_PROJECT_NUMBER, null).orEmpty()
        val storageBucket = prefs.getString(KEY_STORAGE_BUCKET, null).orEmpty()

        val optionsBuilder = FirebaseOptions.Builder()
            .setApplicationId(appId)
            .setApiKey(apiKey)
            .setProjectId(projectId)
        if (projectNumber.isNotBlank()) optionsBuilder.setGcmSenderId(projectNumber)
        if (storageBucket.isNotBlank()) optionsBuilder.setStorageBucket(storageBucket)

        FirebaseApp.initializeApp(context.applicationContext, optionsBuilder.build())
        FirebaseApp.getApps(context).isNotEmpty()
    } catch (e: Exception) {
        Log.w(TAG, "Saved runtime Firebase config initialization failed", e)
        false
    }
}

fun ensureFirebaseInitialized(context: Context): Boolean {
    return try {
        if (FirebaseApp.getApps(context).isNotEmpty()) {
            true
        } else if (FirebaseApp.initializeApp(context) != null || FirebaseApp.getApps(context).isNotEmpty()) {
            true
        } else {
            initializeFromSavedPrefs(context)
        }
    } catch (e: Exception) {
        Log.w(TAG, "FirebaseApp could not be initialized (missing google-services.json?).", e)
        initializeFromSavedPrefs(context)
    }
}

fun getCustomAppCheckDebugToken(context: Context): String? {
    val prefs = context.getSharedPreferences(APPCHECK_CUSTOM_PREFS, Context.MODE_PRIVATE)
    return prefs.getString(KEY_CUSTOM_DEBUG_TOKEN, null)?.trim()?.takeIf { it.isNotBlank() }
}

fun getOrCreateAppCheckDebugUuid(context: Context): String {
    getCustomAppCheckDebugToken(context)?.let { return it }
    if (FirebaseApp.getApps(context).isNotEmpty()) {
        val persistenceKey = runCatching { FirebaseApp.getInstance().persistenceKey }.getOrNull()
        if (!persistenceKey.isNullOrBlank()) {
            val sdkExisting = context.getSharedPreferences(
                "$DEBUG_STORE_PREFIX$persistenceKey",
                Context.MODE_PRIVATE,
            ).getString(DEBUG_SECRET_KEY, null)?.trim()
            if (!sdkExisting.isNullOrBlank()) return sdkExisting
        }
    }
    val customPrefs = context.getSharedPreferences(APPCHECK_CUSTOM_PREFS, Context.MODE_PRIVATE)
    val cached = customPrefs.getString(KEY_GENERATED_DEBUG_UUID, null)?.trim()
    if (!cached.isNullOrBlank()) return cached
    val generated = UUID.randomUUID().toString()
    customPrefs.edit().putString(KEY_GENERATED_DEBUG_UUID, generated).commit()
    return generated
}

fun applyCustomAppCheckDebugToken(context: Context, token: String): Boolean {
    val trimmed = token.trim()
    if (trimmed.isBlank()) return false
    context.getSharedPreferences(APPCHECK_CUSTOM_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_CUSTOM_DEBUG_TOKEN, trimmed)
        .commit()
    return runCatching {
        if (ensureFirebaseInitialized(context) && FirebaseApp.getApps(context).isNotEmpty()) {
            val persistenceKey = FirebaseApp.getInstance().persistenceKey
            context.getSharedPreferences("$DEBUG_STORE_PREFIX$persistenceKey", Context.MODE_PRIVATE)
                .edit()
                .putString(DEBUG_SECRET_KEY, trimmed)
                .commit()
            if (BuildConfig.DEBUG) {
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
            }
        }
        true
    }.getOrDefault(false)
}

fun clearCustomAppCheckDebugToken(context: Context) {
    context.getSharedPreferences(APPCHECK_CUSTOM_PREFS, Context.MODE_PRIVATE)
        .edit()
        .remove(KEY_CUSTOM_DEBUG_TOKEN)
        .commit()
}

fun isAppCheckTokenError(errorText: String?): Boolean {
    val lower = errorText.orEmpty().lowercase()
    return lower.contains("app check") ||
        lower.contains("appcheck") ||
        lower.contains("token is invalid") ||
        lower.contains("attestation")
}

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (!ensureFirebaseInitialized(context)) {
            Log.w(TAG, "Skipping App Check setup because FirebaseApp is not initialized.")
            return
        }

        if (BuildConfig.DEBUG) {
            val intentToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)?.trim()?.takeIf { it.isNotEmpty() }
            val customToken = getCustomAppCheckDebugToken(context)
            val effectiveToken = intentToken ?: customToken
            if (!effectiveToken.isNullOrEmpty()) {
                val persistenceKey = FirebaseApp.getInstance().persistenceKey
                val prefs = context.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                )
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, effectiveToken)
                    .commit()
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                Log.d(TAG, "App Check debug secret configured and DebugAppCheckProviderFactory installed.")
            } else {
                // On sideloaded debug APKs without an explicit registered debug token, installing
                // DebugAppCheckProviderFactory sends an unregistered UUID that fails attestation and
                // injects a dummy error token ('Firebase app check token is invalid'). Skip it unless
                // a token was provided via launch Intent or saved by the user.
                Log.i(TAG, "No debug token supplied; skipping DebugAppCheckProviderFactory.")
            }
        } else {
            val resId = context.resources.getIdentifier(
                RECAPTCHA_SITE_KEY_RES,
                "string",
                context.packageName
            )
            if (resId != 0) {
                val siteKey = context.getString(resId)
                if (siteKey.isNotBlank()) {
                    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                    )
                    Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
                } else {
                    Log.e(TAG, "recaptcha_site_key resource string is blank.")
                }
            } else {
                Log.e(TAG, "recaptcha_site_key string resource not found in package.")
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}
