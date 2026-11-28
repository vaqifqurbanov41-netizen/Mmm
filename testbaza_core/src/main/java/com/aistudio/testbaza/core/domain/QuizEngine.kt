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

    fun buildSessionItemsFromSharedExam(
        sharedExam: SharedExam,
        random: Random = Random.Default,
    ): List<SessionQuestionItem> {
        val mappedQuestions = sharedExam.questions.mapIndexed { idx, snap ->
            QuizQuestion(
                rowId = -(idx + 1).toLong(),
                bankId = -1L,
                questionNumber = snap.qNum.toInt().takeIf { it > 0 } ?: (idx + 1),
                category = sharedExam.sourceBankTitle,
                isExam = false,
                questionText = snap.text,
                allOptions = snap.options,
                correctText = snap.correct,
                hasConfirmedKey = true,
                masteryStatus = QuestionMasteryStatus.NEW,
                wrongCount = 0,
                correctStreak = 0,
            )
        }
        return mappedQuestions.map { q ->
            val targetCorrect = q.correctText.ifBlank { q.allOptions.firstOrNull().orEmpty() }
            val options = if (sharedExam.shuffleOptions) q.allOptions.shuffled(random) else q.allOptions
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
            val qNum = item.question.questionNumber.toLong().coerceAtLeast(1L)
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
