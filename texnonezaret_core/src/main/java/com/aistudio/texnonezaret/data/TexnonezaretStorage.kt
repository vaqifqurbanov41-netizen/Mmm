package com.aistudio.texnonezaret.data

import android.content.Context
import com.aistudio.texnonezaret.domain.ColumnMapping
import com.aistudio.texnonezaret.security.SecurityGuard
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

data class PersistedSheetData(
    val raw: List<List<String>>,
    val hdr: Int,
    val map: ColumnMapping,
    val sourceFileName: String = "",
)

class TexnonezaretStorage(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("texnonezaret_prefs", Context.MODE_PRIVATE)
    private val sheetFile: File
        get() = File(appContext.filesDir, "texnonezaret_data_v2.json")

    companion object {
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_FAILS = "pin_fails"
        private const val KEY_LOCK_UNTIL = "lock_until"
        private const val KEY_MASTER_FAILS = "master_fails"
        private const val KEY_MASTER_LOCK_UNTIL = "master_lock_until"
        private const val KEY_SEALED_SIGNER = "sealed_signer_sha256"
        private const val KEY_PREFS_HMAC = "security_prefs_hmac_v1"
        private const val KEY_THEME_MODE = "theme_mode"
    }

    fun getSavedPinHash(): String? = prefs.getString(KEY_PIN_HASH, null)?.takeIf { it.isNotBlank() }

    fun savePinHash(hash: String) {
        prefs.edit().putString(KEY_PIN_HASH, hash).apply()
        updateSecurityHmac()
    }

    fun clearPinAndSecurity() {
        prefs.edit()
            .remove(KEY_PIN_HASH)
            .remove(KEY_PIN_FAILS)
            .remove(KEY_LOCK_UNTIL)
            .remove(KEY_MASTER_FAILS)
            .remove(KEY_MASTER_LOCK_UNTIL)
            .apply()
        updateSecurityHmac()
    }

    fun getFailCount(): Int = prefs.getInt(KEY_PIN_FAILS, 0)

    fun setFailCount(count: Int) {
        prefs.edit().putInt(KEY_PIN_FAILS, count).apply()
        updateSecurityHmac()
    }

    fun getLockUntilMillis(): Long = prefs.getLong(KEY_LOCK_UNTIL, 0L)

    fun setLockUntilMillis(timestampMs: Long) {
        prefs.edit().putLong(KEY_LOCK_UNTIL, timestampMs).apply()
        updateSecurityHmac()
    }

    fun getMasterFailCount(): Int = prefs.getInt(KEY_MASTER_FAILS, 0)

    fun setMasterFailCount(count: Int) {
        prefs.edit().putInt(KEY_MASTER_FAILS, count).apply()
        updateSecurityHmac()
    }

    fun getMasterLockUntilMillis(): Long = prefs.getLong(KEY_MASTER_LOCK_UNTIL, 0L)

    fun setMasterLockUntilMillis(timestampMs: Long) {
        prefs.edit().putLong(KEY_MASTER_LOCK_UNTIL, timestampMs).apply()
        updateSecurityHmac()
    }

    fun getSealedSignerSha256(): String? =
        prefs.getString(KEY_SEALED_SIGNER, null)?.takeIf { it.isNotBlank() }

    fun saveSealedSignerSha256(sha256: String) {
        prefs.edit().putString(KEY_SEALED_SIGNER, sha256).apply()
        updateSecurityHmac()
    }

    fun verifySecurityPrefsIntegrity(): Boolean {
        val storedHmac = prefs.getString(KEY_PREFS_HMAC, null)
        val hasSecurityFields = getSavedPinHash() != null ||
            getFailCount() > 0 ||
            getLockUntilMillis() > 0L ||
            getMasterFailCount() > 0 ||
            getMasterLockUntilMillis() > 0L ||
            getSealedSignerSha256() != null

        if (!hasSecurityFields && storedHmac == null) {
            return true
        }
        if (storedHmac.isNullOrBlank()) {
            // Migrate existing pre-hardening preferences seamlessly once
            if (hasSecurityFields) {
                updateSecurityHmac()
                return true
            }
            return false
        }
        return SecurityGuard.verifyIntegrityHmac(buildSecurityCanonicalString(), storedHmac)
    }

    fun refreshSecurityIntegritySeal() {
        updateSecurityHmac()
    }

    private fun buildSecurityCanonicalString(): String {
        return listOf(
            getSavedPinHash().orEmpty(),
            getFailCount().toString(),
            getLockUntilMillis().toString(),
            getMasterFailCount().toString(),
            getMasterLockUntilMillis().toString(),
            getSealedSignerSha256().orEmpty(),
        ).joinToString("|")
    }

    private fun updateSecurityHmac() {
        val hmac = SecurityGuard.computeIntegrityHmac(buildSecurityCanonicalString())
        prefs.edit().putString(KEY_PREFS_HMAC, hmac).apply()
    }

    fun getThemeMode(): ThemeMode {
        val raw = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        return runCatching { ThemeMode.valueOf(raw ?: ThemeMode.SYSTEM.name) }.getOrDefault(ThemeMode.SYSTEM)
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun saveSheetData(data: PersistedSheetData) {
        runCatching {
            val json = serializeSheetData(data)
            val encrypted = SecurityGuard.encryptText(json)
            sheetFile.writeText(encrypted, Charsets.UTF_8)
        }
    }

    fun loadSheetData(): PersistedSheetData? {
        if (!sheetFile.exists()) return null
        return runCatching {
            val content = sheetFile.readText(Charsets.UTF_8)
            val json = if (content.startsWith("TNTENC1:")) {
                SecurityGuard.decryptText(content) ?: return@runCatching null
            } else {
                // Seamless upgrade of legacy unencrypted file -> re-encrypt in place
                val parsedLegacy = deserializeSheetData(content)
                if (parsedLegacy != null) {
                    saveSheetData(parsedLegacy)
                }
                content
            }
            deserializeSheetData(json)
        }.getOrNull()
    }

    fun isSheetFileTampered(): Boolean {
        if (!sheetFile.exists()) return false
        val content = runCatching { sheetFile.readText(Charsets.UTF_8) }.getOrNull() ?: return true
        if (!content.startsWith("TNTENC1:")) return false
        return SecurityGuard.decryptText(content) == null
    }

    fun clearSheetData() {
        runCatching {
            if (sheetFile.exists()) sheetFile.delete()
        }
    }

    fun serializeSheetData(data: PersistedSheetData): String {
        val root = JSONObject()
        root.put("hdr", data.hdr)
        root.put("fileName", data.sourceFileName)
        root.put(
            "map",
            JSONObject().apply {
                put("dqn", data.map.dqn)
                put("dqs", data.map.dqs)
                put("date", data.map.date)
                put("name", data.map.name)
                put("phone", data.map.phone)
            }
        )
        val rawArr = JSONArray()
        for (row in data.raw) {
            val rowArr = JSONArray()
            for (cell in row) rowArr.put(cell)
            rawArr.put(rowArr)
        }
        root.put("raw", rawArr)
        return root.toString()
    }

    fun serializeBackupEnvelope(data: PersistedSheetData): String {
        val payloadJson = serializeSheetData(data)
        val digest = SecurityGuard.sha256Hex(payloadJson)
        return JSONObject().apply {
            put("format", "tnt_backup_v1")
            put("sha256", digest)
            put("payload", payloadJson)
        }.toString()
    }

    fun deserializeSheetData(jsonText: String): PersistedSheetData? {
        if (jsonText.isBlank()) return null
        val root = JSONObject(jsonText)
        if (root.optString("format") == "tnt_backup_v1") {
            val payload = root.optString("payload", "")
            val expectedSha = root.optString("sha256", "")
            if (payload.isBlank() || expectedSha.isBlank()) return null
            if (!SecurityGuard.sha256Hex(payload).equals(expectedSha, ignoreCase = true)) {
                return null
            }
            return deserializeSheetData(payload)
        }

        val hdr = root.optInt("hdr", 0)
        val fileName = root.optString("fileName", "")
        val mapObj = root.optJSONObject("map")
        val map = if (mapObj != null) {
            ColumnMapping(
                dqn = mapObj.optInt("dqn", -1),
                dqs = mapObj.optInt("dqs", -1),
                date = mapObj.optInt("date", -1),
                name = mapObj.optInt("name", -1),
                phone = mapObj.optInt("phone", -1),
            )
        } else {
            ColumnMapping()
        }
        val rawArr = root.optJSONArray("raw") ?: return null
        val raw = ArrayList<List<String>>(rawArr.length())
        for (i in 0 until rawArr.length()) {
            val rowArr = rawArr.optJSONArray(i) ?: continue
            val row = ArrayList<String>(rowArr.length())
            for (j in 0 until rowArr.length()) {
                row.add(rowArr.optString(j, ""))
            }
            raw.add(row)
        }
        if (raw.isEmpty()) return null
        return PersistedSheetData(raw = raw, hdr = hdr, map = map, sourceFileName = fileName)
    }
}
