package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.i18n.AppLanguage
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
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
            appendLine("Provide practical, accurate, actionable guidance on crops, irrigation, NPK fertilization, pests, and plant diseases. When recommending crop protection, prioritize stating the active ingredient (təsiredici maddə). Keep responses concise, structured with bullet points, and safe.")
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
    ): Flow<String> = flow {
        val langInstruction = when (language) {
            AppLanguage.AZ -> "Respond strictly in Azerbaijani (Azərbaycan dili)."
            AppLanguage.EN -> "Respond strictly in English."
            AppLanguage.RU -> "Respond strictly in Russian (Русский язык)."
        }

        val localCatalogContext = if (note.isNotBlank()) {
            OfflineAgronomistAdvisor.buildPromptContext(note, language)
        } else ""
        val promptText = buildString {
            appendLine("You are an expert plant pathologist and agronomist.")
            appendLine(langInstruction)
            appendLine("Analyze the provided plant/leaf/soil image and farmer notes.")
            appendLine("Provide: 1) Likely condition or pest/deficiency, 2) Key visual causes, 3) Recommended active ingredient (təsiredici maddə) and safe treatment & prevention steps.")
            if (note.isNotBlank()) {
                appendLine("Farmer observation note: $note")
            }
            if (localCatalogContext.isNotBlank()) {
                appendLine()
                append(localCatalogContext)
            }
        }

        val scaledBitmap = bitmap?.let { scaleBitmapIfNeeded(it, maxDim = 1024) }
        val inputContent = content {
            if (scaledBitmap != null) {
                image(scaledBitmap)
            }
            text(promptText)
        }

        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            generalModel.generateContentStream(inputContent).collect { chunk ->
                val chunkText = chunk.text.orEmpty()
                if (chunkText.isNotEmpty()) {
                    accumulated.append(chunkText)
                    emit(accumulated.toString())
                }
            }
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
