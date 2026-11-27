package com.aistudio.testbaza

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.aistudio.testbaza.core.data.QuizRepository
import com.aistudio.testbaza.core.data.local.QuizDatabase
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QuizParserAndEngineTest {

    @Test
    fun defaultBankSeedsAll143Questions() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val db = Room.inMemoryDatabaseBuilder(context, QuizDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = QuizRepository(context, db.quizDao())
            val bankId = repo.ensureDefaultBankSeeded()
            val questions = repo.observeQuestions(bankId).first()
            assertEquals(143, questions.size)
            assertEquals(1, questions.first().questionNumber)
            assertTrue(questions.all { it.allOptions.size >= 2 && it.hasConfirmedKey })
        } finally {
            db.close()
        }
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
    fun htmlExporterBuildsSelfContainedOutput() {
        val q = QuizQuestion(
            rowId = 1L,
            bankId = 1L,
            questionNumber = 1,
            category = "Torpaq",
            isExam = true,
            questionText = "Humus nədir?",
            allOptions = listOf("Üzvi maddə", "Mineral duz"),
            correctText = "Üzvi maddə",
            hasConfirmedKey = true,
            masteryStatus = QuestionMasteryStatus.MASTERED,
            wrongCount = 0,
            correctStreak = 2,
        )
        val html = HtmlExporter.buildStandaloneHtml("Torpaq Test", listOf(q))
        assertTrue(html.contains("INITIAL_QUESTIONS"))
        assertTrue(html.contains("Humus nədir?"))
        assertTrue(html.contains("Üzvi maddə"))
    }
}
