package com.aistudio.testbaza.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aistudio.testbaza.core.domain.QuizEngine
import com.aistudio.testbaza.core.domain.QuizQuestion
import com.aistudio.testbaza.core.ui.theme.LocalVipColors

@Composable
fun KeyEditorModalDialog(
    questions: List<QuizQuestion>,
    keyedCount: Int,
    onSelectAnswer: (QuizQuestion, String) -> Unit,
    onEditQuestionClick: (QuizQuestion) -> Unit,
    onSelectAllFirst: () -> Unit,
    onStartShuffledQuiz: () -> Unit,
    onDismiss: () -> Unit,
) {
    val vip = LocalVipColors.current
    var searchQuery by remember { mutableStateOf("") }

    val filtered = remember(questions, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) questions
        else questions.filter {
            it.questionText.contains(q, ignoreCase = true) ||
                it.questionNumber.toString() == q
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(vip.background)
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Cavab Seçimi və Redaktə ($keyedCount/${questions.size})",
                            style = MaterialTheme.typography.titleLarge,
                            color = vip.textPrimary,
                        )
                        Text(
                            text = "Doğru cavabı seçin və ya qələm işarəsi ilə sualı redaktə edin",
                            style = MaterialTheme.typography.bodyMedium,
                            color = vip.textSecondary,
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("key_editor_close_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Bağla",
                            tint = vip.textSecondary,
                        )
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Sual nömrəsi və ya söz axtar...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Axtar",
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 10.dp)
                        .testTag("key_editor_search_input"),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onSelectAllFirst,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("key_editor_select_all_first_button"),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, vip.goldBorder),
                    ) {
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = "Hamısında 1-ci variant",
                            tint = vip.gold,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = "Hamısı 1-ci",
                            color = vip.gold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Button(
                        onClick = {
                            onStartShuffledQuiz()
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1.3f)
                            .heightIn(min = 48.dp)
                            .testTag("key_editor_start_quiz_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = vip.emerald,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Testə keç və qarışdır",
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text("Testə Keç (Qarışdır)")
                    }
                }

                HorizontalDivider(color = vip.border)

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(filtered, key = { it.rowId }) { q ->
                        KeyEditorQuestionCard(
                            question = q,
                            onSelectOption = { opt -> onSelectAnswer(q, opt) },
                            onEditClick = { onEditQuestionClick(q) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyEditorQuestionCard(
    question: QuizQuestion,
    onSelectOption: (String) -> Unit,
    onEditClick: () -> Unit,
) {
    val vip = LocalVipColors.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = vip.surface,
        border = BorderStroke(
            1.dp,
            if (question.hasConfirmedKey) vip.emeraldBorder else vip.border,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = "${question.questionNumber}. ${question.questionText}",
                    style = MaterialTheme.typography.titleMedium,
                    color = vip.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (question.hasConfirmedKey) vip.emeraldSoft else vip.surfaceElevated,
                        border = BorderStroke(
                            1.dp,
                            if (question.hasConfirmedKey) vip.emeraldBorder else vip.border,
                        ),
                    ) {
                        Text(
                            text = if (question.hasConfirmedKey) "✓ Seçilib" else "Seçilməyib",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (question.hasConfirmedKey) vip.emerald else vip.textSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("key_editor_edit_button_${question.rowId}"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Sualı redaktə et",
                            tint = vip.gold,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                question.allOptions.forEachIndexed { idx, optStr ->
                    val isSelected = question.hasConfirmedKey && question.correctText == optStr
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectOption(optStr) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) vip.emeraldSoft else vip.surfaceElevated,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) vip.emeraldBorder else vip.border,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) vip.emerald else vip.surface),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Seçilmiş",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(14.dp),
                                    )
                                } else {
                                    Text(
                                        text = QuizEngine.optionPrefix(idx),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = vip.textSecondary,
                                    )
                                }
                            }
                            Text(
                                text = optStr,
                                style = MaterialTheme.typography.bodyMedium,
                                color = vip.textPrimary,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditQuestionDialog(
    question: QuizQuestion,
    onSave: (newQuestionText: String, newOptions: List<String>, newCorrectText: String, reshuffleNow: Boolean) -> Unit,
    onDeleteQuestion: () -> Unit,
    onDismiss: () -> Unit,
) {
    val vip = LocalVipColors.current
    var questionText by remember(question.rowId) { mutableStateOf(question.questionText) }
    val optionsState = remember(question.rowId) {
        val initial = question.allOptions.ifEmpty { listOf("", "") }.toMutableList()
        while (initial.size < 2) initial.add("")
        mutableStateListOf<String>().apply { addAll(initial.take(6)) }
    }
    var correctIdx by remember(question.rowId) {
        val found = question.allOptions.indexOf(question.correctText)
        mutableIntStateOf(if (found >= 0) found else 0)
    }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val cleanedPreviewOptions = optionsState.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    val canSave = questionText.trim().isNotEmpty() && cleanedPreviewOptions.size >= 2

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Sual #${question.questionNumber} silinsin?") },
            text = {
                Text("Bu sual aktiv test bazasından həmişəlik silinəcək.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteQuestion()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = vip.rose),
                    modifier = Modifier.testTag("edit_confirm_delete_question_button"),
                ) {
                    Text("Sil")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Ləğv et")
                }
            },
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = vip.surface,
                border = BorderStroke(1.dp, vip.goldBorder),
                modifier = Modifier
                    .widthIn(max = 580.dp)
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sual #${question.questionNumber} — Redaktə",
                                style = MaterialTheme.typography.titleMedium,
                                color = vip.gold,
                            )
                            Text(
                                text = "Mətni, variantları və doğru cavabı düzəldin",
                                style = MaterialTheme.typography.bodySmall,
                                color = vip.textSecondary,
                            )
                        }
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("edit_question_delete_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Sualı sil",
                                tint = vip.rose,
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("edit_question_close_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Bağla",
                                tint = vip.textSecondary,
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .heightIn(max = 460.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = questionText,
                            onValueChange = { questionText = it },
                            label = { Text("Sual mətni") },
                            minLines = 2,
                            maxLines = 6,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("edit_question_text_input"),
                        )

                        Text(
                            text = "Variantlar (doğru cavab üçün hərfə toxunun):",
                            style = MaterialTheme.typography.labelMedium,
                            color = vip.textSecondary,
                        )

                        optionsState.forEachIndexed { idx, optValue ->
                            val isCorrect = idx == correctIdx
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { correctIdx = idx }
                                        .testTag("edit_option_correct_badge_$idx"),
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isCorrect) vip.emerald else vip.surfaceElevated,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isCorrect) vip.emerald else vip.goldBorder,
                                    ),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (isCorrect) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Doğru cavab seçilib",
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        } else {
                                            Text(
                                                text = QuizEngine.optionPrefix(idx),
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = vip.gold,
                                            )
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = optValue,
                                    onValueChange = { optionsState[idx] = it },
                                    label = { Text("${QuizEngine.optionPrefix(idx)} variantı") },
                                    maxLines = 3,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("edit_option_input_$idx"),
                                )

                                if (optionsState.size > 2) {
                                    IconButton(
                                        onClick = {
                                            optionsState.removeAt(idx)
                                            correctIdx = when {
                                                correctIdx == idx -> 0
                                                correctIdx > idx -> correctIdx - 1
                                                else -> correctIdx
                                            }.coerceIn(0, maxOf(0, optionsState.lastIndex))
                                        },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("edit_option_remove_$idx"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Variantı sil",
                                            tint = vip.rose,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }

                        if (optionsState.size < 6) {
                            OutlinedButton(
                                onClick = { optionsState.add("") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("edit_add_option_button"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, vip.borderStrong),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Variant əlavə et",
                                    tint = vip.gold,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.size(6.dp))
                                Text("Variant əlavə et", color = vip.gold)
                            }
                        }

                        if (!canSave) {
                            Text(
                                text = "Sual mətni və ən azı 2 fərqli variant boş olmamalıdır.",
                                style = MaterialTheme.typography.bodySmall,
                                color = vip.rose,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                val chosenRaw = optionsState.getOrElse(correctIdx) { "" }.trim()
                                val cleaned = optionsState.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                                val resolvedCorrect = if (chosenRaw in cleaned) chosenRaw else cleaned.firstOrNull().orEmpty()
                                onSave(questionText.trim(), cleaned, resolvedCorrect, false)
                                onDismiss()
                            },
                            enabled = canSave,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("edit_question_save_button"),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, vip.goldBorder),
                        ) {
                            Text(
                                text = "Saxla",
                                color = vip.gold,
                                maxLines = 1,
                            )
                        }

                        Button(
                            onClick = {
                                val chosenRaw = optionsState.getOrElse(correctIdx) { "" }.trim()
                                val cleaned = optionsState.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                                val resolvedCorrect = if (chosenRaw in cleaned) chosenRaw else cleaned.firstOrNull().orEmpty()
                                onSave(questionText.trim(), cleaned, resolvedCorrect, true)
                                onDismiss()
                            },
                            enabled = canSave,
                            modifier = Modifier
                                .weight(1.35f)
                                .heightIn(min = 48.dp)
                                .testTag("edit_question_save_shuffle_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = vip.emerald,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Text(
                                text = "Saxla və Qarışdır",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
