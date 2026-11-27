package com.aistudio.testbaza

import com.aistudio.testbaza.core.domain.AnswerRule
import com.aistudio.testbaza.core.domain.HtmlExporter
import com.aistudio.testbaza.core.domain.QuestionMasteryStatus
import com.aistudio.testbaza.core.domain.QuizDocumentParser
import com.aistudio.testbaza.core.domain.QuizEngine
import com.aistudio.testbaza.core.domain.QuizQuestion
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizParserAndEngineTest {

    @Test
    fun docxParserExtractsBoldOptionAndSoftLineBreaksCorrectly() {
        val documentXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                <w:p><w:r><w:t>1. Azərbaycanın paytaxtı hansı şəhərdir?</w:t></w:r></w:p>
                <w:p><w:r><w:t>A) Gəncə</w:t></w:r></w:p>
                <w:p><w:r><w:rPr><w:b/></w:rPr><w:t>B) Bakı</w:t></w:r></w:p>
                <w:p><w:r><w:t>C) Şəki</w:t></w:r></w:p>
                <w:p>
                  <w:r><w:t>2. Hansı rəng göy qurşağında yoxdur?</w:t><w:br/><w:t>A) Qırmızı</w:t><w:br/></w:r>
                  <w:r><w:rPr><w:bCs/></w:rPr><w:t>B) Qara</w:t></w:r>
                  <w:r><w:br/><w:t>C) Yaşıl</w:t></w:r>
                </w:p>
              </w:body>
            </w:document>
        """.trimIndent()

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("word/document.xml"))
            zos.write(documentXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val lines = QuizDocumentParser.extractDocxLines(ByteArrayInputStream(baos.toByteArray()))
        val parsed = QuizDocumentParser.parseStructuredLines(lines, "Coğrafiya", AnswerRule.AUTO).getOrThrow()

        assertEquals(2, parsed.questions.size)
        assertEquals("Bakı", parsed.questions[0].correctText)
        assertTrue(parsed.questions[0].hasConfirmedKey)
        assertEquals(3, parsed.questions[0].allOptions.size)

        assertEquals("Qara", parsed.questions[1].correctText)
        assertTrue(parsed.questions[1].hasConfirmedKey)
        assertEquals(3, parsed.questions[1].allOptions.size)
    }

    @Test
    fun docxParserSupportsAutoNumberedListsFromNumberingXml() {
        val numberingXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:numbering xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:abstractNum w:abstractNumId="10">
                <w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="decimal"/><w:lvlText w:val="%1."/></w:lvl>
                <w:lvl w:ilvl="1"><w:start w:val="1"/><w:numFmt w:val="upperLetter"/><w:lvlText w:val="%2)"/></w:lvl>
              </w:abstractNum>
              <w:num w:numId="1"><w:abstractNumId w:val="10"/></w:num>
            </w:numbering>
        """.trimIndent()

        val documentXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                <w:p>
                  <w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr>
                  <w:r><w:t>Suyun kimyəvi formulu hansıdır?</w:t></w:r>
                </w:p>
                <w:p>
                  <w:pPr><w:numPr><w:ilvl w:val="1"/><w:numId w:val="1"/></w:numPr></w:pPr>
                  <w:r><w:t>CO2</w:t></w:r>
                </w:p>
                <w:p>
                  <w:pPr><w:numPr><w:ilvl w:val="1"/><w:numId w:val="1"/></w:numPr></w:pPr>
                  <w:r><w:rPr><w:b/></w:rPr><w:t>H2O</w:t></w:r>
                </w:p>
                <w:p>
                  <w:pPr><w:numPr><w:ilvl w:val="1"/><w:numId w:val="1"/></w:numPr></w:pPr>
                  <w:r><w:t>NaCl</w:t></w:r>
                </w:p>
              </w:body>
            </w:document>
        """.trimIndent()

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("word/numbering.xml"))
            zos.write(numberingXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("word/document.xml"))
            zos.write(documentXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val lines = QuizDocumentParser.extractDocxLines(ByteArrayInputStream(baos.toByteArray()))
        val parsed = QuizDocumentParser.parseStructuredLines(lines, "Kimya", AnswerRule.AUTO).getOrThrow()

        assertEquals(1, parsed.questions.size)
        val q = parsed.questions.first()
        assertEquals(1, q.questionNumber)
        assertEquals("H2O", q.correctText)
        assertEquals(listOf("CO2", "H2O", "NaCl"), q.allOptions)
    }

    @Test
    fun rawTextParserSupportsWrappedOptionsAndSingleLineAnswerKeys() {
        val sample = """
            1. Birinci sual?
            A) Birinci variantın
            davam edən ikinci sətri
            B) Doğru 1
            C) Yanlış 2
            2. İkinci sual?
            A) Variant A
            B) Variant B
            C) Variant C
            Cavablar:
            1. B
            2. C
        """.trimIndent()

        val parsed = QuizDocumentParser.parseRawText(sample, "Sınaq", AnswerRule.AUTO).getOrThrow()
        assertEquals(2, parsed.questions.size)
        assertEquals(3, parsed.questions[0].allOptions.size)
        assertEquals("Birinci variantın davam edən ikinci sətri", parsed.questions[0].allOptions[0])
        assertEquals("Doğru 1", parsed.questions[0].correctText)
        assertEquals("Variant C", parsed.questions[1].correctText)
        assertTrue(parsed.questions.all { it.hasConfirmedKey })
    }

    @Test
    fun engineShuffleAndJokerPreserveCorrectAnswer() {
        val q = QuizQuestion(
            rowId = 1L,
            bankId = 1L,
            questionNumber = 10,
            category = "Test",
            isExam = false,
            questionText = "Sual?",
            allOptions = listOf("Bir", "İki", "Üç", "Dörd", "Beş"),
            correctText = "Üç",
            hasConfirmedKey = true,
            masteryStatus = QuestionMasteryStatus.NEW,
            wrongCount = 0,
            correctStreak = 0,
        )
        val session = QuizEngine.buildSessionItems(listOf(q), shuffle = true, random = Random(42))
        assertEquals(1, session.size)
        val item = session.first()
        assertEquals("Üç", item.shuffledOptions[item.correctIdx])

        val afterJoker = QuizEngine.applyJoker5050(item, Random(42))
        assertEquals(2, afterJoker.disabledOptions.size)
        assertFalse(item.correctIdx in afterJoker.disabledOptions)
    }

    @Test
    fun exportFilenameUsesTestbazaFallback() {
        assertEquals("Testbaza_Export.html", HtmlExporter.safeExportFileName("   "))
        assertEquals("Torpaq_Test.html", HtmlExporter.safeExportFileName("Torpaq Test"))
    }
}
