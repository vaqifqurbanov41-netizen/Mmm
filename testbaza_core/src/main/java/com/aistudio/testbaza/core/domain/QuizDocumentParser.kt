package com.aistudio.testbaza.core.domain

import android.content.Context
import android.util.Xml
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlin.math.abs
import org.xmlpull.v1.XmlPullParser

data class StyledLine(
    val text: String,
    val isBold: Boolean,
)

data class ParsedQuestion(
    val questionNumber: Int,
    val category: String,
    val isExam: Boolean,
    val questionText: String,
    val allOptions: List<String>,
    val correctText: String,
    val hasConfirmedKey: Boolean,
)

data class ParsedQuizResult(
    val title: String,
    val questions: List<ParsedQuestion>,
)

object QuizDocumentParser {

    private val KEY_REGEX = Regex("""(\d{1,4})\s*[\.\-\)\:]\s*([A-Ea-eА-Еа-е])(?=$|[\s,;.\)])""")
    private val QUESTION_START_REGEX = Regex("""^(?:Sual\s*)?(\d{1,4})\s*[\.\)\-\:\№]\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val OPTION_START_REGEX = Regex("""^(?:[\*\+✓✔•\-–]\s*)?([A-Ea-eА-Еа-е])\s*[\.\)\-\:]\s*(.+)$""")
    private val BULLET_OPT_REGEX = Regex("""^[\*\+✓✔•\-–]\s+(.+)$""")
    private val INLINE_OPTION_SPLIT_REGEX = Regex("""\s+(?=[A-Ea-eА-Еа-е][\.\)]\s+)""")
    private val PAGE_NUMBER_REGEX = Regex("""^[\-–]?\s*\d{1,3}\s*[\-–]?$""")
    private val STAR_PREFIX_REGEX = Regex("""^[\*★🎯]""")
    private val STAR_SUFFIX_REGEX = Regex("""[\*★🎯]$""")
    private val STAR_STRIP_REGEX = Regex("""^[\*★🎯]\s*|\s*[\*★🎯]$""")
    private val MARKER_START_REGEX = Regex("""^[\*\+✓✔]""")
    private val MARKER_END_REGEX = Regex("""[\*\+✓✔]+$""")
    private val BULLET_AND_MARKER_PREFIX_REGEX = Regex("""^[\*\+✓✔•\-–]\s*""")
    private val QUESTION_ENDING_REGEX = Regex("""[\?\:\.…]$""")

    private val LETTER_TO_INDEX = mapOf(
        "A" to 0, "B" to 1, "C" to 2, "D" to 3, "E" to 4,
        "А" to 0, "В" to 1, "С" to 2, "Д" to 3, "Е" to 4,
    )

    fun extractDocxLines(inputStream: InputStream): List<StyledLine> {
        val docXmlBytes = ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            var found: ByteArray? = null
            while (entry != null) {
                if (entry.name.equals("word/document.xml", ignoreCase = true)) {
                    found = zis.readBytes()
                    break
                }
                entry = zis.nextEntry
            }
            found
        } ?: return emptyList()

        return parseDocxDocumentXml(ByteArrayInputStream(docXmlBytes))
    }

    internal fun parseDocxDocumentXml(xmlStream: InputStream): List<StyledLine> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(xmlStream, "UTF-8")

        val lines = mutableListOf<StyledLine>()
        var inParagraph = false
        var inRun = false
        var inRunProps = false
        var inText = false
        var currentParagraphText = StringBuilder()
        var currentParagraphBoldChars = 0
        var currentRunText = StringBuilder()
        var currentRunBold = false

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> when (tagName) {
                    "w:p" -> {
                        inParagraph = true
                        currentParagraphText = StringBuilder()
                        currentParagraphBoldChars = 0
                    }
                    "w:r" -> if (inParagraph) {
                        inRun = true
                        currentRunText = StringBuilder()
                        currentRunBold = false
                    }
                    "w:rPr" -> if (inRun) {
                        inRunProps = true
                    }
                    "w:b" -> if (inRunProps) {
                        val valAttr = parser.getAttributeValue(null, "w:val")
                        if (valAttr != "0" && valAttr != "false") {
                            currentRunBold = true
                        }
                    }
                    "w:t" -> if (inRun) {
                        inText = true
                    }
                    "w:tab" -> if (inRun) {
                        currentRunText.append(' ')
                    }
                    "w:br" -> if (inRun) {
                        currentRunText.append(' ')
                    }
                }

                XmlPullParser.TEXT -> if (inText) {
                    currentRunText.append(parser.text ?: "")
                }

                XmlPullParser.END_TAG -> when (tagName) {
                    "w:t" -> inText = false
                    "w:rPr" -> inRunProps = false
                    "w:r" -> if (inRun) {
                        val txt = currentRunText.toString()
                        if (txt.isNotEmpty()) {
                            currentParagraphText.append(txt)
                            if (currentRunBold) {
                                currentParagraphBoldChars += txt.trim().length
                            }
                        }
                        inRun = false
                    }
                    "w:p" -> if (inParagraph) {
                        val cleanTxt = currentParagraphText.toString()
                            .replace('\u00A0', ' ')
                            .replace(Regex("""\s+"""), " ")
                            .trim()
                        if (cleanTxt.isNotEmpty()) {
                            val isBold = currentParagraphBoldChars > 0 &&
                                (currentParagraphBoldChars.toFloat() / cleanTxt.length.toFloat()) >= 0.45f
                            lines.add(StyledLine(text = cleanTxt, isBold = isBold))
                        }
                        inParagraph = false
                    }
                }
            }
            eventType = parser.next()
        }
        return lines
    }

    fun extractPdfLines(context: Context, inputStream: InputStream): List<StyledLine> {
        PDFBoxResourceLoader.init(context.applicationContext)
        return PDDocument.load(inputStream).use { document ->
            val stripper = BoldAwarePdfStripper()
            stripper.sortByPosition = true
            stripper.startPage = 1
            stripper.endPage = document.numberOfPages
            stripper.getText(document)
            stripper.collectedLines
        }
    }

    fun extractTxtLines(inputStream: InputStream): List<StyledLine> =
        inputStream.bufferedReader(Charsets.UTF_8).useLines { seq ->
            seq.map { StyledLine(text = it.trim(), isBold = false) }
                .filter { it.text.isNotEmpty() }
                .toList()
        }

    fun parseRawText(rawText: String, title: String, rule: AnswerRule): Result<ParsedQuizResult> {
        if (rawText.isBlank()) {
            return Result.failure(IllegalArgumentException("Zəhmət olmasa əvvəlcə test mətnini xanaya yapışdırın."))
        }
        val lines = rawText.lines().map { StyledLine(text = it.trim(), isBold = false) }
        return parseStructuredLines(lines, title.ifBlank { "Yapışdırılmış Test Bazası" }, rule)
    }

    fun parseStructuredLines(
        lines: List<StyledLine>,
        title: String,
        rule: AnswerRule,
    ): Result<ParsedQuizResult> {
        val cleanLines = lines.mapNotNull { line ->
            val cleaned = line.text.replace('\u00A0', ' ').trim()
            if (cleaned.isEmpty()) null else line.copy(text = cleaned)
        }
        if (cleanLines.size < 2) {
            return Result.failure(IllegalArgumentException("Faylda kifayət qədər mətn tapılmadı."))
        }

        val answerKeyMap = mutableMapOf<Int, String>()
        var keyStartIdx = cleanLines.size
        val scanStart = maxOf(0, cleanLines.size - 60)
        for (i in scanStart until cleanLines.size) {
            val matches = KEY_REGEX.findAll(cleanLines[i].text).toList()
            if (matches.size >= 3) {
                if (i < keyStartIdx) keyStartIdx = i
                for (m in matches) {
                    val qNum = m.groupValues[1].toIntOrNull()
                    val letter = m.groupValues[2].uppercase()
                    if (qNum != null) {
                        answerKeyMap[qNum] = letter
                    }
                }
            }
        }

        val contentLines = cleanLines.subList(0, keyStartIdx)
        val parsedQuestions = mutableListOf<ParsedQuestion>()
        var currentQ: MutableParsedQuestion? = null
        var autoIdCounter = 1

        fun finalizeQuestion(qObj: MutableParsedQuestion?) {
            if (qObj == null || qObj.rawOptions.size < 2) return
            var detectedIdx = 0
            var hasConfirmedKey = false

            val keyLetter = answerKeyMap[qObj.id]
            val mappedIdx = if (keyLetter != null) LETTER_TO_INDEX[keyLetter] else null
            if (mappedIdx != null && mappedIdx < qObj.rawOptions.size) {
                detectedIdx = mappedIdx
                hasConfirmedKey = true
            } else {
                when (rule) {
                    AnswerRule.BOLD -> {
                        val bIdx = qObj.rawOptions.indexOfFirst { it.isBold }
                        if (bIdx != -1) {
                            detectedIdx = bIdx
                            hasConfirmedKey = true
                        }
                    }
                    AnswerRule.MARKER -> {
                        val mIdx = qObj.rawOptions.indexOfFirst { it.hasMarker }
                        if (mIdx != -1) {
                            detectedIdx = mIdx
                            hasConfirmedKey = true
                        }
                    }
                    AnswerRule.AUTO -> {
                        val mIdx = qObj.rawOptions.indexOfFirst { it.hasMarker }
                        val boldCount = qObj.rawOptions.count { it.isBold }
                        val bIdx = qObj.rawOptions.indexOfFirst { it.isBold }
                        if (mIdx != -1) {
                            detectedIdx = mIdx
                            hasConfirmedKey = true
                        } else if (bIdx != -1 && boldCount == 1) {
                            detectedIdx = bIdx
                            hasConfirmedKey = true
                        }
                    }
                    AnswerRule.FIRST -> {
                        detectedIdx = 0
                        hasConfirmedKey = true
                    }
                }
            }

            val cleanOpts = qObj.rawOptions.map { it.text }
            val correctText = cleanOpts[detectedIdx]
            parsedQuestions.add(
                ParsedQuestion(
                    questionNumber = qObj.id,
                    category = title,
                    isExam = qObj.isExam,
                    questionText = qObj.qText,
                    allOptions = cleanOpts,
                    correctText = correctText,
                    hasConfirmedKey = hasConfirmedKey,
                )
            )
        }

        for (line in contentLines) {
            val txt = line.text
            val inlineOptParts = txt.split(INLINE_OPTION_SPLIT_REGEX)
            val qMatch = QUESTION_START_REGEX.matchEntire(txt)
            val optMatch = OPTION_START_REGEX.matchEntire(txt)

            if (qMatch != null && optMatch == null) {
                finalizeQuestion(currentQ)
                val qNum = qMatch.groupValues[1].toIntOrNull() ?: autoIdCounter++
                var qBody = qMatch.groupValues[2].trim()
                val isStarred = STAR_PREFIX_REGEX.containsMatchIn(qBody) || STAR_SUFFIX_REGEX.containsMatchIn(qBody)
                qBody = qBody.replace(STAR_STRIP_REGEX, "").trim()

                currentQ = MutableParsedQuestion(
                    id = qNum,
                    qText = qBody,
                    isExam = isStarred,
                )
            } else if (currentQ != null && optMatch != null) {
                if (inlineOptParts.size > 1) {
                    for (part in inlineOptParts) {
                        val subM = OPTION_START_REGEX.matchEntire(part.trim())
                        if (subM != null) {
                            val optBody = subM.groupValues[2].trim()
                            val hasM = MARKER_START_REGEX.containsMatchIn(part.trim()) ||
                                MARKER_END_REGEX.containsMatchIn(optBody)
                            val cleanT = optBody.replace(MARKER_END_REGEX, "").trim()
                            currentQ.rawOptions.add(RawOption(cleanT, line.isBold, hasM))
                        }
                    }
                } else {
                    val optBody = optMatch.groupValues[2].trim()
                    val hasM = MARKER_START_REGEX.containsMatchIn(txt) || MARKER_END_REGEX.containsMatchIn(optBody)
                    val cleanT = optBody.replace(MARKER_END_REGEX, "").trim()
                    currentQ.rawOptions.add(RawOption(cleanT, line.isBold, hasM))
                }
            } else if (currentQ != null && currentQ.rawOptions.isEmpty()) {
                val prevEndsQuestion = QUESTION_ENDING_REGEX.containsMatchIn(currentQ.qText)
                if (prevEndsQuestion && txt.length < 250) {
                    val hasM = MARKER_START_REGEX.containsMatchIn(txt) ||
                        txt.startsWith("•") ||
                        MARKER_END_REGEX.containsMatchIn(txt)
                    val cleanT = txt
                        .replace(BULLET_AND_MARKER_PREFIX_REGEX, "")
                        .replace(MARKER_END_REGEX, "")
                        .trim()
                    currentQ.rawOptions.add(RawOption(cleanT, line.isBold, hasM))
                } else {
                    currentQ.qText = "${currentQ.qText} $txt"
                }
            } else if (currentQ != null && currentQ.rawOptions.isNotEmpty() && currentQ.rawOptions.size < 5) {
                val bulletM = BULLET_OPT_REGEX.matchEntire(txt)
                val rawOptStr = bulletM?.groupValues?.get(1) ?: txt
                val hasM = MARKER_START_REGEX.containsMatchIn(txt) || MARKER_END_REGEX.containsMatchIn(rawOptStr)
                val cleanT = rawOptStr.replace(MARKER_END_REGEX, "").trim()
                currentQ.rawOptions.add(RawOption(cleanT, line.isBold, hasM))
            } else if (currentQ == null && (txt.endsWith("?") || txt.endsWith(":"))) {
                currentQ = MutableParsedQuestion(
                    id = autoIdCounter++,
                    qText = txt,
                    isExam = false,
                )
            }
        }

        finalizeQuestion(currentQ)

        if (parsedQuestions.isEmpty()) {
            val sample = cleanLines.take(4).joinToString(" | ") { it.text }.take(140)
            return Result.failure(
                IllegalArgumentException(
                    "Suallar tanınmadı. Oxunan başlanğıc: [$sample...]. Sualların '1. Sual?' və variantların 'A) ...' formatında olduğunu yoxlayın."
                )
            )
        }

        return Result.success(ParsedQuizResult(title = title, questions = parsedQuestions))
    }

    private data class RawOption(
        val text: String,
        val isBold: Boolean,
        val hasMarker: Boolean,
    )

    private class MutableParsedQuestion(
        val id: Int,
        var qText: String,
        val isExam: Boolean,
        val rawOptions: MutableList<RawOption> = mutableListOf(),
    )

    private class BoldAwarePdfStripper : PDFTextStripper() {
        val collectedLines = mutableListOf<StyledLine>()
        private val currentPageRows = mutableListOf<PdfRow>()

        override fun startPage(page: com.tom_roush.pdfbox.pdmodel.PDPage) {
            super.startPage(page)
            currentPageRows.clear()
        }

        override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
            if (textPositions.isNullOrEmpty()) return
            for (pos in textPositions) {
                val ch = pos.unicode ?: continue
                if (ch.isBlank()) continue
                val x = pos.xDirAdj
                val y = pos.yDirAdj
                val w = pos.widthDirAdj
                val fontName = pos.font?.name?.lowercase().orEmpty()
                val isBold = fontName.contains("bold") ||
                    fontName.contains("black") ||
                    fontName.contains("heavy") ||
                    fontName.contains("bd")

                val existingRow = currentPageRows.firstOrNull { abs(it.y - y) < 4.5f }
                if (existingRow != null) {
                    existingRow.items.add(PdfWordItem(x, ch, isBold, w))
                } else {
                    currentPageRows.add(PdfRow(y, mutableListOf(PdfWordItem(x, ch, isBold, w))))
                }
            }
        }

        override fun endPage(page: com.tom_roush.pdfbox.pdmodel.PDPage) {
            currentPageRows.sortBy { it.y }
            currentPageRows.forEachIndexed { rIdx, row ->
                row.items.sortBy { it.x }
                val sb = StringBuilder()
                var boldChars = 0
                var lastEndX: Float? = null
                for (it in row.items) {
                    if (lastEndX != null && (it.x - lastEndX) > 3f && !sb.endsWith(" ") && !it.str.startsWith(" ")) {
                        sb.append(' ')
                    }
                    sb.append(it.str)
                    if (it.isBold) {
                        boldChars += it.str.trim().length
                    }
                    lastEndX = it.x + it.width
                }
                val cleanTxt = sb.toString().replace(Regex("""\s+"""), " ").trim()
                if (cleanTxt.isNotEmpty()) {
                    val isPageFooter = PAGE_NUMBER_REGEX.matches(cleanTxt) &&
                        (rIdx == 0 || rIdx == currentPageRows.lastIndex)
                    if (!isPageFooter) {
                        val isBold = boldChars > 0 && (boldChars.toFloat() / cleanTxt.length.toFloat()) >= 0.45f
                        collectedLines.add(StyledLine(text = cleanTxt, isBold = isBold))
                    }
                }
            }
            super.endPage(page)
        }

        private data class PdfWordItem(
            val x: Float,
            val str: String,
            val isBold: Boolean,
            val width: Float,
        )

        private class PdfRow(
            val y: Float,
            val items: MutableList<PdfWordItem>,
        )
    }
}
