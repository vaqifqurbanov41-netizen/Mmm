package com.example.domain

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
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
        val kmlText = extractKmlFromBytes(bytes)
        return parseKmlString(kmlText, fallbackName)
    }

    fun parseKmzUri(context: Context, uri: Uri, fallbackName: String = "KMZ"): KmzDocument? = try {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        parseKmzOrKml(bytes, fallbackName)
    } catch (_: Exception) {
        null
    }

    private fun extractKmlFromBytes(bytes: ByteArray): String {
        val isZip = bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
        if (!isZip) {
            return String(bytes, StandardCharsets.UTF_8)
        }
        var docKml: String? = null
        var firstKml: String? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.lowercase(Locale.US).endsWith(".kml")) {
                    val content = zis.readBytes().toString(StandardCharsets.UTF_8)
                    if (entry.name.equals("doc.kml", ignoreCase = true)) {
                        docKml = content
                        break
                    } else if (firstKml == null) {
                        firstKml = content
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return docKml ?: firstKml ?: ""
    }

    fun parseKmlString(kml: String, fallbackName: String = "KMZ"): KmzDocument {
        if (kml.isBlank()) return KmzDocument(fallbackName, emptyList())
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
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tag = parser.name?.substringAfter(':')?.lowercase(Locale.US).orEmpty()
                    if (tag == "placemark" && inPlacemark) {
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

    private fun safeNextText(parser: XmlPullParser): String = try {
        parser.nextText().trim()
    } catch (_: Exception) {
        ""
    }

    private fun parseCoordinatesText(raw: String): List<LatLon> =
        raw.trim()
            .split(Regex("\\s+"))
            .mapNotNull { token ->
                val parts = token.split(",")
                if (parts.size < 2) return@mapNotNull null
                val lon = parts[0].toDoubleOrNull() ?: return@mapNotNull null
                val lat = parts[1].toDoubleOrNull() ?: return@mapNotNull null
                if (kotlin.math.abs(lat) > 90.0 || kotlin.math.abs(lon) > 180.0) return@mapNotNull null
                LatLon(lat, lon)
            }

    private fun stripClosingDuplicate(points: List<LatLon>): List<LatLon> =
        if (points.size > 3 &&
            kotlin.math.abs(points.first().lat - points.last().lat) < 1e-7 &&
            kotlin.math.abs(points.first().lon - points.last().lon) < 1e-7
        ) {
            points.dropLast(1)
        } else {
            points
        }

    private fun stripHtml(text: String): String =
        text.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()

    private fun escapeXml(input: String): String = input
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
