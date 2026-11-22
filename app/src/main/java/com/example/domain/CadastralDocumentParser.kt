package com.example.domain

import java.util.Locale
import kotlin.math.abs

enum class DocCoordTarget {
    UTM_38T,
    LAT_LON,
}

data class OcrToken(
    val text: String,
    val centerX: Float,
    val centerY: Float,
    val height: Float = 20f,
)

data class ParsedDocCoordinate(
    val rawX: String,
    val rawY: String,
    val xVal: Double,
    val yVal: Double,
    val latLon: LatLon,
)

data class ParsedCadastralDocument(
    val target: DocCoordTarget,
    val utmZone: Int = 38,
    val points: List<ParsedDocCoordinate>,
    val polygon: List<LatLon>,
    val areaHa: Double?,
    val utmLinesText: String,
    val latLonLinesText: String,
    val rawPairs: List<Pair<String, String>> = emptyList(),
)

data class ScoredCadastralParse(
    val document: ParsedCadastralDocument,
    val score: Double,
)

object CadastralDocumentParser {

    fun normalizeNumericToken(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val cleaned = buildString(trimmed.length) {
            for (ch in trimmed) {
                when (ch) {
                    ',' -> append('.')
                    'O', 'o', 'О', 'о' -> append('0')
                    'l', 'I' -> append('1')
                    in '0'..'9', '.', '-' -> append(ch)
                }
            }
        }
        val firstDot = cleaned.indexOf('.')
        val lastDot = cleaned.lastIndexOf('.')
        val collapsed = if (firstDot != lastDot && lastDot > 0) {
            val intPart = cleaned.substring(0, lastDot).replace(".", "")
            val decPart = cleaned.substring(lastDot)
            "$intPart$decPart"
        } else {
            cleaned
        }
        return collapsed.trimEnd('.')
    }

    private fun parseCoordinateCandidate(raw: String): Pair<String, Double>? {
        val t = raw.trim()
        if (t.contains('-') && !t.startsWith("-")) return null
        val norm = normalizeNumericToken(t)
        if (norm.isBlank()) return null
        val v = norm.toDoubleOrNull() ?: return null
        return norm to v
    }

    private fun isLikelyUtmEasting(value: Double): Boolean = value in 100_000.0..999_999.999

    private fun isLikelyUtmNorthing(value: Double): Boolean = value in 1_000_000.0..9_999_999.999

    private fun isLikelyUtmCoord(value: Double): Boolean =
        isLikelyUtmEasting(value) || isLikelyUtmNorthing(value)

    private fun isLikelyGeoLatLon(value: Double): Boolean = value in 35.0..53.0

    private fun isPlausibleCoordNumber(value: Double, rawNorm: String): Boolean {
        if (isLikelyUtmCoord(value)) return true
        if (isLikelyGeoLatLon(value) && rawNorm.contains('.') && rawNorm.substringAfter('.').length >= 3) return true
        return false
    }

    fun detectUtmZoneHint(rawText: String): Int {
        if (rawText.contains(Regex("""\b39\s*[TN]\b""", RegexOption.IGNORE_CASE))) return 39
        return 38
    }

    fun extractAreaHa(rawText: String): Double? {
        if (rawText.isBlank()) return null
        val haRegex = Regex("""(\d+(?:[.,]\d+)?)\s*(?:ha|hektar|га)\b""", RegexOption.IGNORE_CASE)
        haRegex.find(rawText)?.groupValues?.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull()?.let {
            if (it in 0.0001..100_000.0) return it
        }
        val labelRegex = Regex(
            """(?:sah[əe]si|sah[əe]|area|площадь)[^\d]{0,30}(\d+[.,]\d{2,4})""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        labelRegex.find(rawText)?.groupValues?.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull()?.let {
            if (it in 0.0001..100_000.0) return it
        }
        if (rawText.contains("sah", ignoreCase = true) || rawText.contains("kadastr", ignoreCase = true)) {
            val fourDec = Regex("""\b(\d{1,3}[.,]\d{4})\b""").findAll(rawText)
                .mapNotNull { it.groupValues[1].replace(',', '.').toDoubleOrNull() }
                .firstOrNull { it in 0.01..50_000.0 }
            if (fourDec != null) return fourDec
        }
        return null
    }

    fun parseSpatialTokens(
        tokens: List<OcrToken>,
        fullTextHint: String = "",
    ): ParsedCadastralDocument? = parseSpatialTokensScored(tokens, fullTextHint)?.document

    fun parseSpatialTokensScored(
        tokens: List<OcrToken>,
        fullTextHint: String = "",
    ): ScoredCadastralParse? {
        if (tokens.isEmpty()) {
            return parseRawText(fullTextHint)?.let {
                ScoredCadastralParse(it, scoreDocument(it, fullTextHint, hasAlignedHeaders = false, hasIndexOrder = false))
            }
        }

        val combinedText = if (fullTextHint.isNotBlank()) fullTextHint else tokens.joinToString(" ") { it.text }
        val areaHa = extractAreaHa(combinedText)
        val zoneHint = detectUtmZoneHint(combinedText)

        // Locate upright X and Y column headers that sit on roughly the same horizontal row, with Y to the right of X
        val xHeaders = tokens.filter { isColumnHeaderToken(it.text, "X") }
        val yHeaders = tokens.filter { isColumnHeaderToken(it.text, "Y") }

        var bestExplicit: ScoredCadastralParse? = null
        for (xh in xHeaders) {
            for (yh in yHeaders) {
                val dx = yh.centerX - xh.centerX
                val minGap = maxOf(xh.height, yh.height, 18f) * 1.1f
                if (dx <= minGap) continue
                val dy = abs(xh.centerY - yh.centerY)
                val maxDy = maxOf(xh.height, yh.height, 24f) * 2.4f
                if (dy > maxDy) continue

                val headerBottomY = maxOf(xh.centerY, yh.centerY) +
                    maxOf(xh.height, yh.height) * 0.2f
                val colSpan = dx.coerceAtLeast(36f)
                val midXY = (xh.centerX + yh.centerX) / 2f

                // Optional left corner-number column ("1..N") and right boundary before "Nöqtələr"/"Məsafə"
                val stopHeaders = tokens.filter { tok ->
                    val lower = tok.text.trim().lowercase(Locale.US)
                    (lower.startsWith("nöqt") || lower.startsWith("noqt") ||
                        lower.startsWith("məsaf") || lower.startsWith("mesaf")) &&
                        tok.centerX > yh.centerX
                }
                val minStopX = stopHeaders.minOfOrNull { it.centerX }
                val leftBound = xh.centerX - colSpan * 0.6f
                val rightBound = if (minStopX != null) {
                    minOf(yh.centerX + colSpan * 0.65f, (yh.centerX + minStopX) / 2f + colSpan * 0.1f)
                } else {
                    yh.centerX + colSpan * 0.72f
                }

                // Merge horizontally split fragments inside each column cell (e.g. "703194." + "12" -> "703194.12")
                val rawXColTokens = mergeColumnFragments(
                    tokens.filter { it.centerY > headerBottomY && it.centerX in leftBound..midXY }
                )
                val rawYColTokens = mergeColumnFragments(
                    tokens.filter { it.centerY > headerBottomY && it.centerX > midXY && it.centerX <= rightBound }
                )

                val xCol = rawXColTokens.mapNotNull { tok ->
                    val parsed = parseCoordinateCandidate(tok.text) ?: return@mapNotNull null
                    if (!isPlausibleCoordNumber(parsed.second, parsed.first)) return@mapNotNull null
                    Triple(tok, parsed.first, parsed.second)
                }.sortedBy { it.first.centerY }

                val yCol = rawYColTokens.mapNotNull { tok ->
                    val parsed = parseCoordinateCandidate(tok.text) ?: return@mapNotNull null
                    if (!isPlausibleCoordNumber(parsed.second, parsed.first)) return@mapNotNull null
                    Triple(tok, parsed.first, parsed.second)
                }.sortedBy { it.first.centerY }

                val indexTokens = tokens.mapNotNull { tok ->
                    if (tok.centerY <= headerBottomY || tok.centerX >= leftBound) return@mapNotNull null
                    val idxVal = tok.text.trim().toIntOrNull() ?: return@mapNotNull null
                    if (idxVal !in 1..500) return@mapNotNull null
                    tok to idxVal
                }.sortedBy { it.first.centerY }

                val pairs = pairColumnsByRow(xCol, yCol, indexTokens)
                val classified = classifyPairs(pairs, areaHa, zoneHint)
                if (classified != null) {
                    val hasSequentialIdx = indexTokens.size >= 3 &&
                        indexTokens.zipWithNext().all { (a, b) -> b.second > a.second }
                    val candidateScore = scoreDocument(
                        doc = classified,
                        rawText = combinedText,
                        hasAlignedHeaders = true,
                        hasIndexOrder = hasSequentialIdx,
                    ) - (dy / maxOf(xh.height, 20f)) * 10.0
                    if (bestExplicit == null || candidateScore > bestExplicit.score) {
                        bestExplicit = ScoredCadastralParse(classified, candidateScore)
                    }
                }
            }
        }
        if (bestExplicit != null) return bestExplicit

        // Headerless spatial column fallback (strict homogeneity enforced inside classifyPairs!)
        val coordTokens = mergeColumnFragments(tokens).mapNotNull { tok ->
            val parsed = parseCoordinateCandidate(tok.text) ?: return@mapNotNull null
            if (!isPlausibleCoordNumber(parsed.second, parsed.first)) return@mapNotNull null
            Triple(tok, parsed.first, parsed.second)
        }
        if (coordTokens.size >= 6) {
            val minX = coordTokens.minOf { it.first.centerX }
            val maxX = coordTokens.maxOf { it.first.centerX }
            if (maxX - minX > 36f) {
                val splitX = (minX + maxX) / 2f
                val leftCol = coordTokens.filter { it.first.centerX <= splitX }.sortedBy { it.first.centerY }
                val rightCol = coordTokens.filter { it.first.centerX > splitX }.sortedBy { it.first.centerY }
                val pairs = pairColumnsByRow(leftCol, rightCol)
                classifyPairs(pairs, areaHa, zoneHint)?.let { doc ->
                    return ScoredCadastralParse(
                        doc,
                        scoreDocument(doc, combinedText, hasAlignedHeaders = false, hasIndexOrder = false),
                    )
                }
            }
        }

        return parseRawText(combinedText)?.let {
            ScoredCadastralParse(
                it,
                scoreDocument(it, combinedText, hasAlignedHeaders = false, hasIndexOrder = false) - 100.0,
            )
        }
    }

    private fun isColumnHeaderToken(text: String, letter: String): Boolean {
        val cleaned = text.trim().replace(Regex("[^A-Za-z]"), "")
        return cleaned.equals(letter, ignoreCase = true)
    }

    private fun mergeColumnFragments(colTokens: List<OcrToken>): List<OcrToken> {
        if (colTokens.size <= 1) return colTokens
        val avgH = colTokens.map { it.height.coerceAtLeast(12f) }.average().toFloat()
        val rowTol = avgH * 0.55f
        val sorted = colTokens.sortedWith(compareBy<OcrToken> { it.centerY }.thenBy { it.centerX })
        val merged = mutableListOf<OcrToken>()

        var i = 0
        while (i < sorted.size) {
            var cur = sorted[i]
            var j = i + 1
            while (j < sorted.size) {
                val next = sorted[j]
                if (abs(next.centerY - cur.centerY) <= rowTol &&
                    abs(next.centerX - cur.centerX) <= avgH * 5.0f &&
                    shouldGlueNumericFragments(cur.text, next.text)
                ) {
                    val glued = "${cur.text.trim()}${next.text.trim()}"
                    cur = OcrToken(
                        text = glued,
                        centerX = (cur.centerX + next.centerX) / 2f,
                        centerY = (cur.centerY + next.centerY) / 2f,
                        height = maxOf(cur.height, next.height),
                    )
                    j++
                } else {
                    break
                }
            }
            merged.add(cur)
            i = j
        }
        return merged
    }

    private fun shouldGlueNumericFragments(left: String, right: String): Boolean {
        val l = left.trim()
        val r = right.trim()
        if (l.isEmpty() || r.isEmpty()) return false
        val lEndsPunct = l.last() == '.' || l.last() == ','
        val rStartsPunct = r.first() == '.' || r.first() == ','
        val lDigits = l.all { it.isDigit() || it == '.' || it == ',' }
        val rDigits = r.all { it.isDigit() || it == '.' || it == ',' }
        if (!lDigits || !rDigits) return false
        if (lEndsPunct && r.all { it.isDigit() } && r.length <= 4) return true
        if (l.all { it.isDigit() } && l.length in 5..7 && rStartsPunct) return true
        if (l.all { it.isDigit() } && l.length in 6..7 && r.all { it.isDigit() } && r.length in 1..2) return true
        return false
    }

    private fun pairColumnsByRow(
        xCol: List<Triple<OcrToken, String, Double>>,
        yCol: List<Triple<OcrToken, String, Double>>,
        indexTokens: List<Pair<OcrToken, Int>> = emptyList(),
    ): List<Pair<String, String>> {
        if (xCol.isEmpty() || yCol.isEmpty()) return emptyList()
        val avgHeight = (xCol + yCol).map { it.first.height.coerceAtLeast(14f) }.average().toFloat()
        val rowTolerance = avgHeight * 1.45f

        val usedY = BooleanArray(yCol.size)
        val matchedWithMeta = mutableListOf<Triple<Float, String, String>>()

        xCol.forEach { (xTok, xRaw, _) ->
            var bestIdx = -1
            var bestDy = Float.MAX_VALUE
            for (j in yCol.indices) {
                if (usedY[j]) continue
                val dy = abs(xTok.centerY - yCol[j].first.centerY)
                if (dy <= rowTolerance && dy < bestDy) {
                    bestDy = dy
                    bestIdx = j
                }
            }
            if (bestIdx >= 0) {
                usedY[bestIdx] = true
                val avgRowY = (xTok.centerY + yCol[bestIdx].first.centerY) / 2f
                matchedWithMeta.add(Triple(avgRowY, xRaw, yCol[bestIdx].second))
            }
        }

        if (matchedWithMeta.size >= 3) {
            if (indexTokens.size >= 3) {
                return matchedWithMeta.map { item ->
                    val nearestIdx = indexTokens
                        .minByOrNull { abs(it.first.centerY - item.first) }
                        ?.takeIf { abs(it.first.centerY - item.first) <= rowTolerance * 1.3f }
                        ?.second
                    (nearestIdx ?: Int.MAX_VALUE) to item
                }
                    .sortedWith(compareBy<Pair<Int, Triple<Float, String, String>>> { it.first }.thenBy { it.second.first })
                    .map { it.second.second to it.second.third }
            }
            return matchedWithMeta.sortedBy { it.first }.map { it.second to it.third }
        }
        if (xCol.size == yCol.size && xCol.size >= 3) {
            return xCol.zip(yCol).map { (xItem, yItem) -> xItem.second to yItem.second }
        }
        return matchedWithMeta.map { it.second to it.third }
    }

    fun parseRawText(rawText: String): ParsedCadastralDocument? {
        if (rawText.isBlank()) return null
        val areaHa = extractAreaHa(rawText)
        val zoneHint = detectUtmZoneHint(rawText)

        // Strategy 1: row-by-row pairs (two plausible coordinate numbers per line, skipping index column 1..N)
        val rowPairs = mutableListOf<Pair<String, String>>()
        rawText.lines().forEach { line ->
            val clean = line.trim()
            if (clean.isEmpty()) return@forEach
            val numbers = Regex("""\d+(?:[.,]\d+)?""").findAll(clean)
                .map { it.value }
                .mapNotNull { token -> parseCoordinateCandidate(token) }
                .filter { (norm, value) -> isPlausibleCoordNumber(value, norm) }
                .toList()
            if (numbers.size >= 2) {
                rowPairs.add(numbers[0].first to numbers[1].first)
            }
        }
        classifyPairs(rowPairs, areaHa, zoneHint)?.let { return it }

        // Strategy 2: column-wise OCR where X values appear as a contiguous block followed by Y values
        val allNums = Regex("""\d+(?:[.,]\d+)?""").findAll(rawText)
            .map { it.value }
            .mapNotNull { parseCoordinateCandidate(it) }
            .filter { (norm, value) -> isPlausibleCoordNumber(value, norm) }
            .toList()

        if (allNums.size >= 6) {
            val utmEasting = allNums.filter { isLikelyUtmEasting(it.second) }
            val utmNorthing = allNums.filter { isLikelyUtmNorthing(it.second) }
            if (utmEasting.size >= 3 && utmEasting.size == utmNorthing.size) {
                val firstEastingIdx = allNums.indexOfFirst { isLikelyUtmEasting(it.second) }
                val firstNorthingIdx = allNums.indexOfFirst { isLikelyUtmNorthing(it.second) }
                val pairs = if (firstEastingIdx <= firstNorthingIdx) {
                    utmEasting.zip(utmNorthing).map { (e, n) -> e.first to n.first }
                } else {
                    utmNorthing.zip(utmEasting).map { (n, e) -> n.first to e.first }
                }
                classifyPairs(pairs, areaHa, zoneHint)?.let { return it }
            }

            if (allNums.size % 2 == 0) {
                val half = allNums.size / 2
                val firstHalf = allNums.subList(0, half)
                val secondHalf = allNums.subList(half, allNums.size)
                val pairs = firstHalf.zip(secondHalf).map { (a, b) -> a.first to b.first }
                classifyPairs(pairs, areaHa, zoneHint)?.let { return it }
            }
        }

        return null
    }

    fun classifyPairs(
        rawPairs: List<Pair<String, String>>,
        areaHaHint: Double? = null,
        utmZoneHint: Int = 38,
    ): ParsedCadastralDocument? {
        if (rawPairs.size < 3) return null

        val numericPairs = rawPairs.mapNotNull { (rx, ry) ->
            val nx = normalizeNumericToken(rx)
            val ny = normalizeNumericToken(ry)
            val xv = nx.toDoubleOrNull() ?: return@mapNotNull null
            val yv = ny.toDoubleOrNull() ?: return@mapNotNull null
            Quad(nx, ny, xv, yv)
        }
        if (numericPairs.size < 3) return null

        // Case 1: Geographic Lat/Lon pairs (e.g., 40.678210, 46.360610)
        val geoCandidates = numericPairs.filter {
            abs(it.xVal) <= 90.0 && abs(it.yVal) <= 180.0 && abs(it.xVal) >= 1.0 && abs(it.yVal) >= 1.0
        }
        if (geoCandidates.size == numericPairs.size) {
            val medLat = medianOf(geoCandidates.map { it.xVal })
            val medLon = medianOf(geoCandidates.map { it.yVal })
            val consistent = geoCandidates.filter {
                abs(it.xVal - medLat) <= 0.45 && abs(it.yVal - medLon) <= 0.45
            }
            if (consistent.size >= 3) {
                val pts = consistent.map { q ->
                    ParsedDocCoordinate(
                        rawX = q.rawX,
                        rawY = q.rawY,
                        xVal = q.xVal,
                        yVal = q.yVal,
                        latLon = LatLon(q.xVal, q.yVal),
                    )
                }
                val poly = pts.map { it.latLon }
                val calcMetrics = GeoUtils.calculateMeasurements(poly)
                if (calcMetrics.areaHa > 0.0001 && calcMetrics.areaHa < 100_000.0) {
                    val area = areaHaHint ?: calcMetrics.areaHa.takeIf { it > 0 }
                    val latLonText = poly.joinToString("\n") { p ->
                        String.format(Locale.US, "%.6f, %.6f", p.lat, p.lon)
                    }
                    return ParsedCadastralDocument(
                        target = DocCoordTarget.LAT_LON,
                        utmZone = utmZoneHint,
                        points = pts,
                        polygon = poly,
                        areaHa = area,
                        utmLinesText = "",
                        latLonLinesText = latLonText,
                        rawPairs = pts.map { it.rawX to it.rawY },
                    )
                }
            }
            return null
        }

        // Reject mixed geo + meter tables
        if (geoCandidates.isNotEmpty() && numericPairs.size - geoCandidates.size < 3) {
            return null
        }

        // Case 2: Swapped Cadastral UTM where X is 7-digit Northing starting with 4 (4xxxxxx)
        // and Y is 6-digit Easting (100000..999999) -> user rule: X starting with 4 goes to Lat/Lon section!
        val swappedRows = numericPairs.filter {
            isLikelyUtmNorthing(it.xVal) && isLikelyUtmEasting(it.yVal) && it.rawX.trimStart('-').startsWith("4")
        }
        if (swappedRows.size >= 3 && swappedRows.size * 3 >= numericPairs.size * 2) {
            val filtered = filterUtmClusterOutliers(swappedRows)
            if (filtered.size >= 3) {
                val pts = filtered.mapNotNull { q ->
                    val ll = GeoUtils.utmToLatLon(easting = q.yVal, northing = q.xVal, zone = utmZoneHint)
                    if (ll.lat.isNaN() || ll.lon.isNaN() || abs(ll.lat) > 85.0) return@mapNotNull null
                    ParsedDocCoordinate(
                        rawX = String.format(Locale.US, "%.6f", ll.lat),
                        rawY = String.format(Locale.US, "%.6f", ll.lon),
                        xVal = ll.lat,
                        yVal = ll.lon,
                        latLon = ll,
                    )
                }
                if (pts.size >= 3) {
                    val poly = pts.map { it.latLon }
                    val calcMetrics = GeoUtils.calculateMeasurements(poly)
                    if (calcMetrics.areaHa in 0.0001..50_000.0) {
                        val area = areaHaHint ?: calcMetrics.areaHa.takeIf { it > 0 }
                        val latLonText = poly.joinToString("\n") { p ->
                            String.format(Locale.US, "%.6f, %.6f", p.lat, p.lon)
                        }
                        return ParsedCadastralDocument(
                            target = DocCoordTarget.LAT_LON,
                            utmZone = utmZoneHint,
                            points = pts,
                            polygon = poly,
                            areaHa = area,
                            utmLinesText = "",
                            latLonLinesText = latLonText,
                            rawPairs = pts.map { it.rawX to it.rawY },
                        )
                    }
                }
            }
        }

        // Case 3: Standard UTM (X = 6-digit Easting 100k..999k, Y = 7-digit Northing 1M..9.99M)
        // Strict homogeneity: NEVER allow columns that mix 6-digit Eastings and 7-digit Northings!
        val standardUtmRows = numericPairs.filter {
            isLikelyUtmEasting(it.xVal) && isLikelyUtmNorthing(it.yVal)
        }
        if (standardUtmRows.size >= 3 && standardUtmRows.size * 3 >= numericPairs.size * 2) {
            val filtered = filterUtmClusterOutliers(standardUtmRows)
            if (filtered.size >= 3) {
                val pts = filtered.mapNotNull { q ->
                    val ll = GeoUtils.utmToLatLon(easting = q.xVal, northing = q.yVal, zone = utmZoneHint)
                    if (ll.lat.isNaN() || ll.lon.isNaN() || abs(ll.lat) > 85.0) return@mapNotNull null
                    ParsedDocCoordinate(
                        rawX = q.rawX,
                        rawY = q.rawY,
                        xVal = q.xVal,
                        yVal = q.yVal,
                        latLon = ll,
                    )
                }
                if (pts.size >= 3) {
                    val poly = pts.map { it.latLon }
                    val calcMetrics = GeoUtils.calculateMeasurements(poly)
                    if (calcMetrics.areaHa in 0.0001..50_000.0) {
                        val area = areaHaHint ?: calcMetrics.areaHa.takeIf { it > 0 }
                        val utmLines = pts.joinToString("\n") { "${it.rawX}, ${it.rawY}" }
                        return ParsedCadastralDocument(
                            target = DocCoordTarget.UTM_38T,
                            utmZone = utmZoneHint,
                            points = pts,
                            polygon = poly,
                            areaHa = area,
                            utmLinesText = utmLines,
                            latLonLinesText = "",
                            rawPairs = pts.map { it.rawX to it.rawY },
                        )
                    }
                }
            }
        }

        return null
    }

    private fun filterUtmClusterOutliers(rows: List<Quad>): List<Quad> {
        if (rows.size < 3) return emptyList()
        val medX = medianOf(rows.map { it.xVal })
        val medY = medianOf(rows.map { it.yVal })
        val cluster = rows.filter { abs(it.xVal - medX) <= 35_000.0 && abs(it.yVal - medY) <= 35_000.0 }
        if (cluster.size < 3) return emptyList()
        val spanX = cluster.maxOf { it.xVal } - cluster.minOf { it.xVal }
        val spanY = cluster.maxOf { it.yVal } - cluster.minOf { it.yVal }
        if (spanX > 50_000.0 || spanY > 50_000.0) return emptyList()
        return cluster
    }

    private fun medianOf(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
    }

    private fun scoreDocument(
        doc: ParsedCadastralDocument,
        rawText: String,
        hasAlignedHeaders: Boolean,
        hasIndexOrder: Boolean,
    ): Double {
        var score = 0.0
        if (hasAlignedHeaders) score += 1000.0
        if (hasIndexOrder) score += 250.0
        score += doc.points.size * 45.0

        val lower = rawText.lowercase(Locale.US)
        val keywords = listOf("koordinat", "nömr", "nomr", "nöqtə", "noqt", "məsaf", "mesaf", "sahə", "sahe", "kadastr")
        score += keywords.count { lower.contains(it) } * 120.0

        // Precision bonus: more numbers with decimal points preserved (avoids losing ".12")
        val decimalBonus = doc.rawPairs.count { (x, y) -> x.contains('.') && y.contains('.') }
        score += decimalBonus * 12.0

        // Geographic check against printed Sahəsi area
        val calcArea = GeoUtils.calculateMeasurements(doc.polygon).areaHa
        val hintArea = doc.areaHa
        if (hintArea != null && hintArea > 0.0 && calcArea > 0.0) {
            val ratio = maxOf(calcArea, hintArea) / minOf(calcArea, hintArea)
            score += when {
                ratio <= 1.35 -> 600.0
                ratio <= 2.5 -> 250.0
                ratio > 10.0 -> -500.0
                else -> 0.0
            }
        }
        return score
    }

    private data class Quad(
        val rawX: String,
        val rawY: String,
        val xVal: Double,
        val yVal: Double,
    )
}
