package com.example

import com.example.domain.CadastralDocumentParser
import com.example.domain.DocCoordTarget
import com.example.domain.KmzPlacemark
import com.example.domain.KmzUtils
import com.example.domain.LatLon
import com.example.domain.OcrToken
import com.example.ui.components.MapTileMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ParserAndKmzUnitTest {

    @Test
    fun cadastralParserHandlesRotatedAndSplitDecimalsAndNinePlusRows() {
        val expectedX = listOf("703195.81", "703095.5", "703094.45", "702729.11", "702701.95", "702701.49", "702700.8", "703194.12")
        val expectedY = listOf("4437081.26", "4437076.25", "4437061.04", "4437042.58", "4437042.62", "4437056.28", "4437076.49", "4437101.43")

        val tokens = mutableListOf(
            OcrToken("Nömrəsi", 60f, 50f),
            OcrToken("X", 200f, 50f),
            OcrToken("Y", 360f, 50f),
        )
        for (i in expectedX.indices) {
            val rowY = 100f + i * 45f
            tokens += OcrToken("${i + 1}", 60f, rowY)
            if (i == 3) {
                tokens += OcrToken("702729.", 185f, rowY)
                tokens += OcrToken("11", 220f, rowY)
            } else if (i == 7) {
                tokens += OcrToken("703194.", 185f, rowY)
                tokens += OcrToken("12", 220f, rowY)
            } else {
                tokens += OcrToken(expectedX[i], 200f, rowY)
            }
            tokens += OcrToken(expectedY[i], 360f, rowY)
        }

        val parsed = CadastralDocumentParser.parseSpatialTokensScored(
            tokens,
            "Torpaq sahəsinin döngə nöqtələrinin koordinatları Sahəsi: 1.6069",
        )
        assertNotNull(parsed)
        assertEquals(DocCoordTarget.UTM_38T, parsed!!.document.target)
        assertEquals(8, parsed.document.points.size)
        assertEquals(expectedX, parsed.document.rawPairs.map { it.first })
        assertEquals(expectedY, parsed.document.rawPairs.map { it.second })

        // Verify sideways corrupted screenshot pairs are strictly rejected
        val corruptedPairs = listOf(
            "703194." to "702729.",
            "702700.8" to "703094.45",
            "702701.49" to "703195.81",
            "702701.95" to "703095.5",
            "4437101.43" to "4437042.58",
            "4437076.49" to "4437076.25",
            "4437056.28" to "4437061.04",
            "4437042.62" to "4437081.26",
        )
        assertNull(CadastralDocumentParser.classifyPairs(corruptedPairs, areaHaHint = 1.6069))

        // Verify 10-row sequential extraction
        val tenTokens = tokens.toMutableList()
        tenTokens += listOf(
            OcrToken("9", 60f, 100f + 8 * 45f),
            OcrToken("703196.10", 200f, 100f + 8 * 45f),
            OcrToken("4437105.00", 360f, 100f + 8 * 45f),
            OcrToken("10", 60f, 100f + 9 * 45f),
            OcrToken("703197.25", 200f, 100f + 9 * 45f),
            OcrToken("4437095.00", 360f, 100f + 9 * 45f),
        )
        val parsedTen = CadastralDocumentParser.parseSpatialTokensScored(tenTokens, "Sahəsi: 1.6069")
        assertNotNull(parsedTen)
        assertEquals(10, parsedTen!!.document.points.size)
        assertEquals("703197.25", parsedTen.document.rawPairs[9].first)
        assertEquals("4437095.00", parsedTen.document.rawPairs[9].second)
    }

    @Test
    fun kmzParserHandlesBomUnescapedAmpersandGxTrackWrapperZipAndUtm() {
        val pts = listOf(
            LatLon(40.6780, 46.3600),
            LatLon(40.6795, 46.3600),
            LatLon(40.6795, 46.3620),
            LatLon(40.6780, 46.3620),
        )
        val bytes = KmzUtils.buildKmzBytes("Ganja_Test", listOf(KmzPlacemark("Sahə 1", "Test", pts)))
        val roundTrip = KmzUtils.parseKmzOrKml(bytes)
        assertEquals(1, roundTrip.placemarks.size)
        assertEquals(4, roundTrip.placemarks.first().points.size)

        val trickyKml = "\uFEFF<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<kml xmlns=\"http://www.opengis.net/kml/2.2\" xmlns:gx=\"http://www.google.com/kml/ext/2.2\">" +
            "<Document><name>Sabirabad & Samux</name>" +
            "<Placemark><name>Track 1 & 2</name><description>Taxıl & Yonca</description>" +
            "<gx:Track><gx:coord>46.3600 40.6780 0</gx:coord>" +
            "<gx:coord>46.3620 40.6780 0</gx:coord>" +
            "<gx:coord>46.3620 40.6795 0</gx:coord></gx:Track>" +
            "</Placemark></Document></kml>"
        val parsedTricky = KmzUtils.parseKmzOrKml(trickyKml.toByteArray(Charsets.UTF_8))
        assertEquals(1, parsedTricky.placemarks.size)
        assertEquals(3, parsedTricky.placemarks.first().points.size)

        val zipBaos = ByteArrayOutputStream()
        ZipOutputStream(zipBaos).use { zos ->
            zos.putNextEntry(ZipEntry("doc.kml"))
            zos.write("<kml><Document><name>Wrapper</name></Document></kml>".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("layers/field_polygon.kml"))
            zos.write(
                ("<kml><Document><Placemark><name>İkinci KML</name>" +
                    "<Polygon><outerBoundaryIs><LinearRing><coordinates>" +
                    "46.3600,40.6780,0 46.3620,40.6780,0 46.3620,40.6795,0 46.3600,40.6780,0" +
                    "</coordinates></LinearRing></outerBoundaryIs></Polygon>" +
                    "</Placemark></Document></kml>").toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()
        }
        val nestedKmz = KmzUtils.parseKmzOrKml(zipBaos.toByteArray())
        assertEquals(1, nestedKmz.placemarks.size)
        assertEquals("İkinci KML", nestedKmz.placemarks.first().name)
        assertEquals(3, nestedKmz.placemarks.first().points.size)
    }

    @Test
    fun mapTileMathComputesAncestorOverzoomCropAndDetectsPlaceholders() {
        // Zoom 19 tile (40961, 24578) from Zoom 17 ancestor (40961 shr 2 = 10240, 24578 shr 2 = 6144)
        val crop = MapTileMath.computeAncestorCrop(
            targetZ = 19,
            targetX = 40961,
            targetY = 24578,
            ancestorZ = 17,
            tileWidth = 256,
            tileHeight = 256,
        )
        assertEquals(17, crop.ancestorZ)
        assertEquals(10240, crop.ancestorX)
        assertEquals(6144, crop.ancestorY)
        assertEquals(64, crop.srcOffsetX)
        assertEquals(128, crop.srcOffsetY)
        assertEquals(64, crop.srcWidth)
        assertEquals(64, crop.srcHeight)

        // Esri-style uniform light-grey "Map data not yet available" tile samples
        val esriGrey = IntArray(64) { i ->
            if (i in 24..31) 0xFFEFEFEF.toInt() else 0xFFCCCCCC.toInt()
        }
        assertTrue(MapTileMath.isLikelyPlaceholderTilePixels(esriGrey))

        // Real aerial field tile with vegetation & soil chroma
        val realSatellite = IntArray(64) { i ->
            when {
                i % 3 == 0 -> 0xFF3E6B2E.toInt() // green crop
                i % 3 == 1 -> 0xFF7B5A3C.toInt() // brown soil
                else -> 0xFFC5B091.toInt()       // road / roof
            }
        }
        assertFalse(MapTileMath.isLikelyPlaceholderTilePixels(realSatellite))
    }
}
