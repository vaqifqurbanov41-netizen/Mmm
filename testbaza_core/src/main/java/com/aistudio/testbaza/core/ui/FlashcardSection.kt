package com.aistudio.testbaza.core.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aistudio.testbaza.core.domain.SessionQuestionItem
import com.aistudio.testbaza.core.ui.theme.LocalVipColors

@Composable
fun FlashcardSection(
    item: SessionQuestionItem,
    currentIndex: Int,
    totalCount: Int,
    isFlipped: Boolean,
    onFlip: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleStar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vip = LocalVipColors.current
    val density = LocalDensity.current.density
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
        label = "flashcard_flip",
    )

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 320.dp, max = 440.dp)
                    .graphicsLayer {
                        rotationY = rotation
                        cameraDistance = 14f * density
                    }
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(onClick = onFlip)
                    .testTag("flashcard_surface"),
                shape = RoundedCornerShape(24.dp),
                color = vip.surface,
                border = BorderStroke(
                    1.2.dp,
                    if (rotation > 90f) vip.emeraldBorder else vip.goldBorder,
                ),
                shadowElevation = 6.dp,
            ) {
                if (rotation <= 90f) {
                    FlashcardFrontFace(
                        item = item,
                        currentIndex = currentIndex,
                        totalCount = totalCount,
                        onToggleStar = onToggleStar,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { rotationY = 180f },
                    ) {
                        FlashcardBackFace(item = item)
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onPrevious,
                enabled = currentIndex > 0,
                modifier = Modifier
                    .heightIn(min = 50.dp)
                    .weight(1f)
                    .testTag("flashcard_prev_button"),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, vip.borderStrong),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Əvvəlki kart",
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text("Əvvəlki")
            }

            Button(
                onClick = onFlip,
                modifier = Modifier
                    .heightIn(min = 50.dp)
                    .weight(1.1f)
                    .testTag("flashcard_flip_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = vip.goldSoft,
                    contentColor = vip.gold,
                ),
                border = BorderStroke(1.dp, vip.goldBorder),
            ) {
                Icon(
                    imageVector = Icons.Default.Flip,
                    contentDescription = "Kartı çevir",
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text("Çevir")
            }

            Button(
                onClick = onNext,
                enabled = currentIndex < totalCount - 1,
                modifier = Modifier
                    .heightIn(min = 50.dp)
                    .weight(1f)
                    .testTag("flashcard_next_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = vip.emerald,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text("Növbəti")
                Spacer(Modifier.size(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Növbəti kart",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun FlashcardFrontFace(
    item: SessionQuestionItem,
    currentIndex: Int,
    totalCount: Int,
    onToggleStar: () -> Unit,
) {
    val vip = LocalVipColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(vip.surfaceElevated.copy(alpha = 0.65f), vip.surface),
                )
            )
            .padding(22.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = vip.goldSoft,
                border = BorderStroke(1.dp, vip.goldBorder),
            ) {
                Text(
                    text = "SUAL #${item.question.questionNumber} • ${currentIndex + 1}/$totalCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = vip.gold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }

            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onToggleStar)
                    .testTag("flashcard_star_button"),
                shape = RoundedCornerShape(10.dp),
                color = if (item.question.isExam) vip.goldSoft else vip.surfaceElevated,
                border = BorderStroke(1.dp, if (item.question.isExam) vip.goldBorder else vip.border),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = if (item.question.isExam) Icons.Default.Star else Icons.Outlined.StarOutline,
                        contentDescription = "Seçilmiş sual",
                        tint = if (item.question.isExam) vip.gold else vip.textSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = if (item.question.isExam) "Seçilib" else "Seç",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (item.question.isExam) vip.gold else vip.textSecondary,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = item.question.questionText,
                style = MaterialTheme.typography.titleLarge,
                color = vip.textPrimary,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Text(
            text = "Cavabı görmək üçün karta toxunun",
            style = MaterialTheme.typography.labelMedium,
            color = vip.textSecondary,
        )
    }
}

@Composable
private fun FlashcardBackFace(item: SessionQuestionItem) {
    val vip = LocalVipColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(vip.emeraldSoft, vip.surface),
                )
            )
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Doğru cavab",
                tint = vip.emerald,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "DOĞRU CAVAB • SUAL #${item.question.questionNumber}",
                style = MaterialTheme.typography.labelSmall,
                color = vip.emerald,
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = item.question.correctText,
                style = MaterialTheme.typography.titleLarge,
                color = vip.textPrimary,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
            )
        }

        Text(
            text = "Suala qayıtmaq üçün toxunun",
            style = MaterialTheme.typography.labelMedium,
            color = vip.textSecondary,
        )
    }
}
