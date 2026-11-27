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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
                            text = "Cavab Seçimi ($keyedCount/${questions.size})",
                            style = MaterialTheme.typography.titleLarge,
                            color = vip.textPrimary,
                        )
                        Text(
                            text = "Hər sualın doğru cavabına toxunun, sonra Testə Keçin",
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
fun SingleQuestionKeyDialog(
    question: QuizQuestion,
    onSaveAndShuffle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val vip = LocalVipColors.current
    var selectedOption by remember(question.rowId) {
        mutableStateOf(question.correctText.ifBlank { question.allOptions.firstOrNull().orEmpty() })
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = vip.surface,
            border = BorderStroke(1.dp, vip.goldBorder),
            modifier = Modifier.fillMaxWidth(),
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
                    Text(
                        text = "Sual #${question.questionNumber} — Doğru Cavab",
                        style = MaterialTheme.typography.titleMedium,
                        color = vip.gold,
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("single_key_close_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Bağla",
                            tint = vip.textSecondary,
                        )
                    }
                }

                Text(
                    text = question.questionText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = vip.textPrimary,
                )

                Column(
                    modifier = Modifier
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    question.allOptions.forEachIndexed { idx, opt ->
                        val active = selectedOption == opt
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { selectedOption = opt },
                            shape = RoundedCornerShape(14.dp),
                            color = if (active) vip.emeraldSoft else vip.surfaceElevated,
                            border = BorderStroke(1.dp, if (active) vip.emeraldBorder else vip.border),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (active) vip.emerald else vip.surface,
                                ) {
                                    Text(
                                        text = if (active) "✓" else QuizEngine.optionPrefix(idx),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (active) MaterialTheme.colorScheme.onPrimary else vip.gold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                                Text(
                                    text = opt,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = vip.textPrimary,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        if (selectedOption.isNotBlank()) {
                            onSaveAndShuffle(selectedOption)
                        }
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("single_key_save_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = vip.emerald,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text("Yadda Saxla və Qarışdır")
                }
            }
        }
    }
}
