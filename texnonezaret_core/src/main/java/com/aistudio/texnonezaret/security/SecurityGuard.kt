package com.aistudio.texnonezaret.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class SecurityIntegrityReport(
    val packageName: String,
    val packageNameValid: Boolean,
    val signatureSha256: String,
    val signatureVerified: Boolean,
    val signatureMatchesKnownOrSealed: Boolean,
    val noDebuggerAttached: Boolean,
    val noHookFramework: Boolean,
    val noRootDetected: Boolean = true,
    val keyStoreActive: Boolean = true,
    val offlineIsolated: Boolean,
    val storageIntegrityOk: Boolean,
    val photoVaultIntegrityOk: Boolean = true,
    val screenProtectionEnabled: Boolean = true,
    val tapjackingProtected: Boolean = true,
    val isDebuggableBuild: Boolean = false,
    val tamperReasons: List<String> = emptyList(),
) {
    val hasCriticalTamper: Boolean
        get() = tamperReasons.isNotEmpty()
}

object SecurityGuard {

    private const val INTERVENTION_SALT = "TNT_VAQIF_INTERVENTION_SALT_2026_v1"
    private const val INTERVENTION_ITERATIONS = 12_000
    // PBKDF2-HMAC-SHA256 digest of the authorized intervention password (never stored in plaintext)
    private const val EXPECTED_INTERVENTION_DIGEST_HEX =
        "8e6e319c6ffa31ead4830e75f208aaf22b813a92bd21c2d8681d380a6626c080"

    // Official project signing certificate SHA-256 from debug.keystore
    private const val OFFICIAL_DEBUG_SIGNER_SHA256 =
        "74:19:5D:F7:59:5A:13:2B:7D:68:74:3E:59:23:8D:46:14:1F:D3:5E:8E:AF:54:AB:C3:F8:ED:E8:EC:6E:A4:6A"

    private val ALLOWED_PACKAGES = setOf(
        "com.aistudio.texnonezaret.app",
        "com.aistudio.aqromeslehet.vqrban",
    )

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val AES_KEY_ALIAS = "tnt_master_aes_v1"
    private const val HMAC_KEY_ALIAS = "tnt_prefs_hmac_v1"
    private const val ENC_PREFIX = "TNTENC1:"
    private val BIN_MAGIC = "TNTBIN1".toByteArray(StandardCharsets.US_ASCII)
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128

    // Fallback in-process deterministic key used only when AndroidKeyStore is absent (e.g., JVM tests)
    private val jvmFallbackAesKey: SecretKey by lazy {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("TNT_JVM_AES_FALLBACK_2026".toByteArray(StandardCharsets.UTF_8))
        SecretKeySpec(digest, "AES")
    }

    private val jvmFallbackHmacKey: SecretKey by lazy {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("TNT_JVM_HMAC_FALLBACK_2026".toByteArray(StandardCharsets.UTF_8))
        SecretKeySpec(digest, "HmacSHA256")
    }

    fun verifyInterventionPassword(candidate: String): Boolean {
        if (candidate.isEmpty()) return false
        val derived = pbkdf2HmacSha256(
            password = candidate.toByteArray(StandardCharsets.UTF_8),
            salt = INTERVENTION_SALT.toByteArray(StandardCharsets.UTF_8),
            iterations = INTERVENTION_ITERATIONS,
            dkLen = 32,
        )
        val expected = hexToBytes(EXPECTED_INTERVENTION_DIGEST_HEX)
        return MessageDigest.isEqual(derived, expected)
    }

    fun pbkdf2HmacSha256(
        password: ByteArray,
        salt: ByteArray,
        iterations: Int,
        dkLen: Int,
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password, "HmacSHA256"))
        val hashLen = 32
        val blocks = (dkLen + hashLen - 1) / hashLen
        val out = ByteArray(blocks * hashLen)
        var outPos = 0

        for (blockIdx in 1..blocks) {
            val blockInput = ByteArray(salt.size + 4)
            System.arraycopy(salt, 0, blockInput, 0, salt.size)
            blockInput[salt.size] = ((blockIdx ushr 24) and 0xFF).toByte()
            blockInput[salt.size + 1] = ((blockIdx ushr 16) and 0xFF).toByte()
            blockInput[salt.size + 2] = ((blockIdx ushr 8) and 0xFF).toByte()
            blockInput[salt.size + 3] = (blockIdx and 0xFF).toByte()

            var u = mac.doFinal(blockInput)
            val t = u.copyOf()
            for (iter in 2..iterations) {
                u = mac.doFinal(u)
                for (j in t.indices) {
                    t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
                }
            }
            System.arraycopy(t, 0, out, outPos, hashLen)
            outPos += hashLen
        }
        return if (out.size == dkLen) out else out.copyOf(dkLen)
    }

    fun sha256Hex(text: String): String = sha256Hex(text.toByteArray(StandardCharsets.UTF_8))

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return bytesToHex(digest)
    }

    fun encryptText(plainText: String): String {
        val key = getOrCreateAesKey()
        val iv = ByteArray(GCM_IV_BYTES)
        SecureRandom().nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        return "$ENC_PREFIX${bytesToHex(iv)}:${bytesToHex(encrypted)}"
    }

    fun decryptText(payload: String): String? {
        if (!payload.startsWith(ENC_PREFIX)) return null
        val rest = payload.removePrefix(ENC_PREFIX)
        val sep = rest.indexOf(':')
        if (sep <= 0) return null
        val ivHex = rest.substring(0, sep)
        val ctHex = rest.substring(sep + 1)
        val iv = hexToBytesOrNull(ivHex) ?: return null
        val ct = hexToBytesOrNull(ctHex) ?: return null
        if (iv.size != GCM_IV_BYTES || ct.isEmpty()) return null

        return runCatching {
            val key = getOrCreateAesKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            val decrypted = cipher.doFinal(ct)
            String(decrypted, StandardCharsets.UTF_8)
        }.getOrNull()
    }

    fun encryptBytes(plainBytes: ByteArray, contextAad: String = ""): ByteArray {
        val key = getOrCreateAesKey()
        val iv = ByteArray(GCM_IV_BYTES)
        SecureRandom().nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        if (contextAad.isNotEmpty()) {
            cipher.updateAAD(contextAad.toByteArray(StandardCharsets.UTF_8))
        }
        val encrypted = cipher.doFinal(plainBytes)
        val out = ByteArray(BIN_MAGIC.size + GCM_IV_BYTES + encrypted.size)
        System.arraycopy(BIN_MAGIC, 0, out, 0, BIN_MAGIC.size)
        System.arraycopy(iv, 0, out, BIN_MAGIC.size, GCM_IV_BYTES)
        System.arraycopy(encrypted, 0, out, BIN_MAGIC.size + GCM_IV_BYTES, encrypted.size)
        return out
    }

    fun decryptBytes(cipherBytes: ByteArray, contextAad: String = ""): ByteArray? {
        if (cipherBytes.size <= BIN_MAGIC.size + GCM_IV_BYTES) return null
        for (i in BIN_MAGIC.indices) {
            if (cipherBytes[i] != BIN_MAGIC[i]) return null
        }
        val iv = cipherBytes.copyOfRange(BIN_MAGIC.size, BIN_MAGIC.size + GCM_IV_BYTES)
        val ct = cipherBytes.copyOfRange(BIN_MAGIC.size + GCM_IV_BYTES, cipherBytes.size)
        return runCatching {
            val key = getOrCreateAesKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            if (contextAad.isNotEmpty()) {
                cipher.updateAAD(contextAad.toByteArray(StandardCharsets.UTF_8))
            }
            cipher.doFinal(ct)
        }.getOrNull()
    }

    fun encryptBytesWithPassword(
        plainBytes: ByteArray,
        password: String,
        salt: ByteArray,
        contextAad: String = "",
    ): ByteArray {
        val derived = pbkdf2HmacSha256(
            password = password.toByteArray(StandardCharsets.UTF_8),
            salt = salt,
            iterations = INTERVENTION_ITERATIONS,
            dkLen = 32,
        )
        val key = SecretKeySpec(derived, "AES")
        val iv = ByteArray(GCM_IV_BYTES)
        SecureRandom().nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        if (contextAad.isNotEmpty()) {
            cipher.updateAAD(contextAad.toByteArray(StandardCharsets.UTF_8))
        }
        val encrypted = cipher.doFinal(plainBytes)
        val out = ByteArray(GCM_IV_BYTES + encrypted.size)
        System.arraycopy(iv, 0, out, 0, GCM_IV_BYTES)
        System.arraycopy(encrypted, 0, out, GCM_IV_BYTES, encrypted.size)
        return out
    }

    fun decryptBytesWithPassword(
        cipherWithIv: ByteArray,
        password: String,
        salt: ByteArray,
        contextAad: String = "",
    ): ByteArray? {
        if (password.isEmpty() || cipherWithIv.size <= GCM_IV_BYTES) return null
        val iv = cipherWithIv.copyOfRange(0, GCM_IV_BYTES)
        val ct = cipherWithIv.copyOfRange(GCM_IV_BYTES, cipherWithIv.size)
        return runCatching {
            val derived = pbkdf2HmacSha256(
                password = password.toByteArray(StandardCharsets.UTF_8),
                salt = salt,
                iterations = INTERVENTION_ITERATIONS,
                dkLen = 32,
            )
            val key = SecretKeySpec(derived, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            if (contextAad.isNotEmpty()) {
                cipher.updateAAD(contextAad.toByteArray(StandardCharsets.UTF_8))
            }
            cipher.doFinal(ct)
        }.getOrNull()
    }

    fun isHardwareKeyStoreActive(): Boolean {
        return runCatching {
            val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            getOrCreateAesKey()
            ks.containsAlias(AES_KEY_ALIAS)
        }.getOrDefault(false)
    }

    fun computeIntegrityHmac(payload: String): String {
        val key = getOrCreateHmacKey()
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key)
        val bytes = mac.doFinal("TNT_HMAC_V1:$payload".toByteArray(StandardCharsets.UTF_8))
        return bytesToHex(bytes)
    }

    fun verifyIntegrityHmac(payload: String, expectedHex: String): Boolean {
        val actualHex = computeIntegrityHmac(payload)
        val a = hexToBytesOrNull(actualHex) ?: return false
        val b = hexToBytesOrNull(expectedHex) ?: return false
        return MessageDigest.isEqual(a, b)
    }

    private fun getOrCreateAesKey(): SecretKey {
        return runCatching {
            val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val existing = ks.getKey(AES_KEY_ALIAS, null) as? SecretKey
            if (existing != null) return existing

            val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            val spec = KeyGenParameterSpec.Builder(
                AES_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(false)
                .build()
            gen.init(spec)
            gen.generateKey()
        }.getOrElse {
            jvmFallbackAesKey
        }
    }

    private fun getOrCreateHmacKey(): SecretKey {
        return runCatching {
            val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val existing = ks.getKey(HMAC_KEY_ALIAS, null) as? SecretKey
            if (existing != null) return existing

            val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, KEYSTORE_PROVIDER)
            val spec = KeyGenParameterSpec.Builder(
                HMAC_KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
            ).build()
            gen.init(spec)
            gen.generateKey()
        }.getOrElse {
            jvmFallbackHmacKey
        }
    }

    fun inspectApkIntegrity(
        context: Context,
        sealedSignerSha256: String?,
        onSealFirstSigner: (String) -> Unit,
        storageIntegrityOk: Boolean,
        photoVaultIntegrityOk: Boolean = true,
    ): SecurityIntegrityReport {
        val pkgName = context.packageName
        val pkgValid = pkgName in ALLOWED_PACKAGES
        val isStandaloneTnt = pkgName == "com.aistudio.texnonezaret.app"

        val sigHashes = readSigningCertSha256List(context)
        val primarySig = sigHashes.firstOrNull().orEmpty()
        val sigVerified = sigHashes.size == 1 && primarySig.isNotBlank()

        val matchesKnownOrSealed = when {
            !sigVerified -> false
            sealedSignerSha256.isNullOrBlank() -> {
                onSealFirstSigner(primarySig)
                true
            }
            else -> primarySig.equals(sealedSignerSha256, ignoreCase = true) ||
                primarySig.equals(OFFICIAL_DEBUG_SIGNER_SHA256, ignoreCase = true)
        }

        val noDebugger = !Debug.isDebuggerConnected() && !Debug.waitingForDebugger()
        val noHooks = !detectHookFrameworks()
        val noRoot = !detectRootIndicators()
        val emulator = isLikelyEmulator()
        val keyStoreOk = isHardwareKeyStoreActive()

        val hasInternetPerm = context.packageManager.checkPermission(
            android.Manifest.permission.INTERNET,
            pkgName,
        ) == PackageManager.PERMISSION_GRANTED
        val offlineIsolated = if (isStandaloneTnt) !hasInternetPerm else true

        val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

        val reasons = buildList {
            if (!pkgValid) add("Paket adı klonlanıb və ya dəyişdirilib")
            if (!sigVerified || !matchesKnownOrSealed) add("APK imzası dəyişdirilib və ya yenidən imzalanıb")
            if (!noDebugger) add("Aktiv debugger qoşulması aşkarlandı")
            if (!noHooks) add("Kənar müdaxilə (Hook / Frida / Xposed) aşkarlandı")
            if (!noRoot && !emulator) add("Root / icazəsiz sistem müdaxiləsi (su/Magisk) aşkarlandı")
            if (!offlineIsolated) add("İcazəsiz şəbəkə icazəsi əlavə edilib")
            if (!storageIntegrityOk) add("Yaddaşdakı qorunan fayllara kənar müdaxilə edilib")
            if (!photoVaultIntegrityOk) add("Şifrələnmiş foto qalereya fayllarına kənar müdaxilə aşkarlandı")
        }

        return SecurityIntegrityReport(
            packageName = pkgName,
            packageNameValid = pkgValid,
            signatureSha256 = primarySig,
            signatureVerified = sigVerified,
            signatureMatchesKnownOrSealed = matchesKnownOrSealed,
            noDebuggerAttached = noDebugger,
            noHookFramework = noHooks,
            noRootDetected = noRoot,
            keyStoreActive = keyStoreOk,
            offlineIsolated = offlineIsolated,
            storageIntegrityOk = storageIntegrityOk,
            photoVaultIntegrityOk = photoVaultIntegrityOk,
            screenProtectionEnabled = true,
            tapjackingProtected = true,
            isDebuggableBuild = isDebuggable,
            tamperReasons = reasons,
        )
    }

    @Suppress("DEPRECATION")
    private fun readSigningCertSha256List(context: Context): List<String> {
        return runCatching {
            val pm = context.packageManager
            val certs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signingInfo = info.signingInfo ?: return emptyList()
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
            } else {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
            } ?: return emptyList()

            certs.mapNotNull { sig ->
                runCatching {
                    val digest = MessageDigest.getInstance("SHA-256").digest(sig.toByteArray())
                    digest.joinToString(":") { "%02X".format(it) }
                }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    private fun detectHookFrameworks(): Boolean {
        val signatures = listOf("frida", "xposed", "lsposed", "edxp", "cydiasubstrate")
        val mapsHit = runCatching {
            val f = File("/proc/self/maps")
            if (!f.canRead()) return@runCatching false
            f.bufferedReader().useLines { lines ->
                lines.any { line ->
                    val lower = line.lowercase()
                    signatures.any { token -> lower.contains(token) }
                }
            }
        }.getOrDefault(false)
        if (mapsHit) return true

        val fridaPortHit = runCatching {
            val tcpFile = File("/proc/net/tcp")
            if (!tcpFile.canRead()) return@runCatching false
            tcpFile.bufferedReader().useLines { lines ->
                lines.any { line ->
                    // 27042 (0x69A2) and 27043 (0x69A3) in LISTEN state (" 0A ")
                    (line.contains(":69A2", ignoreCase = true) || line.contains(":69A3", ignoreCase = true)) &&
                        line.contains(" 0A ")
                }
            }
        }.getOrDefault(false)
        if (fridaPortHit) return true

        val stackHit = Thread.currentThread().stackTrace.any { frame ->
            val cls = frame.className.lowercase()
            signatures.any { token -> cls.contains(token) }
        }
        return stackHit
    }

    private fun detectRootIndicators(): Boolean {
        val testKeys = Build.TAGS?.contains("test-keys") == true
        if (testKeys) return true
        val rootPaths = listOf(
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/bin/su",
            "/data/local/xbin/su",
            "/system/sd/xbin/su",
            "/sbin/.magisk",
            "/data/adb/magisk",
            "/system/app/Superuser.apk",
        )
        return rootPaths.any { path -> runCatching { File(path).exists() }.getOrDefault(false) }
    }

    private fun isLikelyEmulator(): Boolean {
        val fp = Build.FINGERPRINT.orEmpty().lowercase()
        val model = Build.MODEL.orEmpty().lowercase()
        val brand = Build.BRAND.orEmpty().lowercase()
        val device = Build.DEVICE.orEmpty().lowercase()
        val product = Build.PRODUCT.orEmpty().lowercase()
        val hardware = Build.HARDWARE.orEmpty().lowercase()
        return fp.startsWith("generic") ||
            fp.contains("emulator") ||
            model.contains("emulator") ||
            model.contains("android sdk built for") ||
            hardware.contains("goldfish") ||
            hardware.contains("ranchu") ||
            product.contains("sdk_gphone") ||
            product.contains("emulator") ||
            (brand.startsWith("generic") && device.startsWith("generic"))
    }

    fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        val digits = "0123456789abcdef"
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = digits[v ushr 4]
            hexChars[i * 2 + 1] = digits[v and 0x0F]
        }
        return String(hexChars)
    }

    fun hexToBytes(hex: String): ByteArray =
        hexToBytesOrNull(hex) ?: throw IllegalArgumentException("Invalid hex string")

    fun hexToBytesOrNull(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(hex[i * 2], 16)
            val lo = Character.digit(hex[i * 2 + 1], 16)
            if (hi < 0 || lo < 0) return null
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }
}
