package com.aistudio.testbaza.core.domain

import kotlin.random.Random

object QuizEngine {

    private val OPTION_LETTERS = listOf("A", "B", "C", "D", "E", "F")

    fun optionPrefix(index: Int): String =
        OPTION_LETTERS.getOrElse(index) { (index + 1).toString() }

    fun buildChunkOptions(
        totalCount: Int,
        chunkSize: Int = 30,
    ): List<ChunkOption> {
        if (totalCount <= 0) return listOf(ChunkOption(index = null, label = "Bütün baza"))
        val options = mutableListOf(
            ChunkOption(index = null, label = "Bütün baza (1–$totalCount)")
        )
        var start = 0
        var idx = 0
        while (start < totalCount) {
            val end = minOf(start + chunkSize, totalCount)
            options.add(
                ChunkOption(
                    index = idx,
                    label = "Blok ${idx + 1} (${start + 1}–$end)",
                )
            )
            start += chunkSize
            idx++
        }
        return options
    }

    fun filterBaseQuestions(
        allQuestions: List<QuizQuestion>,
        mode: StudyMode,
        selectedChunkIndex: Int?,
        chunkSize: Int = 30,
    ): List<QuizQuestion> {
        if (allQuestions.isEmpty()) return emptyList()
        return when (mode) {
            StudyMode.EXAM -> allQuestions.filter { it.isExam }
            StudyMode.WEAK -> allQuestions.filter { it.masteryStatus == QuestionMasteryStatus.WEAK }
            StudyMode.QUIZ, StudyMode.FLASHCARD -> {
                if (selectedChunkIndex == null) {
                    allQuestions
                } else {
                    val start = selectedChunkIndex * chunkSize
                    if (start >= allQuestions.size) emptyList()
                    else allQuestions.subList(start, minOf(start + chunkSize, allQuestions.size))
                }
            }
        }
    }

    fun selectExamQuestions(
        allQuestions: List<QuizQuestion>,
        requestedCount: Int,
        shuffle: Boolean = true,
        random: Random = Random.Default,
    ): List<QuizQuestion> {
        if (allQuestions.isEmpty()) return emptyList()
        val limit = requestedCount.coerceIn(1, minOf(ExamConfig.MAX_QUESTIONS, allQuestions.size))
        val pool = if (shuffle) allQuestions.shuffled(random) else allQuestions
        return pool.take(limit)
    }

    fun buildSessionItems(
        questions: List<QuizQuestion>,
        shuffle: Boolean,
        random: Random = Random.Default,
    ): List<SessionQuestionItem> {
        val orderedQuestions = if (shuffle) questions.shuffled(random) else questions
        return orderedQuestions.map { q ->
            val targetCorrect = q.correctText.ifBlank { q.allOptions.firstOrNull().orEmpty() }
            val options = if (shuffle) q.allOptions.shuffled(random) else q.allOptions
            val correctIndex = options.indexOf(targetCorrect).let { if (it >= 0) it else 0 }
            SessionQuestionItem(
                question = q,
                shuffledOptions = options,
                correctIdx = correctIndex,
            )
        }
    }

    fun buildExamEvaluationSummary(
        items: List<SessionQuestionItem>,
        perQuestionSeconds: List<Long>,
    ): ExamEvaluationSummary {
        var answered = 0
        var correct = 0
        var wrong = 0
        var unanswered = 0
        val wrongList = mutableListOf<ExamWrongDetail>()
        val timings = mutableListOf<QuestionTimingDetail>()

        items.forEachIndexed { idx, item ->
            val spentSec = perQuestionSeconds.getOrElse(idx) { 0L }.coerceAtLeast(0L)
            val qNum = item.question.questionNumber.coerceAtLeast(1)
            val qText = item.question.questionText.take(240)
            val selectedText = item.selectedIdx?.let { item.shuffledOptions.getOrNull(it) }.orEmpty()
            val correctText = item.shuffledOptions.getOrElse(item.correctIdx) { item.question.correctText }

            val status = when {
                !item.isAnswered -> {
                    unanswered++
                    "SKIPPED"
                }
                item.isCorrect == true -> {
                    answered++
                    correct++
                    "CORRECT"
                }
                else -> {
                    answered++
                    wrong++
                    wrongList.add(
                        ExamWrongDetail(
                            questionNumber = qNum,
                            questionText = qText,
                            selectedAnswer = selectedText.take(240),
                            correctAnswer = correctText.take(240),
                            timeSpentSeconds = spentSec,
                        )
                    )
                    "WRONG"
                }
            }
            timings.add(
                QuestionTimingDetail(
                    questionNumber = qNum,
                    questionText = qText,
                    status = status,
                    timeSpentSeconds = spentSec,
                )
            )
        }

        return ExamEvaluationSummary(
            totalQuestions = items.size,
            answeredCount = answered,
            correctCount = correct,
            wrongCount = wrong,
            unansweredCount = unanswered,
            wrongDetails = wrongList,
            questionTimings = timings,
        )
    }

    fun computeFileStatistics(
        bankId: Long,
        bankTitle: String,
        questions: List<QuizQuestion>,
    ): FileStatisticsSummary {
        val total = questions.size
        val answeredQuestions = questions.count { it.attemptCount > 0 || it.masteryStatus != QuestionMasteryStatus.NEW }
        val mastered = questions.count { it.masteryStatus == QuestionMasteryStatus.MASTERED }
        val weak = questions.count { it.masteryStatus == QuestionMasteryStatus.WEAK }
        val newCount = (total - mastered - weak).coerceAtLeast(0)

        val totalAttempts = questions.sumOf { it.attemptCount }
        val totalCorrect = questions.sumOf { it.correctCount }
        val totalWrong = questions.sumOf { it.wrongCount }
        val denom = (totalCorrect + totalWrong).takeIf { it > 0 } ?: totalAttempts
        val accuracy = if (denom > 0) ((totalCorrect * 100) / denom).coerceIn(0, 100) else 0

        val totalTime = questions.sumOf { it.totalTimeSeconds }
        val totalTimedCount = questions.sumOf { it.timedCount }
        val avgSec = if (totalTimedCount > 0) (totalTime + totalTimedCount / 2) / totalTimedCount else 0L

        val timed = questions
            .filter { it.totalTimeSeconds > 0L || it.lastTimeSeconds > 0L }
            .sortedByDescending { it.averageTimeSeconds }
        val slowest = timed.take(5)
        val mostWrong = questions
            .filter { it.wrongCount > 0 }
            .sortedWith(compareByDescending<QuizQuestion> { it.wrongCount }.thenByDescending { it.averageTimeSeconds })

        val estimated50 = if (avgSec > 0L) avgSec * minOf(50, maxOf(1, total)) else 0L

        return FileStatisticsSummary(
            bankId = bankId,
            bankTitle = bankTitle.ifBlank { "Testbaza" },
            totalQuestions = total,
            answeredQuestionsCount = answeredQuestions,
            masteredCount = mastered,
            weakCount = weak,
            newCount = newCount,
            totalAttempts = totalAttempts,
            totalCorrectAttempts = totalCorrect,
            totalWrongAttempts = totalWrong,
            accuracyPercent = accuracy,
            totalTimeSeconds = totalTime,
            avgSecondsPerTimedQuestion = avgSec,
            slowestQuestions = slowest,
            mostWrongQuestions = mostWrong,
            timedQuestions = questions.filter { it.attemptCount > 0 || it.totalTimeSeconds > 0L || it.wrongCount > 0 },
            estimated50QuestionSeconds = estimated50,
        )
    }

    fun formatResultShareText(
        payload: ShareResultPayload,
        includeDetails: Boolean,
    ): String {
        val s = payload.summary
        val pct = if (s.totalQuestions > 0) (s.correctCount * 100) / s.totalQuestions else 0
        return buildString {
            appendLine("Testbaza — Nəticə Hesabatı")
            appendLine("Test: ${payload.title.ifBlank { "Sınaq İmtahanı" }}")
            appendLine("Nəticə: $pct% (${s.correctCount}/${s.totalQuestions} düzgün)")
            appendLine("Düzgün: ${s.correctCount} | Səhv: ${s.wrongCount} | Boş: ${s.unansweredCount}")
            if (payload.totalElapsedSeconds > 0L) {
                val limitSuffix = payload.durationLimitMinutes?.let { " / Limit: $it dəq" }.orEmpty()
                val timeoutTag = if (payload.timedOut) " (Vaxt bitdi)" else ""
                append("Ümumi vaxt: ${formatDurationHumanAz(payload.totalElapsedSeconds)}$limitSuffix$timeoutTag")
            } else {
                append("Status: Tamamlandı")
            }

            if (includeDetails) {
                appendLine()
                appendLine()
                appendLine("--- Statistik Məlumatlar ---")
                val timedEntries = s.questionTimings.filter { it.timeSpentSeconds > 0L }
                if (timedEntries.isNotEmpty()) {
                    val avg = timedEntries.sumOf { it.timeSpentSeconds } / timedEntries.size
                    val fastest = timedEntries.minByOrNull { it.timeSpentSeconds }
                    val slowest = timedEntries.maxByOrNull { it.timeSpentSeconds }
                    appendLine("Orta sual vaxtı: ${formatDurationHumanAz(avg)}")
                    if (fastest != null) {
                        appendLine("Ən sürətli sual: #${fastest.questionNumber} (${fastest.timeSpentSeconds} san)")
                    }
                    if (slowest != null) {
                        appendLine("Ən yavaş sual: #${slowest.questionNumber} (${slowest.timeSpentSeconds} san)")
                    }
                }
                if (s.questionTimings.isNotEmpty()) {
                    appendLine("Hər sual üzrə vaxt:")
                    val timingLine = s.questionTimings.joinToString(", ") { t ->
                        val mark = when (t.status) {
                            "CORRECT" -> "✓"
                            "WRONG" -> "✗"
                            else -> "—"
                        }
                        "#${t.questionNumber}: ${t.timeSpentSeconds}s ($mark)"
                    }
                    appendLine(timingLine)
                }

                appendLine()
                if (s.wrongDetails.isEmpty()) {
                    append("Səhv edilən sual yoxdur.")
                } else {
                    appendLine("--- Səhv Edilən Suallar (${s.wrongDetails.size}) ---")
                    s.wrongDetails.forEach { w ->
                        appendLine("• Sual #${w.questionNumber} (${w.timeSpentSeconds} san): ${w.questionText}")
                        appendLine("  Verilən cavab: ${w.selectedAnswer}")
                        appendLine("  Doğru cavab: ${w.correctAnswer}")
                    }
                }
            }
        }.trim()
    }

    fun applyJoker5050(
        item: SessionQuestionItem,
        random: Random = Random.Default,
    ): SessionQuestionItem {
        if (item.isAnswered) return item
        val availableWrongIndices = item.shuffledOptions.indices.filter { idx ->
            idx != item.correctIdx && idx !in item.disabledOptions
        }
        if (availableWrongIndices.isEmpty()) return item
        val newlyDisabled = availableWrongIndices.shuffled(random).take(2)
        return item.copy(disabledOptions = item.disabledOptions + newlyDisabled)
    }

    fun formatTimerMmSs(totalSeconds: Int): String {
        val safe = totalSeconds.coerceAtLeast(0)
        val mins = safe / 60
        val secs = safe % 60
        return "%02d:%02d".format(mins, secs)
    }

    fun formatDurationHumanAz(totalSeconds: Long): String {
        val safe = totalSeconds.coerceAtLeast(0L)
        val hours = safe / 3600L
        val mins = (safe % 3600L) / 60L
        val secs = safe % 60L
        return when {
            hours > 0L -> "$hours saat $mins dəq $secs san"
            mins > 0L -> "$mins dəq $secs san"
            else -> "$secs san"
        }
    }
}
