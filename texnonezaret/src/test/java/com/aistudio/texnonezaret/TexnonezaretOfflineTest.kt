package com.aistudio.texnonezaret

import com.aistudio.texnonezaret.data.SpreadsheetParser
import com.aistudio.texnonezaret.data.TntCapturedPhoto
import com.aistudio.texnonezaret.data.TntEncryptedJpgCodec
import com.aistudio.texnonezaret.data.TntPhotoWatermarkFormatter
import com.aistudio.texnonezaret.domain.InspectionStatusKind
import com.aistudio.texnonezaret.domain.SearchMode
import com.aistudio.texnonezaret.domain.StatusFilter
import com.aistudio.texnonezaret.domain.TexnonezaretEngine
import com.aistudio.texnonezaret.security.SecurityGuard
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TexnonezaretOfflineTest {

    @Test
    fun securityVerificationAndTamperDetectionWorkAccurately() {
        val exactPassword = "WVaqif\u018F\u042D41%@${'$'}Q"
        assertTrue(SecurityGuard.verifyInterventionPassword(exactPassword))
        assertFalse(SecurityGuard.verifyInterventionPassword(""))
        assertFalse(SecurityGuard.verifyInterventionPassword("WVaqif\u018F\u042D41%@${'$'}q"))
        assertFalse(SecurityGuard.verifyInterventionPassword("wvaqif\u018F\u042D41%@${'$'}Q"))
        assertFalse(SecurityGuard.verifyInterventionPassword("WVaqifEE41%@${'$'}Q"))

        val sampleJson = """{"hdr":0,"raw":[["04BA-172","Vaqif"]]}"""
        val encrypted = SecurityGuard.encryptText(sampleJson)
        assertTrue(encrypted.startsWith("TNTENC1:"))
        assertFalse(encrypted.contains("04BA-172"))
        assertEquals(sampleJson, SecurityGuard.decryptText(encrypted))

        // Corrupt the last hex character -> GCM auth tag check must fail (null)
        val corruptedChar = if (encrypted.last() == 'a') 'b' else 'a'
        val tampered = encrypted.dropLast(1) + corruptedChar
        assertNull(SecurityGuard.decryptText(tampered))

        val payload = "hash123|0|0|0|0|sig"
        val hmac = SecurityGuard.computeIntegrityHmac(payload)
        assertTrue(SecurityGuard.verifyIntegrityHmac(payload, hmac))
        assertFalse(SecurityGuard.verifyIntegrityHmac("hash123|0|0|0|0|tampered", hmac))
    }

    @Test
    fun parsesCsvWithAzerbaijaniCharsAutoDetectsColumnsAndSearchesCorrectly() {
        val csv = """
            №;DQN;DQŞ;Texniki baxış tarixi;Mülkiyyətçi;Əlaqə telefonu
            1;04-BA-172;SH123456;15.05.2026;Əliyev Vəli Həsən oğlu;+994 50 123 45 67
            2;90 AA 999;CH987654;01.01.2024;Qurbanov Şahin;055-999-88-77
            3;10-TX-010;AZ0001;;Bərdə Aqro MMC;+994 51 222 33 44
        """.trimIndent()

        val raw = SpreadsheetParser.parseBytes(csv.toByteArray(Charsets.UTF_8), "baxis.csv")
        assertEquals(4, raw.size)

        val (hdr, map) = TexnonezaretEngine.detectColumns(raw)
        assertEquals(0, hdr)
        assertEquals(1, map.dqn)
        assertEquals(2, map.dqs)
        assertEquals(3, map.date)
        assertEquals(4, map.name)
        assertEquals(5, map.phone)

        val rows = TexnonezaretEngine.buildRows(raw, hdr, map)
        assertEquals(3, rows.size)

        // Exact & partial plate search ("04ba172" matches "04-BA-172")
        val plateRes = TexnonezaretEngine.searchRows(rows, "04ba172", "", SearchMode.PLATE)
        assertEquals(1, plateRes.matches.size)
        assertEquals("04-BA-172", plateRes.matches.first().plate)

        // Azerbaijani folded owner search ("eliyev sahin" / "qurbanov sahin" without ə/ş diacritics)
        val ownerRes = TexnonezaretEngine.searchRows(rows, "", "qurbanov sahin", SearchMode.NAME)
        assertEquals(1, ownerRes.matches.size)
        assertEquals("90 AA 999", ownerRes.matches.first().plate)

        val refToday = TexnonezaretEngine.parseDateUtcMillis("22.11.2026")!!
        val sValid = TexnonezaretEngine.evaluateStatus(rows[0].inspectionUtcMillis, refToday)
        val sOverdue = TexnonezaretEngine.evaluateStatus(rows[1].inspectionUtcMillis, refToday)
        val sNone = TexnonezaretEngine.evaluateStatus(rows[2].inspectionUtcMillis, refToday)
        assertEquals(InspectionStatusKind.VALID, sValid.kind)
        assertEquals("15.05.2027", sValid.expiryDateText)
        assertEquals(InspectionStatusKind.OVERDUE, sOverdue.kind)
        assertEquals(InspectionStatusKind.NONE, sNone.kind)

        val overdueFiltered = TexnonezaretEngine.searchRows(
            rows = rows,
            plateQuery = "",
            nameQuery = "",
            mode = SearchMode.PLATE,
            filter = StatusFilter.OVERDUE,
            todayUtcMillis = refToday,
        )
        assertEquals(1, overdueFiltered.matches.size)
        assertEquals("90 AA 999", overdueFiltered.matches.first().plate)
    }

    @Test
    fun parsesMinimalOfflineXlsxZipAndHtmlXlsAndXmlXls() {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("xl/workbook.xml"))
            zos.write(
                """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Sheet1" sheetId="1" r:id="rId1"/></sheets></workbook>"""
                    .toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
            zos.write(
                """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>"""
                    .toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
            zos.write(
                """<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><si><t>DQN</t></si><si><t>Baxış tarixi</t></si><si><t>Mülkiyyətçi</t></si><si><t>04BA-172</t></si><si><t>Vaqif Qurbanov</t></si></sst>"""
                    .toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            // Excel serial 46157 = 2026-05-15
            zos.write(
                """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData><row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="s"><v>2</v></c></row><row r="2"><c r="A2" t="s"><v>3</v></c><c r="B2"><v>46157</v></c><c r="C2" t="s"><v>4</v></c></row></sheetData></worksheet>"""
                    .toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()
        }

        val xlsxRows = SpreadsheetParser.parseBytes(baos.toByteArray(), "texniki.xlsx")
        assertEquals(2, xlsxRows.size)
        val (hdr, map) = TexnonezaretEngine.detectColumns(xlsxRows)
        val built = TexnonezaretEngine.buildRows(xlsxRows, hdr, map)
        assertEquals(1, built.size)
        assertEquals("04BA-172", built.first().plate)
        assertNotNull(built.first().inspectionUtcMillis)
        assertEquals("15.05.2026", TexnonezaretEngine.formatUtcDate(built.first().inspectionUtcMillis!!))

        // HTML table disguised as .xls
        val htmlXls = "<html><body><table><tr><th>DQN</th><th>Tarix</th><th>Sahib</th></tr><tr><td>50-CC-500</td><td>10.10.2026</td><td>Rəşad</td></tr></table></body></html>"
        val htmlRows = SpreadsheetParser.parseBytes(htmlXls.toByteArray(Charsets.UTF_8), "hesabat.xls")
        assertEquals(2, htmlRows.size)
        assertEquals("50-CC-500", htmlRows[1][0])

        // PIN hash matches deterministically and rejects mismatched PIN
        val h1 = TexnonezaretEngine.hashPin("1234")
        val h2 = TexnonezaretEngine.hashPin("1234")
        val hWrong = TexnonezaretEngine.hashPin("4321")
        assertEquals(h1, h2)
        assertNotEquals(h1, hWrong)
        assertTrue(h1.isNotBlank())
    }

    @Test
    fun parsesSpokenPlateNumbersAndHidesEmptyExcelPrompt() {
        val emptyOutcome = TexnonezaretEngine.searchRows(emptyList(), "", "", SearchMode.PLATE)
        assertNull(emptyOutcome.promptMessage)

        assertEquals("04BA172", TexnonezaretEngine.parseSpokenPlateQuery("sıfır dörd BA yüz yetmiş iki"))
        assertEquals("90AA999", TexnonezaretEngine.parseSpokenPlateQuery("doqquz sıfır AA doqquz yüz doxsan doqquz"))
        assertEquals("10TX010", TexnonezaretEngine.parseSpokenPlateQuery("10 tx 010"))
        assertEquals("77BB205", TexnonezaretEngine.parseSpokenPlateQuery("yetmiş yeddi BB iki yüz beş"))
        assertEquals("04BA172", TexnonezaretEngine.parseSpokenPlateQuery("sıfırdörd BA yüzyetmişiki"))
        assertEquals("15", TexnonezaretEngine.parseSpokenPlateQuery("onbeş"))
        assertEquals("205", TexnonezaretEngine.parseSpokenPlateQuery("2 yüz 5"))
        assertEquals("172", TexnonezaretEngine.parseSpokenPlateQuery("100 yetmiş 2"))
        assertEquals("72", TexnonezaretEngine.parseSpokenPlateQuery("70 iki"))
        assertEquals("04", TexnonezaretEngine.parseSpokenPlateQuery("nol dörd"))
        assertEquals("478", TexnonezaretEngine.parseSpokenPlateQuery("dört yedi sekiz"))
        assertEquals("10AO123", TexnonezaretEngine.parseSpokenPlateQuery("10 A O 123"))
        assertEquals("ONUR", TexnonezaretEngine.parseSpokenPlateQuery("ONUR"))
    }

    @Test
    fun photoVaultBinaryEncryptionPinPbkdf2AndWatermarkFormattingWork() {
        val rawJpegMock = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 10, 20, 30, 40)
        val aad = "tnt_photo:abc123"
        val enc = SecurityGuard.encryptBytes(rawJpegMock, aad)
        assertTrue(enc.size > rawJpegMock.size + 19)
        assertTrue(rawJpegMock.contentEquals(SecurityGuard.decryptBytes(enc, aad)!!))
        // Wrong AAD or tampered byte must reject decryption
        assertNull(SecurityGuard.decryptBytes(enc, "tnt_photo:wrong"))
        val tampered = enc.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 0xFF).toByte() }
        assertNull(SecurityGuard.decryptBytes(tampered, aad))

        // Strong PBKDF2-HMAC-SHA256 PIN verification + legacy FNV compatibility
        val strongHash = TexnonezaretEngine.hashPinStrong("7391")
        assertTrue(strongHash.startsWith("TNTPIN2:"))
        assertTrue(TexnonezaretEngine.isStrongPinHash(strongHash))
        assertTrue(TexnonezaretEngine.verifyPinHash("7391", strongHash))
        assertFalse(TexnonezaretEngine.verifyPinHash("0000", strongHash))

        val legacyHash = TexnonezaretEngine.hashPin("4820")
        assertFalse(TexnonezaretEngine.isStrongPinHash(legacyHash))
        assertTrue(TexnonezaretEngine.verifyPinHash("4820", legacyHash))
        assertFalse(TexnonezaretEngine.verifyPinHash("4821", legacyHash))

        // Exact date/time watermark formatting + coordinate formatting + path traversal guard
        val formattedTime = TntPhotoWatermarkFormatter.formatTimestamp(
            epochMillis = 1653980051_000L, // 31/05/2022 06:54:11 UTC
            timeZone = TimeZone.getTimeZone("UTC"),
        )
        assertEquals("31/05/2022 06:54:11", formattedTime)
        assertEquals(
            "40.40926, 49.86709",
            TntPhotoWatermarkFormatter.formatCompactCoordinatesForStrip(40.40926, 49.86709),
        )
        assertEquals(
            "40.40926, 49.86709 (±8m)",
            TntPhotoWatermarkFormatter.formatCoordinates(40.40926, 49.86709, 8.2f),
        )
        assertEquals(
            "Koordinat təyin edilmədi",
            TntPhotoWatermarkFormatter.formatCoordinates(null, null, null),
        )
        assertTrue(TntPhotoWatermarkFormatter.isSafeChildFileName("tnt_photo_123abc.tntimg"))
        assertTrue(TntPhotoWatermarkFormatter.isSafeChildFileName("tnt_photo_123abc.jpg"))
        assertFalse(TntPhotoWatermarkFormatter.isSafeChildFileName("../secret.txt"))
        assertFalse(TntPhotoWatermarkFormatter.isSafeChildFileName("sub/photo.tntimg"))
    }

    @Test
    fun encryptedJpgEnvelopeRoundTripAndTamperProtectionWork() {
        val coverJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3, 0xFF.toByte(), 0xD9.toByte())
        val stampedJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xDB.toByte(), 11, 22, 33, 44, 55, 0xFF.toByte(), 0xD9.toByte())
        val sampleMeta = TntCapturedPhoto(
            id = "p1234567890",
            fileName = "tnt_photo_p1234567890.jpg",
            capturedAtMillis = 1764240000_000L,
            timestampText = "27/11/2025 10:40:00",
            latitude = 40.40926,
            longitude = 49.86709,
            accuracyMeters = 7.5f,
            coordinatesText = "40.40926, 49.86709 (±8m)",
            plateTag = "04BA172",
            sha256Hex = "abc",
        )

        // Plain JPEG without TNT trailer must inspect as null
        assertNull(TntEncryptedJpgCodec.inspectEncryptedJpg(coverJpeg))

        // 1) Device KeyStore mode (no password)
        val keyStoreJpg = TntEncryptedJpgCodec.encodeEncryptedJpgEnvelope(
            coverJpegBytes = coverJpeg,
            photo = sampleMeta,
            stampedJpegBytes = stampedJpeg,
            password = null,
        )
        assertTrue(keyStoreJpg.size > coverJpeg.size + stampedJpeg.size)
        // Starts with valid JPEG SOI marker so system treats it as a valid .jpg
        assertEquals(0xFF.toByte(), keyStoreJpg[0])
        assertEquals(0xD8.toByte(), keyStoreJpg[1])

        val ksInspection = TntEncryptedJpgCodec.inspectEncryptedJpg(keyStoreJpg)
        assertNotNull(ksInspection)
        assertFalse(ksInspection!!.requiresPassword)

        val ksDecoded = TntEncryptedJpgCodec.decodeEncryptedJpgEnvelope(keyStoreJpg)
        assertNotNull(ksDecoded)
        assertTrue(stampedJpeg.contentEquals(ksDecoded!!.stampedJpegBytes))
        assertEquals(sampleMeta.capturedAtMillis, ksDecoded.capturedAtMillis)
        assertEquals(sampleMeta.timestampText, ksDecoded.timestampText)
        assertEquals(sampleMeta.latitude!!, ksDecoded.latitude!!, 0.00001)
        assertEquals(sampleMeta.longitude!!, ksDecoded.longitude!!, 0.00001)
        assertEquals(sampleMeta.accuracyMeters!!, ksDecoded.accuracyMeters!!, 0.01f)
        assertEquals(sampleMeta.coordinatesText, ksDecoded.coordinatesText)
        assertEquals(sampleMeta.plateTag, ksDecoded.plateTag)

        // 2) Cross-device password mode
        val pwJpg = TntEncryptedJpgCodec.encodeEncryptedJpgEnvelope(
            coverJpegBytes = coverJpeg,
            photo = sampleMeta,
            stampedJpegBytes = stampedJpeg,
            password = "TntExport#2026",
        )
        val pwInspection = TntEncryptedJpgCodec.inspectEncryptedJpg(pwJpg)
        assertNotNull(pwInspection)
        assertTrue(pwInspection!!.requiresPassword)

        assertNull(TntEncryptedJpgCodec.decodeEncryptedJpgEnvelope(pwJpg, null))
        assertNull(TntEncryptedJpgCodec.decodeEncryptedJpgEnvelope(pwJpg, ""))
        assertNull(TntEncryptedJpgCodec.decodeEncryptedJpgEnvelope(pwJpg, "wrong-password"))

        val pwDecoded = TntEncryptedJpgCodec.decodeEncryptedJpgEnvelope(pwJpg, "TntExport#2026")
        assertNotNull(pwDecoded)
        assertTrue(stampedJpeg.contentEquals(pwDecoded!!.stampedJpegBytes))
        assertEquals("04BA172", pwDecoded.plateTag)

        // 3) Tampering ciphertext inside envelope must fail GCM verification
        val tampered = pwJpg.copyOf()
        val payloadByteIdx = tampered.size - 16
        tampered[payloadByteIdx] = (tampered[payloadByteIdx].toInt() xor 0xFF).toByte()
        assertNull(TntEncryptedJpgCodec.decodeEncryptedJpgEnvelope(tampered, "TntExport#2026"))
    }
}
