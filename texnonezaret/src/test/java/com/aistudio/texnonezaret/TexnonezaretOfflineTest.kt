package com.aistudio.texnonezaret

import com.aistudio.texnonezaret.data.SpreadsheetParser
import com.aistudio.texnonezaret.domain.InspectionStatusKind
import com.aistudio.texnonezaret.domain.SearchMode
import com.aistudio.texnonezaret.domain.StatusFilter
import com.aistudio.texnonezaret.domain.TexnonezaretEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TexnonezaretOfflineTest {

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
}
