package com.example.data.ai

import android.graphics.Bitmap
import com.example.domain.CadastralDocumentParser
import com.example.domain.DocCoordTarget
import com.example.domain.OcrToken
import com.example.domain.ParsedCadastralDocument
import com.example.i18n.AppLanguage
import com.example.i18n.StringsAz
import com.example.i18n.StringsEn
import com.example.i18n.StringsRu
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

object OfflineDocumentScanner {

    suspend fun scanBitmap(bitmap: Bitmap, language: AppLanguage): DocumentScanResult? {
        val scaled = scaleIfTooLarge(bitmap, maxDim = 2000)
        val rotations = listOf(0, 90, 270, 180)
        var bestTextFallback = ""
        var bestParsed: ParsedCadastralDocument? = null

        for (rotation in rotations) {
            val recognized = recognizeText(scaled, rotation) ?: continue
            val fullText = recognized.text
            if (fullText.length > bestTextFallback.length) {
                bestTextFallback = fullText
            }

            val tokens = extractTokens(recognized)
            val parsed = CadastralDocumentParser.parseSpatialTokens(tokens, fullText)
            if (parsed != null && parsed.polygon.size >= 3) {
                if (bestParsed == null || parsed.polygon.size > bestParsed.polygon.size) {
                    bestParsed = parsed
                }
                if (parsed.polygon.size >= 4) {
                    break
                }
            }
        }

        val finalParsed = bestParsed ?: CadastralDocumentParser.parseRawText(bestTextFallback)
        if (finalParsed != null && finalParsed.polygon.size >= 3) {
            return toDocumentScanResult(finalParsed, language)
        }
        return null
    }

    fun toDocumentScanResult(
        parsed: ParsedCadastralDocument,
        language: AppLanguage,
    ): DocumentScanResult {
        val strings = when (language) {
            AppLanguage.AZ -> StringsAz
            AppLanguage.EN -> StringsEn
            AppLanguage.RU -> StringsRu
        }
        val countLine = String.format(Locale.getDefault(), strings.docScanFoundPointsFormat, parsed.polygon.size)
        val modeNote = when (parsed.target) {
            DocCoordTarget.UTM_38T -> strings.docScanUtm38Applied
            DocCoordTarget.LAT_LON -> strings.docScanLatLonApplied
        }
        return DocumentScanResult(
            areaHa = parsed.areaHa,
            summary = "$countLine\n$modeNote",
            coordinates = parsed.polygon.map { it.lat to it.lon },
            coordTarget = parsed.target,
            utmZone = parsed.utmZone,
            utmLinesText = parsed.utmLinesText,
            latLonLinesText = parsed.latLonLinesText,
        )
    }

    private fun extractTokens(result: Text): List<OcrToken> {
        val tokens = mutableListOf<OcrToken>()
        for (block in result.textBlocks) {
            for (line in block.lines) {
                val elems = line.elements
                if (elems.size <= 1) {
                    val box = line.boundingBox
                    if (box != null && line.text.isNotBlank()) {
                        tokens.add(
                            OcrToken(
                                text = line.text,
                                centerX = box.exactCenterX(),
                                centerY = box.exactCenterY(),
                                height = box.height().toFloat().coerceAtLeast(12f),
                            )
                        )
                    }
                } else {
                    for (el in elems) {
                        val box = el.boundingBox ?: continue
                        if (el.text.isBlank()) continue
                        tokens.add(
                            OcrToken(
                                text = el.text,
                                centerX = box.exactCenterX(),
                                centerY = box.exactCenterY(),
                                height = box.height().toFloat().coerceAtLeast(12f),
                            )
                        )
                    }
                }
            }
        }
        return tokens
    }

    private suspend fun recognizeText(bitmap: Bitmap, rotationDegrees: Int): Text? {
        return runCatching {
            val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val input = InputImage.fromBitmap(bitmap, rotationDegrees)
            suspendCancellableCoroutine<Text?> { cont ->
                client.process(input)
                    .addOnSuccessListener { result ->
                        if (cont.isActive) cont.resume(result)
                    }
                    .addOnFailureListener {
                        if (cont.isActive) cont.resume(null)
                    }
            }
        }.getOrNull()
    }

    private fun scaleIfTooLarge(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val ratio = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
        val nw = (w * ratio).toInt().coerceAtLeast(1)
        val nh = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, nw, nh, true)
    }
}
