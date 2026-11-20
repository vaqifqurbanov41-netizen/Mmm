package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.LruCache
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.data.model.CropThreatEntry
import com.example.data.model.DiseaseVisualCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.sqrt

data class VisualSignature(
    val clues: BitmapVisualClues,
    val hueBins: FloatArray,
    val lumBins: FloatArray,
)

object ReferenceImageMatcher {

    private const val USER_AGENT =
        "AqroMeslehet/1.0 (https://aqromeslehet.az; agro-diagnostics@aqromeslehet.az)"

    val sharedImageOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "image/webp,image/png,image/jpeg,image/*;q=0.8")
                    .build()
                chain.proceed(req)
            }
            .build()
    }

    @Volatile
    private var cachedLoader: ImageLoader? = null

    fun getImageLoader(context: Context): ImageLoader {
        val existing = cachedLoader
        if (existing != null) return existing
        return synchronized(this) {
            cachedLoader ?: ImageLoader.Builder(context.applicationContext)
                .okHttpClient(sharedImageOkHttpClient)
                .memoryCache {
                    MemoryCache.Builder(context.applicationContext)
                        .maxSizePercent(0.18)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(File(context.applicationContext.cacheDir, "disease_ref_photos"))
                        .maxSizeBytes(32L * 1024L * 1024L)
                        .build()
                }
                .crossfade(true)
                .build()
                .also { cachedLoader = it }
        }
    }

    private val signatureCache = object : LruCache<String, VisualSignature>(64) {}

    fun extractVisualSignature(bitmap: Bitmap): VisualSignature? {
        val clues = OfflineAgronomistAdvisor.analyzeBitmapVisualClues(bitmap) ?: return null
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return null

        val hueBins = FloatArray(12)
        val lumBins = FloatArray(4)
        val hsv = FloatArray(3)

        val stepX = (w / 48).coerceAtLeast(1)
        val stepY = (h / 48).coerceAtLeast(1)
        var total = 0

        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val pixel = bitmap.getPixel(x, y)
                Color.colorToHSV(pixel, hsv)
                val hue = hsv[0] // 0..360
                val sat = hsv[1] // 0..1
                val value = hsv[2] // 0..1

                // Hue bin weighted by saturation so chromatic disease tones dominate over neutral backdrop
                val hueIdx = ((hue / 30f).toInt()).coerceIn(0, 11)
                hueBins[hueIdx] += (0.25f + sat * 0.75f)

                val lumIdx = ((value * 4f).toInt()).coerceIn(0, 3)
                lumBins[lumIdx] += 1f
                total++
                x += stepX
            }
            y += stepY
        }

        if (total == 0) return null
        val hueSum = hueBins.sum().coerceAtLeast(1e-4f)
        for (i in hueBins.indices) hueBins[i] = hueBins[i] / hueSum
        val lumSum = lumBins.sum().coerceAtLeast(1e-4f)
        for (i in lumBins.indices) lumBins[i] = lumBins[i] / lumSum

        return VisualSignature(clues = clues, hueBins = hueBins, lumBins = lumBins)
    }

    fun compareSignatures(query: VisualSignature, reference: VisualSignature): Float {
        val hueSim = cosineSimilarity(query.hueBins, reference.hueBins)
        val lumSim = cosineSimilarity(query.lumBins, reference.lumBins)

        val qC = query.clues
        val rC = reference.clues
        val clueDist = (
            abs(qC.brownDarkLesionPct - rC.brownDarkLesionPct) / 100f * 1.3f +
                abs(qC.yellowChlorosisPct - rC.yellowChlorosisPct) / 100f * 1.1f +
                abs(qC.whitePowderyPct - rC.whitePowderyPct) / 100f * 1.2f +
                abs(qC.rustOrangePct - rC.rustOrangePct) / 100f * 1.4f +
                abs(qC.spotContrastPct - rC.spotContrastPct) / 100f * 0.9f
            ) / 5.9f
        val clueSim = (1f - clueDist.coerceIn(0f, 1f))

        return (hueSim * 0.45f + clueSim * 0.40f + lumSim * 0.15f).coerceIn(0f, 1f)
    }

    suspend fun fetchAndCompareRealPhotos(
        bitmap: Bitmap,
        candidates: List<CropThreatEntry>,
        maxCandidates: Int = 12,
    ): Map<String, Float> {
        val querySig = withContext(Dispatchers.Default) {
            extractVisualSignature(bitmap)
        } ?: return emptyMap()

        val limited = candidates.take(maxCandidates)
        val semaphore = Semaphore(4)

        return withContext(Dispatchers.IO) {
            coroutineScope {
                limited.mapNotNull { threat ->
                    val profile = DiseaseVisualCatalog.profileFor(threat.id) ?: return@mapNotNull null
                    val thumbUrl = DiseaseVisualCatalog.resizedUrl(profile.referenceImageUrl, 320)
                    async {
                        semaphore.withPermit {
                            val refSig = signatureCache.get(thumbUrl) ?: fetchSignatureFromUrl(thumbUrl)?.also {
                                signatureCache.put(thumbUrl, it)
                            }
                            if (refSig != null) {
                                threat.id to compareSignatures(querySig, refSig)
                            } else {
                                null
                            }
                        }
                    }
                }.awaitAll().filterNotNull().toMap()
            }
        }
    }

    private fun fetchSignatureFromUrl(url: String): VisualSignature? = runCatching {
        val request = Request.Builder().url(url).get().build()
        sharedImageOkHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bytes = response.body?.bytes() ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sampleSize = 1
            while (bounds.outWidth / sampleSize > 256 || bounds.outHeight / sampleSize > 256) {
                sampleSize *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
            try {
                extractVisualSignature(bmp)
            } finally {
                bmp.recycle()
            }
        }
    }.getOrNull()

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var magA = 0f
        var magB = 0f
        val len = minOf(a.size, b.size)
        for (i in 0 until len) {
            dot += a[i] * b[i]
            magA += a[i] * a[i]
            magB += b[i] * b[i]
        }
        val denom = sqrt(magA) * sqrt(magB)
        return if (denom <= 1e-6f) 0f else (dot / denom).coerceIn(0f, 1f)
    }
}
