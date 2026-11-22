package com.example.domain

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt

data class KmzPlacemark(
    val name: String,
    val description: String = "",
    val points: List<LatLon>,
)

data class KmzDocument(
    val documentName: String,
    val placemarks: List<KmzPlacemark>,
)

object KmzUtils {
    fun buildKmlString(
        documentName: String,
        placemarks: List<KmzPlacemark>,
    ): String {
        val safeDocName = escapeXml(documentName.ifBlank { "AqroMeslehet" })
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n")
        sb.append("  <Document>\n")
        sb.append("    <name>").append(safeDocName).append("</name>\n")
        sb.append("    <Style id=\"aqroFieldStyle\">\n")
        sb.append("      <LineStyle><color>ff2ecc71</color><width>3</width></LineStyle>\n")
        sb.append("      <PolyStyle><color>661b8a5a</color></PolyStyle>\n")
        sb.append("    </Style>\n")

        placemarks.forEachIndexed { idx, pm ->
            if (pm.points.isEmpty()) return@forEachIndexed
            val pName = escapeXml(pm.name.ifBlank { "Sahə ${idx + 1}" })
            val metrics = GeoUtils.calculateMeasurements(pm.points)
            val autoDesc = if (pm.description.isNotBlank()) {
                pm.description
            } else if (pm.points.size >= 3) {
                String.format(
                    Locale.US,
                    "Sahə: %.4f ha (%.1f sot, %d m²), Perimetr: %d m",
                    metrics.areaHa,
                    metrics.areaSot,
                    metrics.areaM2.roundToInt(),
                    metrics.perimeterM.roundToInt(),
                )
            } else {
                "${pm.points.size} nöqtə"
            }

            sb.append("    <Placemark>\n")
            sb.append("      <name>").append(pName).append("</name>\n")
            sb.append("      <description>").append(escapeXml(autoDesc)).append("</description>\n")
            sb.append("      <styleUrl>#aqroFieldStyle</styleUrl>\n")

            when {
                pm.points.size >= 3 -> {
                    val closed = if (pm.points.first() == pm.points.last()) pm.points else pm.points + pm.points.first()
                    val coordText = closed.joinToString(" ") {
                        String.format(Locale.US, "%.6f,%.6f,0", it.lon, it.lat)
                    }
                    sb.append("      <Polygon>\n")
                    sb.append("        <tessellate>1</tessellate>\n")
                    sb.append("        <outerBoundaryIs><LinearRing><coordinates>")
                    sb.append(coordText)
                    sb.append("</coordinates></LinearRing></outerBoundaryIs>\n")
                    sb.append("      </Polygon>\n")
                }
                pm.points.size == 2 -> {
                    val coordText = pm.points.joinToString(" ") {
                        String.format(Locale.US, "%.6f,%.6f,0", it.lon, it.lat)
                    }
                    sb.append("      <LineString><tessellate>1</tessellate><coordinates>")
                    sb.append(coordText)
                    sb.append("</coordinates></LineString>\n")
                }
                else -> {
                    val p = pm.points.first()
                    sb.append("      <Point><coordinates>")
                    sb.append(String.format(Locale.US, "%.6f,%.6f,0", p.lon, p.lat))
                    sb.append("</coordinates></Point>\n")
                }
            }
            sb.append("    </Placemark>\n")
        }

        sb.append("  </Document>\n")
        sb.append("</kml>\n")
        return sb.toString()
    }

    fun buildKmzBytes(
        documentName: String,
        placemarks: List<KmzPlacemark>,
    ): ByteArray {
        val kmlBytes = buildKmlString(documentName, placemarks).toByteArray(StandardCharsets.UTF_8)
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("doc.kml"))
            zos.write(kmlBytes)
            zos.closeEntry()
        }
        return baos.toByteArray()
    }

    fun writeKmzToCacheFile(
        context: Context,
        fileNameBase: String,
        placemarks: List<KmzPlacemark>,
    ): Pair<File, Uri> {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeBase = fileNameBase.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .trim('_')
            .ifBlank { "aqromeslehet_field" }
        val file = File(dir, "$safeBase.kmz")
        val bytes = buildKmzBytes(fileNameBase, placemarks)
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return file to uri
    }

    fun parseKmzOrKml(bytes: ByteArray, fallbackName: String = "KMZ"): KmzDocument {
        val kmlDocuments = extractAllKmlsFromBytes(bytes)
        var resolvedDocName = fallbackName
        val mergedPlacemarks = mutableListOf<KmzPlacemark>()
        for (kmlText in kmlDocuments) {
            val doc = parseKmlString(kmlText, fallbackName)
            if (resolvedDocName == fallbackName && doc.documentName != fallbackName && doc.documentName.isNotBlank()) {
                resolvedDocName = doc.documentName
            }
            mergedPlacemarks += doc.placemarks
        }
        return KmzDocument(documentName = resolvedDocName, placemarks = mergedPlacemarks)
    }

    fun parseKmzUri(context: Context, uri: Uri, fallbackName: String = "KMZ"): KmzDocument? {
        return try {
            val resolvedName = resolveDisplayNameWithoutExt(context, uri) ?: fallbackName
            val bytes = readUriBytes(context, uri) ?: return null
            if (bytes.isEmpty()) return null
            parseKmzOrKml(bytes, resolvedName)
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveDisplayNameWithoutExt(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    val raw = cursor.getString(idx)?.trim().orEmpty()
                    val stripped = raw.replace(Regex("\\.(kmz|kml)$", RegexOption.IGNORE_CASE), "").trim()
                    if (stripped.isNotBlank()) return@runCatching stripped
                }
            }
        }
        null
    }.getOrNull()

    private fun readUriBytes(context: Context, uri: Uri): ByteArray? {
        val streamBytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        if (streamBytes != null && streamBytes.isNotEmpty()) return streamBytes

        return runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).use { it.readBytes() }
            }
        }.getOrNull()
    }

    private fun extractAllKmlsFromBytes(bytes: ByteArray): List<String> {
        val zipOffset = findZipHeaderOffset(bytes)
        if (zipOffset < 0) {
            return listOf(decodeTextSmart(bytes))
        }
        val docKmls = mutableListOf<String>()
        val otherKmls = mutableListOf<String>()
        runCatching {
            ZipInputStream(ByteArrayInputStream(bytes, zipOffset, bytes.size - zipOffset)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val nameClean = entry.name.trim()
                    if (!entry.isDirectory && nameClean.lowercase(Locale.US).endsWith(".kml")) {
                        val content = decodeTextSmart(zis.readBytes())
                        val simpleName = nameClean.substringAfterLast('/')
                        if (simpleName.equals("doc.kml", ignoreCase = true)) {
                            docKmls.add(content)
                        } else {
                            otherKmls.add(content)
                        }
                    }
                    runCatching { zis.closeEntry() }
                    entry = zis.nextEntry
                }
            }
        }
        val combined = docKmls + otherKmls
        if (combined.isNotEmpty()) return combined
        // Fallback: if ZIP header false-matched or ZIP contains raw KML text
        return listOf(decodeTextSmart(bytes))
    }

    private fun findZipHeaderOffset(bytes: ByteArray): Int {
        val limit = minOf(bytes.size - 3, 512)
        for (i in 0 until limit) {
            if (bytes[i] == 0x50.toByte() &&
                bytes[i + 1] == 0x4B.toByte() &&
                bytes[i + 2] == 0x03.toByte() &&
                bytes[i + 3] == 0x04.toByte()
            ) {
                return i
            }
        }
        return -1
    }

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
        return String(bytes, StandardCharsets.UTF_8).removePrefix("\uFEFF")
    }

    fun parseKmlString(kml: String, fallbackName: String = "KMZ"): KmzDocument {
        if (kml.isBlank()) return KmzDocument(fallbackName, emptyList())
        val sanitized = sanitizeKmlForXmlPullParser(kml)
        val xmlDoc = runCatching { parseKmlWithPullParser(sanitized, fallbackName) }.getOrNull()
        if (xmlDoc != null && xmlDoc.placemarks.isNotEmpty()) {
            return xmlDoc
        }
        val fallbackDoc = parseKmlFallbackRegex(kml, xmlDoc?.documentName ?: fallbackName)
        return if (fallbackDoc.placemarks.isNotEmpty()) fallbackDoc else (xmlDoc ?: fallbackDoc)
    }

    private fun sanitizeKmlForXmlPullParser(raw: String): String {
        val stripped = raw.removePrefix("\uFEFF")
        val firstTagIdx = stripped.indexOf('<')
        val xmlBody = if (firstTagIdx > 0) stripped.substring(firstTagIdx) else stripped
        // Escape stray '&' outside CDATA blocks so real-world GIS KML descriptions don't crash XmlPullParser
        val cdataParts = Regex("<!\\[CDATA\\[.*?\\]\\]>", setOf(RegexOption.DOT_MATCHES_ALL))
        val bareAmpRegex = Regex("&(?!(?:amp|lt|gt|quot|apos|#\\d+|#x[0-9a-fA-F]+);)")
        val sb = StringBuilder(xmlBody.length)
        var lastIdx = 0
        for (match in cdataParts.findAll(xmlBody)) {
            if (match.range.first > lastIdx) {
                val outside = xmlBody.substring(lastIdx, match.range.first)
                sb.append(bareAmpRegex.replace(outside, "&amp;"))
            }
            sb.append(match.value)
            lastIdx = match.range.last + 1
        }
        if (lastIdx < xmlBody.length) {
            sb.append(bareAmpRegex.replace(xmlBody.substring(lastIdx), "&amp;"))
        }
        return sb.toString()
    }

    private fun parseKmlWithPullParser(kml: String, fallbackName: String): KmzDocument {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        val parser = factory.newPullParser()
        parser.setInput(StringReader(kml))

        var docName = fallbackName
        val placemarks = mutableListOf<KmzPlacemark>()
        var inDocumentHeader = false
        var inPlacemark = false
        var currentName = ""
        var currentDesc = ""
        val currentCoordsList = mutableListOf<List<LatLon>>()
        val currentGxTrack = mutableListOf<LatLon>()
        val orphanCoords = mutableListOf<List<LatLon>>()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name?.substringAfter(':')?.lowercase(Locale.US).orEmpty()
                    when (tag) {
                        "document" -> inDocumentHeader = true
                        "placemark" -> {
                            inDocumentHeader = false
                            inPlacemark = true
                            currentName = ""
                            currentDesc = ""
                            currentCoordsList.clear()
                            currentGxTrack.clear()
                        }
                        "name" -> {
                            val text = safeNextText(parser)
                            if (inPlacemark) {
                                if (currentName.isBlank()) currentName = text
                            } else if (inDocumentHeader && docName == fallbackName && text.isNotBlank()) {
                                docName = text
                            }
                        }
                        "description" -> {
                            val text = safeNextText(parser)
                            if (inPlacemark && currentDesc.isBlank()) {
                                currentDesc = stripHtml(text)
                            }
                        }
                        "coordinates" -> {
                            val text = safeNextText(parser)
                            val pts = parseCoordinatesText(text)
                            if (pts.isNotEmpty()) {
                                if (inPlacemark) currentCoordsList.add(pts) else orphanCoords.add(pts)
                            }
                        }
                        "coord" -> {
                            // <gx:coord>lon lat alt</gx:coord>
                            val text = safeNextText(parser)
                            parseSingleSpaceTuple(text)?.let { pt ->
                                if (inPlacemark) {
                                    currentGxTrack.add(pt)
                                } else {
                                    orphanCoords.add(listOf(pt))
                                }
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tag = parser.name?.substringAfter(':')?.lowercase(Locale.US).orEmpty()
                    when (tag) {
                        "track" -> {
                            if (inPlacemark && currentGxTrack.isNotEmpty()) {
                                currentCoordsList.add(currentGxTrack.toList())
                                currentGxTrack.clear()
                            }
                        }
                        "placemark" -> if (inPlacemark) {
                            if (currentGxTrack.isNotEmpty()) {
                                currentCoordsList.add(currentGxTrack.toList())
                                currentGxTrack.clear()
                            }
                            currentCoordsList.forEachIndexed { polyIdx, pts ->
                                val suffix = if (currentCoordsList.size > 1) " (${polyIdx + 1})" else ""
                                placemarks.add(
                                    KmzPlacemark(
                                        name = (currentName.ifBlank { "Sahə ${placemarks.size + 1}" }) + suffix,
                                        description = currentDesc,
                                        points = stripClosingDuplicate(pts),
                                    )
                                )
                            }
                            inPlacemark = false
                        }
                    }
                }
            }
            event = parser.next()
        }

        if (placemarks.isEmpty() && orphanCoords.isNotEmpty()) {
            orphanCoords.forEachIndexed { idx, pts ->
                placemarks.add(
                    KmzPlacemark(
                        name = "$fallbackName ${idx + 1}",
                        description = "",
                        points = stripClosingDuplicate(pts),
                    )
                )
            }
        }
        return KmzDocument(documentName = docName, placemarks = placemarks)
    }

    private fun parseKmlFallbackRegex(kml: String, fallbackName: String): KmzDocument {
        val docNameMatch = Regex("<Document\\b[^>]*>.*?<name>([^<]+)</name>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).find(kml)
        val docName = docNameMatch?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() } ?: fallbackName

        val placemarks = mutableListOf<KmzPlacemark>()
        val pmRegex = Regex("<Placemark\\b[^>]*>(.*?)</Placemark>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val nameRegex = Regex("<name>(.*?)</name>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val coordsRegex = Regex("<coordinates>(.*?)</coordinates>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val gxCoordRegex = Regex("<(?:[a-zA-Z0-9_]+:)?coord>([^<]+)</(?:[a-zA-Z0-9_]+:)?coord>", RegexOption.IGNORE_CASE)

        for (pmMatch in pmRegex.findAll(kml)) {
            val block = pmMatch.groupValues[1]
            val pName = nameRegex.find(block)?.groupValues?.getOrNull(1)?.let(::stripHtml).orEmpty()
            val allLists = coordsRegex.findAll(block)
                .map { parseCoordinatesText(it.groupValues[1]) }
                .filter { it.isNotEmpty() }
                .toMutableList()
            val gxPts = gxCoordRegex.findAll(block)
                .mapNotNull { parseSingleSpaceTuple(it.groupValues[1]) }
                .toList()
            if (gxPts.isNotEmpty()) allLists.add(gxPts)

            allLists.forEachIndexed { idx, pts ->
                val suffix = if (allLists.size > 1) " (${idx + 1})" else ""
                placemarks.add(
                    KmzPlacemark(
                        name = (pName.ifBlank { "Sahə ${placemarks.size + 1}" }) + suffix,
                        description = "",
                        points = stripClosingDuplicate(pts),
                    )
                )
            }
        }

        if (placemarks.isEmpty()) {
            coordsRegex.findAll(kml).forEachIndexed { idx, match ->
                val pts = parseCoordinatesText(match.groupValues[1])
                if (pts.isNotEmpty()) {
                    placemarks.add(
                        KmzPlacemark(
                            name = "$docName ${idx + 1}",
                            description = "",
                            points = stripClosingDuplicate(pts),
                        )
                    )
                }
            }
        }

        return KmzDocument(documentName = docName, placemarks = placemarks)
    }

    private fun safeNextText(parser: XmlPullParser): String = try {
        parser.nextText().trim()
    } catch (_: Exception) {
        ""
    }

    private fun parseSingleSpaceTuple(raw: String): LatLon? {
        val parts = raw.trim().split(Regex("[\\s,;]+")).filter { it.isNotBlank() }
        if (parts.size < 2) return null
        val lon = parts[0].toDoubleOrNull() ?: return null
        val lat = parts[1].toDoubleOrNull() ?: return null
        return toValidLatLon(lon = lon, lat = lat)
    }

    private fun parseCoordinatesText(raw: String): List<LatLon> =
        raw.trim()
            .replace(Regex(",\\s+"), ",")
            .split(Regex("[\\s;]+"))
            .filter { it.isNotBlank() }
            .mapNotNull { token ->
                val parts = token.split(",")
                if (parts.size < 2) return@mapNotNull null
                val first = parts[0].toDoubleOrNull() ?: return@mapNotNull null
                val second = parts[1].toDoubleOrNull() ?: return@mapNotNull null
                toValidLatLon(lon = first, lat = second)
            }

    private fun toValidLatLon(lon: Double, lat: Double): LatLon? {
        if (abs(lat) <= 90.0 && abs(lon) <= 180.0) {
            return LatLon(lat, lon)
        }
        // Rescue KMLs that erroneously put UTM meters inside <coordinates>
        if (lon in 100_000.0..999_999.9 && lat in 1_000_000.0..9_999_999.9) {
            return GeoUtils.utmToLatLon(easting = lon, northing = lat, zone = 38)
        }
        if (lat in 100_000.0..999_999.9 && lon in 1_000_000.0..9_999_999.9) {
            return GeoUtils.utmToLatLon(easting = lat, northing = lon, zone = 38)
        }
        return null
    }

    private fun stripClosingDuplicate(points: List<LatLon>): List<LatLon> =
        if (points.size > 3 &&
            abs(points.first().lat - points.last().lat) < 1e-7 &&
            abs(points.first().lon - points.last().lon) < 1e-7
        ) {
            points.dropLast(1)
        } else {
            points
        }

    private fun stripHtml(text: String): String =
        text.replace(Regex("<!\\[CDATA\\[(.*?)\\]\\]>", RegexOption.DOT_MATCHES_ALL), "$1")
            .replace(Regex("<[^>]*>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun escapeXml(input: String): String = input
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
