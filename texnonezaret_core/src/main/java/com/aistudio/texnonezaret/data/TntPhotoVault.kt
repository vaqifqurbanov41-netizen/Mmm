package com.aistudio.texnonezaret.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.ExifInterface
import android.os.Build
import android.os.CancellationSignal
import android.os.Environment
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.aistudio.texnonezaret.security.SecurityGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.roundToInt

data class TntCapturedPhoto(
    val id: String,
    val fileName: String,
    val capturedAtMillis: Long,
    val timestampText: String,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Float?,
    val coordinatesText: String,
    val plateTag: String = "",
    val sha256Hex: String,
)

data class TntLocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
)

object TntPhotoWatermarkFormatter {

    fun formatTimestamp(
        epochMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US)
        sdf.timeZone = timeZone
        return sdf.format(Date(epochMillis))
    }

    fun formatCoordinates(
        latitude: Double?,
        longitude: Double?,
        accuracyMeters: Float? = null,
    ): String {
        if (latitude == null || longitude == null || !latitude.isFinite() || !longitude.isFinite()) {
            return "Koordinat təyin edilmədi"
        }
        val coord = String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
        return if (accuracyMeters != null && accuracyMeters.isFinite() && accuracyMeters > 0f) {
            "$coord (±${accuracyMeters.roundToInt()}m)"
        } else {
            coord
        }
    }

    fun formatCompactCoordinatesForStrip(
        latitude: Double?,
        longitude: Double?,
    ): String {
        if (latitude == null || longitude == null || !latitude.isFinite() || !longitude.isFinite()) {
            return "GPS: Təyin edilmədi"
        }
        return String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
    }

    fun isSafeChildFileName(fileName: String): Boolean {
        if (fileName.isBlank() || fileName.length > 96) return false
        if (fileName.contains('/') || fileName.contains('\\') || fileName.contains("..")) return false
        return fileName.matches(Regex("""^[A-Za-z0-9._-]+$"""))
    }
}

object TntLocationHelper {

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    suspend fun acquireBestFix(context: Context, timeoutMs: Long = 2_400L): TntLocationFix? {
        if (!hasLocationPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

        val lastBest = getBestLastKnownLocation(lm)
        val now = System.currentTimeMillis()
        if (lastBest != null && (now - lastBest.time) <= 20_000L && lastBest.accuracy <= 30f) {
            return TntLocationFix(
                latitude = lastBest.latitude,
                longitude = lastBest.longitude,
                accuracyMeters = if (lastBest.hasAccuracy()) lastBest.accuracy else null,
            )
        }

        val freshFix = withTimeoutOrNull(timeoutMs) {
            requestFreshFix(context, lm)
        }
        val chosen = freshFix ?: lastBest ?: return null
        return TntLocationFix(
            latitude = chosen.latitude,
            longitude = chosen.longitude,
            accuracyMeters = if (chosen.hasAccuracy()) chosen.accuracy else null,
        )
    }

    @SuppressLint("MissingPermission")
    private fun getBestLastKnownLocation(lm: LocationManager): Location? {
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        var best: Location? = null
        for (provider in providers) {
            val loc = runCatching { lm.getLastKnownLocation(provider) }.getOrNull() ?: continue
            val currentBest = best
            if (currentBest == null) {
                best = loc
            } else {
                val newer = loc.time - currentBest.time > 30_000L
                val sameAgeBetterAcc = kotlin.math.abs(loc.time - currentBest.time) <= 30_000L &&
                    loc.accuracy < currentBest.accuracy
                if (newer || sameAgeBetterAcc) {
                    best = loc
                }
            }
        }
        return best
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestFreshFix(
        context: Context,
        lm: LocationManager,
    ): Location? = suspendCancellableCoroutine { cont ->
        val provider = when {
            runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) ->
                LocationManager.GPS_PROVIDER
            runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false) ->
                LocationManager.NETWORK_PROVIDER
            else -> {
                if (cont.isActive) cont.resume(null)
                return@suspendCancellableCoroutine
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val cancelSignal = CancellationSignal()
            val executor = Executors.newSingleThreadExecutor()
            cont.invokeOnCancellation {
                cancelSignal.cancel()
                executor.shutdown()
            }
            runCatching {
                lm.getCurrentLocation(provider, cancelSignal, executor) { loc ->
                    executor.shutdown()
                    if (cont.isActive) cont.resume(loc)
                }
            }.onFailure {
                executor.shutdown()
                if (cont.isActive) cont.resume(null)
            }
        } else {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    runCatching { lm.removeUpdates(this) }
                    if (cont.isActive) cont.resume(location)
                }
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = Unit
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
            }
            cont.invokeOnCancellation {
                runCatching { lm.removeUpdates(listener) }
            }
            runCatching {
                @Suppress("DEPRECATION")
                lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            }.onFailure {
                if (cont.isActive) cont.resume(null)
            }
        }
    }
}

data class ExifPhotoHints(
    val timestampMillis: Long? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

object TntPhotoProcessor {

    private const val MAX_LONG_EDGE_PX = 1920

    fun extractExifHints(sourceFile: File): ExifPhotoHints {
        return runCatching {
            val exif = ExifInterface(sourceFile.absolutePath)
            val rawDate = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            val ts = rawDate?.trim()?.let { txt ->
                runCatching {
                    SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(txt)?.time
                }.getOrNull()
            }
            val latLon = FloatArray(2)
            @Suppress("DEPRECATION")
            val hasGeo = exif.getLatLong(latLon)
            ExifPhotoHints(
                timestampMillis = ts,
                latitude = if (hasGeo) latLon[0].toDouble() else null,
                longitude = if (hasGeo) latLon[1].toDouble() else null,
            )
        }.getOrDefault(ExifPhotoHints())
    }

    fun createEncryptedShieldCoverJpeg(timestampText: String): ByteArray {
        val w = 720
        val h = 480
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(18, 25, 19))

        val borderPaint = Paint().apply {
            color = Color.rgb(47, 93, 52)
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawRect(24f, 24f, w - 24f, h - 24f, borderPaint)

        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(47, 93, 52)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(w / 2f - 70f, 90f, w / 2f + 70f, 146f, 16f, 16f, badgePaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 30f
        }
        canvas.drawText("TNT", w / 2f, 129f, titlePaint)

        titlePaint.textSize = 28f
        canvas.drawText("ŞİFRƏLİ ŞƏKİL (AES-256)", w / 2f, 224f, titlePaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(182, 196, 180)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = 20f
        }
        canvas.drawText("Məzmun şifrələnib. Açmaq üçün TNT proqramından istifadə edin.", w / 2f, 274f, subPaint)

        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(143, 214, 155)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 20f
        }
        canvas.drawText(timestampText, w / 2f, 350f, timePaint)

        return ByteArrayOutputStream().use { baos ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 88, baos)
            bmp.recycle()
            baos.toByteArray()
        }
    }

    fun processAndStampPhoto(
        sourceFile: File,
        timestampText: String,
        coordinatesStripText: String,
        plateTag: String,
    ): ByteArray {
        val rawBmp = decodeScaledBitmap(sourceFile)
            ?: throw IllegalStateException("Şəkil emal edilə bilmədi")
        val orientedBmp = applyExifOrientation(sourceFile, rawBmp)
        val stampedBmp = drawTelemetryStripBelow(
            source = orientedBmp,
            timestampText = timestampText,
            coordinatesStripText = coordinatesStripText,
            plateTag = plateTag.trim(),
        )
        if (orientedBmp !== stampedBmp && !orientedBmp.isRecycled) {
            orientedBmp.recycle()
        }
        return ByteArrayOutputStream().use { baos ->
            stampedBmp.compress(Bitmap.CompressFormat.JPEG, 90, baos)
            stampedBmp.recycle()
            baos.toByteArray()
        }
    }

    private fun decodeScaledBitmap(file: File): Bitmap? {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null
        val longEdge = max(boundsOpts.outWidth, boundsOpts.outHeight)
        var sampleSize = 1
        while (longEdge / sampleSize > MAX_LONG_EDGE_PX * 2) {
            sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null
        val curLong = max(decoded.width, decoded.height)
        if (curLong <= MAX_LONG_EDGE_PX) return decoded
        val ratio = MAX_LONG_EDGE_PX.toFloat() / curLong.toFloat()
        val dstW = (decoded.width * ratio).roundToInt().coerceAtLeast(1)
        val dstH = (decoded.height * ratio).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(decoded, dstW, dstH, true)
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    private fun applyExifOrientation(file: File, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun drawTelemetryStripBelow(
        source: Bitmap,
        timestampText: String,
        coordinatesStripText: String,
        plateTag: String,
    ): Bitmap {
        val w = source.width
        val h = source.height
        val stripHeight = (w * 0.056f).roundToInt().coerceIn(46, 92)
        val result = Bitmap.createBitmap(w, h + stripHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(source, 0f, 0f, null)

        val barPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, h.toFloat(), w.toFloat(), (h + stripHeight).toFloat(), barPaint)

        val dividerPaint = Paint().apply {
            color = Color.argb(90, 255, 255, 255)
            strokeWidth = max(1f, w * 0.0012f)
        }
        canvas.drawLine(0f, h.toFloat(), w.toFloat(), h.toFloat(), dividerPaint)

        val padX = (w * 0.022f).coerceAtLeast(14f)
        val availableWidth = (w - padX * 2f).coerceAtLeast(100f)

        val leftSeg = timestampText
        val midSeg = coordinatesStripText
        val plateSeg = plateTag
        val badgeSeg = "TNT"

        val baseSegments = listOf(leftSeg, midSeg, plateSeg, badgeSeg).filter { it.isNotBlank() }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = (stripHeight * 0.44f).coerceIn(14f, 36f)
        }

        // Ensure all telemetry segments fit horizontally without colliding
        val minGap = (w * 0.025f).coerceAtLeast(12f)
        while (textPaint.textSize > 11f) {
            val totalTextW = baseSegments.sumOf { textPaint.measureText(it).toDouble() }.toFloat()
            val totalNeed = totalTextW + minGap * (baseSegments.size - 1).coerceAtLeast(0)
            if (totalNeed <= availableWidth) break
            textPaint.textSize -= 1f
        }

        val fm = textPaint.fontMetrics
        val baselineY = h + stripHeight / 2f - (fm.ascent + fm.descent) / 2f

        // Left: date + exact time
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(leftSeg, padX, baselineY, textPaint)

        // Right: TNT badge
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(badgeSeg, w - padX, baselineY, textPaint)

        // Middle-left & optional plate tag cleanly spaced between timestamp and badge
        val leftEnd = padX + textPaint.measureText(leftSeg) + minGap
        val rightStart = (w - padX) - textPaint.measureText(badgeSeg) - minGap

        if (plateSeg.isNotBlank() && rightStart - leftEnd > 40f) {
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(plateSeg, rightStart, baselineY, textPaint)
            val midAvailableCenter = (leftEnd + (rightStart - textPaint.measureText(plateSeg) - minGap)) / 2f
            if (midAvailableCenter > leftEnd) {
                textPaint.textAlign = Paint.Align.CENTER
                canvas.drawText(midSeg, midAvailableCenter, baselineY, textPaint)
            }
        } else if (rightStart > leftEnd) {
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(midSeg, (leftEnd + rightStart) / 2f, baselineY, textPaint)
        }

        return result
    }
}

class TntPhotoVault(context: Context) {

    private val appContext = context.applicationContext
    private val vaultDir: File
        get() = File(appContext.filesDir, VAULT_DIR_NAME).apply { mkdirs() }
    private val indexFile: File
        get() = File(vaultDir, INDEX_FILE_NAME)

    companion object {
        private const val VAULT_DIR_NAME = "tnt_vault_photos"
        private const val INDEX_FILE_NAME = "tnt_vault_index.enc"
        private const val MAX_PHOTO_CIPHER_BYTES = 20 * 1024 * 1024
    }

    fun resolveConstrainedVaultFile(fileName: String): File? {
        if (!TntPhotoWatermarkFormatter.isSafeChildFileName(fileName)) return null
        return runCatching {
            val base = vaultDir.canonicalFile
            val candidate = File(base, fileName).canonicalFile
            if (candidate.parentFile == base) candidate else null
        }.getOrNull()
    }

    fun isVaultTampered(): Boolean {
        if (!indexFile.exists()) return false
        val enc = runCatching { indexFile.readText(Charsets.UTF_8) }.getOrNull() ?: return true
        val json = SecurityGuard.decryptText(enc) ?: return true
        val photos = parseIndexJson(json) ?: return true
        for (p in photos) {
            val file = resolveConstrainedVaultFile(p.fileName) ?: return true
            if (!file.exists()) return true
        }
        return false
    }

    suspend fun loadPhotos(): List<TntCapturedPhoto> = withContext(Dispatchers.IO) {
        if (!indexFile.exists()) return@withContext emptyList()
        val enc = runCatching { indexFile.readText(Charsets.UTF_8) }.getOrNull() ?: return@withContext emptyList()
        val json = SecurityGuard.decryptText(enc) ?: return@withContext emptyList()
        parseIndexJson(json).orEmpty().sortedByDescending { it.capturedAtMillis }
    }

    suspend fun saveStampedPhoto(
        jpegBytes: ByteArray,
        capturedAtMillis: Long,
        timestampText: String,
        latitude: Double?,
        longitude: Double?,
        accuracyMeters: Float?,
        coordinatesText: String,
        plateTag: String,
    ): TntCapturedPhoto = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString().replace("-", "").take(16)
        val fileName = "tnt_photo_$id.jpg"
        val targetFile = resolveConstrainedVaultFile(fileName)
            ?: throw SecurityException("Qorunan qalereya yolu etibarsızdır")

        val sha256Hex = SecurityGuard.sha256Hex(jpegBytes)
        val encryptedBytes = SecurityGuard.encryptBytes(jpegBytes, contextAad = "tnt_photo:$id")
        targetFile.writeBytes(encryptedBytes)

        val item = TntCapturedPhoto(
            id = id,
            fileName = fileName,
            capturedAtMillis = capturedAtMillis,
            timestampText = timestampText,
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters,
            coordinatesText = coordinatesText,
            plateTag = plateTag.trim(),
            sha256Hex = sha256Hex,
        )

        val current = loadPhotos().filterNot { it.id == id }
        persistIndex(listOf(item) + current)
        item
    }

    suspend fun loadDecryptedPhotoBytes(photo: TntCapturedPhoto): ByteArray? = withContext(Dispatchers.IO) {
        val file = resolveConstrainedVaultFile(photo.fileName) ?: return@withContext null
        if (!file.exists() || file.length() > MAX_PHOTO_CIPHER_BYTES) return@withContext null
        val cipherBytes = runCatching { file.readBytes() }.getOrNull() ?: return@withContext null
        val plain = SecurityGuard.decryptBytes(cipherBytes, contextAad = "tnt_photo:${photo.id}")
            ?: return@withContext null
        val actualSha = SecurityGuard.sha256Hex(plain)
        if (!actualSha.equals(photo.sha256Hex, ignoreCase = true)) return@withContext null
        plain
    }

    suspend fun deletePhoto(photoId: String): List<TntCapturedPhoto> = withContext(Dispatchers.IO) {
        val current = loadPhotos()
        val target = current.firstOrNull { it.id == photoId }
        if (target != null) {
            resolveConstrainedVaultFile(target.fileName)?.let { file ->
                if (file.exists()) file.delete()
            }
        }
        val remaining = current.filterNot { it.id == photoId }
        persistIndex(remaining)
        remaining
    }

    suspend fun wipeAllPhotos() = withContext(Dispatchers.IO) {
        runCatching {
            vaultDir.listFiles()?.forEach { f -> f.delete() }
        }
    }

    private fun persistIndex(items: List<TntCapturedPhoto>) {
        val arr = JSONArray()
        for (item in items) {
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("fileName", item.fileName)
                    put("capturedAtMillis", item.capturedAtMillis)
                    put("timestampText", item.timestampText)
                    if (item.latitude != null) put("latitude", item.latitude)
                    if (item.longitude != null) put("longitude", item.longitude)
                    if (item.accuracyMeters != null) put("accuracyMeters", item.accuracyMeters.toDouble())
                    put("coordinatesText", item.coordinatesText)
                    put("plateTag", item.plateTag)
                    put("sha256Hex", item.sha256Hex)
                }
            )
        }
        val root = JSONObject().apply { put("photos", arr) }.toString()
        val encrypted = SecurityGuard.encryptText(root)
        indexFile.writeText(encrypted, Charsets.UTF_8)
    }

    private fun parseIndexJson(jsonText: String): List<TntCapturedPhoto>? {
        return runCatching {
            val root = JSONObject(jsonText)
            val arr = root.optJSONArray("photos") ?: return@runCatching emptyList()
            val out = ArrayList<TntCapturedPhoto>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val fileName = obj.optString("fileName", "")
                if (!TntPhotoWatermarkFormatter.isSafeChildFileName(fileName)) return@runCatching null
                val lat = if (obj.has("latitude")) obj.optDouble("latitude") else null
                val lon = if (obj.has("longitude")) obj.optDouble("longitude") else null
                val acc = if (obj.has("accuracyMeters")) obj.optDouble("accuracyMeters").toFloat() else null
                out.add(
                    TntCapturedPhoto(
                        id = obj.optString("id", ""),
                        fileName = fileName,
                        capturedAtMillis = obj.optLong("capturedAtMillis", 0L),
                        timestampText = obj.optString("timestampText", ""),
                        latitude = lat,
                        longitude = lon,
                        accuracyMeters = acc,
                        coordinatesText = obj.optString("coordinatesText", ""),
                        plateTag = obj.optString("plateTag", ""),
                        sha256Hex = obj.optString("sha256Hex", ""),
                    )
                )
            }
            out
        }.getOrNull()
    }
}

data class EncryptedJpgInspection(
    val requiresPassword: Boolean,
)

data class DecodedEncryptedJpg(
    val stampedJpegBytes: ByteArray,
    val capturedAtMillis: Long,
    val timestampText: String,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Float?,
    val coordinatesText: String,
    val plateTag: String,
)

object TntEncryptedJpgCodec {

    private val START_MAGIC = "TNTJENC2".toByteArray(Charsets.US_ASCII)
    private val END_MAGIC = "ENDTNTJ2".toByteArray(Charsets.US_ASCII)
    private const val MODE_KEYSTORE: Byte = 0
    private const val MODE_PASSWORD: Byte = 1
    private const val SALT_BYTES = 16
    private const val AAD_EXPORT = "tnt_export_jpg_v2"

    fun encodeEncryptedJpgEnvelope(
        coverJpegBytes: ByteArray,
        photo: TntCapturedPhoto,
        stampedJpegBytes: ByteArray,
        password: String? = null,
    ): ByteArray {
        val metaBytes = ByteArrayOutputStream().use { metaOut ->
            java.io.DataOutputStream(metaOut).use { dos ->
                dos.writeLong(photo.capturedAtMillis)
                dos.writeUTF(photo.timestampText)
                dos.writeBoolean(photo.latitude != null)
                if (photo.latitude != null) dos.writeDouble(photo.latitude)
                dos.writeBoolean(photo.longitude != null)
                if (photo.longitude != null) dos.writeDouble(photo.longitude)
                dos.writeBoolean(photo.accuracyMeters != null)
                if (photo.accuracyMeters != null) dos.writeFloat(photo.accuracyMeters)
                dos.writeUTF(photo.coordinatesText)
                dos.writeUTF(photo.plateTag)
            }
            metaOut.toByteArray()
        }

        val bundle = ByteArray(4 + metaBytes.size + stampedJpegBytes.size)
        ByteBuffer.wrap(bundle, 0, 4).putInt(metaBytes.size)
        System.arraycopy(metaBytes, 0, bundle, 4, metaBytes.size)
        System.arraycopy(stampedJpegBytes, 0, bundle, 4 + metaBytes.size, stampedJpegBytes.size)

        val salt = ByteArray(SALT_BYTES)
        val usePassword = !password.isNullOrBlank()
        val mode = if (usePassword) MODE_PASSWORD else MODE_KEYSTORE
        val encrypted = if (usePassword) {
            SecureRandom().nextBytes(salt)
            SecurityGuard.encryptBytesWithPassword(bundle, password!!, salt, AAD_EXPORT)
        } else {
            SecurityGuard.encryptBytes(bundle, AAD_EXPORT)
        }

        val footerLen = START_MAGIC.size + 1 + SALT_BYTES + 4 + encrypted.size + END_MAGIC.size
        val out = ByteArray(coverJpegBytes.size + footerLen)
        var pos = 0
        System.arraycopy(coverJpegBytes, 0, out, pos, coverJpegBytes.size)
        pos += coverJpegBytes.size
        System.arraycopy(START_MAGIC, 0, out, pos, START_MAGIC.size)
        pos += START_MAGIC.size
        out[pos++] = mode
        System.arraycopy(salt, 0, out, pos, SALT_BYTES)
        pos += SALT_BYTES
        ByteBuffer.wrap(out, pos, 4).putInt(encrypted.size)
        pos += 4
        System.arraycopy(encrypted, 0, out, pos, encrypted.size)
        pos += encrypted.size
        System.arraycopy(END_MAGIC, 0, out, pos, END_MAGIC.size)
        return out
    }

    fun inspectEncryptedJpg(fileBytes: ByteArray): EncryptedJpgInspection? {
        val header = parseTrailerBounds(fileBytes) ?: return null
        return EncryptedJpgInspection(requiresPassword = header.mode == MODE_PASSWORD)
    }

    fun decodeEncryptedJpgEnvelope(
        fileBytes: ByteArray,
        password: String? = null,
    ): DecodedEncryptedJpg? {
        val bounds = parseTrailerBounds(fileBytes) ?: return null
        val cipherSlice = fileBytes.copyOfRange(bounds.payloadStart, bounds.payloadEnd)
        val bundle = when (bounds.mode) {
            MODE_KEYSTORE -> SecurityGuard.decryptBytes(cipherSlice, AAD_EXPORT)
            MODE_PASSWORD -> {
                if (password.isNullOrBlank()) return null
                SecurityGuard.decryptBytesWithPassword(cipherSlice, password, bounds.salt, AAD_EXPORT)
            }
            else -> null
        } ?: return null

        if (bundle.size <= 4) return null
        val metaLen = ByteBuffer.wrap(bundle, 0, 4).int
        if (metaLen <= 0 || 4 + metaLen >= bundle.size) return null
        val jpegBytes = bundle.copyOfRange(4 + metaLen, bundle.size)

        return runCatching {
            java.io.DataInputStream(java.io.ByteArrayInputStream(bundle, 4, metaLen)).use { dis ->
                val capturedAtMillis = dis.readLong()
                val timestampText = dis.readUTF()
                val lat = if (dis.readBoolean()) dis.readDouble() else null
                val lon = if (dis.readBoolean()) dis.readDouble() else null
                val acc = if (dis.readBoolean()) dis.readFloat() else null
                val coordinatesText = dis.readUTF()
                val plateTag = dis.readUTF()
                DecodedEncryptedJpg(
                    stampedJpegBytes = jpegBytes,
                    capturedAtMillis = capturedAtMillis,
                    timestampText = timestampText,
                    latitude = lat,
                    longitude = lon,
                    accuracyMeters = acc,
                    coordinatesText = coordinatesText,
                    plateTag = plateTag,
                )
            }
        }.getOrNull()
    }

    private data class TrailerBounds(
        val mode: Byte,
        val salt: ByteArray,
        val payloadStart: Int,
        val payloadEnd: Int,
    )

    private fun parseTrailerBounds(bytes: ByteArray): TrailerBounds? {
        val minHeader = START_MAGIC.size + 1 + SALT_BYTES + 4 + END_MAGIC.size
        if (bytes.size <= minHeader) return null
        val endPos = bytes.size - END_MAGIC.size
        for (i in END_MAGIC.indices) {
            if (bytes[endPos + i] != END_MAGIC[i]) return null
        }
        // Scan backwards from end for START_MAGIC within valid length
        for (idx in bytes.size - minHeader downTo 0) {
            var match = true
            for (j in START_MAGIC.indices) {
                if (bytes[idx + j] != START_MAGIC[j]) {
                    match = false
                    break
                }
            }
            if (match) {
                val modePos = idx + START_MAGIC.size
                val mode = bytes[modePos]
                val saltPos = modePos + 1
                val salt = bytes.copyOfRange(saltPos, saltPos + SALT_BYTES)
                val lenPos = saltPos + SALT_BYTES
                val payloadLen = ByteBuffer.wrap(bytes, lenPos, 4).int
                val payloadStart = lenPos + 4
                val payloadEnd = payloadStart + payloadLen
                if (payloadLen > 0 && payloadEnd == endPos) {
                    return TrailerBounds(
                        mode = mode,
                        salt = salt,
                        payloadStart = payloadStart,
                        payloadEnd = payloadEnd,
                    )
                }
            }
        }
        return null
    }
}

object TntPhotoExporter {

    fun buildExportFileName(photo: TntCapturedPhoto, encrypted: Boolean): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(photo.capturedAtMillis))
        val prefix = if (encrypted) "TNT_Sifreli" else "TNT_Foto"
        return "${prefix}_$stamp.jpg"
    }

    fun cleanupStaleExportCache(context: Context, maxAgeMs: Long = 10 * 60 * 1000L) {
        runCatching {
            val dir = File(context.cacheDir, "exports")
            if (!dir.exists()) return
            val cutoff = System.currentTimeMillis() - maxAgeMs
            dir.listFiles()?.forEach { f ->
                if (f.lastModified() < cutoff) f.delete()
            }
        }
    }

    fun writeShareTempJpg(context: Context, fileName: String, jpgBytes: ByteArray): File {
        cleanupStaleExportCache(context)
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeName = if (TntPhotoWatermarkFormatter.isSafeChildFileName(fileName)) {
            fileName
        } else {
            "TNT_export_${System.currentTimeMillis()}.jpg"
        }
        val outFile = File(exportsDir, safeName)
        outFile.writeBytes(jpgBytes)
        return outFile
    }

    fun saveEncryptedJpgToDownloadsApi29Plus(
        context: Context,
        fileName: String,
        encryptedJpgBytes: ByteArray,
    ): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "image/jpeg")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/TNT")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return null
        return try {
            resolver.openOutputStream(itemUri)?.use { out ->
                out.write(encryptedJpgBytes)
            } ?: throw IllegalStateException("Yaddaşa yazıla bilmədi")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(itemUri, values, null, null)
            "Downloads/TNT/$fileName"
        } catch (e: Throwable) {
            runCatching { resolver.delete(itemUri, null, null) }
            throw e
        }
    }
}

