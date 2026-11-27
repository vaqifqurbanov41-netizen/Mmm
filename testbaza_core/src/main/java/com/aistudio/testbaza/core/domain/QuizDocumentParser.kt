package com.aistudio.testbaza.core.domain

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs
import org.w3c.dom.Element
import org.w3c.dom.Node

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

    private val KEY_REGEX = Regex("""(\d{1,4})\s*[\.\-\)\:]\s*([A-Ea-eƏəА-Еа-е])(?=$|[\s,;.\)])""")
    private val SINGLE_KEY_LOOSE_REGEX = Regex("""^(\d{1,4})\s*[\.\-\)\:\s]\s*([A-Ea-eƏəА-Еа-е])[\.\)]?$""")
    private val KEY_SECTION_HEADER_REGEX = Regex(
        """^(?:doğru\s+)?(?:cavablar|cavab\s+açarı|test\s+açarı|açar|answers|answer\s+key|keys)\b.*$""",
        RegexOption.IGNORE_CASE,
    )
    private val QUESTION_START_REGEX = Regex("""^(?:Sual\s*|№\s*)?(\d{1,4})\s*[\.\)\-\:\№]\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val STANDALONE_QNUM_REGEX = Regex("""^(?:Sual\s*)?(\d{1,4})\s*[\.\)\:\№]$""", RegexOption.IGNORE_CASE)
    private val OPTION_START_REGEX = Regex("""^(?:[\*\+✓✔•\-–]\s*)?([A-Ea-eƏəА-Еа-е])\s*[\.\)\-\:]\s*(.+)$""")
    private val BULLET_OPT_REGEX = Regex("""^[\*\+✓✔•\-–]\s+(.+)$""")
    private val INLINE_OPTION_SPLIT_REGEX = Regex("""\s+(?=[A-Ea-eƏəА-Еа-е][\.\)]\s+)""")
    private val PAGE_NUMBER_REGEX = Regex("""^[\-–]?\s*\d{1,4}\s*[\-–]?$""")
    private val STAR_PREFIX_REGEX = Regex("""^[\*★🎯]""")
    private val STAR_SUFFIX_REGEX = Regex("""[\*★🎯]$""")
    private val STAR_STRIP_REGEX = Regex("""^[\*★🎯]\s*|\s*[\*★🎯]$""")
    private val MARKER_START_REGEX = Regex("""^[\*\+✓✔]""")
    private val MARKER_END_REGEX = Regex("""[\*\+✓✔]+$""")
    private val BULLET_AND_MARKER_PREFIX_REGEX = Regex("""^[\*\+✓✔•\-–]\s*""")
    private val QUESTION_ENDING_REGEX = Regex("""[\?\:\.…]$""")
    private val UNSAFE_XML_DECL_REGEX = Regex("""<!(?:DOCTYPE|ENTITY)\b""", RegexOption.IGNORE_CASE)

    private val LETTER_TO_INDEX = mapOf(
        "A" to 0, "B" to 1, "C" to 2, "D" to 3, "E" to 4, "Ə" to 4,
        "А" to 0, "В" to 1, "С" to 2, "Д" to 3, "Е" to 4,
    )

    fun extractDocxLines(inputStream: InputStream): List<StyledLine> {
        var docXmlBytes: ByteArray? = null
        var numberingXmlBytes: ByteArray? = null

        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    when {
                        entry.name.equals("word/document.xml", ignoreCase = true) -> {
                            docXmlBytes = zis.readBytes()
                        }
                        entry.name.equals("word/numbering.xml", ignoreCase = true) -> {
                            numberingXmlBytes = zis.readBytes()
                        }
                    }
                }
                if (docXmlBytes != null && numberingXmlBytes != null) break
                entry = zis.nextEntry
            }
        }

        val docBytes = docXmlBytes ?: return emptyList()
        val numberingModel = numberingXmlBytes?.let { bytes ->
            runCatching { parseDocxNumberingXml(ByteArrayInputStream(bytes)) }.getOrNull()
        }
        return parseDocxDocumentXml(ByteArrayInputStream(docBytes), numberingModel)
    }

    private fun createSafeDocumentBuilderFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        }

    private fun parseSafeXmlDocument(xmlStream: InputStream): org.w3c.dom.Document {
        val rawBytes = xmlStream.readBytes()
        val headerSample = String(rawBytes, 0, minOf(rawBytes.size, 4096), Charsets.UTF_8)
        if (UNSAFE_XML_DECL_REGEX.containsMatchIn(headerSample)) {
            throw SecurityException("Etibarsız XML sənədi (DOCTYPE/ENTITY icazəli deyil).")
        }
        val factory = createSafeDocumentBuilderFactory()
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(rawBytes))
    }

    internal fun parseDocxDocumentXml(
        xmlStream: InputStream,
        numberingModel: DocxNumberingModel? = null,
    ): List<StyledLine> {
        val doc = parseSafeXmlDocument(xmlStream)
        val paragraphs = doc.getElementsByTagName("w:p")
        val lines = mutableListOf<StyledLine>()
        val counters = mutableMapOf<Pair<String, String>, Int>()

        for (pIdx in 0 until paragraphs.length) {
            val pNode = paragraphs.item(pIdx) as? Element ?: continue
            val segments = mutableListOf<Pair<String, Int>>() // text + boldChars
            val currentText = StringBuilder()
            var currentBoldChars = 0

            fun flushSegment() {
                val cleaned = currentText.toString()
                    .replace('\u00A0', ' ')
                    .replace(Regex("""\s+"""), " ")
                    .trim()
                if (cleaned.isNotEmpty()) {
                    segments.add(cleaned to currentBoldChars)
                }
                currentText.clear()
                currentBoldChars = 0
            }

            fun visitParagraphChild(node: Node, parentIsDeleted: Boolean) {
                if (parentIsDeleted || node.nodeType != Node.ELEMENT_NODE) return
                val el = node as Element
                when (el.tagName) {
                    "w:del", "m:oMath", "m:oMathPara" -> return
                    "w:r" -> {
                        val isBold = isRunBold(el)
                        val rChildren = el.childNodes
                        for (cIdx in 0 until rChildren.length) {
                            val child = rChildren.item(cIdx)
                            if (child.nodeType != Node.ELEMENT_NODE) continue
                            val cEl = child as Element
                            when (cEl.tagName) {
                                "w:t" -> {
                                    val txt = cEl.textContent.orEmpty()
                                    if (txt.isNotEmpty()) {
                                        currentText.append(txt)
                                        if (isBold) {
                                            currentBoldChars += txt.trim().length
                                        }
                                    }
                                }
                                "w:tab" -> currentText.append(' ')
                                "w:br", "w:cr" -> flushSegment()
                            }
                        }
                    }
                    else -> {
                        val children = el.childNodes
                        for (cIdx in 0 until children.length) {
                            visitParagraphChild(children.item(cIdx), parentIsDeleted = false)
                        }
                    }
                }
            }

            val pChildren = pNode.childNodes
            for (cIdx in 0 until pChildren.length) {
                visitParagraphChild(pChildren.item(cIdx), parentIsDeleted = false)
            }
            flushSegment()

            if (segments.isEmpty()) continue

            val autoPrefix = numberingModel?.let { model ->
                extractParagraphNumberingPrefix(pNode, model, counters)
            }.orEmpty()

            segments.forEachIndexed { segIdx, (segText, boldChars) ->
                val finalText = if (segIdx == 0 && autoPrefix.isNotEmpty() && !startsWithQuestionOrOptionLabel(segText)) {
                    "$autoPrefix$segText"
                } else {
                    segText
                }
                val isBold = boldChars > 0 && (boldChars.toFloat() / maxOf(1, segText.length).toFloat()) >= 0.45f
                lines.add(StyledLine(text = finalText, isBold = isBold))
            }
        }
        return lines
    }

    private fun startsWithQuestionOrOptionLabel(text: String): Boolean =
        QUESTION_START_REGEX.matches(text) ||
            STANDALONE_QNUM_REGEX.matches(text) ||
            OPTION_START_REGEX.matches(text)

    private fun isRunBold(runEl: Element): Boolean {
        val children = runEl.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) continue
            val cEl = child as Element
            if (cEl.tagName == "w:rPr") {
                val rPrChildren = cEl.childNodes
                for (j in 0 until rPrChildren.length) {
                    val prop = rPrChildren.item(j)
                    if (prop.nodeType != Node.ELEMENT_NODE) continue
                    val pEl = prop as Element
                    if (pEl.tagName == "w:b" || pEl.tagName == "w:bCs") {
                        val valAttr = pEl.getAttribute("w:val").orEmpty()
                        if (valAttr != "0" && !valAttr.equals("false", ignoreCase = true)) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    internal data class DocxLevelDef(
        val numFmt: String,
        val lvlText: String,
        val start: Int,
    )

    internal data class DocxNumberingModel(
        val numToAbstract: Map<String, String>,
        val abstractLevels: Map<Pair<String, String>, DocxLevelDef>,
    )

    private fun parseDocxNumberingXml(xmlStream: InputStream): DocxNumberingModel {
        val doc = parseSafeXmlDocument(xmlStream)
        val numToAbstract = mutableMapOf<String, String>()
        val abstractLevels = mutableMapOf<Pair<String, String>, DocxLevelDef>()

        val nums = doc.getElementsByTagName("w:num")
        for (i in 0 until nums.length) {
            val numEl = nums.item(i) as? Element ?: continue
            val numId = numEl.getAttribute("w:numId")
            val absList = numEl.getElementsByTagName("w:abstractNumId")
            val absId = (absList.item(0) as? Element)?.getAttribute("w:val").orEmpty()
            if (numId.isNotBlank() && absId.isNotBlank()) {
                numToAbstract[numId] = absId
            }
        }

        val abstractNums = doc.getElementsByTagName("w:abstractNum")
        for (i in 0 until abstractNums.length) {
            val absEl = abstractNums.item(i) as? Element ?: continue
            val absId = absEl.getAttribute("w:abstractNumId")
            val lvls = absEl.getElementsByTagName("w:lvl")
            for (j in 0 until lvls.length) {
                val lvlEl = lvls.item(j) as? Element ?: continue
                val ilvl = lvlEl.getAttribute("w:ilvl").ifBlank { "0" }
                val numFmt = (lvlEl.getElementsByTagName("w:numFmt").item(0) as? Element)
                    ?.getAttribute("w:val").orEmpty()
                val lvlText = (lvlEl.getElementsByTagName("w:lvlText").item(0) as? Element)
                    ?.getAttribute("w:val").orEmpty()
                val start = (lvlEl.getElementsByTagName("w:start").item(0) as? Element)
                    ?.getAttribute("w:val")?.toIntOrNull() ?: 1
                if (absId.isNotBlank()) {
                    abstractLevels[absId to ilvl] = DocxLevelDef(numFmt, lvlText, start)
                }
            }
        }

        return DocxNumberingModel(numToAbstract, abstractLevels)
    }

    private fun extractParagraphNumberingPrefix(
        pNode: Element,
        model: DocxNumberingModel,
        counters: MutableMap<Pair<String, String>, Int>,
    ): String {
        val pPrList = pNode.getElementsByTagName("w:pPr")
        if (pPrList.length == 0) return ""
        val pPr = pPrList.item(0) as? Element ?: return ""
        val numPrList = pPr.getElementsByTagName("w:numPr")
        if (numPrList.length == 0) return ""
        val numPr = numPrList.item(0) as? Element ?: return ""
        val numId = (numPr.getElementsByTagName("w:numId").item(0) as? Element)
            ?.getAttribute("w:val").orEmpty()
        if (numId.isBlank() || numId == "0") return ""
        val ilvl = (numPr.getElementsByTagName("w:ilvl").item(0) as? Element)
            ?.getAttribute("w:val").orEmpty().ifBlank { "0" }

        val absId = model.numToAbstract[numId] ?: return ""
        val def = model.abstractLevels[absId to ilvl] ?: return ""
        val key = numId to ilvl
        val nextVal = (counters[key] ?: (def.start - 1)) + 1
        counters[key] = nextVal

        // Reset deeper levels for same numId
        val ilvlInt = ilvl.toIntOrNull() ?: 0
        counters.keys.filter { it.first == numId && (it.second.toIntOrNull() ?: 0) > ilvlInt }
            .forEach { counters.remove(it) }

        return when (def.numFmt) {
            "decimal" -> "$nextVal. "
            "upperLetter" -> {
                val idx = (nextVal - 1).coerceIn(0, 25)
                "${('A' + idx)}) "
            }
            "lowerLetter" -> {
                val idx = (nextVal - 1).coerceIn(0, 25)
                "${('a' + idx)}) "
            }
            "bullet" -> "• "
            else -> ""
        }
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
        val scanStart = maxOf(0, cleanLines.size / 2)
        var inExplicitKeySection = false

        for (i in scanStart until cleanLines.size) {
            val txt = cleanLines[i].text
            if (KEY_SECTION_HEADER_REGEX.matches(txt)) {
                inExplicitKeySection = true
                if (i < keyStartIdx) keyStartIdx = i
                continue
            }
            val matches = KEY_REGEX.findAll(txt).toList()
            if (matches.size >= 3) {
                if (i < keyStartIdx) keyStartIdx = i
                for (m in matches) {
                    val qNum = m.groupValues[1].toIntOrNull()
                    val letter = m.groupValues[2].uppercase()
                    if (qNum != null) {
                        answerKeyMap[qNum] = letter
                    }
                }
            } else if (inExplicitKeySection) {
                val looseM = SINGLE_KEY_LOOSE_REGEX.matchEntire(txt)
                if (looseM != null) {
                    val qNum = looseM.groupValues[1].toIntOrNull()
                    val letter = looseM.groupValues[2].uppercase()
                    if (qNum != null) {
                        answerKeyMap[qNum] = letter
                    }
                } else if (matches.isNotEmpty()) {
                    for (m in matches) {
                        val qNum = m.groupValues[1].toIntOrNull()
                        val letter = m.groupValues[2].uppercase()
                        if (qNum != null) {
                            answerKeyMap[qNum] = letter
                        }
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

            val cleanOpts = qObj.rawOptions.map { it.text.trim() }
            val correctText = cleanOpts[detectedIdx]
            parsedQuestions.add(
                ParsedQuestion(
                    questionNumber = qObj.id,
                    category = title,
                    isExam = qObj.isExam,
                    questionText = qObj.qText.trim(),
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
            val standaloneNumMatch = STANDALONE_QNUM_REGEX.matchEntire(txt)
            val optMatch = OPTION_START_REGEX.matchEntire(txt)

            if (qMatch != null && optMatch == null) {
                finalizeQuestion(currentQ)
                val qNum = qMatch.groupValues[1].toIntOrNull() ?: autoIdCounter++
                autoIdCounter = maxOf(autoIdCounter, qNum + 1)
                var qBody = qMatch.groupValues[2].trim()
                val qInlineParts = qBody.split(INLINE_OPTION_SPLIT_REGEX)
                val extractedInlineOpts = mutableListOf<RawOption>()
                if (qInlineParts.size >= 2 && OPTION_START_REGEX.matches(qInlineParts[1].trim())) {
                    qBody = qInlineParts[0].trim()
                    for (part in qInlineParts.drop(1)) {
                        val subM = OPTION_START_REGEX.matchEntire(part.trim())
                        if (subM != null) {
                            val optBody = subM.groupValues[2].trim()
                            val hasM = MARKER_START_REGEX.containsMatchIn(part.trim()) ||
                                MARKER_END_REGEX.containsMatchIn(optBody)
                            val cleanT = optBody.replace(MARKER_END_REGEX, "").trim()
                            extractedInlineOpts.add(RawOption(cleanT, line.isBold, hasM))
                        }
                    }
                }
                val isStarred = STAR_PREFIX_REGEX.containsMatchIn(qBody) || STAR_SUFFIX_REGEX.containsMatchIn(qBody)
                qBody = qBody.replace(STAR_STRIP_REGEX, "").trim()

                currentQ = MutableParsedQuestion(
                    id = qNum,
                    qText = qBody,
                    isExam = isStarred,
                    usesExplicitOptionLabels = extractedInlineOpts.isNotEmpty(),
                    rawOptions = extractedInlineOpts,
                )
            } else if (standaloneNumMatch != null && optMatch == null) {
                finalizeQuestion(currentQ)
                val qNum = standaloneNumMatch.groupValues[1].toIntOrNull() ?: autoIdCounter++
                autoIdCounter = maxOf(autoIdCounter, qNum + 1)
                currentQ = MutableParsedQuestion(
                    id = qNum,
                    qText = "",
                    isExam = false,
                )
            } else if (currentQ != null && optMatch != null) {
                currentQ.usesExplicitOptionLabels = true
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
                if (currentQ.qText.isBlank()) {
                    val isStarred = STAR_PREFIX_REGEX.containsMatchIn(txt) || STAR_SUFFIX_REGEX.containsMatchIn(txt)
                    currentQ.qText = txt.replace(STAR_STRIP_REGEX, "").trim()
                    if (isStarred) currentQ.isExam = true
                } else {
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
                }
            } else if (currentQ != null && currentQ.rawOptions.isNotEmpty()) {
                val bulletM = BULLET_OPT_REGEX.matchEntire(txt)
                if (currentQ.usesExplicitOptionLabels && bulletM == null) {
                    // Wrapped continuation line of the previous labeled option
                    val lastOpt = currentQ.rawOptions.last()
                    val hasM = lastOpt.hasMarker || MARKER_END_REGEX.containsMatchIn(txt)
                    val cleanAppend = txt.replace(MARKER_END_REGEX, "").trim()
                    lastOpt.text = "${lastOpt.text} $cleanAppend".trim()
                    lastOpt.hasMarker = hasM
                } else if (currentQ.rawOptions.size < 5) {
                    val rawOptStr = bulletM?.groupValues?.get(1) ?: txt
                    val hasM = MARKER_START_REGEX.containsMatchIn(txt) || MARKER_END_REGEX.containsMatchIn(rawOptStr)
                    val cleanT = rawOptStr.replace(MARKER_END_REGEX, "").trim()
                    currentQ.rawOptions.add(RawOption(cleanT, line.isBold, hasM))
                }
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
        var text: String,
        val isBold: Boolean,
        var hasMarker: Boolean,
    )

    private class MutableParsedQuestion(
        val id: Int,
        var qText: String,
        var isExam: Boolean,
        var usesExplicitOptionLabels: Boolean = false,
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
                val x = pos.xDirAdj
                val y = pos.yDirAdj
                val w = pos.widthDirAdj
                val fontSize = pos.fontSizeInPt.takeIf { it > 0f } ?: 10f
                val fontName = pos.font?.name?.lowercase().orEmpty()
                val descriptor = pos.font?.fontDescriptor
                val isBold = fontName.contains("bold") ||
                    fontName.contains("black") ||
                    fontName.contains("heavy") ||
                    fontName.contains("semibold") ||
                    fontName.contains("demibold") ||
                    fontName.contains("bd") ||
                    (descriptor?.fontWeight ?: 0f) >= 600f ||
                    descriptor?.isForceBold == true

                val existingRow = currentPageRows.firstOrNull { abs(it.y - y) < maxOf(3.5f, fontSize * 0.45f) }
                if (existingRow != null) {
                    existingRow.items.add(PdfWordItem(x, ch, isBold, w, fontSize))
                } else {
                    currentPageRows.add(PdfRow(y, mutableListOf(PdfWordItem(x, ch, isBold, w, fontSize))))
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
                    if (it.str.isBlank()) {
                        if (sb.isNotEmpty() && !sb.endsWith(" ")) {
                            sb.append(' ')
                        }
                        lastEndX = it.x + it.width
                        continue
                    }
                    val spaceThreshold = maxOf(1.5f, it.fontSize * 0.18f)
                    if (lastEndX != null && (it.x - lastEndX) > spaceThreshold && !sb.endsWith(" ") && !it.str.startsWith(" ")) {
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
            val fontSize: Float,
        )

        private class PdfRow(
            val y: Float,
            val items: MutableList<PdfWordItem>,
        )
    }
}
