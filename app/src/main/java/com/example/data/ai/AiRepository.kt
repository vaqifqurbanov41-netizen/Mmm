package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.i18n.AppLanguage
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

import com.example.domain.CadastralDocumentParser
import com.example.domain.DocCoordTarget
import com.example.domain.LatLon
import com.example.i18n.StringsAz
import com.example.i18n.StringsEn
import com.example.i18n.StringsRu

data class ChatHistoryTurn(
    val isUser: Boolean,
    val text: String,
)

data class DocumentScanResult(
    val areaHa: Double?,
    val summary: String,
    val coordinates: List<Pair<Double, Double>>,
    val coordTarget: DocCoordTarget = DocCoordTarget.UTM_38T,
    val utmZone: Int = 38,
    val utmLinesText: String = "",
    val latLonLinesText: String = "",
    val rawPairs: List<Pair<String, String>> = emptyList(),
)

interface AiRepository {
    fun askAgronomistStream(
        question: String,
        history: List<ChatHistoryTurn>,
        farmerName: String,
        farmerRegion: String,
        language: AppLanguage,
    ): Flow<String>

    fun analyzePlantPhotoStream(
        bitmap: Bitmap?,
        note: String,
        language: AppLanguage,
        realPhotoSimilarities: Map<String, Float> = emptyMap(),
    ): Flow<String>

    suspend fun scanFieldDocument(
        bitmap: Bitmap,
        baseLat: Double,
        baseLon: Double,
        language: AppLanguage,
    ): DocumentScanResult
}

class FirebaseAiRepository : AiRepository {

    private val generalModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
    }

    private val scannerGroundedModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            tools = listOf(Tool.googleSearch()),
        )
    }

    private val documentScanSchema = Schema.obj(
        properties = mapOf(
            "areaHa" to Schema.double(description = "Total parcel area in hectares if shown on the document", nullable = true),
            "summary" to Schema.string(description = "Brief summary of the land document details and findings"),
            "rawPairs" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "x" to Schema.string(description = "Value from X / Easting / Latitude column in exact row order"),
                        "y" to Schema.string(description = "Value from Y / Northing / Longitude column in exact row order"),
                    )
                ),
                description = "Exact sequential X and Y pairs from a cadastral coordinate table if present",
            ),
            "coordinates" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "lat" to Schema.double(description = "WGS84 latitude"),
                        "lon" to Schema.double(description = "WGS84 longitude"),
                    )
                ),
                description = "Polygon corner coordinates in WGS84 lat/lon if extracted from the document",
            ),
        ),
        optionalProperties = listOf("areaHa", "rawPairs", "coordinates")
    )

    private val documentModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = documentScanSchema
            }
        )
    }

    override fun askAgronomistStream(
        question: String,
        history: List<ChatHistoryTurn>,
        farmerName: String,
        farmerRegion: String,
        language: AppLanguage,
    ): Flow<String> = flow {
        val langInstruction = when (language) {
            AppLanguage.AZ -> "Respond strictly in natural, professional Azerbaijani (Azərbaycan dili)."
            AppLanguage.EN -> "Respond strictly in clear, professional English."
            AppLanguage.RU -> "Respond strictly in natural, professional Russian (Русский язык)."
        }

        val localCatalogContext = OfflineAgronomistAdvisor.buildPromptContext(question, language)
        val systemContext = buildString {
            appendLine("You are AqroMəsləhət AI Agronomist, an expert agricultural advisor for farmers in Azerbaijan and the region.")
            appendLine(langInstruction)
            if (farmerName.isNotBlank()) appendLine("Farmer name: $farmerName.")
            if (farmerRegion.isNotBlank()) appendLine("Farmer region: $farmerRegion.")
            appendLine("You MUST know and accurately diagnose every single crop and all of its diseases/pests listed in the AqroMəsləhət reference catalog below. Compare the user's described symptoms against all matching diseases for that crop, highlight key visual diagnostic differences, prioritize active ingredients (təsiredici ana maddə), and keep advice structured and safe.")
            if (localCatalogContext.isNotBlank()) {
                appendLine()
                append(localCatalogContext)
            }
        }

        val prompt = buildString {
            appendLine(systemContext)
            if (history.isNotEmpty()) {
                appendLine("\nRecent conversation:")
                history.takeLast(6).forEach { turn ->
                    val role = if (turn.isUser) "Farmer" else "Agronomist"
                    appendLine("$role: ${turn.text}")
                }
            }
            appendLine("\nFarmer question: $question")
        }

        val accumulated = StringBuilder()
        val firstChunkChannel = Channel<String>(Channel.UNLIMITED)
        var streamFailure: Throwable? = null
        coroutineScope {
            val collectorJob = launch(Dispatchers.IO) {
                try {
                    generalModel.generateContentStream(prompt).collect { chunk ->
                        val chunkText = chunk.text.orEmpty()
                        if (chunkText.isNotEmpty()) {
                            firstChunkChannel.trySend(chunkText)
                        }
                    }
                    firstChunkChannel.close()
                } catch (t: Throwable) {
                    firstChunkChannel.close(t)
                }
            }

            val firstResult = withTimeoutOrNull(12_000L) {
                firstChunkChannel.receiveCatching()
            }

            if (firstResult != null && firstResult.isSuccess) {
                val firstText = firstResult.getOrNull().orEmpty()
                if (firstText.isNotEmpty()) {
                    accumulated.append(firstText)
                    emit(accumulated.toString())
                }
                withTimeout(25_000L) {
                    while (true) {
                        val next = firstChunkChannel.receiveCatching()
                        if (next.isClosed) {
                            next.exceptionOrNull()?.let { throw it }
                            break
                        }
                        val nextText = next.getOrNull().orEmpty()
                        if (nextText.isNotEmpty()) {
                            accumulated.append(nextText)
                            emit(accumulated.toString())
                        }
                    }
                }
            } else {
                streamFailure = firstResult?.exceptionOrNull()
                collectorJob.cancel()
                firstChunkChannel.close()
                if (streamFailure is CancellationException) {
                    throw streamFailure!!
                }
                val streamErrMsg = streamFailure?.localizedMessage.orEmpty()
                if (com.example.data.firebase.isAppCheckTokenError(streamErrMsg)) {
                    throw streamFailure!!
                }
                // Stream stalled or closed empty; try fast unary generateContent fallback
                val unaryResponse = withTimeout(12_000L) {
                    generalModel.generateContent(prompt)
                }
                val unaryText = unaryResponse.text.orEmpty()
                if (unaryText.isNotBlank()) {
                    accumulated.append(unaryText)
                    emit(unaryText)
                } else if (streamFailure != null) {
                    throw streamFailure!!
                }
            }
        }
        if (accumulated.isBlank()) {
            throw (streamFailure ?: IllegalStateException("AI model returned an empty response."))
        }
    }.flowOn(Dispatchers.IO)

    override fun analyzePlantPhotoStream(
        bitmap: Bitmap?,
        note: String,
        language: AppLanguage,
        realPhotoSimilarities: Map<String, Float>,
    ): Flow<String> = flow {
        val langInstruction = when (language) {
            AppLanguage.AZ -> "Respond strictly in natural, clear Azerbaijani (Azərbaycan dili)."
            AppLanguage.EN -> "Respond strictly in clear, professional English."
            AppLanguage.RU -> "Respond strictly in natural, clear Russian (Русский язык)."
        }

        val visualClues = OfflineAgronomistAdvisor.describeBitmapClues(bitmap, language)
        val assessment = OfflineAgronomistAdvisor.assessPlantPhoto(
            bitmap = bitmap,
            note = note,
            realPhotoSimilarities = realPhotoSimilarities,
            limit = 5,
        )
        val matchedContext = buildString {
            if (assessment.matches.isNotEmpty()) {
                appendLine("Top candidate comparisons from real reference photos + internal crop pathology database:")
                assessment.matches.forEach { m ->
                    val cropName = com.example.data.model.CropProtectionCatalog.crops
                        .firstOrNull { it.id == m.threat.cropId }?.name?.get(language).orEmpty()
                    val refPct = realPhotoSimilarities[m.threat.id]?.let { "${(it * 100).toInt()}% ref photo" }.orEmpty()
                    appendLine("- $cropName | ${m.threat.name.get(language)} (~${m.similarityPct}%${if (refPct.isNotBlank()) ", $refPct" else ""}): Visual cue: ${m.profile.visualCue.get(language)} | Active: ${m.threat.activeIngredient.get(language)}")
                }
            }
        }
        val catalogDigest = OfflineAgronomistAdvisor.buildScannerCatalogDigest(language)
        val precautionLine = when (language) {
            AppLanguage.AZ -> "Hər ehtimala qarşı sahədə dəqiq diaqnoz və tətbiq dozası üçün mütləq canlı aqronomla məsləhətləşin."
            AppLanguage.EN -> "As a precaution, always consult a qualified field agronomist before spraying or finalizing dosages."
            AppLanguage.RU -> "На всякий случай обязательно проконсультируйтесь с агрономом перед обработкой и выбором дозировки."
        }
        val noMatchHeader = when (language) {
            AppLanguage.AZ -> "✅ Aydın xəstəlik/zərərverici uyğunluğu tapılmadı"
            AppLanguage.EN -> "✅ No clear disease or pest match found"
            AppLanguage.RU -> "✅ Явных совпадений с болезнями/вредителями не найдено"
        }

        val promptText = buildString {
            appendLine("You are AqroMəsləhət AI Plant Pathologist & Agronomist.")
            appendLine(langInstruction)
            appendLine("CRITICAL GENUINE-MATCH RULE: First verify whether the photo actually shows a plant/crop organ AND whether genuine disease, pest, or deficiency symptoms exist.")
            appendLine("- If the image does NOT clearly show a plant/crop OR shows healthy green foliage with no disease/pest match against real reference photos, start with \"$noMatchHeader\", briefly explain what you see, and DO NOT force or invent a false disease diagnosis. Still end with the agronomist precaution line.")
            appendLine("- ONLY if genuine disease/pest symptoms match, combine the real-reference-photo comparison candidates below, the internal AqroMəsləhət catalog, and online pathology search in 4 concise numbered sections:")
            appendLine("1) Probable disease / pest / deficiency prediction (cite closest match & visual evidence)")
            appendLine("2) Key visual causes, distinguishing lesion traits & spreading conditions")
            appendLine("3) Recommended active ingredient (təsiredici ana maddə) and safe preventive/control measures")
            appendLine("4) Precautionary note — always explicitly end with: \"$precautionLine\"")
            if (!assessment.hasMatch && bitmap != null) {
                appendLine("\nPre-scan note: Local healthy/non-plant gate flagged low lesion signal (looksLikePlant=${assessment.looksLikePlantMaterial}, appearsHealthyGreen=${assessment.appearsHealthyGreen}). Double-check image carefully before diagnosing any disease.")
            }
            if (note.isNotBlank()) {
                appendLine("\nFarmer observation note: $note")
            }
            if (visualClues.isNotBlank()) {
                appendLine("\nOn-device pre-scan metrics: $visualClues")
            }
            if (matchedContext.isNotBlank()) {
                appendLine("\n$matchedContext")
            }
            appendLine("\n$catalogDigest")
        }

        val scaledBitmap = bitmap?.let { scaleBitmapIfNeeded(it, maxDim = 1024) }
        val inputContent = content {
            if (scaledBitmap != null) {
                image(scaledBitmap)
            }
            text(promptText)
        }

        val accumulated = StringBuilder()
        suspend fun streamFromModel(useSearchGrounding: Boolean) {
            val targetModel = if (useSearchGrounding) scannerGroundedModel else generalModel
            val ch = Channel<String>(Channel.UNLIMITED)
            coroutineScope {
                val job = launch(Dispatchers.IO) {
                    try {
                        targetModel.generateContentStream(inputContent).collect { chunk ->
                            val t = chunk.text.orEmpty()
                            if (t.isNotEmpty()) ch.trySend(t)
                        }
                        ch.close()
                    } catch (t: Throwable) {
                        ch.close(t)
                    }
                }
                val first = withTimeoutOrNull(14_000L) { ch.receiveCatching() }
                if (first != null && first.isSuccess) {
                    val firstText = first.getOrNull().orEmpty()
                    if (firstText.isNotEmpty()) {
                        accumulated.append(firstText)
                        emit(accumulated.toString())
                    }
                    withTimeout(30_000L) {
                        while (true) {
                            val next = ch.receiveCatching()
                            if (next.isClosed) {
                                next.exceptionOrNull()?.let { throw it }
                                break
                            }
                            val nextText = next.getOrNull().orEmpty()
                            if (nextText.isNotEmpty()) {
                                accumulated.append(nextText)
                                emit(accumulated.toString())
                            }
                        }
                    }
                } else {
                    val err = first?.exceptionOrNull()
                    job.cancel()
                    ch.close()
                    if (err is CancellationException) throw err
                    if (err != null && com.example.data.firebase.isAppCheckTokenError(err.localizedMessage.orEmpty())) {
                        throw err
                    }
                    val unary = withTimeout(14_000L) { targetModel.generateContent(inputContent) }
                    val txt = unary.text.orEmpty()
                    if (txt.isNotBlank()) {
                        accumulated.append(txt)
                        emit(txt)
                    } else if (err != null) {
                        throw err
                    }
                }
            }
        }

        try {
            streamFromModel(useSearchGrounding = true)
        } catch (e: CancellationException) {
            throw e
        } catch (groundedError: Exception) {
            if (accumulated.isNotBlank()) throw groundedError
            val msg = groundedError.localizedMessage.orEmpty()
            if (com.example.data.firebase.isAppCheckTokenError(msg)) throw groundedError
            Log.w(TAG, "Grounded search model unavailable, falling back to standard multimodal model: $msg")
            streamFromModel(useSearchGrounding = false)
        }

        if (accumulated.isBlank()) {
            throw IllegalStateException("AI model returned an empty diagnosis.")
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun scanFieldDocument(
        bitmap: Bitmap,
        baseLat: Double,
        baseLon: Double,
        language: AppLanguage,
    ): DocumentScanResult {
        val strings = when (language) {
            AppLanguage.AZ -> StringsAz
            AppLanguage.EN -> StringsEn
            AppLanguage.RU -> StringsRu
        }
        // 1. Run bundled 100% offline ML Kit OCR table parser first
        val offlineResult = OfflineDocumentScanner.scanBitmap(bitmap, language)
        if (offlineResult != null && offlineResult.coordinates.size >= 3) {
            return offlineResult
        }

        val langName = when (language) {
            AppLanguage.AZ -> "Azerbaijani"
            AppLanguage.EN -> "English"
            AppLanguage.RU -> "Russian"
        }
        val scaled = scaleBitmapIfNeeded(bitmap, maxDim = 1600)
        val prompt = """
            Inspect this agricultural land parcel / cadastral extract document image (note: the image may be rotated 90 degrees).
            Extract:
            - areaHa: the land area in hectares if readable (convert sot/m2 to hectares if needed, e.g. 'Sahəsi 1.6069', otherwise null).
            - summary: a short 1-2 sentence summary in $langName of what is shown on the document.
            - rawPairs: if the document has a coordinate table with columns X and Y, extract every corner point's X and Y string values in EXACT sequential order (1, 2, 3...). Do not include point numbers (1, 2, 3) or segment distances ('Məsafə').
            - coordinates: any WGS84 lat/lon boundary corner points if explicitly printed on the document.
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                documentModel.generateContent(
                    content {
                        image(scaled)
                        text(prompt)
                    }
                )
            }
            val jsonText = response.text ?: throw IllegalStateException("Empty document scan response")
            val json = JSONObject(jsonText)
            val areaHa = if (json.has("areaHa") && !json.isNull("areaHa")) {
                json.optDouble("areaHa").takeIf { !it.isNaN() && it > 0 }
            } else {
                null
            }
            val summary = json.optString("summary", "")

            val rawPairs = mutableListOf<Pair<String, String>>()
            val pairsArr = json.optJSONArray("rawPairs")
            if (pairsArr != null) {
                for (i in 0 until pairsArr.length()) {
                    val item = pairsArr.optJSONObject(i) ?: continue
                    val x = item.optString("x", "").trim()
                    val y = item.optString("y", "").trim()
                    if (x.isNotBlank() && y.isNotBlank()) {
                        rawPairs.add(x to y)
                    }
                }
            }
            val classifiedFromPairs = CadastralDocumentParser.classifyPairs(rawPairs, areaHa)
            if (classifiedFromPairs != null && classifiedFromPairs.polygon.size >= 3) {
                val baseDoc = OfflineDocumentScanner.toDocumentScanResult(classifiedFromPairs, language)
                return baseDoc.copy(
                    summary = if (summary.isNotBlank()) "$summary\n${baseDoc.summary}" else baseDoc.summary,
                )
            }

            val coordsList = mutableListOf<Pair<Double, Double>>()
            val arr = json.optJSONArray("coordinates")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val lat = item.optDouble("lat", Double.NaN)
                    val lon = item.optDouble("lon", Double.NaN)
                    if (!lat.isNaN() && !lon.isNaN() && kotlin.math.abs(lat) <= 90.0 && kotlin.math.abs(lon) <= 180.0) {
                        coordsList.add(lat to lon)
                    }
                }
            }
            val latLonText = coordsList.joinToString("\n") { (lat, lon) ->
                String.format(java.util.Locale.US, "%.6f, %.6f", lat, lon)
            }
            val geoRawPairs = coordsList.map { (lat, lon) ->
                String.format(java.util.Locale.US, "%.6f", lat) to String.format(java.util.Locale.US, "%.6f", lon)
            }
            DocumentScanResult(
                areaHa = areaHa,
                summary = summary.ifBlank { strings.docScanNoCoordsMsg },
                coordinates = coordsList,
                coordTarget = DocCoordTarget.LAT_LON,
                utmZone = 38,
                utmLinesText = "",
                latLonLinesText = latLonText,
                rawPairs = geoRawPairs,
            )
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Document scan timed out, returning offline result", e)
            DocumentScanResult(areaHa = null, summary = strings.docScanNoCoordsMsg, coordinates = emptyList())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Document scan AI fallback failed, returning offline status", e)
            DocumentScanResult(areaHa = null, summary = strings.docScanNoCoordsMsg, coordinates = emptyList())
        }
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val ratio = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
        val nw = (w * ratio).toInt().coerceAtLeast(1)
        val nh = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, nw, nh, true)
    }

    private companion object {
        const val TAG = "FirebaseAiRepository"
    }
}
