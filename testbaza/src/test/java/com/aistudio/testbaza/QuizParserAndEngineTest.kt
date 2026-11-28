package com.aistudio.testbaza

import com.aistudio.testbaza.core.domain.AnswerRule
import com.aistudio.testbaza.core.domain.HtmlExporter
import com.aistudio.testbaza.core.domain.QuestionMasteryStatus
import com.aistudio.testbaza.core.domain.QuizDocumentParser
import com.aistudio.testbaza.core.domain.QuizEngine
import com.aistudio.testbaza.core.domain.StyledLine
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
    fun pdfParserRecoversValidPagesWhenCorruptFlateStreamThrowsDataFormatException() {
        val pdfBytes = buildTwoPagePdfWithCorruptFirstStream()
        val lines = QuizDocumentParser.extractPdfLines(null, ByteArrayInputStream(pdfBytes))
        assertTrue(lines.isNotEmpty())

        val parsed = QuizDocumentParser.parseStructuredLines(lines, "PDF Sınaq", AnswerRule.AUTO).getOrThrow()
        assertEquals(1, parsed.questions.size)
        assertEquals("Dogru variant", parsed.questions.first().correctText)
        assertTrue(parsed.questions.first().hasConfirmedKey)
    }

    @Test
    fun multiChapterTabularKeysAndSublistsParseAccurately() {
        val lines = listOf(
            StyledLine("Əsas anlayışlar", isBold = true),
            StyledLine("1. Aşağıdakıların hansıları proqramdır:", isBold = false),
            StyledLine("1. koaksial 2. optik lifli", isBold = false),
            StyledLine("3. peyk 4. burulmuş cüt", isBold = false),
            StyledLine("a) 11 b)10 c) 9 d)11 e) 5", isBold = false),
            StyledLine("2. Məsələnin həlli zamanı", isBold = false),
            StyledLine("2-yə vurulması) və", isBold = false),
            StyledLine("4.0, 98 versiyası nədir?", isBold = false),
            StyledLine("a) Variant A b) Variant B c) Variant C", isBold = false),
            StyledLine("3. Üçüncü sual", isBold = false),
            StyledLine("a) Bir b) İki c) Üç", isBold = false),
            StyledLine("4. Dördüncü sual", isBold = false),
            StyledLine("a) Bir b) İki c) Üç", isBold = false),
            StyledLine("5. Beşinci sual", isBold = false),
            StyledLine("a) Bir b) İki c) Üç", isBold = false),
            StyledLine("İkinci bölmə", isBold = true),
            StyledLine("1. Yeni bölmənin birinci sualı?", isBold = false),
            StyledLine("a) Alfa b) Beta c) Qamma", isBold = false),
            StyledLine("Test suallarının cavabları", isBold = true),
            StyledLine("Əsas anlayışlar", isBold = true),
            StyledLine("1 2 3 4 5", isBold = false),
            StyledLine("b c a b c", isBold = false),
            StyledLine("İkinci bölmə", isBold = true),
            StyledLine("1", isBold = false),
            StyledLine("c", isBold = false),
            StyledLine("Ədəbiyyat", isBold = true),
            StyledLine("1. Kitab mənbəyi", isBold = false),
        )

        val parsed = QuizDocumentParser.parseStructuredLines(lines, "İnformatika", AnswerRule.AUTO).getOrThrow()
        assertEquals(6, parsed.questions.size)
        assertTrue(parsed.questions[0].questionText.contains("3. peyk 4. burulmuş cüt"))
        assertEquals(listOf("11", "10", "9", "11", "5"), parsed.questions[0].allOptions)
        assertEquals("10", parsed.questions[0].correctText)
        assertEquals("Variant C", parsed.questions[1].correctText)
        assertTrue(parsed.questions[1].questionText.contains("2-yə vurulması)"))
        assertTrue(parsed.questions[1].questionText.contains("4.0, 98"))
        assertEquals("Qamma", parsed.questions[5].correctText)
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

    @Test
    fun examSelectionRespectsQuestionLimitAndBuildsTimingAndWrongSummary() {
        val questions = (1..10).map { num ->
            QuizQuestion(
                rowId = num.toLong(),
                bankId = 1L,
                questionNumber = num,
                category = "İnformatika",
                isExam = false,
                questionText = "Sual $num?",
                allOptions = listOf("Doğru $num", "Yanlış A", "Yanlış B"),
                correctText = "Doğru $num",
                hasConfirmedKey = true,
                masteryStatus = QuestionMasteryStatus.NEW,
                wrongCount = 0,
                correctStreak = 0,
            )
        }

        val picked = QuizEngine.selectExamQuestions(questions, requestedCount = 4, shuffle = true, random = Random(7))
        assertEquals(4, picked.size)
        assertEquals(4, picked.map { it.questionNumber }.toSet().size)

        val session = QuizEngine.buildSessionItems(picked.take(3), shuffle = false, random = Random(7))
        assertEquals(3, session.size)

        val answeredSession = listOf(
            session[0].copy(selectedIdx = 0, isCorrect = true),
            session[1].copy(selectedIdx = 1, isCorrect = false),
            session[2], // skipped
        )
        val summary = QuizEngine.buildExamEvaluationSummary(answeredSession, listOf(12L, 35L, 8L))
        assertEquals(3, summary.totalQuestions)
        assertEquals(2, summary.answeredCount)
        assertEquals(1, summary.correctCount)
        assertEquals(1, summary.wrongCount)
        assertEquals(1, summary.unansweredCount)
        assertEquals(1, summary.wrongDetails.size)
        assertEquals("Yanlış A", summary.wrongDetails.first().selectedAnswer)
        assertEquals(35L, summary.wrongDetails.first().timeSpentSeconds)
        assertEquals(listOf("CORRECT", "WRONG", "SKIPPED"), summary.questionTimings.map { it.status })
        assertEquals(listOf(12L, 35L, 8L), summary.questionTimings.map { it.timeSpentSeconds })

        val payload = com.aistudio.testbaza.core.domain.ShareResultPayload(
            title = "İnformatika Sınağı",
            summary = summary,
            totalElapsedSeconds = 55L,
            durationLimitMinutes = 20,
            timedOut = false,
        )
        val compactText = QuizEngine.formatResultShareText(payload, includeDetails = false)
        assertTrue(compactText.contains("İnformatika Sınağı"))
        assertTrue(compactText.contains("1/3 düzgün"))
        assertFalse(compactText.contains("Statistik Məlumatlar"))
        assertFalse(compactText.contains("Yanlış A"))

        val detailedText = QuizEngine.formatResultShareText(payload, includeDetails = true)
        assertTrue(detailedText.contains("Statistik Məlumatlar"))
        assertTrue(detailedText.contains("Yanlış A"))
        assertTrue(detailedText.contains("Hər sual üzrə vaxt:"))

        assertEquals("02:05", QuizEngine.formatTimerMmSs(125))
        assertEquals("1 dəq 15 san", QuizEngine.formatDurationHumanAz(75L))
    }

    @Test
    fun fileStatisticsComputesAccuracyTimingsAndWrongRanking() {
        val questions = listOf(
            QuizQuestion(
                rowId = 1L,
                bankId = 9L,
                questionNumber = 1,
                category = "Ümumi",
                isExam = false,
                questionText = "Yavaş və səhv sual",
                allOptions = listOf("A", "B"),
                correctText = "A",
                hasConfirmedKey = true,
                masteryStatus = QuestionMasteryStatus.WEAK,
                wrongCount = 3,
                correctStreak = 0,
                attemptCount = 4,
                correctCount = 1,
                totalTimeSeconds = 120L,
                lastTimeSeconds = 40L,
                timedCount = 4,
            ),
            QuizQuestion(
                rowId = 2L,
                bankId = 9L,
                questionNumber = 2,
                category = "Ümumi",
                isExam = false,
                questionText = "Sürətli və düzgün sual",
                allOptions = listOf("X", "Y"),
                correctText = "X",
                hasConfirmedKey = true,
                masteryStatus = QuestionMasteryStatus.MASTERED,
                wrongCount = 0,
                correctStreak = 2,
                attemptCount = 2,
                correctCount = 2,
                totalTimeSeconds = 20L,
                lastTimeSeconds = 10L,
                timedCount = 2,
            ),
        )

        val stats = QuizEngine.computeFileStatistics(bankId = 9L, bankTitle = "Torpaq.docx", questions = questions)
        assertEquals(2, stats.totalQuestions)
        assertEquals(1, stats.masteredCount)
        assertEquals(1, stats.weakCount)
        assertEquals(140L, stats.totalTimeSeconds)
        assertEquals(23L, stats.avgSecondsPerTimedQuestion)
        assertEquals(50, stats.accuracyPercent)
        assertEquals(1L, stats.mostWrongQuestions.first().rowId)
        assertEquals(1L, stats.slowestQuestions.first().rowId)
    }

    private fun buildTwoPagePdfWithCorruptFirstStream(): ByteArray {
        val corruptFlate = byteArrayOf(0x78.toByte(), 0x9C.toByte(), 0xBD.toByte(), 0x5B.toByte(), 0xFF.toByte(), 0x00.toByte())
        val validContent = """
            BT
            /F1 12 Tf
            72 720 Td (1. Test suali?) Tj
            0 -20 Td (A\) Dogru variant*) Tj
            0 -20 Td (B\) Yanlis variant) Tj
            ET
        """.trimIndent().toByteArray(Charsets.US_ASCII)

        val out = ByteArrayOutputStream()
        fun writeAscii(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        writeAscii("%PDF-1.4\n")
        val offsets = mutableListOf(0)

        fun startObj(id: Int) {
            offsets.add(out.size())
            writeAscii("$id 0 obj\n")
        }

        startObj(1)
        writeAscii("<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")

        startObj(2)
        writeAscii("<< /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 >>\nendobj\n")

        startObj(3)
        writeAscii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 5 0 R >> >> /Contents 6 0 R >>\nendobj\n")

        startObj(4)
        writeAscii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 5 0 R >> >> /Contents 7 0 R >>\nendobj\n")

        startObj(5)
        writeAscii("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n")

        startObj(6)
        writeAscii("<< /Length ${corruptFlate.size} /Filter /FlateDecode >>\nstream\n")
        out.write(corruptFlate)
        writeAscii("\nendstream\nendobj\n")

        startObj(7)
        writeAscii("<< /Length ${validContent.size} >>\nstream\n")
        out.write(validContent)
        writeAscii("\nendstream\nendobj\n")

        val xrefOffset = out.size()
        writeAscii("xref\n0 ${offsets.size}\n")
        writeAscii("0000000000 65535 f \n")
        for (i in 1 until offsets.size) {
            writeAscii(String.format("%010d 00000 n \n", offsets[i]))
        }
        writeAscii("trailer\n<< /Size ${offsets.size} /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")
        return out.toByteArray()
    }

    @Test
    fun twoColumnPdfParserSeparatesColumnsAndMergesStackedFractions() {
        val content = """
            BT
            /F1 11 Tf
            1 0 0 1 54 720 Tm (1. Sol birinci sual?) Tj
            1 0 0 1 54 700 Tm (A\) 14 B\) 10) Tj
            1 0 0 1 54 682 Tm (C\) 11 D\) 18) Tj
            1 0 0 1 54 656 Tm (2. Sol ikinci sual hansidir?) Tj
            1 0 0 1 320 720 Tm (A\) 5 sm B\) 5 dm) Tj
            1 0 0 1 320 700 Tm (C\) 2 sm D\) 3 sm) Tj
            1 0 0 1 320 674 Tm (3. Sag sutun suali: hissesi necedir?) Tj
            1 0 0 1 390 658 Tm (1) Tj
            1 0 0 1 390 644 Tm (6) Tj
            1 0 0 1 320 626 Tm (A\) 20 B\) 30) Tj
            1 0 0 1 320 608 Tm (C\) 40 D\) 50) Tj
            ET
        """.trimIndent().toByteArray(Charsets.US_ASCII)

        val pdfBytes = buildSinglePagePdf(content)
        val lines = QuizDocumentParser.extractPdfLines(null, ByteArrayInputStream(pdfBytes))
        val parsed = QuizDocumentParser.parseStructuredLines(lines, "Iki Sutun", AnswerRule.AUTO).getOrThrow()

        assertEquals(3, parsed.questions.size)
        assertEquals(listOf("14", "10", "11", "18"), parsed.questions[0].allOptions)
        assertEquals(listOf("5 sm", "5 dm", "2 sm", "3 sm"), parsed.questions[1].allOptions)
        assertEquals(listOf("20", "30", "40", "50"), parsed.questions[2].allOptions)
        assertTrue(parsed.questions[2].questionText.contains("1/6"))
    }

    @Test
    fun sampleTwoColumnMathPdfParsesCleanlyWithoutColumnMixing() {
        val sampleFile = listOf(
            java.io.File("public/assets/aistudio/iv-riyaziyyat_1aab370b.pdf"),
            java.io.File("../public/assets/aistudio/iv-riyaziyyat_1aab370b.pdf"),
        ).firstOrNull { it.exists() } ?: return

        val lines = sampleFile.inputStream().use { QuizDocumentParser.extractPdfLines(null, it) }
        val parsed = QuizDocumentParser.parseStructuredLines(lines, "IV Riyaziyyat", AnswerRule.AUTO).getOrThrow()

        assertEquals(42, parsed.questions.size)
        val q2 = parsed.questions.first { it.questionNumber == 2 }
        assertEquals(listOf("14", "10", "11", "18"), q2.allOptions)
        assertFalse(q2.questionText.contains("dm"))

        val q8 = parsed.questions.first { it.questionNumber == 8 }
        assertEquals(listOf("5 sm", "5 dm", "2 sm", "3 sm"), q8.allOptions)

        val q12 = parsed.questions.first { it.questionNumber == 12 }
        assertTrue("Expected stacked fraction 1/6 in Q12, got: ${q12.questionText}", q12.questionText.contains("1/6"))

        val q19 = parsed.questions.first { it.questionNumber == 19 }
        assertEquals(listOf("8", "13", "15", "16"), q19.allOptions)

        val q43 = parsed.questions.first { it.questionNumber == 43 }
        assertEquals(4, q43.allOptions.size)
    }

    private fun buildSinglePagePdf(contentStream: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        fun writeAscii(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        writeAscii("%PDF-1.4\n")
        val offsets = mutableListOf(0)

        fun startObj(id: Int) {
            offsets.add(out.size())
            writeAscii("$id 0 obj\n")
        }

        startObj(1)
        writeAscii("<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")

        startObj(2)
        writeAscii("<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n")

        startObj(3)
        writeAscii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>\nendobj\n")

        startObj(4)
        writeAscii("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n")

        startObj(5)
        writeAscii("<< /Length ${contentStream.size} >>\nstream\n")
        out.write(contentStream)
        writeAscii("\nendstream\nendobj\n")

        val xrefOffset = out.size()
        writeAscii("xref\n0 ${offsets.size}\n")
        writeAscii("0000000000 65535 f \n")
        for (i in 1 until offsets.size) {
            writeAscii(String.format("%010d 00000 n \n", offsets[i]))
        }
        writeAscii("trailer\n<< /Size ${offsets.size} /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")
        return out.toByteArray()
    }
}
