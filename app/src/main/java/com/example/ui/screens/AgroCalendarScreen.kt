package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AdminContentItem
import com.example.data.model.AgroCalendarCatalog
import com.example.data.model.AgroMonthPlan
import com.example.i18n.AppLanguage
import com.example.i18n.LocalStrings
import com.example.ui.components.ScreenTopBar
import com.example.ui.theme.LocalAqroColors
import java.util.Calendar

private data class MonthPalette(
    val accent: Color,
    val softBgLight: Color,
    val softBgDark: Color,
)

private fun paletteForMonth(month: Int): MonthPalette = when ((month - 1) % 6) {
    0 -> MonthPalette(Color(0xFFB91C1C), Color(0xFFFEF2F2), Color(0xFF311517))
    1 -> MonthPalette(Color(0xFF3B5B78), Color(0xFFF1F5F9), Color(0xFF17222D))
    2 -> MonthPalette(Color(0xFFA1821B), Color(0xFFF6F9ED), Color(0xFF232614))
    3 -> MonthPalette(Color(0xFF0F766E), Color(0xFFEEF9F7), Color(0xFF132624))
    4 -> MonthPalette(Color(0xFF6D28D9), Color(0xFFF5F3FF), Color(0xFF231933))
    else -> MonthPalette(Color(0xFF15803D), Color(0xFFF0FDF4), Color(0xFF13281A))
}

@Composable
fun AgroCalendarScreen(
    language: AppLanguage,
    adminCalendarNotes: List<AdminContentItem> = emptyList(),
    onBack: () -> Unit,
    onAskAiAboutMonth: ((String) -> Unit)? = null,
) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current
    val context = LocalContext.current

    var selectedCategoryId by remember { mutableStateOf(AgroCalendarCatalog.categories.first().id) }
    val categoryCrops = remember(selectedCategoryId) {
        AgroCalendarCatalog.cropsForCategory(selectedCategoryId)
    }
    var selectedCropId by remember { mutableStateOf(categoryCrops.firstOrNull()?.id ?: "wheat") }

    val activeCrop = remember(selectedCategoryId, selectedCropId) {
        categoryCrops.firstOrNull { it.id == selectedCropId } ?: categoryCrops.firstOrNull()
    }

    var categoryMenuOpen by remember { mutableStateOf(false) }
    var cropMenuOpen by remember { mutableStateOf(false) }

    val currentMonth = remember { Calendar.getInstance().get(Calendar.MONTH) + 1 }

    fun shareMonthPlan(cropName: String, plan: AgroMonthPlan) {
        val monthTitle = AgroCalendarCatalog.monthName(plan.monthNumber, language)
        val tasks = plan.localizedTasks(language)
        val text = buildString {
            appendLine("${strings.appName} — ${strings.agroCalendarTitle}")
            appendLine("$cropName • $monthTitle")
            appendLine()
            tasks.forEach { appendLine("• $it") }
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "$cropName — $monthTitle")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, monthTitle))
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("agro_calendar_screen"),
    ) {
        item {
            ScreenTopBar(title = strings.agroCalendarTitle, onBack = onBack)
        }

        // Category & Crop Selector Row (matching Yonca design)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = strings.agroCategoryLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = extra.muted,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // Category Selector
                    Box {
                        val selectedCategoryName = AgroCalendarCatalog.categories
                            .firstOrNull { it.id == selectedCategoryId }
                            ?.localizedName(language)
                            .orEmpty()
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { categoryMenuOpen = true }
                                .testTag("calendar_category_btn"),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    text = selectedCategoryName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowDown,
                                    contentDescription = strings.agroCategoryLabel,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = categoryMenuOpen,
                            onDismissRequest = { categoryMenuOpen = false },
                        ) {
                            AgroCalendarCatalog.categories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.localizedName(language)) },
                                    onClick = {
                                        selectedCategoryId = category.id
                                        val firstInCategory = AgroCalendarCatalog
                                            .cropsForCategory(category.id)
                                            .firstOrNull()
                                        if (firstInCategory != null) {
                                            selectedCropId = firstInCategory.id
                                        }
                                        categoryMenuOpen = false
                                    },
                                    modifier = Modifier.testTag("calendar_category_item_${category.id}"),
                                )
                            }
                        }
                    }

                    // Crop Selector
                    Box {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { cropMenuOpen = true }
                                .testTag("calendar_crop_btn"),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    text = activeCrop?.localizedName(language).orEmpty(),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowDown,
                                    contentDescription = strings.agroCropLabel,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = cropMenuOpen,
                            onDismissRequest = { cropMenuOpen = false },
                        ) {
                            categoryCrops.forEach { crop ->
                                DropdownMenuItem(
                                    text = { Text(crop.localizedName(language)) },
                                    onClick = {
                                        selectedCropId = crop.id
                                        cropMenuOpen = false
                                    },
                                    modifier = Modifier.testTag("calendar_crop_item_${crop.id}"),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Admin-published expert calendar additions (if any)
        if (adminCalendarNotes.isNotEmpty()) {
            items(adminCalendarNotes, key = { "admin_cal_${it.id}" }) { note ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = extra.pale),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth().testTag("admin_calendar_note_${note.id}"),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(14.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AdminPanelSettings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = note.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (note.region.isNotBlank()) {
                            Text(
                                text = note.region,
                                style = MaterialTheme.typography.labelSmall,
                                color = extra.muted,
                            )
                        }
                        Text(
                            text = note.details,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        // 12-Month Timeline Cards
        val months = activeCrop?.months.orEmpty()
        items(months, key = { "${activeCrop?.id}_${it.monthNumber}" }) { monthPlan ->
            val palette = paletteForMonth(monthPlan.monthNumber)
            val cardBg = if (extra.isDark) palette.softBgDark else palette.softBgLight
            val isCurrent = monthPlan.monthNumber == currentMonth
            val monthTitle = AgroCalendarCatalog.monthName(monthPlan.monthNumber, language)
            val tasks = monthPlan.localizedTasks(language)

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = if (isCurrent) BorderStroke(1.5.dp, palette.accent) else null,
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("calendar_month_card_${monthPlan.monthNumber}"),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                ) {
                    // Left colored accent bar matching Yonca design
                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .fillMaxHeight()
                            .background(palette.accent),
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CalendarMonth,
                                    contentDescription = monthTitle,
                                    tint = palette.accent,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = monthTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.accent,
                                )
                                if (isCurrent) {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = palette.accent.copy(alpha = 0.16f),
                                        border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.4f)),
                                    ) {
                                        Text(
                                            text = strings.currentMonthBadge,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = palette.accent,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = {
                                    shareMonthPlan(
                                        cropName = activeCrop?.localizedName(language).orEmpty(),
                                        plan = monthPlan,
                                    )
                                },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = strings.seedShareBtn,
                                    tint = palette.accent,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        // Tasks Timeline with dashed vertical connector
                        Column {
                            tasks.forEachIndexed { idx, taskText ->
                                val isLast = idx == tasks.lastIndex
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min),
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.width(18.dp).fillMaxHeight(),
                                    ) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(9.dp)
                                                .clip(CircleShape)
                                                .background(palette.accent)
                                                .border(1.5.dp, cardBg, CircleShape),
                                        )
                                        if (!isLast) {
                                            Canvas(
                                                modifier = Modifier
                                                    .width(2.dp)
                                                    .weight(1f)
                                                    .padding(top = 2.dp),
                                            ) {
                                                drawLine(
                                                    color = palette.accent.copy(alpha = 0.45f),
                                                    start = Offset(size.width / 2f, 0f),
                                                    end = Offset(size.width / 2f, size.height),
                                                    strokeWidth = 3f,
                                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = taskText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(bottom = if (isLast) 2.dp else 12.dp),
                                    )
                                }
                            }
                        }

                        if (onAskAiAboutMonth != null) {
                            val cropName = activeCrop?.localizedName(language).orEmpty()
                            Row(
                                horizontalArrangement = Arrangement.End,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                TextButton(
                                    onClick = {
                                        val prompt = when (language) {
                                            AppLanguage.AZ -> "$cropName bitkisi üçün $monthTitle ayında aqrotexniki işlər (gübrə norması, suvarma, xəstəliklərdən mühafizə) barədə ətraflı məsləhət ver."
                                            AppLanguage.EN -> "Give detailed agronomic guidance for $cropName during $monthTitle (fertilizer rates, irrigation, pest/disease control)."
                                            AppLanguage.RU -> "Дай подробные агрономические советы для культуры $cropName на месяц $monthTitle (удобрения, полив, защита растений)."
                                        }
                                        onAskAiAboutMonth(prompt)
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AutoAwesome,
                                        contentDescription = null,
                                        tint = palette.accent,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = strings.agroAskAiMonthBtn,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = palette.accent,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
