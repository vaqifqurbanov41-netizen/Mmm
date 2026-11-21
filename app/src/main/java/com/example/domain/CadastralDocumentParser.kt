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
        // Collapse multiple dots if OCR inserted stray punctuation, keeping last decimal dot
        val firstDot = cleaned.indexOf('.')
        val lastDot = cleaned.lastIndexOf('.')
        return if (firstDot != lastDot && lastDot > 0) {
            val intPart = cleaned.substring(0, lastDot).replace(".", "")
            val decPart = cleaned.substring(lastDot)
            "$intPart$decPart"
        } else {
            cleaned
        }
    }

    private fun parseCoordinateCandidate(raw: String): Pair<String, Double>? {
        // Ignore obvious hyphenated point labels like "1-2", "8-1"
        val t = raw.trim()
        if (t.contains('-') && !t.startsWith("-")) return null
        val norm = normalizeNumericToken(t)
        if (norm.isBlank()) return null
        val v = norm.toDoubleOrNull() ?: return null
        // Geographic latitudes/longitudes in AZ/region or UTM coordinates are >= 30
        // Distance column values like 15.24, 20.24, 13.67, 27.16 are < 1000 and should not be
        // confused with UTM (>= 100,000) or AZ geographic lat/lon (35..52 with >=3 decimals)
        return norm to v
    }

    private fun isLikelyUtmCoord(value: Double): Boolean = value >= 100_000.0 && value <= 10_000_000.0

    private fun isLikelyGeoLatLon(value: Double): Boolean = value in 35.0..53.0

    private fun isPlausibleCoordNumber(value: Double, rawNorm: String): Boolean {
        if (isLikelyUtmCoord(value)) return true
        if (isLikelyGeoLatLon(value) && rawNorm.contains('.') && rawNorm.substringAfter('.').length >= 3) return true
        return false
    }

    fun extractAreaHa(rawText: String): Double? {
        if (rawText.isBlank()) return null
        // 1. Explicit number with "ha" / "hektar" / "га"
        val haRegex = Regex("""(\d+(?:[.,]\d+)?)\s*(?:ha|hektar|га)\b""", RegexOption.IGNORE_CASE)
        haRegex.find(rawText)?.groupValues?.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull()?.let {
            if (it in 0.0001..100_000.0) return it
        }
        // 2. Following "Sahəsi" / "Sahesi" / "Area" / "Площадь" within next ~40 chars
        val labelRegex = Regex(
            """(?:sah[əe]si|sah[əe]|area|площадь)[^\d]{0,30}(\d+[.,]\d{2,4})""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        labelRegex.find(rawText)?.groupValues?.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull()?.let {
            if (it in 0.0001..100_000.0) return it
        }
        // 3. Nearby number with 4 decimal places typical for cadastral hectare fields (e.g. 1.6069)
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
    ): ParsedCadastralDocument? {
        if (tokens.isEmpty()) return parseRawText(fullTextHint)

        val areaHa = extractAreaHa(
            if (fullTextHint.isNotBlank()) fullTextHint else tokens.joinToString(" ") { it.text }
        )

        // Locate X and Y column headers that sit on roughly the same horizontal row
        val xHeaders = tokens.filter { it.text.trim().equals("X", ignoreCase = true) }
        val yHeaders = tokens.filter { it.text.trim().equals("Y", ignoreCase = true) }

        var chosenXHeader: OcrToken? = null
        var chosenYHeader: OcrToken? = null
        var bestHeaderScore = Float.MAX_VALUE
        for (xh in xHeaders) {
            for (yh in yHeaders) {
                if (yh.centerX <= xh.centerX) continue
                val dy = abs(xh.centerY - yh.centerY)
                val maxDy = maxOf(xh.height, yh.height, 24f) * 2.2f
                if (dy <= maxDy && dy < bestHeaderScore) {
                    bestHeaderScore = dy
                    chosenXHeader = xh
                    chosenYHeader = yh
                }
            }
        }

        if (chosenXHeader != null && chosenYHeader != null) {
            val headerBottomY = maxOf(chosenXHeader.centerY, chosenYHeader.centerY) +
                maxOf(chosenXHeader.height, chosenYHeader.height) * 0.25f
            val colSpan = (chosenYHeader.centerX - chosenXHeader.centerX).coerceAtLeast(30f)
            val midXY = (chosenXHeader.centerX + chosenYHeader.centerX) / 2f

            // Optional right-side guard before "Nöqtələr" / "Məsafə" columns
            val stopHeaders = tokens.filter { tok ->
                val lower = tok.text.trim().lowercase(Locale.US)
                (lower.startsWith("nöqt") || lower.startsWith("noqt") ||
                    lower.startsWith("məsaf") || lower.startsWith("mesaf")) &&
                    tok.centerX > chosenYHeader.centerX
            }
            val minStopX = stopHeaders.minOfOrNull { it.centerX }
            val leftBound = chosenXHeader.centerX - colSpan * 0.65f
            val rightBound = if (minStopX != null) {
                minOf(chosenYHeader.centerX + colSpan * 0.7f, (chosenYHeader.centerX + minStopX) / 2f + colSpan * 0.15f)
            } else {
                chosenYHeader.centerX + colSpan * 0.75f
            }

            val dataCandidates = tokens.mapNotNull { tok ->
                if (tok.centerY <= headerBottomY) return@mapNotNull null
                if (tok.centerX < leftBound || tok.centerX > rightBound) return@mapNotNull null
                val parsed = parseCoordinateCandidate(tok.text) ?: return@mapNotNull null
                if (!isPlausibleCoordNumber(parsed.second, parsed.first)) return@mapNotNull null
                Triple(tok, parsed.first, parsed.second)
            }

            val xCol = dataCandidates
                .filter { (tok, _, _) -> tok.centerX <= midXY }
                .sortedBy { it.first.centerY }
            val yCol = dataCandidates
                .filter { (tok, _, _) -> tok.centerX > midXY }
                .sortedBy { it.first.centerY }

            val pairs = pairColumnsByRow(xCol, yCol)
            classifyPairs(pairs, areaHa)?.let { return it }
        }

        // Fallback: infer left/right columns directly from plausible coordinate tokens without header
        val coordTokens = tokens.mapNotNull { tok ->
            val parsed = parseCoordinateCandidate(tok.text) ?: return@mapNotNull null
            if (!isPlausibleCoordNumber(parsed.second, parsed.first)) return@mapNotNull null
            Triple(tok, parsed.first, parsed.second)
        }
        if (coordTokens.size >= 6) {
            val minX = coordTokens.minOf { it.first.centerX }
            val maxX = coordTokens.maxOf { it.first.centerX }
            if (maxX - minX > 30f) {
                val splitX = (minX + maxX) / 2f
                val leftCol = coordTokens.filter { it.first.centerX <= splitX }.sortedBy { it.first.centerY }
                val rightCol = coordTokens.filter { it.first.centerX > splitX }.sortedBy { it.first.centerY }
                val pairs = pairColumnsByRow(leftCol, rightCol)
                classifyPairs(pairs, areaHa)?.let { return it }
            }
        }

        return parseRawText(fullTextHint)
    }

    private fun pairColumnsByRow(
        xCol: List<Triple<OcrToken, String, Double>>,
        yCol: List<Triple<OcrToken, String, Double>>,
    ): List<Pair<String, String>> {
        if (xCol.isEmpty() || yCol.isEmpty()) return emptyList()
        val avgHeight = (xCol + yCol).map { it.first.height.coerceAtLeast(16f) }.average().toFloat()
        val rowTolerance = avgHeight * 1.45f

        val usedY = BooleanArray(yCol.size)
        val matched = mutableListOf<Pair<String, String>>()

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
                matched.add(xRaw to yCol[bestIdx].second)
            }
        }

        if (matched.size >= 3) return matched
        if (xCol.size == yCol.size && xCol.size >= 3) {
            return xCol.zip(yCol).map { (xItem, yItem) -> xItem.second to yItem.second }
        }
        return matched
    }

    fun parseRawText(rawText: String): ParsedCadastralDocument? {
        if (rawText.isBlank()) return null
        val areaHa = extractAreaHa(rawText)

        // Strategy 1: row-by-row pairs (two or more numbers per line, skipping index column 1..8)
        val rowPairs = mutableListOf<Pair<String, String>>()
        rawText.lines().forEach { line ->
            val clean = line.trim()
            if (clean.isEmpty()) return@forEach
            val numbers = Regex("""\d+(?:[.,]\d+)?""").findAll(clean)
                .map { it.value }
                .mapNotNull { token -> parseCoordinateCandidate(token)?.let { it.first to it.second } }
                .filter { (norm, value) -> isPlausibleCoordNumber(value, norm) }
                .toList()
            if (numbers.size >= 2) {
                // Take the first two plausible coordinate numbers on that row (X then Y)
                rowPairs.add(numbers[0].first to numbers[1].first)
            }
        }
        classifyPairs(rowPairs, areaHa)?.let { return it }

        // Strategy 2: column-wise OCR where X values appear as a block followed by Y values
        val allNums = Regex("""\d+(?:[.,]\d+)?""").findAll(rawText)
            .map { it.value }
            .mapNotNull { parseCoordinateCandidate(it) }
            .filter { (norm, value) -> isPlausibleCoordNumber(value, norm) }
            .toList()

        if (allNums.size >= 6) {
            // Look for two equal-sized consecutive sublists or Easting block + Northing block
            val utmEasting = allNums.filter { it.second in 100_000.0..999_999.9 }
            val utmNorthing = allNums.filter { it.second in 1_000_000.0..9_999_999.9 }
            if (utmEasting.size >= 3 && utmEasting.size == utmNorthing.size) {
                val firstEastingIdx = allNums.indexOfFirst { it.second in 100_000.0..999_999.9 }
                val firstNorthingIdx = allNums.indexOfFirst { it.second in 1_000_000.0..9_999_999.9 }
                val pairs = if (firstEastingIdx <= firstNorthingIdx) {
                    utmEasting.zip(utmNorthing).map { (e, n) -> e.first to n.first }
                } else {
                    utmNorthing.zip(utmEasting).map { (n, e) -> n.first to e.first }
                }
                classifyPairs(pairs, areaHa)?.let { return it }
            }

            if (allNums.size % 2 == 0) {
                val half = allNums.size / 2
                val firstHalf = allNums.subList(0, half)
                val secondHalf = allNums.subList(half, allNums.size)
                val pairs = firstHalf.zip(secondHalf).map { (a, b) -> a.first to b.first }
                classifyPairs(pairs, areaHa)?.let { return it }
            }
        }

        return null
    }

    fun classifyPairs(
        rawPairs: List<Pair<String, String>>,
        areaHaHint: Double? = null,
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

        val avgX = numericPairs.map { it.xVal }.average()
        val avgY = numericPairs.map { it.yVal }.average()

        val geoPairs = numericPairs.all { abs(it.xVal) <= 90.0 && abs(it.yVal) <= 180.0 }
        val xStartsWithFourCount = numericPairs.count { it.rawX.trimStart('-').startsWith("4") }
        val majorityXStartsWithFour = xStartsWithFourCount * 2 >= numericPairs.size

        // Case 1: Geographic Lat/Lon pairs (e.g., X starts with 4 like 40.6782, Y = 46.3606)
        if (geoPairs) {
            val pts = numericPairs.map { q ->
                ParsedDocCoordinate(
                    rawX = q.rawX,
                    rawY = q.rawY,
                    xVal = q.xVal,
                    yVal = q.yVal,
                    latLon = LatLon(q.xVal, q.yVal),
                )
            }
            val poly = pts.map { it.latLon }
            val area = areaHaHint ?: GeoUtils.calculateMeasurements(poly).areaHa.takeIf { it > 0 }
            val latLonText = poly.joinToString("\n") { p ->
                String.format(Locale.US, "%.6f, %.6f", p.lat, p.lon)
            }
            return ParsedCadastralDocument(
                target = DocCoordTarget.LAT_LON,
                utmZone = 38,
                points = pts,
                polygon = poly,
                areaHa = area,
                utmLinesText = "",
                latLonLinesText = latLonText,
            )
        }

        // Case 2: Swapped Cadastral Gauss-Kruger / UTM where X is 7-digit Northing starting with 4 (4xxxxxx)
        // and Y is 6-digit Easting (100000..999999) -> user rule: X starting with 4 goes to Lat/Lon section!
        val swappedUtmInX = majorityXStartsWithFour &&
            avgX in 1_000_000.0..9_999_999.0 &&
            avgY in 100_000.0..999_999.0

        if (swappedUtmInX) {
            val pts = numericPairs.mapNotNull { q ->
                val ll = GeoUtils.utmToLatLon(easting = q.yVal, northing = q.xVal, zone = 38)
                if (ll.lat.isNaN() || ll.lon.isNaN() || abs(ll.lat) > 85.0) return@mapNotNull null
                ParsedDocCoordinate(
                    rawX = q.rawX,
                    rawY = q.rawY,
                    xVal = q.xVal,
                    yVal = q.yVal,
                    latLon = ll,
                )
            }
            if (pts.size < 3) return null
            val poly = pts.map { it.latLon }
            val area = areaHaHint ?: GeoUtils.calculateMeasurements(poly).areaHa.takeIf { it > 0 }
            val latLonText = poly.joinToString("\n") { p ->
                String.format(Locale.US, "%.6f, %.6f", p.lat, p.lon)
            }
            return ParsedCadastralDocument(
                target = DocCoordTarget.LAT_LON,
                utmZone = 38,
                points = pts,
                polygon = poly,
                areaHa = area,
                utmLinesText = "",
                latLonLinesText = latLonText,
            )
        }

        // Case 3: Standard UTM (X = Easting ~100000..999999, Y = Northing ~1000000..9999999) -> UTM Zone 38T
        val eastingFirst = avgX in 100_000.0..999_999.0 && avgY in 1_000_000.0..9_999_999.0
        if (eastingFirst || (avgX >= 10_000.0 && avgY >= 10_000.0)) {
            val pts = numericPairs.mapNotNull { q ->
                val (e, n) = if (q.xVal >= 1_000_000.0 && q.yVal < 1_000_000.0) {
                    q.yVal to q.xVal
                } else {
                    q.xVal to q.yVal
                }
                val ll = GeoUtils.utmToLatLon(easting = e, northing = n, zone = 38)
                if (ll.lat.isNaN() || ll.lon.isNaN() || abs(ll.lat) > 85.0) return@mapNotNull null
                ParsedDocCoordinate(
                    rawX = q.rawX,
                    rawY = q.rawY,
                    xVal = q.xVal,
                    yVal = q.yVal,
                    latLon = ll,
                )
            }
            if (pts.size < 3) return null
            val poly = pts.map { it.latLon }
            val area = areaHaHint ?: GeoUtils.calculateMeasurements(poly).areaHa.takeIf { it > 0 }
            val utmLines = pts.joinToString("\n") { "${it.rawX}, ${it.rawY}" }
            return ParsedCadastralDocument(
                target = DocCoordTarget.UTM_38T,
                utmZone = 38,
                points = pts,
                polygon = poly,
                areaHa = area,
                utmLinesText = utmLines,
                latLonLinesText = "",
            )
        }

        return null
    }

    private data class Quad(
        val rawX: String,
        val rawY: String,
        val xVal: Double,
        val yVal: Double,
    )
}
