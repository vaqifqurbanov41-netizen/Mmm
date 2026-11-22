package com.example.data.ai

import android.graphics.Bitmap
import android.graphics.Matrix
import com.example.domain.CadastralDocumentParser
import com.example.domain.DocCoordTarget
import com.example.domain.OcrToken
import com.example.domain.ParsedCadastralDocument
import com.example.domain.ScoredCadastralParse
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
import kotlin.math.abs

object OfflineDocumentScanner {

    suspend fun scanBitmap(bitmap: Bitmap, language: AppLanguage): DocumentScanResult? {
        val scaled = scaleIfTooLarge(bitmap, maxDim = 2200)
        // Evaluate ALL 4 upright physical rotations so rotated documents never stop early on sideways hits
        val rotations = listOf(0, 270, 90, 180)
        var bestScored: ScoredCadastralParse? = null
        var bestRawFallback: ScoredCadastralParse? = null

        for (rotation in rotations) {
            val oriented = rotateBitmap(scaled, rotation)
            try {
                val recognized = recognizeText(oriented, 0) ?: continue
                val fullText = recognized.text
                val tokens = extractTokens(recognized)
                val scored = CadastralDocumentParser.parseSpatialTokensScored(tokens, fullText)
                if (scored != null && scored.document.polygon.size >= 3) {
                    if (bestScored == null || scored.score > bestScored.score) {
                        bestScored = scored
                    }
                } else {
                    val rawDoc = CadastralDocumentParser.parseRawText(fullText)
                    if (rawDoc != null && rawDoc.polygon.size >= 3) {
                        val rawScore = rawDoc.polygon.size * 30.0
                        if (bestRawFallback == null || rawScore > bestRawFallback.score) {
                            bestRawFallback = ScoredCadastralParse(rawDoc, rawScore)
                        }
                    }
                }
            } finally {
                if (oriented !== scaled && !oriented.isRecycled) {
                    oriented.recycle()
                }
            }
        }
        if (scaled !== bitmap && !scaled.isRecycled) {
            scaled.recycle()
        }

        val winner = bestScored?.document ?: bestRawFallback?.document
        if (winner != null && winner.polygon.size >= 3) {
            return toDocumentScanResult(winner, language)
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
        val pairs = if (parsed.rawPairs.isNotEmpty()) {
            parsed.rawPairs
        } else {
            parsed.points.map { it.rawX to it.rawY }
        }
        return DocumentScanResult(
            areaHa = parsed.areaHa,
            summary = "$countLine\n$modeNote",
            coordinates = parsed.polygon.map { it.lat to it.lon },
            coordTarget = parsed.target,
            utmZone = parsed.utmZone,
            utmLinesText = parsed.utmLinesText,
            latLonLinesText = parsed.latLonLinesText,
            rawPairs = pairs,
        )
    }

    private fun extractTokens(result: Text): List<OcrToken> {
        val tokens = mutableListOf<OcrToken>()
        for (block in result.textBlocks) {
            for (line in block.lines) {
                val elems = line.elements.filter { it.boundingBox != null && it.text.isNotBlank() }
                if (elems.isEmpty()) {
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
                    continue
                }
                val sortedElems = elems.sortedBy { it.boundingBox!!.left }
                val lineHeight = (line.boundingBox?.height()?.toFloat()
                    ?: sortedElems.mapNotNull { it.boundingBox?.height()?.toFloat() }.average().toFloat())
                    .coerceAtLeast(12f)

                var groupText = StringBuilder(sortedElems.first().text.trim())
                var groupLeft = sortedElems.first().boundingBox!!.left
                var groupTop = sortedElems.first().boundingBox!!.top
                var groupRight = sortedElems.first().boundingBox!!.right
                var groupBottom = sortedElems.first().boundingBox!!.bottom

                fun flushGroup() {
                    val txt = groupText.toString().trim()
                    if (txt.isNotEmpty()) {
                        tokens.add(
                            OcrToken(
                                text = txt,
                                centerX = (groupLeft + groupRight) / 2f,
                                centerY = (groupTop + groupBottom) / 2f,
                                height = (groupBottom - groupTop).toFloat().coerceAtLeast(12f),
                            )
                        )
                    }
                }

                for (i in 1 until sortedElems.size) {
                    val el = sortedElems[i]
                    val box = el.boundingBox!!
                    val gap = (box.left - groupRight).toFloat()
                    val curStr = groupText.toString()
                    val nextStr = el.text.trim()
                    val glueDecimal = curStr.endsWith(".") || curStr.endsWith(",") ||
                        nextStr.startsWith(".") || nextStr.startsWith(",")
                    val sameCellThreshold = if (glueDecimal) maxOf(lineHeight * 1.4f, 24f) else maxOf(lineHeight * 0.55f, 10f)
                    if (gap <= sameCellThreshold && abs(box.exactCenterY() - (groupTop + groupBottom) / 2f) <= lineHeight * 1.1f) {
                        if (!glueDecimal && gap > lineHeight * 0.12f) {
                            groupText.append(' ')
                        }
                        groupText.append(nextStr)
                        groupLeft = minOf(groupLeft, box.left)
                        groupTop = minOf(groupTop, box.top)
                        groupRight = maxOf(groupRight, box.right)
                        groupBottom = maxOf(groupBottom, box.bottom)
                    } else {
                        flushGroup()
                        groupText = StringBuilder(nextStr)
                        groupLeft = box.left
                        groupTop = box.top
                        groupRight = box.right
                        groupBottom = box.bottom
                    }
                }
                flushGroup()
            }
        }
        return tokens
    }

    private fun rotateBitmap(source: Bitmap, degrees: Int): Bitmap {
        val norm = ((degrees % 360) + 360) % 360
        if (norm == 0) return source
        val matrix = Matrix().apply { postRotate(norm.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
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
