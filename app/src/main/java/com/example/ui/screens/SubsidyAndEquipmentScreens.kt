package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.EquipmentItem
import com.example.data.model.SubsidyDeclaration
import com.example.i18n.AppLanguage
import com.example.i18n.LocalStrings
import com.example.notifications.EquipmentReminderManager
import com.example.ui.components.AqroCard
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.DangerBannerBox
import com.example.ui.components.EmptyStateMessage
import com.example.ui.components.ScreenTopBar
import com.example.ui.components.formatNumber
import com.example.ui.components.openExternalUrl
import com.example.ui.components.todayIsoDateString
import com.example.ui.theme.DangerRed
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.LocalAqroColors
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.EquipmentStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SubsidyScreen(
    language: AppLanguage,
    declarations: List<SubsidyDeclaration>,
    onAddDeclaration: suspend (String, String) -> Unit,
    onDeleteDeclaration: suspend (String) -> Unit,
    onBack: () -> Unit,
    isLocalOnly: Boolean = false,
) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var cropInput by remember { mutableStateOf("") }
    var areaInput by remember { mutableStateOf("") }
    var rateInput by remember { mutableStateOf("") }
    var calcResult by remember { mutableStateOf<String?>(null) }

    var declDate by remember { mutableStateOf(todayIsoDateString()) }
    var declTitle by remember { mutableStateOf("") }
    var declError by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<SubsidyDeclaration?>(null) }

    ConfirmDeleteDialog(
        visible = pendingDelete != null,
        itemLabel = pendingDelete?.title.orEmpty(),
        onConfirm = {
            val target = pendingDelete
            pendingDelete = null
            if (target != null && target.id.isNotBlank()) {
                scope.launch { onDeleteDeclaration(target.id) }
            }
        },
        onDismiss = { pendingDelete = null },
    )

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            ScreenTopBar(title = strings.subsidyTitle, onBack = onBack)
        }

        item {
            AqroCard(containerColor = extra.pale) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.subsidyPortalTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(strings.subsidyPortalSub, style = MaterialTheme.typography.bodySmall, color = extra.muted)
                    }
                    Button(
                        onClick = {
                            openExternalUrl(context, "https://aqrarsubsidiya.az/")
                        },
                        modifier = Modifier.testTag("open_subsidy_portal_btn"),
                    ) {
                        Text(strings.subsidyPortalBtn)
                        Spacer(modifier = Modifier.size(4.dp))
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        item {
            AqroCard {
                Text(strings.subsidyCalcTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = cropInput,
                    onValueChange = { cropInput = it },
                    label = { Text(strings.subsidyCropLabel) },
                    placeholder = { Text(strings.subsidyCropHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                ) {
                    OutlinedTextField(
                        value = areaInput,
                        onValueChange = { areaInput = it },
                        label = { Text(strings.subsidyAreaLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).fillMaxHeight().testTag("subsidy_area_input"),
                    )
                    OutlinedTextField(
                        value = rateInput,
                        onValueChange = { rateInput = it },
                        label = { Text(strings.subsidyRateLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        placeholder = { Text(strings.subsidyRateHint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).fillMaxHeight().testTag("subsidy_rate_input"),
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        val area = areaInput.replace(",", ".").toDoubleOrNull()
                        val rate = rateInput.replace(",", ".").toDoubleOrNull()
                        calcResult = if (area == null || rate == null || area <= 0 || rate <= 0) {
                            strings.invalidInput
                        } else {
                            String.format(
                                language.locale,
                                strings.subsidyResultFormat,
                                cropInput.ifBlank { strings.subsidyCropLabel },
                                area * rate,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("calc_subsidy_btn"),
                ) {
                    Text(strings.btnCalcSubsidy)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = extra.pale,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = calcResult ?: strings.subsidyEmptyOut,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (calcResult != null) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }

        if (!isLocalOnly) {
            item {
                AqroCard {
                    Text(strings.declTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = declDate,
                        onValueChange = { declDate = it; declError = null },
                        label = { Text(strings.declDateLabel) },
                        placeholder = { Text("YYYY-MM-DD") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = declTitle,
                        onValueChange = { declTitle = it; declError = null },
                        label = { Text(strings.declNameLabel) },
                        placeholder = { Text(strings.declNameHint) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (declError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        DangerBannerBox(text = declError!!)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (declDate.isBlank() || declTitle.isBlank()) {
                                declError = strings.fillRequiredFields
                            } else {
                                scope.launch {
                                    onAddDeclaration(declDate, declTitle)
                                    declTitle = ""
                                    declError = null
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("save_decl_btn"),
                    ) {
                        Text(strings.btnSaveDecl)
                    }
                }
            }

            if (declarations.isEmpty()) {
                item {
                    AqroCard {
                        EmptyStateMessage(icon = Icons.Filled.CalendarMonth, message = strings.declEmpty)
                    }
                }
            } else {
                items(declarations, key = { it.id }) { decl ->
                    AqroCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(decl.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(decl.date, style = MaterialTheme.typography.bodySmall, color = extra.muted)
                            }
                            IconButton(onClick = { pendingDelete = decl }) {
                                Icon(Icons.Filled.Delete, contentDescription = strings.delete, tint = DangerRed)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EquipmentScreen(
    equipment: List<EquipmentItem>,
    computeStatus: (String) -> EquipmentStatus?,
    onSaveEquipment: suspend (EquipmentItem?, String, String, String, String, String) -> Unit,
    onDeleteEquipment: suspend (String) -> Unit,
    onExportJson: () -> String,
    onImportJson: suspend (String) -> Int,
    onBack: () -> Unit,
    reminderDays: Int = 7,
    onSelectReminderDays: (Int) -> Unit = {},
) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var editingItem by remember { mutableStateOf<EquipmentItem?>(null) }
    var name by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var plateCode by remember { mutableStateOf("") }
    var inspectionDate by remember { mutableStateOf("") }
    var insuranceDate by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }
    var backupStatus by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<EquipmentItem?>(null) }
    var hasNotifPermission by remember {
        mutableStateOf(EquipmentReminderManager.hasNotificationPermission(context))
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted || EquipmentReminderManager.hasNotificationPermission(context)
        if (hasNotifPermission) {
            EquipmentReminderManager.sendTestReminderNotification(context, strings)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    try {
                        val json = onExportJson()
                        context.contentResolver.openOutputStream(uri)?.use { os ->
                            os.write(json.toByteArray(Charsets.UTF_8))
                        }
                        true
                    } catch (_: Exception) {
                        false
                    }
                }
                backupStatus = if (ok) strings.backupExportSuccess else strings.backupError
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val resultMsg = try {
                    val jsonText = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use {
                            it.bufferedReader().readText()
                        }.orEmpty()
                    }
                    val count = onImportJson(jsonText)
                    "${strings.backupImportSuccess} ($count)"
                } catch (_: Exception) {
                    strings.backupError
                }
                backupStatus = resultMsg
            }
        }
    }

    ConfirmDeleteDialog(
        visible = pendingDelete != null,
        itemLabel = pendingDelete?.name.orEmpty(),
        onConfirm = {
            val target = pendingDelete
            pendingDelete = null
            if (target != null && target.id.isNotBlank()) {
                scope.launch { onDeleteEquipment(target.id) }
            }
        },
        onDismiss = { pendingDelete = null },
    )

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().testTag("equipment_screen"),
    ) {
        item {
            ScreenTopBar(title = strings.equipmentTitle, onBack = onBack)
        }

        // Inspection & Insurance Sound Reminder Timing Card (1 day / 1 week / 1 month)
        item {
            AqroCard(containerColor = extra.pale) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = strings.eqReminderSettingsTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = strings.eqReminderSettingsSub,
                    style = MaterialTheme.typography.bodySmall,
                    color = extra.muted,
                )
                Spacer(modifier = Modifier.height(8.dp))
                val options = listOf(
                    1 to strings.eqReminder1Day,
                    7 to strings.eqReminder1Week,
                    30 to strings.eqReminder1Month,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    options.forEach { (days, label) ->
                        FilterChip(
                            selected = reminderDays == days,
                            onClick = { onSelectReminderDays(days) },
                            label = { Text(label, fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.testTag("reminder_lead_$days"),
                        )
                    }
                }
                if (!hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = strings.eqNotifPermissionNeeded,
                        style = MaterialTheme.typography.bodySmall,
                        color = WarningAmber,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = {
                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        modifier = Modifier.fillMaxWidth().testTag("enable_sound_notif_btn"),
                    ) {
                        Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(strings.eqEnableSoundNotifBtn)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(
                    onClick = {
                        if (!hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            EquipmentReminderManager.sendTestReminderNotification(context, strings)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("test_sound_notification_btn"),
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(strings.eqTestSoundNotifBtn)
                }
            }
        }

        item {
            AqroCard {
                Text(
                    text = if (editingItem == null) strings.eqFormAddTitle else strings.eqFormEditTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; formError = null },
                    label = { Text(strings.eqNameLabel) },
                    placeholder = { Text(strings.eqNameHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("eq_name_input"),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                ) {
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text(strings.eqModelLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        placeholder = { Text(strings.eqModelHint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    OutlinedTextField(
                        value = plateCode,
                        onValueChange = { plateCode = it },
                        label = { Text(strings.eqCodeLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        placeholder = { Text(strings.eqCodeHint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                ) {
                    OutlinedTextField(
                        value = inspectionDate,
                        onValueChange = { inspectionDate = it },
                        label = { Text(strings.eqInspectionLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        placeholder = { Text("YYYY-MM-DD") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    OutlinedTextField(
                        value = insuranceDate,
                        onValueChange = { insuranceDate = it },
                        label = { Text(strings.eqInsuranceLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        placeholder = { Text("YYYY-MM-DD") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                if (formError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    DangerBannerBox(text = formError!!)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                ) {
                    Button(
                        onClick = {
                            if (name.isBlank()) {
                                formError = strings.fillRequiredFields
                            } else {
                                scope.launch {
                                    onSaveEquipment(editingItem, name, model, plateCode, inspectionDate, insuranceDate)
                                    editingItem = null
                                    name = ""
                                    model = ""
                                    plateCode = ""
                                    inspectionDate = ""
                                    insuranceDate = ""
                                    formError = null
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .heightIn(min = 48.dp)
                            .testTag("save_equipment_btn"),
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(strings.save, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (editingItem != null) {
                        OutlinedButton(
                            onClick = {
                                editingItem = null
                                name = ""
                                model = ""
                                plateCode = ""
                                inspectionDate = ""
                                insuranceDate = ""
                            },
                            modifier = Modifier
                                .fillMaxHeight()
                                .heightIn(min = 48.dp),
                        ) {
                            Text(strings.cancel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        item {
            Text(strings.eqListTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        if (equipment.isEmpty()) {
            item {
                AqroCard {
                    EmptyStateMessage(icon = Icons.Filled.Agriculture, message = strings.eqEmpty)
                }
            }
        } else {
            items(equipment, key = { it.id }) { item ->
                val inspStatus = computeStatus(item.inspectionDate)
                val insStatus = computeStatus(item.insuranceDate)
                AqroCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = listOf(item.name, item.model).filter { it.isNotBlank() }.joinToString(" • "),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            if (item.plateCode.isNotBlank()) {
                                Text(item.plateCode, style = MaterialTheme.typography.bodySmall, color = extra.muted)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                if (item.inspectionDate.isNotBlank()) {
                                    StatusBadge("${strings.eqInspectionLabel}: ${item.inspectionDate}", inspStatus)
                                }
                                if (item.insuranceDate.isNotBlank()) {
                                    StatusBadge("${strings.eqInsuranceLabel}: ${item.insuranceDate}", insStatus)
                                }
                            }
                        }
                        Row {
                            IconButton(
                                onClick = {
                                    editingItem = item
                                    name = item.name
                                    model = item.model
                                    plateCode = item.plateCode
                                    inspectionDate = item.inspectionDate
                                    insuranceDate = item.insuranceDate
                                }
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = strings.edit)
                            }
                            IconButton(onClick = { pendingDelete = item }) {
                                Icon(Icons.Filled.Delete, contentDescription = strings.delete, tint = DangerRed)
                            }
                        }
                    }
                }
            }
        }

        // JSON Backup & Restore Card
        item {
            AqroCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(strings.backupTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                ) {
                    OutlinedButton(
                        onClick = { exportLauncher.launch("aqromeslehet_backup_${todayIsoDateString()}.json") },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .heightIn(min = 48.dp)
                            .testTag("export_backup_btn"),
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(strings.btnExportBackup, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .heightIn(min = 48.dp)
                            .testTag("import_backup_btn"),
                    ) {
                        Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(strings.btnImportBackup, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (backupStatus != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(backupStatus!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(
    label: String,
    status: EquipmentStatus?,
) {
    val strings = LocalStrings.current
    val (bg, fg, tagText) = when (status) {
        EquipmentStatus.OVERDUE -> Triple(DangerRed.copy(alpha = 0.14f), DangerRed, strings.eqStatusOverdue)
        EquipmentStatus.SOON -> Triple(WarningAmber.copy(alpha = 0.16f), WarningAmber, strings.eqStatusSoon)
        EquipmentStatus.VALID -> Triple(EmeraldPrimary.copy(alpha = 0.12f), EmeraldPrimary, strings.eqStatusValid)
        null -> Triple(Color.Gray.copy(alpha = 0.12f), Color.Gray, "")
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.35f)),
    ) {
        Text(
            text = if (tagText.isNotBlank()) "$label ($tagText)" else label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
