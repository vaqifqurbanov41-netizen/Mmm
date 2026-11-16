package com.example.data.firebase

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory
import org.json.JSONObject

private const val TAG = "AppCheckInit"
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

private const val RUNTIME_FIREBASE_PREFS = "aqro_firebase_runtime"
private const val KEY_APP_ID = "mobilesdk_app_id"
private const val KEY_API_KEY = "api_key"
private const val KEY_PROJECT_ID = "project_id"
private const val KEY_PROJECT_NUMBER = "project_number"
private const val KEY_STORAGE_BUCKET = "storage_bucket"
private const val KEY_WEB_CLIENT_ID = "web_client_id"

fun getRuntimeWebClientId(context: Context): String? {
    val prefs = context.getSharedPreferences(RUNTIME_FIREBASE_PREFS, Context.MODE_PRIVATE)
    return prefs.getString(KEY_WEB_CLIENT_ID, null)?.takeIf { it.isNotBlank() }
}

fun saveAndInitializeFirebaseFromJson(context: Context, jsonText: String): Result<String> {
    return try {
        val root = JSONObject(jsonText.trim())
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
        val oauthClients = client.optJSONArray("oauth_client")
        if (oauthClients != null) {
            for (i in 0 until oauthClients.length()) {
                val oc = oauthClients.getJSONObject(i)
                if (oc.optInt("client_type") == 3) {
                    webClientId = oc.optString("client_id", "").trim()
                    break
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

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (!ensureFirebaseInitialized(context)) {
            Log.w(TAG, "Skipping App Check setup because FirebaseApp is not initialized.")
            return
        }

        if (BuildConfig.DEBUG) {
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
            if (!debugToken.isNullOrEmpty()) {
                val persistenceKey = FirebaseApp.getInstance().persistenceKey
                val prefs = context.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                )
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                Log.d(TAG, "App Check debug secret configured successfully.")
            } else {
                Log.w(TAG, "No debug token found in Intent extra.")
            }

            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "DebugAppCheckProviderFactory installed.")
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
