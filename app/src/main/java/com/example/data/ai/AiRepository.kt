package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.i18n.AppLanguage
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeout
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
        farmContextSummary: String = "",
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

    private val agronomistSystemInstruction by lazy {
        content {
            text(
                """
                You are AqroMəsləhət AI Agronomist, a senior agricultural advisor specializing in Azerbaijan and regional farming (grains, vegetables, cotton, orchards, vineyards, alfalfa, hazelnut, livestock feed, NPK fertilization, drip irrigation, EKTIS subsidies, and Aqrosığorta).
                Guidelines:
                - Always respond strictly in the language requested by the prompt (Azerbaijani, English, or Russian).
                - Give practical, field-ready, structured recommendations (short paragraphs or bullet points, clear dosages/timing, safe pre-harvest intervals).
                - Never invent non-existent chemical active ingredients; prioritize approved active ingredients ("təsiredici ana maddə") and safe PPE/quarantine practices.
                - When farmer field data or weather warnings are provided, tailor your advice directly to those crops, areas, and conditions.
                """.trimIndent()
            )
        }
    }

    private val chatGenerationConfig by lazy {
        generationConfig {
            temperature = 0.45f
            topP = 0.9f
        }
    }

    private val primaryChatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = chatGenerationConfig,
            systemInstruction = agronomistSystemInstruction,
        )
    }

    private val fallbackChatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = chatGenerationConfig,
            systemInstruction = agronomistSystemInstruction,
        )
    }

    private val generalModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
    }

    private val fallbackMultimodalModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.5-flash-lite")
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
        farmContextSummary: String,
    ): Flow<String> = flow {
        val langInstruction = when (language) {
            AppLanguage.AZ -> "Respond strictly in natural, professional Azerbaijani (Azərbaycan dili)."
            AppLanguage.EN -> "Respond strictly in clear, professional English."
            AppLanguage.RU -> "Respond strictly in natural, professional Russian (Русский язык)."
        }

        val localCatalogContext = OfflineAgronomistAdvisor.buildPromptContext(question, language)
        val turnPrompt = buildString {
            appendLine("[Instructions]")
            appendLine(langInstruction)
            if (farmerName.isNotBlank()) appendLine("Farmer name: $farmerName.")
            if (farmerRegion.isNotBlank()) appendLine("Farmer region: $farmerRegion.")
            if (farmContextSummary.isNotBlank()) {
                appendLine("Farmer fields & weather context:")
                appendLine(farmContextSummary)
            }
            if (localCatalogContext.isNotBlank()) {
                appendLine("Reference context:")
                appendLine(localCatalogContext)
            }
            appendLine()
            appendLine("[Farmer Question]")
            append(question.trim())
        }

        val chatHistory = buildNormalizedChatHistory(history.takeLast(10))

        suspend fun tryChatModel(model: GenerativeModel): String {
            val accumulated = StringBuilder()
            withTimeout(60_000L) {
                val chat = model.startChat(history = chatHistory)
                chat.sendMessageStream(turnPrompt).collect { chunk ->
                    val chunkText = chunk.text.orEmpty()
                    if (chunkText.isNotEmpty()) {
                        accumulated.append(chunkText)
                        emit(accumulated.toString())
                    }
                }
                if (accumulated.isBlank()) {
                    val unary = model.startChat(history = chatHistory).sendMessage(turnPrompt)
                    val unaryText = unary.text.orEmpty()
                    if (unaryText.isNotBlank()) {
                        accumulated.append(unaryText)
                        emit(unaryText)
                    }
                }
            }
            return accumulated.toString()
        }

        var responseText = ""
        try {
            responseText = tryChatModel(primaryChatModel)
        } catch (e: CancellationException) {
            throw e
        } catch (primaryErr: Exception) {
            val msg = primaryErr.localizedMessage ?: primaryErr.message.orEmpty()
            Log.e(TAG, "Primary chat model failed: $msg", primaryErr)
            if (com.example.data.firebase.isAppCheckTokenError(msg)) {
                throw primaryErr
            }
            // Fallback to ultra-fast Gemini 3.5 Flash-Lite
            try {
                responseText = tryChatModel(fallbackChatModel)
            } catch (e: CancellationException) {
                throw e
            } catch (fallbackErr: Exception) {
                Log.e(TAG, "Fallback chat model failed: ${fallbackErr.localizedMessage}", fallbackErr)
                throw fallbackErr
            }
        }

        if (responseText.isBlank()) {
            throw IllegalStateException("AI model returned an empty response.")
        }
    }.flowOn(Dispatchers.IO)

    private fun buildNormalizedChatHistory(turns: List<ChatHistoryTurn>): List<Content> {
        val cleaned = turns.filter { it.text.isNotBlank() }
        if (cleaned.isEmpty()) return emptyList()
        val result = mutableListOf<Content>()
        var expectUser = true
        for (turn in cleaned) {
            if (turn.isUser == expectUser) {
                result += content(if (turn.isUser) "user" else "model") { text(turn.text.trim()) }
                expectUser = !expectUser
            }
        }
        // startChat history must end with a "model" turn before sendMessageStream sends the next "user" message
        if (result.isNotEmpty() && expectUser.not()) {
            result.removeAt(result.lastIndex)
        }
        return result
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

        suspend fun runPlantModel(model: GenerativeModel): String {
            val accumulated = StringBuilder()
            withTimeout(60_000L) {
                model.generateContentStream(inputContent).collect { chunk ->
                    val t = chunk.text.orEmpty()
                    if (t.isNotEmpty()) {
                        accumulated.append(t)
                        emit(accumulated.toString())
                    }
                }
                if (accumulated.isBlank()) {
                    val unary = model.generateContent(inputContent)
                    val txt = unary.text.orEmpty()
                    if (txt.isNotBlank()) {
                        accumulated.append(txt)
                        emit(txt)
                    }
                }
            }
            return accumulated.toString()
        }

        val modelsToTry = listOf(scannerGroundedModel, generalModel, fallbackMultimodalModel)
        var finalText = ""
        var lastError: Throwable? = null
        for (model in modelsToTry) {
            try {
                finalText = runPlantModel(model)
                if (finalText.isNotBlank()) break
            } catch (e: CancellationException) {
                throw e
            } catch (err: Exception) {
                val msg = err.localizedMessage.orEmpty()
                Log.w(TAG, "Plant scanner model attempt failed: $msg")
                if (com.example.data.firebase.isAppCheckTokenError(msg)) throw err
                lastError = err
            }
        }

        if (finalText.isBlank()) {
            throw (lastError ?: IllegalStateException("AI model returned an empty diagnosis."))
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
