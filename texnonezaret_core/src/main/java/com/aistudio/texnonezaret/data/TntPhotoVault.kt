package com.aistudio.texnonezaret.data

import android.Manifest
import android.annotation.SuppressLint
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
import android.os.Looper
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

object TntPhotoProcessor {

    private const val MAX_LONG_EDGE_PX = 1920

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
        val fileName = "tnt_photo_$id.tntimg"
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
