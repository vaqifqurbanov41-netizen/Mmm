package com.aistudio.texnonezaret.data

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory
import kotlin.math.abs
import kotlin.math.pow

/**
 * 100% offline parser supporting .xlsx (OOXML ZIP), .xls (BIFF8 OLE2 binary,
 * XML Spreadsheet 2003, or HTML table export), and .csv / .tsv spreadsheets.
 */
object SpreadsheetParser {

    fun parseBytes(bytes: ByteArray, fileName: String = ""): List<List<String>> {
        if (bytes.isEmpty()) return emptyList()
        val lowerName = fileName.lowercase(Locale.US)

        // 1. OOXML (.xlsx) — starts with ZIP local header PK\x03\x04
        if (isZipSignature(bytes) || lowerName.endsWith(".xlsx")) {
            val xlsxRows = runCatching { parseXlsx(bytes) }.getOrDefault(emptyList())
            if (xlsxRows.isNotEmpty()) return xlsxRows
        }

        // 2. Binary OLE2 Compound Document (.xls BIFF8) — D0 CF 11 E0 A1 B1 1A E1
        if (isOle2Signature(bytes)) {
            val biffRows = runCatching { parseOle2Biff8(bytes) }.getOrDefault(emptyList())
            if (biffRows.isNotEmpty()) return biffRows
        }

        // 3. Text-based spreadsheets: SpreadsheetML 2003 XML, HTML-disguised .xls, or CSV/TSV
        val text = decodeTextSmart(bytes).trim()
        if (text.isEmpty()) return emptyList()

        val headSample = text.take(600).lowercase(Locale.US)
        if (headSample.contains("<workbook") || headSample.contains("schemas-microsoft-com:office:spreadsheet")) {
            val xmlRows = runCatching { parseSpreadsheetMl2003(text) }.getOrDefault(emptyList())
            if (xmlRows.isNotEmpty()) return xmlRows
        }

        if (headSample.contains("<table") || headSample.contains("<html")) {
            val htmlRows = runCatching { parseHtmlTable(text) }.getOrDefault(emptyList())
            if (htmlRows.isNotEmpty()) return htmlRows
        }

        return parseDelimitedText(text)
    }

    private fun isZipSignature(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() &&
            bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() &&
            bytes[3] == 0x04.toByte()

    private fun isOle2Signature(bytes: ByteArray): Boolean =
        bytes.size >= 8 &&
            bytes[0] == 0xD0.toByte() &&
            bytes[1] == 0xCF.toByte() &&
            bytes[2] == 0x11.toByte() &&
            bytes[3] == 0xE0.toByte() &&
            bytes[4] == 0xA1.toByte() &&
            bytes[5] == 0xB1.toByte() &&
            bytes[6] == 0x1A.toByte() &&
            bytes[7] == 0xE1.toByte()

    private fun decodeTextSmart(bytes: ByteArray): String {
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16LE).removePrefix("\uFEFF")
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE).removePrefix("\uFEFF")
        }
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8).removePrefix("\uFEFF")
        }
        val utf8 = String(bytes, StandardCharsets.UTF_8)
        if (!utf8.contains('\uFFFD')) return utf8.removePrefix("\uFEFF")
        val cp1251 = runCatching { String(bytes, Charset.forName("windows-1251")) }.getOrNull()
        if (cp1251 != null && !cp1251.contains('\uFFFD')) return cp1251
        return utf8.removePrefix("\uFEFF")
    }

    // -------------------------------------------------------------------------
    // 1. XLSX (OOXML ZIP)
    // -------------------------------------------------------------------------

    private fun parseXlsx(bytes: ByteArray): List<List<String>> {
        val entries = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val normName = entry.name.trimStart('/').lowercase(Locale.US)
                    if (normName.endsWith(".xml") || normName.endsWith(".rels")) {
                        entries[normName] = zis.readBytes()
                    }
                }
                runCatching { zis.closeEntry() }
                entry = zis.nextEntry
            }
        }
        if (entries.isEmpty()) return emptyList()

        val sharedStrings = entries["xl/sharedstrings.xml"]
            ?.let { parseSharedStrings(decodeTextSmart(it)) }
            ?: emptyList()

        val firstSheetPath = findFirstWorksheetPath(entries)
            ?: entries.keys.filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
                .sorted()
                .firstOrNull()
            ?: return emptyList()

        val sheetBytes = entries[firstSheetPath] ?: return emptyList()
        return parseWorksheetXml(decodeTextSmart(sheetBytes), sharedStrings)
    }

    private fun findFirstWorksheetPath(entries: Map<String, ByteArray>): String? {
        val wbBytes = entries["xl/workbook.xml"] ?: return null
        val relsBytes = entries["xl/_rels/workbook.xml.rels"] ?: return null

        var firstRelId: String? = null
        runCatching {
            parseXmlWithSax(decodeTextSmart(wbBytes), object : DefaultHandler() {
                override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                    if (firstRelId != null) return
                    val tag = (qName ?: localName).orEmpty().substringAfter(':')
                    if (tag == "sheet" && attributes != null) {
                        for (i in 0 until attributes.length) {
                            val attrName = attributes.getQName(i).ifBlank { attributes.getLocalName(i) }
                            if (attrName == "id" || attrName.endsWith(":id")) {
                                firstRelId = attributes.getValue(i)
                                break
                            }
                        }
                    }
                }
            })
        }
        val relId = firstRelId ?: return null

        var resolvedTarget: String? = null
        runCatching {
            parseXmlWithSax(decodeTextSmart(relsBytes), object : DefaultHandler() {
                override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                    if (resolvedTarget != null) return
                    val tag = (qName ?: localName).orEmpty().substringAfter(':')
                    if (tag == "Relationship" && attributes != null) {
                        val id = attributes.getValue("Id")
                        val target = attributes.getValue("Target")
                        if (id == relId && !target.isNullOrBlank()) {
                            resolvedTarget = if (target.startsWith("/")) {
                                target.trimStart('/')
                            } else {
                                "xl/${target.trimStart('/')}"
                            }.lowercase(Locale.US)
                        }
                    }
                }
            })
        }
        return resolvedTarget
    }

    private fun parseSharedStrings(xml: String): List<String> {
        val list = mutableListOf<String>()
        parseXmlWithSax(xml, object : DefaultHandler() {
            var inSi = false
            var inT = false
            var ignorePhonetic = false
            val sb = StringBuilder()

            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                when ((qName ?: localName).orEmpty().substringAfter(':')) {
                    "si" -> {
                        inSi = true
                        sb.setLength(0)
                    }
                    "rPh" -> ignorePhonetic = true
                    "t" -> if (inSi && !ignorePhonetic) inT = true
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inSi && inT && !ignorePhonetic) {
                    sb.append(ch, start, length)
                }
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                when ((qName ?: localName).orEmpty().substringAfter(':')) {
                    "t" -> inT = false
                    "rPh" -> ignorePhonetic = false
                    "si" -> {
                        list.add(sb.toString())
                        inSi = false
                    }
                }
            }
        })
        return list
    }

    private fun parseWorksheetXml(xml: String, sharedStrings: List<String>): List<List<String>> {
        val rawRows = mutableListOf<Pair<Int, MutableMap<Int, String>>>()
        var maxCol = 0

        parseXmlWithSax(xml, object : DefaultHandler() {
            var inRow = false
            var currentRowIdx = 0
            var currentRowMap = mutableMapOf<Int, String>()
            var inCell = false
            var cellColIdx = -1
            var cellType = ""
            var inValue = false
            var inInlineT = false
            val textBuf = StringBuilder()

            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                when ((qName ?: localName).orEmpty().substringAfter(':')) {
                    "row" -> {
                        inRow = true
                        val rAttr = attributes?.getValue("r")?.toIntOrNull()
                        currentRowIdx = if (rAttr != null && rAttr > 0) rAttr - 1 else rawRows.size
                        currentRowMap = mutableMapOf()
                    }
                    "c" -> if (inRow) {
                        inCell = true
                        val ref = attributes?.getValue("r")
                        cellColIdx = if (!ref.isNullOrBlank()) colIndexFromRef(ref) else (currentRowMap.keys.maxOrNull() ?: -1) + 1
                        if (cellColIdx > maxCol) maxCol = cellColIdx
                        cellType = attributes?.getValue("t").orEmpty()
                        textBuf.setLength(0)
                    }
                    "v" -> if (inCell) {
                        inValue = true
                        textBuf.setLength(0)
                    }
                    "is" -> if (inCell) {
                        textBuf.setLength(0)
                    }
                    "t" -> if (inCell && cellType == "inlineStr") {
                        inInlineT = true
                    }
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inCell && (inValue || inInlineT)) {
                    textBuf.append(ch, start, length)
                }
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                when ((qName ?: localName).orEmpty().substringAfter(':')) {
                    "v" -> inValue = false
                    "t" -> inInlineT = false
                    "c" -> if (inCell) {
                        val rawVal = textBuf.toString()
                        val resolved = when (cellType) {
                            "s" -> {
                                val idx = rawVal.trim().toIntOrNull()
                                if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else ""
                            }
                            "b" -> if (rawVal.trim() == "1") "TRUE" else "FALSE"
                            else -> cleanNumericCellString(rawVal)
                        }
                        if (cellColIdx >= 0 && resolved.isNotEmpty()) {
                            currentRowMap[cellColIdx] = resolved
                        }
                        inCell = false
                    }
                    "row" -> if (inRow) {
                        if (currentRowMap.isNotEmpty()) {
                            rawRows.add(currentRowIdx to currentRowMap)
                        }
                        inRow = false
                    }
                }
            }
        })

        if (rawRows.isEmpty()) return emptyList()
        val colCount = maxCol + 1
        return rawRows.map { (_, map) ->
            List(colCount) { col -> map[col].orEmpty() }
        }
    }

    private fun colIndexFromRef(ref: String): Int {
        var col = 0
        for (ch in ref) {
            if (ch in 'A'..'Z') {
                col = col * 26 + (ch - 'A' + 1)
            } else if (ch in 'a'..'z') {
                col = col * 26 + (ch - 'a' + 1)
            } else {
                break
            }
        }
        return (col - 1).coerceAtLeast(0)
    }

    // -------------------------------------------------------------------------
    // 2. Binary OLE2 / BIFF8 (.xls)
    // -------------------------------------------------------------------------

    private fun parseOle2Biff8(bytes: ByteArray): List<List<String>> {
        val workbookStream = extractOle2WorkbookStream(bytes) ?: return emptyList()
        return parseBiff8Stream(workbookStream)
    }

    private fun extractOle2WorkbookStream(bytes: ByteArray): ByteArray? {
        if (bytes.size < 512) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val sectorShift = buf.getShort(0x1E).toInt() and 0xFFFF
        val miniSectorShift = buf.getShort(0x20).toInt() and 0xFFFF
        val sectorSize = 1 shl sectorShift
        val miniSectorSize = 1 shl miniSectorShift
        if (sectorSize < 512 || sectorSize > 8192 || miniSectorSize <= 0) return null

        val fatSectorCount = buf.getInt(0x2C)
        val firstDirSector = buf.getInt(0x30)
        val miniStreamCutoff = buf.getInt(0x38)
        var firstMiniFatSector = buf.getInt(0x3C)
        val firstDifatSector = buf.getInt(0x44)
        val difatSectorCount = buf.getInt(0x48)

        val difat = mutableListOf<Int>()
        for (i in 0 until 109) {
            val sec = buf.getInt(0x4C + i * 4)
            if (sec >= 0) difat.add(sec)
        }
        var difatSec = firstDifatSector
        var difatRemaining = difatSectorCount
        while (difatSec >= 0 && difatRemaining > 0 && difat.size < fatSectorCount + 128) {
            val off = (difatSec + 1) * sectorSize
            if (off + sectorSize > bytes.size) break
            val perSec = sectorSize / 4 - 1
            for (i in 0 until perSec) {
                val s = buf.getInt(off + i * 4)
                if (s >= 0) difat.add(s)
            }
            difatSec = buf.getInt(off + sectorSize - 4)
            difatRemaining--
        }

        val fat = IntArray(difat.size * (sectorSize / 4))
        var fatIdx = 0
        for (sec in difat) {
            val off = (sec + 1) * sectorSize
            if (off + sectorSize > bytes.size) break
            for (j in 0 until sectorSize / 4) {
                fat[fatIdx++] = buf.getInt(off + j * 4)
            }
        }

        fun readSectorChain(startSec: Int, expectedSize: Int = Int.MAX_VALUE): ByteArray {
            val out = ByteArrayOutputStream()
            var cur = startSec
            val seen = HashSet<Int>()
            while (cur >= 0 && seen.add(cur) && out.size() < expectedSize) {
                val off = (cur + 1) * sectorSize
                if (off < 0 || off + sectorSize > bytes.size) break
                out.write(bytes, off, sectorSize)
                cur = if (cur < fat.size) fat[cur] else -2
            }
            val arr = out.toByteArray()
            return if (expectedSize != Int.MAX_VALUE && arr.size > expectedSize) {
                arr.copyOf(expectedSize)
            } else {
                arr
            }
        }

        val dirBytes = readSectorChain(firstDirSector)
        if (dirBytes.size < 128) return null
        val dirBuf = ByteBuffer.wrap(dirBytes).order(ByteOrder.LITTLE_ENDIAN)
        val entryCount = dirBytes.size / 128

        var rootStartSec = -2
        var rootSize = 0
        var targetStartSec = -2
        var targetSize = 0

        for (i in 0 until entryCount) {
            val base = i * 128
            val nameLenBytes = (dirBuf.getShort(base + 0x40).toInt() and 0xFFFF).coerceAtMost(64)
            val objType = dirBytes[base + 0x42].toInt() and 0xFF
            if (nameLenBytes < 2 || objType == 0) continue
            val name = String(dirBytes, base, (nameLenBytes - 2).coerceAtLeast(0), StandardCharsets.UTF_16LE)
            val startSec = dirBuf.getInt(base + 0x74)
            val streamSize = dirBuf.getInt(base + 0x78)
            if (objType == 5) {
                rootStartSec = startSec
                rootSize = streamSize
            } else if (name.equals("Workbook", ignoreCase = true) || name.equals("Book", ignoreCase = true)) {
                targetStartSec = startSec
                targetSize = streamSize
            }
        }

        if (targetStartSec < 0 || targetSize <= 0) return null
        if (targetSize >= miniStreamCutoff || rootStartSec < 0) {
            return readSectorChain(targetStartSec, targetSize)
        }

        val miniContainer = readSectorChain(rootStartSec, rootSize)
        val miniFatBytes = if (firstMiniFatSector >= 0) readSectorChain(firstMiniFatSector) else return null
        val miniFatBuf = ByteBuffer.wrap(miniFatBytes).order(ByteOrder.LITTLE_ENDIAN)
        val miniFat = IntArray(miniFatBytes.size / 4) { miniFatBuf.getInt(it * 4) }

        val out = ByteArrayOutputStream()
        var curMini = targetStartSec
        val seenMini = HashSet<Int>()
        while (curMini >= 0 && seenMini.add(curMini) && out.size() < targetSize) {
            val off = curMini * miniSectorSize
            if (off + miniSectorSize > miniContainer.size) break
            out.write(miniContainer, off, miniSectorSize)
            curMini = if (curMini < miniFat.size) miniFat[curMini] else -2
        }
        return out.toByteArray().let { if (it.size > targetSize) it.copyOf(targetSize) else it }
    }

    private fun parseBiff8Stream(stream: ByteArray): List<List<String>> {
        val buf = ByteBuffer.wrap(stream).order(ByteOrder.LITTLE_ENDIAN)
        val records = mutableListOf<Pair<Int, ByteArray>>()
        var pos = 0
        while (pos + 4 <= stream.size) {
            val id = buf.getShort(pos).toInt() and 0xFFFF
            val len = buf.getShort(pos + 2).toInt() and 0xFFFF
            pos += 4
            if (pos + len > stream.size) break
            records.add(id to stream.copyOfRange(pos, pos + len))
            pos += len
        }
        if (records.isEmpty()) return emptyList()

        // Coalesce SST + CONTINUE records cleanly
        val sstStrings = mutableListOf<String>()
        var idx = 0
        while (idx < records.size) {
            val (recId, data) = records[idx]
            if (recId == 0x00FC) { // SST
                val chunks = mutableListOf(data)
                var nextIdx = idx + 1
                while (nextIdx < records.size && records[nextIdx].first == 0x003C) { // CONTINUE
                    chunks.add(records[nextIdx].second)
                    nextIdx++
                }
                sstStrings.addAll(decodeBiff8Sst(chunks))
                idx = nextIdx
            } else {
                idx++
            }
        }

        val rowsMap = sortedMapOf<Int, MutableMap<Int, String>>()
        var maxCol = 0
        var bofDepth = 0
        var seenFirstWorksheet = false
        var readingWorksheet = false

        for ((recId, data) in records) {
            when (recId) {
                0x0809 -> { // BOF
                    val bofType = if (data.size >= 4) ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getShort(2).toInt() and 0xFFFF else 0
                    bofDepth++
                    if (bofType == 0x0010 && !seenFirstWorksheet) {
                        seenFirstWorksheet = true
                        readingWorksheet = true
                    } else if (bofType == 0x0010 && seenFirstWorksheet) {
                        break
                    }
                }
                0x000A -> { // EOF
                    if (readingWorksheet) {
                        readingWorksheet = false
                        break
                    }
                    if (bofDepth > 0) bofDepth--
                }
                0x00FD -> if (readingWorksheet && data.size >= 10) { // LABELSST
                    val rBuf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                    val row = rBuf.getShort(0).toInt() and 0xFFFF
                    val col = rBuf.getShort(2).toInt() and 0xFFFF
                    val sstIdx = rBuf.getInt(6)
                    val value = sstStrings.getOrNull(sstIdx).orEmpty()
                    if (col > maxCol) maxCol = col
                    if (value.isNotEmpty()) rowsMap.getOrPut(row) { mutableMapOf() }[col] = value
                }
                0x0204 -> if (readingWorksheet && data.size >= 8) { // LABEL (BIFF8 Unicode)
                    val rBuf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                    val row = rBuf.getShort(0).toInt() and 0xFFFF
                    val col = rBuf.getShort(2).toInt() and 0xFFFF
                    val charCount = rBuf.getShort(6).toInt() and 0xFFFF
                    val value = if (data.size >= 9) {
                        val flags = data[8].toInt() and 0xFF
                        val isUtf16 = (flags and 0x01) != 0
                        decodeFixedBiffString(data, 9, charCount, isUtf16)
                    } else ""
                    if (col > maxCol) maxCol = col
                    if (value.isNotEmpty()) rowsMap.getOrPut(row) { mutableMapOf() }[col] = value
                }
                0x0203 -> if (readingWorksheet && data.size >= 14) { // NUMBER
                    val rBuf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                    val row = rBuf.getShort(0).toInt() and 0xFFFF
                    val col = rBuf.getShort(2).toInt() and 0xFFFF
                    val d = rBuf.getDouble(6)
                    if (col > maxCol) maxCol = col
                    rowsMap.getOrPut(row) { mutableMapOf() }[col] = formatExcelDouble(d)
                }
                0x027E -> if (readingWorksheet && data.size >= 10) { // RK
                    val rBuf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                    val row = rBuf.getShort(0).toInt() and 0xFFFF
                    val col = rBuf.getShort(2).toInt() and 0xFFFF
                    val d = decodeRkNumber(rBuf.getInt(6))
                    if (col > maxCol) maxCol = col
                    rowsMap.getOrPut(row) { mutableMapOf() }[col] = formatExcelDouble(d)
                }
                0x00BD -> if (readingWorksheet && data.size >= 10) { // MULRK
                    val rBuf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                    val row = rBuf.getShort(0).toInt() and 0xFFFF
                    val firstCol = rBuf.getShort(2).toInt() and 0xFFFF
                    val count = (data.size - 6) / 6
                    for (i in 0 until count) {
                        val col = firstCol + i
                        val rk = rBuf.getInt(4 + i * 6 + 2)
                        if (col > maxCol) maxCol = col
                        rowsMap.getOrPut(row) { mutableMapOf() }[col] = formatExcelDouble(decodeRkNumber(rk))
                    }
                }
            }
        }

        if (rowsMap.isEmpty()) return emptyList()
        val colCount = maxCol + 1
        return rowsMap.values.map { rowMap ->
            List(colCount) { col -> rowMap[col].orEmpty() }
        }
    }

    private fun decodeBiff8Sst(chunks: List<ByteArray>): List<String> {
        if (chunks.isEmpty() || chunks[0].size < 8) return emptyList()
        val firstBuf = ByteBuffer.wrap(chunks[0]).order(ByteOrder.LITTLE_ENDIAN)
        val uniqueCount = firstBuf.getInt(4).coerceAtLeast(0).coerceAtMost(200_000)
        val result = ArrayList<String>(uniqueCount)

        var chunkIdx = 0
        var offset = 8

        fun currentChunk(): ByteArray = chunks[chunkIdx]
        fun ensureBytes(need: Int): Boolean {
            if (offset + need <= currentChunk().size) return true
            if (chunkIdx + 1 < chunks.size && offset >= currentChunk().size) {
                chunkIdx++
                offset = 0
                return offset + need <= currentChunk().size
            }
            return false
        }

        fun readU8(): Int {
            if (!ensureBytes(1)) return 0
            return currentChunk()[offset++].toInt() and 0xFF
        }

        fun readU16(): Int {
            if (!ensureBytes(2)) return 0
            val b0 = currentChunk()[offset++].toInt() and 0xFF
            val b1 = currentChunk()[offset++].toInt() and 0xFF
            return b0 or (b1 shl 8)
        }

        fun readS32(): Int {
            if (!ensureBytes(4)) return 0
            val v = ByteBuffer.wrap(currentChunk(), offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
            offset += 4
            return v
        }

        fun skipAcrossChunks(count: Int) {
            var rem = count
            while (rem > 0 && chunkIdx < chunks.size) {
                val avail = currentChunk().size - offset
                if (avail <= 0) {
                    chunkIdx++
                    offset = 0
                    continue
                }
                val take = minOf(rem, avail)
                offset += take
                rem -= take
            }
        }

        for (i in 0 until uniqueCount) {
            if (chunkIdx >= chunks.size) break
            if (offset >= currentChunk().size) {
                chunkIdx++
                offset = 0
                if (chunkIdx >= chunks.size) break
            }
            if (offset + 3 > currentChunk().size) break
            val cch = readU16()
            val flags = readU8()
            var isUtf16 = (flags and 0x01) != 0
            val hasExt = (flags and 0x04) != 0
            val hasRich = (flags and 0x08) != 0
            val richRuns = if (hasRich) readU16() else 0
            val extLen = if (hasExt) readS32() else 0

            val sb = StringBuilder(cch)
            var charsLeft = cch
            while (charsLeft > 0 && chunkIdx < chunks.size) {
                if (offset >= currentChunk().size) {
                    chunkIdx++
                    offset = 0
                    if (chunkIdx >= chunks.size || offset >= currentChunk().size) break
                    // First byte of a CONTINUE chunk splitting an active string is its high-byte flag
                    val contFlag = currentChunk()[offset++].toInt() and 0xFF
                    isUtf16 = (contFlag and 0x01) != 0
                }
                val availBytes = currentChunk().size - offset
                val bytesPerChar = if (isUtf16) 2 else 1
                val canReadChars = minOf(charsLeft, availBytes / bytesPerChar)
                if (canReadChars <= 0) {
                    offset = currentChunk().size
                    continue
                }
                if (isUtf16) {
                    for (c in 0 until canReadChars) {
                        val lo = currentChunk()[offset++].toInt() and 0xFF
                        val hi = currentChunk()[offset++].toInt() and 0xFF
                        sb.append(((hi shl 8) or lo).toChar())
                    }
                } else {
                    for (c in 0 until canReadChars) {
                        val b = currentChunk()[offset++].toInt() and 0xFF
                        sb.append(b.toChar())
                    }
                }
                charsLeft -= canReadChars
            }

            if (richRuns > 0) skipAcrossChunks(richRuns * 4)
            if (extLen > 0) skipAcrossChunks(extLen)
            result.add(sb.toString())
        }
        return result
    }

    private fun decodeFixedBiffString(data: ByteArray, start: Int, charCount: Int, isUtf16: Boolean): String {
        val sb = StringBuilder(charCount)
        var off = start
        for (i in 0 until charCount) {
            if (isUtf16) {
                if (off + 1 >= data.size) break
                val ch = ((data[off + 1].toInt() and 0xFF) shl 8) or (data[off].toInt() and 0xFF)
                sb.append(ch.toChar())
                off += 2
            } else {
                if (off >= data.size) break
                sb.append((data[off].toInt() and 0xFF).toChar())
                off += 1
            }
        }
        return sb.toString()
    }

    private fun decodeRkNumber(rk: Int): Double {
        val div100 = (rk and 0x01) != 0
        val isInt = (rk and 0x02) != 0
        val base = if (isInt) {
            (rk shr 2).toDouble()
        } else {
            val bits = (rk.toLong() and 0xFFFFFFFCL) shl 32
            java.lang.Double.longBitsToDouble(bits)
        }
        return if (div100) base / 100.0 else base
    }

    private fun formatExcelDouble(d: Double): String {
        if (d.isNaN() || d.isInfinite()) return ""
        val asLong = d.toLong()
        return if (abs(d - asLong.toDouble()) < 1e-9) {
            asLong.toString()
        } else {
            d.toBigDecimal().stripTrailingZeros().toPlainString()
        }
    }

    private fun cleanNumericCellString(raw: String): String {
        val t = raw.trim()
        val d = t.toDoubleOrNull() ?: return t
        val asLong = d.toLong()
        return if (t.contains('.') && abs(d - asLong.toDouble()) < 1e-9 && abs(d) < 10.0.pow(15)) {
            asLong.toString()
        } else {
            t
        }
    }

    // -------------------------------------------------------------------------
    // 3. SpreadsheetML 2003 XML & HTML Table & CSV / TSV
    // -------------------------------------------------------------------------

    private fun parseSpreadsheetMl2003(xml: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        parseXmlWithSax(xml, object : DefaultHandler() {
            var inTable = false
            var finishedFirstTable = false
            var inRow = false
            var inData = false
            val currentRow = mutableListOf<String>()
            val dataBuf = StringBuilder()

            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                if (finishedFirstTable) return
                when ((qName ?: localName).orEmpty().substringAfter(':')) {
                    "Table" -> if (rows.isEmpty()) inTable = true
                    "Row" -> if (inTable) {
                        inRow = true
                        currentRow.clear()
                    }
                    "Cell" -> if (inRow && attributes != null) {
                        val idxAttr = (0 until attributes.length)
                            .firstOrNull { attributes.getQName(it).endsWith("Index") || attributes.getLocalName(it).endsWith("Index") }
                            ?.let { attributes.getValue(it)?.toIntOrNull() }
                        if (idxAttr != null && idxAttr - 1 > currentRow.size) {
                            repeat(idxAttr - 1 - currentRow.size) { currentRow.add("") }
                        }
                    }
                    "Data" -> if (inRow) {
                        inData = true
                        dataBuf.setLength(0)
                    }
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (!finishedFirstTable && inData) {
                    dataBuf.append(ch, start, length)
                }
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                if (finishedFirstTable) return
                when ((qName ?: localName).orEmpty().substringAfter(':')) {
                    "Data" -> if (inData) {
                        inData = false
                        currentRow.add(dataBuf.toString().trim())
                    }
                    "Row" -> if (inRow) {
                        rows.add(currentRow.toList())
                        inRow = false
                    }
                    "Table" -> if (inTable) {
                        inTable = false
                        finishedFirstTable = true
                    }
                }
            }
        })
        return rows
    }

    private fun parseHtmlTable(html: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val trRegex = Regex("<tr\\b[^>]*>(.*?)</tr>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val cellRegex = Regex("<t[hd]\\b[^>]*>(.*?)</t[hd]>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val tagStrip = Regex("<[^>]+>")
        for (tr in trRegex.findAll(html)) {
            val cells = cellRegex.findAll(tr.groupValues[1]).map { match ->
                decodeHtmlEntities(tagStrip.replace(match.groupValues[1], "")).trim()
            }.toList()
            if (cells.isNotEmpty()) rows.add(cells)
        }
        return rows
    }

    private fun decodeHtmlEntities(s: String): String =
        s.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")

    fun parseDelimitedText(text: String): List<List<String>> {
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return emptyList()
        val sep = detectDelimiter(firstLine)
        val rows = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < text.length) {
            val ch = text[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        cell.append('"')
                        i += 2
                        continue
                    } else {
                        inQuotes = false
                    }
                } else {
                    cell.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> inQuotes = true
                    sep -> {
                        currentRow.add(cell.toString().trim())
                        cell.setLength(0)
                    }
                    '\r' -> {
                        if (i + 1 < text.length && text[i + 1] == '\n') i++
                        currentRow.add(cell.toString().trim())
                        cell.setLength(0)
                        if (currentRow.any { it.isNotEmpty() }) rows.add(currentRow.toList())
                        currentRow.clear()
                    }
                    '\n' -> {
                        currentRow.add(cell.toString().trim())
                        cell.setLength(0)
                        if (currentRow.any { it.isNotEmpty() }) rows.add(currentRow.toList())
                        currentRow.clear()
                    }
                    else -> cell.append(ch)
                }
            }
            i++
        }
        if (cell.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(cell.toString().trim())
            if (currentRow.any { it.isNotEmpty() }) rows.add(currentRow.toList())
        }
        return rows
    }

    private fun detectDelimiter(sampleLine: String): Char {
        val candidates = listOf(';', ',', '\t')
        return candidates.maxByOrNull { delim -> sampleLine.count { it == delim } } ?: ','
    }

    private fun parseXmlWithSax(xml: String, handler: DefaultHandler) {
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = false
        }
        factory.newSAXParser().parse(InputSource(StringReader(xml)), handler)
    }
}
