package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ADMIN_EMAIL
import com.example.data.model.AdminContentItem
import com.example.data.model.isAdminEmail
import com.example.i18n.LocalStrings
import com.example.ui.components.AqroCard
import com.example.ui.components.DangerBannerBox
import com.example.ui.components.EmptyStateMessage
import com.example.ui.components.ScreenTopBar
import com.example.ui.theme.DangerRed
import com.example.ui.theme.LocalAqroColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminScreen(
    userEmail: String?,
    entries: List<AdminContentItem>,
    onPublishEntry: suspend (category: String, region: String, title: String, details: String, contactOrUrl: String) -> Unit,
    onDeleteEntry: suspend (String) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current
    val scope = rememberCoroutineScope()

    val isAuthorizedAdmin = isAdminEmail(userEmail)

    var selectedCategory by remember { mutableStateOf("notice") }
    var regionText by remember { mutableStateOf("") }
    var titleText by remember { mutableStateOf("") }
    var detailsText by remember { mutableStateOf("") }
    var contactText by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val categories = listOf(
        "notice" to strings.adminCatNotice,
        "seed" to strings.adminCatSeed,
        "station" to strings.adminCatStation,
        "calendar" to strings.adminCatCalendar,
    )

    fun categoryLabel(code: String): String =
        categories.firstOrNull { it.first == code }?.second ?: code

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("admin_screen"),
    ) {
        item {
            ScreenTopBar(title = strings.adminTitle, onBack = onBack)
        }

        if (!isAuthorizedAdmin) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = extra.dangerContainer),
                    modifier = Modifier.fillMaxWidth().testTag("admin_restricted_card"),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = DangerRed,
                            modifier = Modifier.size(28.dp),
                        )
                        Text(
                            text = "${strings.adminTitle}: $ADMIN_EMAIL",
                            style = MaterialTheme.typography.bodyMedium,
                            color = extra.onDangerContainer,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            return@LazyColumn
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = extra.pale),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(14.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.AdminPanelSettings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userEmail ?: ADMIN_EMAIL,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = strings.adminSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        item {
            AqroCard {
                Text(
                    text = strings.adminCategoryLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    categories.forEach { (id, label) ->
                        FilterChip(
                            selected = selectedCategory == id,
                            onClick = { selectedCategory = id },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            modifier = Modifier.testTag("admin_cat_$id"),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = regionText,
                    onValueChange = { regionText = it; statusMessage = null },
                    label = { Text(strings.adminRegionCropLabel) },
                    placeholder = { Text(strings.adminRegionCropHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("admin_region_input"),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it; statusMessage = null },
                    label = { Text(strings.adminEntryTitleLabel) },
                    placeholder = { Text(strings.adminEntryTitleHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("admin_title_input"),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = detailsText,
                    onValueChange = { detailsText = it; statusMessage = null },
                    label = { Text(strings.adminDetailsLabel) },
                    placeholder = { Text(strings.adminDetailsHint) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth().testTag("admin_details_input"),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = contactText,
                    onValueChange = { contactText = it; statusMessage = null },
                    label = { Text(strings.adminContactLabel) },
                    placeholder = { Text(strings.adminContactHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("admin_contact_input"),
                )

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    enabled = !isSaving,
                    onClick = {
                        val t = titleText.trim()
                        val d = detailsText.trim()
                        if (t.isBlank() || d.isBlank()) {
                            statusMessage = strings.fillRequiredFields
                        } else {
                            scope.launch {
                                isSaving = true
                                val res = runCatching {
                                    onPublishEntry(
                                        selectedCategory,
                                        regionText.trim(),
                                        t,
                                        d,
                                        contactText.trim(),
                                    )
                                }
                                isSaving = false
                                if (res.isSuccess) {
                                    titleText = ""
                                    detailsText = ""
                                    regionText = ""
                                    contactText = ""
                                    statusMessage = strings.adminSavedMsg
                                } else {
                                    statusMessage = res.exceptionOrNull()?.localizedMessage ?: strings.invalidInput
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("admin_publish_btn"),
                ) {
                    Icon(Icons.Filled.Publish, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.adminPublishBtn, fontWeight = FontWeight.Bold)
                }

                statusMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(10.dp))
                    if (msg == strings.adminSavedMsg) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = extra.pale,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(12.dp),
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(msg, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    } else {
                        DangerBannerBox(text = msg)
                    }
                }
            }
        }

        item {
            Text(
                text = strings.adminPublishedListTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (entries.isEmpty()) {
            item {
                AqroCard {
                    EmptyStateMessage(
                        icon = Icons.Filled.AdminPanelSettings,
                        message = strings.adminEmptyList,
                    )
                }
            }
        } else {
            items(entries, key = { it.id }) { item ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, extra.line),
                    modifier = Modifier.fillMaxWidth().testTag("admin_entry_card_${item.id}"),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(14.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = extra.pale,
                            ) {
                                Text(
                                    text = categoryLabel(item.category),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        runCatching { onDeleteEntry(item.id) }
                                    }
                                },
                                modifier = Modifier.size(36.dp).testTag("admin_delete_btn_${item.id}"),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = strings.delete,
                                    tint = DangerRed,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        if (item.region.isNotBlank()) {
                            Text(
                                text = item.region,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            text = item.details,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (item.contactOrUrl.isNotBlank()) {
                            Text(
                                text = item.contactOrUrl,
                                style = MaterialTheme.typography.labelSmall,
                                color = extra.muted,
                            )
                        }
                    }
                }
            }
        }
    }
}
