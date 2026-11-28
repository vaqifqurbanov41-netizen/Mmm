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
    private val TOC_DOT_LEADER_REGEX = Regex("""\.{4,}\s*\d*\s*$""")
    private val KEY_SECTION_HEADER_REGEX = Regex(
        """^(?:test\s+suallarının\s+cavabları|sualların\s+cavabları|(?:doğru\s+)?(?:cavablar|cavab\s+açarı|test\s+açarı|açar|answers|answer\s+key|keys))(?=$|[\s:\.\-–—]).*$""",
        RegexOption.IGNORE_CASE,
    )
    private val BACKMATTER_HEADER_REGEX = Regex(
        """^(?:ədəbiyyat|istifadə\s+olunmuş\s+ədəbiyyat|qeydlər|mündəricat|bibliography|references|notes)\s*$""",
        RegexOption.IGNORE_CASE,
    )
    private val DIFFICULTY_LABEL_REGEX = Regex(
        """^(?:asan|orta|çətin|asan\s+suallar|orta\s+suallar|çətin\s+suallar)\s*$""",
        RegexOption.IGNORE_CASE,
    )
    private val QUESTION_START_REGEX = Regex(
        """^(?:Sual\s*|№\s*)?(\d{1,4})(?:\s*[\.\)\:\№](?:\s+|(?=\p{Lu}))|\s+[-–]\s+)(.+)$""",
        RegexOption.IGNORE_CASE,
    )
    private val SUBITEM_LINE_PAIR_REGEX = Regex("""^\s*(\d{1,2})\s*[\.\)]\s+.+\s+(\d{1,2})\s*[\.\)]\s+.+$""")
    private val SUBITEM_SINGLE_TOKEN_REGEX = Regex("""\b(\d{1,2})\s*[\.\)]\s+""")
    private val STANDALONE_QNUM_REGEX = Regex("""^(?:Sual\s*)?(\d{1,4})\s*[\.\)\:\№]$""", RegexOption.IGNORE_CASE)
    private val OPTION_START_REGEX = Regex("""^(?:[\*\+✓✔•\-–]\s*)?([A-Ea-eƏəА-Еа-е])\s*[\.\)\-\:]\s*(.+)$""")
    private val BULLET_OPT_REGEX = Regex("""^[\*\+✓✔•\-–]\s+(.+)$""")
    private val INLINE_OPTION_SPLIT_REGEX = Regex("""\s+(?=[A-Ea-eƏəА-Еа-е]\)(?:\s+|\d)|[A-Ea-eƏəА-Еа-е]\.\s+)""")
    private val PAGE_NUMBER_REGEX = Regex("""^[\-–]?\s*\d{1,4}\s*[\-–]?$""")
    private val NUMBER_ROW_TOKEN_REGEX = Regex("""^\d{1,4}$""")
    private val LETTER_ROW_TOKEN_REGEX = Regex("""^[A-Ea-eƏəА-Еа-е]{1,2}$""")
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

    fun extractPdfLines(context: Context?, inputStream: InputStream): List<StyledLine> {
        if (context != null) {
            PDFBoxResourceLoader.init(context.applicationContext)
        }
        return PDDocument.load(inputStream).use { document ->
            val totalPages = document.numberOfPages
            if (totalPages <= 0) return@use emptyList()

            val stripper = BoldAwarePdfStripper()
            stripper.sortByPosition = true

            var succeededPages = 0
            var lastError: Exception? = null

            for (pageNum in 1..totalPages) {
                try {
                    stripper.startPage = pageNum
                    stripper.endPage = pageNum
                    stripper.getText(document)
                    succeededPages++
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    lastError = e
                    // Salvage any text rows parsed before a mid-page stream error
                    if (stripper.flushCurrentPageIfNeeded()) {
                        succeededPages++
                    }
                }
            }

            val finalLines = stripper.buildCleanLines()
            if (finalLines.isEmpty() && succeededPages == 0 && lastError != null) {
                throw IllegalArgumentException(
                    "Bu PDF faylının mətn qatı zədələnib və ya dəstəklənməyən sıxılma formatındadır (${lastError.cause?.message ?: lastError.message ?: "oxuma xətası"}).",
                    lastError,
                )
            }
            finalLines
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

        val keySections = mutableListOf(MutableKeySection(title = ""))
        var keyStartIdx = cleanLines.size
        val scanStart = maxOf(0, cleanLines.size / 2)
        var inExplicitKeySection = false
        var pendingTabularNumbers: List<Int>? = null

        fun currentKeySection(): MutableKeySection = keySections.last()

        fun ensureSectionForNumber(qNum: Int) {
            val sec = currentKeySection()
            if (qNum == 1 && sec.answers.isNotEmpty()) {
                keySections.add(MutableKeySection(title = ""))
            }
        }

        for (i in scanStart until cleanLines.size) {
            val txt = cleanLines[i].text
            if (TOC_DOT_LEADER_REGEX.containsMatchIn(txt)) {
                pendingTabularNumbers = null
                continue
            }
            if (BACKMATTER_HEADER_REGEX.matches(txt)) {
                pendingTabularNumbers = null
                if (i < keyStartIdx) keyStartIdx = i
                if (inExplicitKeySection) break
                continue
            }
            if (KEY_SECTION_HEADER_REGEX.matches(txt)) {
                inExplicitKeySection = true
                pendingTabularNumbers = null
                if (i < keyStartIdx) keyStartIdx = i
                continue
            }

            val matches = KEY_REGEX.findAll(txt).toList()
            if (matches.size >= 3) {
                pendingTabularNumbers = null
                if (i < keyStartIdx) keyStartIdx = i
                for (m in matches) {
                    val qNum = m.groupValues[1].toIntOrNull()
                    val letter = normalizeAnswerLetter(m.groupValues[2])
                    if (qNum != null && letter != null) {
                        ensureSectionForNumber(qNum)
                        currentKeySection().answers[qNum] = letter
                    }
                }
                continue
            }

            if (inExplicitKeySection) {
                // Tabular key format: a row of integers followed by a row of answer letters
                val tokens = txt.split(Regex("""\s+""")).filter { it.isNotBlank() }
                if (tokens.isNotEmpty() && tokens.all { NUMBER_ROW_TOKEN_REGEX.matches(it) }) {
                    val nums = tokens.mapNotNull { it.toIntOrNull() }
                    val lastNum = currentKeySection().answers.keys.maxOrNull() ?: 0
                    val isValidNumberRow = nums.size >= 2 ||
                        (nums.size == 1 && (nums[0] == 1 || nums[0] == lastNum + 1))
                    if (isValidNumberRow) {
                        if (nums.first() == 1 && currentKeySection().answers.isNotEmpty()) {
                            keySections.add(MutableKeySection(title = ""))
                        }
                        pendingTabularNumbers = nums
                        continue
                    }
                }

                if (pendingTabularNumbers != null &&
                    tokens.isNotEmpty() &&
                    tokens.all { LETTER_ROW_TOKEN_REGEX.matches(it) }
                ) {
                    val nums = pendingTabularNumbers!!
                    val pairCount = minOf(nums.size, tokens.size)
                    for (idx in 0 until pairCount) {
                        val letter = normalizeAnswerLetter(tokens[idx].take(1))
                        if (letter != null) {
                            currentKeySection().answers[nums[idx]] = letter
                        }
                    }
                    pendingTabularNumbers = null
                    continue
                }
                pendingTabularNumbers = null

                val looseM = SINGLE_KEY_LOOSE_REGEX.matchEntire(txt)
                if (looseM != null) {
                    val qNum = looseM.groupValues[1].toIntOrNull()
                    val letter = normalizeAnswerLetter(looseM.groupValues[2])
                    if (qNum != null && letter != null) {
                        ensureSectionForNumber(qNum)
                        currentKeySection().answers[qNum] = letter
                    }
                } else if (matches.isNotEmpty()) {
                    for (m in matches) {
                        val qNum = m.groupValues[1].toIntOrNull()
                        val letter = normalizeAnswerLetter(m.groupValues[2])
                        if (qNum != null && letter != null) {
                            ensureSectionForNumber(qNum)
                            currentKeySection().answers[qNum] = letter
                        }
                    }
                } else if (!PAGE_NUMBER_REGEX.matches(txt) && txt.length in 4..120) {
                    // Chapter/section heading inside the answer key
                    val normalizedTitle = normalizeSectionTitle(txt)
                    if (normalizedTitle.isNotEmpty()) {
                        val sec = currentKeySection()
                        if (sec.answers.isEmpty()) {
                            sec.title = normalizedTitle
                        } else {
                            keySections.add(MutableKeySection(title = normalizedTitle))
                        }
                    }
                }
            }
        }

        val populatedKeySections = keySections.filter { it.answers.isNotEmpty() }
        val flatAnswerKeyMap = populatedKeySections.singleOrNull()?.answers
            ?: populatedKeySections.flatMap { it.answers.entries }.associate { it.key to it.value }
        val useMultiSectionKeys = populatedKeySections.size > 1

        val contentLines = cleanLines.subList(0, keyStartIdx)
        val parsedQuestions = mutableListOf<ParsedQuestion>()
        var currentQ: MutableParsedQuestion? = null
        var autoIdCounter = 1
        var currentSectionIdx = 0
        var lastQuestionNumSeen: Int? = null
        var pendingSectionTitle = ""

        fun resolveSectionIndexForNewQuestion(qNum: Int): Int {
            if (!useMultiSectionKeys) return 0
            val titleMatchIdx = if (pendingSectionTitle.isNotEmpty()) {
                findMatchingKeySectionIndex(
                    sectionTitle = pendingSectionTitle,
                    keySections = populatedKeySections,
                    minIndex = currentSectionIdx,
                )
            } else {
                null
            }
            if (titleMatchIdx != null) {
                currentSectionIdx = titleMatchIdx
            } else if (lastQuestionNumSeen != null && qNum < lastQuestionNumSeen!! && lastQuestionNumSeen!! >= 5) {
                if (currentSectionIdx + 1 < populatedKeySections.size) {
                    currentSectionIdx++
                }
            }
            pendingSectionTitle = ""
            return currentSectionIdx
        }

        fun finalizeQuestion(qObj: MutableParsedQuestion?) {
            if (qObj == null || qObj.rawOptions.size < 2) return
            var detectedIdx = 0
            var hasConfirmedKey = false

            val keyLetter = if (useMultiSectionKeys) {
                populatedKeySections.getOrNull(qObj.sectionIdx)?.answers?.get(qObj.id)
            } else {
                flatAnswerKeyMap[qObj.id]
            }
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
            if (TOC_DOT_LEADER_REGEX.containsMatchIn(txt)) continue
            if (DIFFICULTY_LABEL_REGEX.matches(txt)) continue

            val inlineOptParts = txt.split(INLINE_OPTION_SPLIT_REGEX)
            val qMatch = QUESTION_START_REGEX.matchEntire(txt)
            val standaloneNumMatch = STANDALONE_QNUM_REGEX.matchEntire(txt)
            val optMatch = OPTION_START_REGEX.matchEntire(txt)

            // Detect inline or continuing numbered sub-list inside an open question (e.g., "1) MS Word 2) Winzip ..." or "4. burulmuş cüt 5. radiokanal")
            val subPairMatch = SUBITEM_LINE_PAIR_REGEX.matchEntire(txt)
            val subPairFirst = subPairMatch?.groupValues?.get(1)?.toIntOrNull()
            val subPairSecond = subPairMatch?.groupValues?.get(2)?.toIntOrNull()
            val isSubListInsideOpenQuestion = currentQ != null &&
                currentQ.rawOptions.isEmpty() &&
                optMatch == null &&
                (
                    (subPairFirst != null && subPairSecond != null && (subPairFirst == 1 || (currentQ.lastSubItemNum > 0 && subPairFirst == currentQ.lastSubItemNum + 1))) ||
                        (
                            qMatch != null &&
                                qMatch.groupValues[1].toIntOrNull()?.let { num ->
                                    (num == 1 && txt.contains("1)") && currentQ!!.qText.endsWith(":")) ||
                                        (currentQ!!.lastSubItemNum > 0 && num == currentQ!!.lastSubItemNum + 1)
                                } == true
                            )
                    )

            if (isSubListInsideOpenQuestion) {
                currentQ!!.qText = "${currentQ.qText}\n$txt"
                val maxSubOnLine = SUBITEM_SINGLE_TOKEN_REGEX.findAll(txt)
                    .mapNotNull { it.groupValues[1].toIntOrNull() }
                    .maxOrNull()
                if (maxSubOnLine != null) {
                    currentQ.lastSubItemNum = maxOf(currentQ.lastSubItemNum, maxSubOnLine)
                }
                continue
            }

            if (qMatch != null && optMatch == null) {
                finalizeQuestion(currentQ)
                val qNum = qMatch.groupValues[1].toIntOrNull() ?: autoIdCounter++
                autoIdCounter = maxOf(autoIdCounter, qNum + 1)
                val secIdx = resolveSectionIndexForNewQuestion(qNum)
                lastQuestionNumSeen = qNum
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
                    sectionIdx = secIdx,
                    qText = qBody,
                    isExam = isStarred,
                    usesExplicitOptionLabels = extractedInlineOpts.isNotEmpty(),
                    rawOptions = extractedInlineOpts,
                )
            } else if (standaloneNumMatch != null && optMatch == null) {
                finalizeQuestion(currentQ)
                val qNum = standaloneNumMatch.groupValues[1].toIntOrNull() ?: autoIdCounter++
                autoIdCounter = maxOf(autoIdCounter, qNum + 1)
                val secIdx = resolveSectionIndexForNewQuestion(qNum)
                lastQuestionNumSeen = qNum
                currentQ = MutableParsedQuestion(
                    id = qNum,
                    sectionIdx = secIdx,
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
                    // Check if this line is actually a bold chapter heading starting the next section
                    if (line.isBold &&
                        useMultiSectionKeys &&
                        findMatchingKeySectionIndex(
                            sectionTitle = normalizeSectionTitle(txt),
                            keySections = populatedKeySections,
                            minIndex = currentSectionIdx + 1,
                        ) != null
                    ) {
                        finalizeQuestion(currentQ)
                        currentQ = null
                        pendingSectionTitle = normalizeSectionTitle(txt)
                    } else {
                        // Wrapped continuation line of the previous labeled option
                        val lastOpt = currentQ.rawOptions.last()
                        val hasM = lastOpt.hasMarker || MARKER_END_REGEX.containsMatchIn(txt)
                        val cleanAppend = txt.replace(MARKER_END_REGEX, "").trim()
                        lastOpt.text = "${lastOpt.text} $cleanAppend".trim()
                        lastOpt.hasMarker = hasM
                    }
                } else if (currentQ.rawOptions.size < 5) {
                    val rawOptStr = bulletM?.groupValues?.get(1) ?: txt
                    val hasM = MARKER_START_REGEX.containsMatchIn(txt) || MARKER_END_REGEX.containsMatchIn(rawOptStr)
                    val cleanT = rawOptStr.replace(MARKER_END_REGEX, "").trim()
                    currentQ.rawOptions.add(RawOption(cleanT, line.isBold, hasM))
                }
            } else if (currentQ == null && (txt.endsWith("?") || txt.endsWith(":"))) {
                val qNum = autoIdCounter++
                val secIdx = resolveSectionIndexForNewQuestion(qNum)
                currentQ = MutableParsedQuestion(
                    id = qNum,
                    sectionIdx = secIdx,
                    qText = txt,
                    isExam = false,
                )
            } else if (currentQ == null && line.isBold && txt.length in 4..120) {
                val normalized = normalizeSectionTitle(txt)
                if (normalized.isNotEmpty()) {
                    pendingSectionTitle = normalized
                }
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

    private fun normalizeAnswerLetter(raw: String): String? {
        val upper = raw.trim().uppercase()
        return if (upper in LETTER_TO_INDEX) upper else null
    }

    private fun normalizeSectionTitle(raw: String): String =
        raw.lowercase()
            .replace(Regex("""^\d{1,3}\s*[\.\)]\s*"""), "")
            .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun findMatchingKeySectionIndex(
        sectionTitle: String,
        keySections: List<MutableKeySection>,
        minIndex: Int,
    ): Int? {
        if (sectionTitle.isBlank()) return null
        val queryWords = sectionTitle.split(' ').filter { it.length >= 3 }.toSet()
        if (queryWords.isEmpty()) return null
        var bestIdx: Int? = null
        var bestScore = 0
        for (idx in minIndex until keySections.size) {
            val candidate = keySections[idx].title
            if (candidate.isBlank()) continue
            if (candidate == sectionTitle || candidate.contains(sectionTitle) || sectionTitle.contains(candidate)) {
                return idx
            }
            val candidateWords = candidate.split(' ').filter { it.length >= 3 }.toSet()
            val overlap = queryWords.intersect(candidateWords).size
            if (overlap >= 2 && overlap > bestScore) {
                bestScore = overlap
                bestIdx = idx
            }
        }
        return bestIdx
    }

    private class MutableKeySection(
        var title: String,
        val answers: MutableMap<Int, String> = mutableMapOf(),
    )

    private data class RawOption(
        var text: String,
        val isBold: Boolean,
        var hasMarker: Boolean,
    )

    private class MutableParsedQuestion(
        val id: Int,
        val sectionIdx: Int,
        var qText: String,
        var isExam: Boolean,
        var usesExplicitOptionLabels: Boolean = false,
        var lastSubItemNum: Int = 0,
        val rawOptions: MutableList<RawOption> = mutableListOf(),
    )

    private class BoldAwarePdfStripper : PDFTextStripper() {
        private val rawPages = mutableListOf<List<PageLineEntry>>()
        private val currentPageRows = mutableListOf<PdfRow>()
        private var pageActive = false

        override fun startPage(page: com.tom_roush.pdfbox.pdmodel.PDPage) {
            super.startPage(page)
            currentPageRows.clear()
            pageActive = true
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
            flushRowsToPageList()
            super.endPage(page)
        }

        fun flushCurrentPageIfNeeded(): Boolean {
            if (!pageActive && currentPageRows.isEmpty()) return false
            val hadRows = currentPageRows.isNotEmpty()
            flushRowsToPageList()
            return hadRows
        }

        private fun flushRowsToPageList() {
            pageActive = false
            if (currentPageRows.isEmpty()) return
            currentPageRows.sortBy { it.y }
            val pageEntries = mutableListOf<PageLineEntry>()
            val lastRowIdx = currentPageRows.lastIndex
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
                    val isEdge = (rIdx == 0 || rIdx == lastRowIdx)
                    if (!(isEdge && PAGE_NUMBER_REGEX.matches(cleanTxt))) {
                        val isBold = boldChars > 0 && (boldChars.toFloat() / cleanTxt.length.toFloat()) >= 0.45f
                        pageEntries.add(
                            PageLineEntry(
                                line = StyledLine(text = cleanTxt, isBold = isBold),
                                isEdgeRow = isEdge,
                            )
                        )
                    }
                }
            }
            currentPageRows.clear()
            if (pageEntries.isNotEmpty()) {
                rawPages.add(pageEntries)
            }
        }

        fun buildCleanLines(): List<StyledLine> {
            val edgePatternCounts = mutableMapOf<String, Int>()
            for (page in rawPages) {
                val seenOnPage = mutableSetOf<String>()
                for (entry in page) {
                    if (entry.isEdgeRow) {
                        val sig = normalizeEdgeHeaderSignature(entry.line.text)
                        if (sig.isNotEmpty() && seenOnPage.add(sig)) {
                            edgePatternCounts[sig] = (edgePatternCounts[sig] ?: 0) + 1
                        }
                    }
                }
            }

            val result = mutableListOf<StyledLine>()
            for (page in rawPages) {
                for (entry in page) {
                    if (entry.isEdgeRow) {
                        val sig = normalizeEdgeHeaderSignature(entry.line.text)
                        val repeatCount = edgePatternCounts[sig] ?: 0
                        if (repeatCount >= 3 && !QUESTION_START_REGEX.matches(entry.line.text) && !OPTION_START_REGEX.matches(entry.line.text)) {
                            continue
                        }
                    }
                    result.add(entry.line)
                }
            }
            return result
        }

        private fun normalizeEdgeHeaderSignature(text: String): String =
            text.replace(Regex("""\d+"""), "#").replace(Regex("""\s+"""), " ").trim().lowercase()

        private data class PageLineEntry(
            val line: StyledLine,
            val isEdgeRow: Boolean,
        )

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
