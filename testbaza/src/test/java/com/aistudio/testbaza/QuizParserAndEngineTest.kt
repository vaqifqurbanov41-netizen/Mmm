package com.aistudio.testbaza

import com.aistudio.testbaza.core.domain.AnswerRule
import com.aistudio.testbaza.core.domain.HtmlExporter
import com.aistudio.testbaza.core.domain.QuestionMasteryStatus
import com.aistudio.testbaza.core.domain.QuizDocumentParser
import com.aistudio.testbaza.core.domain.QuizEngine
import com.aistudio.testbaza.core.domain.QuizQuestion
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizParserAndEngineTest {

    @Test
    fun bundledDefaultQuestionsJsonContainsAll1000Questions() {
        val candidates = listOf(
            File("testbaza_core/src/main/res/raw/default_questions.json"),
            File("../testbaza_core/src/main/res/raw/default_questions.json"),
        )
        val rawFile = candidates.firstOrNull { it.exists() }
            ?: error("default_questions.json not found in test working dir")
        val content = rawFile.readText(Charsets.UTF_8)
        val questionCount = Regex(""""hasConfirmedKey"\s*:""").findAll(content).count()
        assertEquals(1000, questionCount)
        val ids = Regex(""""id"\s*:\s*(\d+)""").findAll(content).map { it.groupValues[1].toInt() }.toList()
        assertEquals(1000, ids.distinct().size)
        assertEquals((1..1000).toList(), ids)
        assertTrue(content.contains("torpaq münbitliyi test A variant qiyabi"))
    }

    @Test
    fun docxParserExtractsBoldOptionCorrectly() {
        val documentXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                <w:p><w:r><w:t>1. Azərbaycanın paytaxtı hansı şəhərdir?</w:t></w:r></w:p>
                <w:p><w:r><w:t>A) Gəncə</w:t></w:r></w:p>
                <w:p><w:r><w:rPr><w:b/></w:rPr><w:t>B) Bakı</w:t></w:r></w:p>
                <w:p><w:r><w:t>C) Şəki</w:t></w:r></w:p>
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

        assertEquals(1, parsed.questions.size)
        val q = parsed.questions.first()
        assertEquals("Bakı", q.correctText)
        assertTrue(q.hasConfirmedKey)
        assertEquals(3, q.allOptions.size)
    }

    @Test
    fun rawTextParserSupportsBottomAnswerKeysAndInlineOptions() {
        val sample = """
            1. Birinci sual?
            A) Yanlış 1   B) Doğru 1   C) Yanlış 2
            2. İkinci sual?
            A) Variant A
            B) Variant B
            C) Variant C
            Cavablar:
            1. B 2. C 3. A
        """.trimIndent()

        val parsed = QuizDocumentParser.parseRawText(sample, "Sınaq", AnswerRule.AUTO).getOrThrow()
        assertEquals(2, parsed.questions.size)
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
