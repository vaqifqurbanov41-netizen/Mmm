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
}
