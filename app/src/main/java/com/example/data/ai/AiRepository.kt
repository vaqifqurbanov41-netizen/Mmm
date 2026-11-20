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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class ChatHistoryTurn(
    val isUser: Boolean,
    val text: String,
)

data class DocumentScanResult(
    val areaHa: Double?,
    val summary: String,
    val coordinates: List<Pair<Double, Double>>,
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
        optionalProperties = listOf("areaHa", "coordinates")
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
        withTimeout(60_000L) {
            generalModel.generateContentStream(prompt).collect { chunk ->
                val chunkText = chunk.text.orEmpty()
                if (chunkText.isNotEmpty()) {
                    accumulated.append(chunkText)
                    emit(accumulated.toString())
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("AI model returned an empty response.")
        }
    }

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
            withTimeout(60_000L) {
                targetModel.generateContentStream(inputContent).collect { chunk ->
                    val chunkText = chunk.text.orEmpty()
                    if (chunkText.isNotEmpty()) {
                        accumulated.append(chunkText)
                        emit(accumulated.toString())
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
    }

    override suspend fun scanFieldDocument(
        bitmap: Bitmap,
        baseLat: Double,
        baseLon: Double,
        language: AppLanguage,
    ): DocumentScanResult {
        val langName = when (language) {
            AppLanguage.AZ -> "Azerbaijani"
            AppLanguage.EN -> "English"
            AppLanguage.RU -> "Russian"
        }
        val scaled = scaleBitmapIfNeeded(bitmap, maxDim = 1280)
        val prompt = """
            Inspect this agricultural land parcel / cadastral extract document image.
            Extract:
            - areaHa: the land area in hectares if readable (convert sot/m2 to hectares if needed, otherwise null).
            - summary: a short 1-2 sentence summary in $langName of what is shown on the document.
            - coordinates: any WGS84 lat/lon boundary corner points if explicitly printed on the document; otherwise return an empty array.
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
            val areaHa = if (json.has("areaHa") && !json.isNull("areaHa")) json.optDouble("areaHa") else null
            val summary = json.optString("summary", "")
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
            DocumentScanResult(
                areaHa = if (areaHa != null && !areaHa.isNaN() && areaHa > 0) areaHa else null,
                summary = summary,
                coordinates = coordsList,
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Document scan timed out", e)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Document scan failed", e)
            throw e
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
