package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.data.model.CustomLink
import com.example.data.model.FarmField
import com.example.data.model.FarmerProfile
import com.example.data.model.FinanceEntry
import com.example.data.model.HarvestEntry
import com.example.i18n.AppLanguage
import com.example.i18n.LocalStrings
import com.example.ui.auth.signOutUser
import com.example.ui.components.AqroCard
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.DangerBannerBox
import com.example.ui.components.EmptyStateMessage
import com.example.ui.components.KTN_TELEGRAM_BOT_URL
import com.example.ui.components.ScreenTopBar
import com.example.ui.components.TabPillsRow
import com.example.ui.components.formatNumber
import com.example.ui.components.openExternalUrl
import com.example.ui.components.todayIsoDateString
import com.example.ui.theme.DangerRed
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.LocalAqroColors
import com.example.ui.theme.ThemePreference
import kotlinx.coroutines.launch

@Composable
fun FinanceScreen(
    language: AppLanguage,
    fields: List<FarmField>,
    entries: List<FinanceEntry>,
    onAddFinance: suspend (String, String, String, Double, String) -> Unit,
    onDeleteFinance: suspend (String) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var typeIndex by remember { mutableIntStateOf(0) } // 0=expense, 1=income
    var date by remember { mutableStateOf(todayIsoDateString()) }
    var fieldName by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<FinanceEntry?>(null) }

    var filterTypeIndex by remember { mutableIntStateOf(0) } // 0=all, 1=income, 2=expense
    var selectedMonth by remember { mutableStateOf("") } // "" = all periods

    val availableMonths = remember(entries) {
        entries.mapNotNull { e ->
            val trimmed = e.date.trim()
            if (trimmed.length >= 7 && trimmed[4] == '-') trimmed.substring(0, 7) else null
        }.distinct().sortedDescending()
    }

    val periodEntries = remember(entries, selectedMonth) {
        if (selectedMonth.isBlank()) entries
        else entries.filter { it.date.trim().startsWith(selectedMonth) }
    }

    val filteredEntries = remember(periodEntries, filterTypeIndex) {
        when (filterTypeIndex) {
            1 -> periodEntries.filter { it.type == "income" }
            2 -> periodEntries.filter { it.type != "income" }
            else -> periodEntries
        }
    }

    val totalIncome = periodEntries.filter { it.type == "income" }.sumOf { it.amount }
    val totalExpense = periodEntries.filter { it.type != "income" }.sumOf { it.amount }
    val netBalance = totalIncome - totalExpense

    val categoryBreakdown = remember(filteredEntries, strings) {
        val grouped = filteredEntries.groupBy { entry ->
            val cat = entry.category.ifBlank {
                if (entry.type == "income") strings.financeIncome else strings.financeExpense
            }
            (entry.type == "income") to cat
        }.map { (key, list) ->
            Triple(key.first, key.second, list.sumOf { it.amount })
        }.sortedByDescending { it.third }
        grouped
    }

    val quickCategories = if (typeIndex == 1) strings.financeIncomeCategories else strings.financeExpenseCategories

    fun shareAccountingReport() {
        val periodLabel = if (selectedMonth.isBlank()) strings.financePeriodAll else selectedMonth
        val report = buildString {
            appendLine("${strings.appName} — ${strings.financeTitle} ($periodLabel)")
            appendLine("------------------------------")
            appendLine("${strings.financeIncomeLabel}: +${formatNumber(totalIncome, 2)} ${strings.aznCurrency}")
            appendLine("${strings.financeExpenseLabel}: -${formatNumber(totalExpense, 2)} ${strings.aznCurrency}")
            val sign = if (netBalance >= 0) "+" else ""
            appendLine("${strings.financeNetBalanceLabel}: $sign${formatNumber(netBalance, 2)} ${strings.aznCurrency}")
            if (categoryBreakdown.isNotEmpty()) {
                appendLine()
                appendLine(strings.financeByCategoryTitle)
                categoryBreakdown.forEach { (isInc, cat, sum) ->
                    val prefix = if (isInc) "+" else "-"
                    appendLine("• $cat: $prefix${formatNumber(sum, 2)} ${strings.aznCurrency}")
                }
            }
            if (filteredEntries.isNotEmpty()) {
                appendLine()
                filteredEntries.take(25).forEach { e ->
                    val prefix = if (e.type == "income") "+" else "-"
                    val meta = listOf(e.date, e.category, e.fieldName).filter { it.isNotBlank() }.joinToString(" | ")
                    appendLine("$prefix${formatNumber(e.amount, 2)} ${strings.aznCurrency} — $meta")
                }
            }
        }
        runCatching {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "${strings.appName} — ${strings.financeTitle}")
                putExtra(Intent.EXTRA_TEXT, report)
            }
            context.startActivity(Intent.createChooser(sendIntent, strings.financeShareReport))
        }
    }

    ConfirmDeleteDialog(
        visible = pendingDelete != null,
        itemLabel = pendingDelete?.category.orEmpty(),
        onConfirm = {
            val target = pendingDelete
            pendingDelete = null
            if (target != null && target.id.isNotBlank()) {
                scope.launch { onDeleteFinance(target.id) }
            }
        },
        onDismiss = { pendingDelete = null },
    )

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().testTag("finance_screen"),
    ) {
        item {
            ScreenTopBar(title = strings.financeTitle, onBack = onBack)
        }

        // Accounting Summary KPI Card + Share Report
        item {
            AqroCard(containerColor = extra.pale) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = String.format(language.locale, strings.financeRecordCountFormat, periodEntries.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = extra.muted,
                    )
                    if (periodEntries.isNotEmpty()) {
                        OutlinedButton(
                            onClick = ::shareAccountingReport,
                            modifier = Modifier.testTag("finance_share_report_btn"),
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(strings.financeShareReport, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AccountingKpiCell(
                        label = strings.financeIncomeLabel,
                        value = "+${formatNumber(totalIncome, 2)}",
                        currency = strings.aznCurrency,
                        accent = EmeraldPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    AccountingKpiCell(
                        label = strings.financeExpenseLabel,
                        value = "-${formatNumber(totalExpense, 2)}",
                        currency = strings.aznCurrency,
                        accent = DangerRed,
                        modifier = Modifier.weight(1f),
                    )
                    AccountingKpiCell(
                        label = strings.financeNetBalanceLabel,
                        value = "${if (netBalance >= 0) "+" else ""}${formatNumber(netBalance, 2)}",
                        currency = strings.aznCurrency,
                        accent = if (netBalance >= 0) EmeraldPrimary else DangerRed,
                        modifier = Modifier.weight(1f),
                    )
                }

                if (availableMonths.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        val allSelected = selectedMonth.isBlank()
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (allSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, if (allSelected) MaterialTheme.colorScheme.primary else extra.line),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { selectedMonth = "" },
                        ) {
                            Text(
                                text = strings.financePeriodAll,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (allSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                        availableMonths.forEach { m ->
                            val isSel = selectedMonth == m
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, if (isSel) MaterialTheme.colorScheme.primary else extra.line),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable { selectedMonth = m },
                            ) {
                                Text(
                                    text = m,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Add Accounting Record Form
        item {
            AqroCard {
                Text(strings.financeTypeLabel, style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))
                TabPillsRow(
                    tabs = listOf(strings.financeExpense, strings.financeIncome),
                    selectedIndex = typeIndex,
                    onSelect = { typeIndex = it },
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text(strings.financeDateLabel) },
                    placeholder = { Text("YYYY-MM-DD") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (fields.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        fields.forEach { f ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = extra.pale,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable { fieldName = f.name },
                            ) {
                                Text(f.name, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                OutlinedTextField(
                    value = fieldName,
                    onValueChange = { fieldName = it },
                    label = { Text(strings.financeFieldLabel) },
                    placeholder = { Text(strings.financeFieldHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    quickCategories.forEach { quickCat ->
                        val activeCat = category.equals(quickCat, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (activeCat) MaterialTheme.colorScheme.primary else extra.pale,
                            border = BorderStroke(1.dp, if (activeCat) MaterialTheme.colorScheme.primary else extra.line),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { category = quickCat },
                        ) {
                            Text(
                                text = quickCat,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (activeCat) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it; formError = null },
                        label = { Text(strings.financeAmountLabel) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("finance_amount_input"),
                    )
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text(strings.financeCategoryLabel) },
                        placeholder = { Text(strings.financeCategoryHint) },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f).testTag("finance_category_input"),
                    )
                }
                if (formError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    DangerBannerBox(text = formError!!)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        val amt = amountText.replace(",", ".").toDoubleOrNull()
                        if (amt == null || amt <= 0) {
                            formError = strings.invalidInput
                        } else {
                            val type = if (typeIndex == 1) "income" else "expense"
                            val finalCategory = category.ifBlank {
                                if (type == "income") strings.financeIncome else strings.financeExpense
                            }
                            scope.launch {
                                onAddFinance(type, date, fieldName, amt, finalCategory)
                                amountText = ""
                                category = ""
                                formError = null
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("add_finance_btn"),
                ) {
                    Text(strings.btnAddFinance)
                }
            }
        }

        // Category Breakdown Card
        if (categoryBreakdown.isNotEmpty()) {
            item {
                val maxCatAmount = categoryBreakdown.maxOf { it.third }.coerceAtLeast(1.0)
                AqroCard {
                    Text(
                        text = strings.financeByCategoryTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        categoryBreakdown.forEach { (isInc, catName, sum) ->
                            val barColor = if (isInc) EmeraldPrimary else DangerRed
                            val prefix = if (isInc) "+" else "-"
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = catName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "$prefix${formatNumber(sum, 2)} ${strings.aznCurrency}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = barColor,
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { (sum / maxCatAmount).toFloat().coerceIn(0.05f, 1f) },
                                    color = barColor,
                                    trackColor = barColor.copy(alpha = 0.14f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Type Filter Pills
        item {
            TabPillsRow(
                tabs = listOf(strings.financeAllTab, strings.financeIncome, strings.financeExpense),
                selectedIndex = filterTypeIndex,
                onSelect = { filterTypeIndex = it },
            )
        }

        if (filteredEntries.isEmpty()) {
            item {
                AqroCard {
                    EmptyStateMessage(icon = Icons.Filled.AccountBalanceWallet, message = strings.financeEmpty)
                }
            }
        } else {
            items(filteredEntries, key = { it.id }) { item ->
                val isIncome = item.type == "income"
                AqroCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${if (isIncome) "+" else "-"}${formatNumber(item.amount, 2)} ${strings.aznCurrency}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isIncome) EmeraldPrimary else DangerRed,
                            )
                            Text(
                                text = listOf(item.category, item.fieldName, item.date).filter { it.isNotBlank() }.joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall,
                                color = extra.muted,
                            )
                        }
                        IconButton(onClick = { pendingDelete = item }) {
                            Icon(Icons.Filled.Delete, contentDescription = strings.delete, tint = DangerRed)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountingKpiCell(
    label: String,
    value: String,
    currency: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val extra = LocalAqroColors.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = extra.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = currency,
                style = MaterialTheme.typography.labelSmall,
                color = accent.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
fun HarvestScreen(
    language: AppLanguage,
    fields: List<FarmField>,
    entries: List<HarvestEntry>,
    onAddHarvest: suspend (String, String, Double, Double) -> Unit,
    onDeleteHarvest: suspend (String) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current
    val scope = rememberCoroutineScope()

    var date by remember { mutableStateOf(todayIsoDateString()) }
    var crop by remember { mutableStateOf("") }
    var qtyText by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<HarvestEntry?>(null) }

    val totalKg = entries.sumOf { it.quantityKg }
    val totalVal = entries.sumOf { it.quantityKg * it.pricePerKg }

    ConfirmDeleteDialog(
        visible = pendingDelete != null,
        itemLabel = pendingDelete?.crop.orEmpty(),
        onConfirm = {
            val target = pendingDelete
            pendingDelete = null
            if (target != null && target.id.isNotBlank()) {
                scope.launch { onDeleteHarvest(target.id) }
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
            ScreenTopBar(title = strings.harvestTitle, onBack = onBack)
        }

        item {
            AqroCard(containerColor = extra.pale) {
                Text(
                    text = String.format(language.locale, strings.harvestSummaryFormat, totalKg, totalVal),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        item {
            AqroCard {
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text(strings.harvestDateLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (fields.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        fields.forEach { f ->
                            val label = "${f.crop} — ${f.name}"
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = extra.pale,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable { crop = label },
                            ) {
                                Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                OutlinedTextField(
                    value = crop,
                    onValueChange = { crop = it; formError = null },
                    label = { Text(strings.harvestCropLabel) },
                    placeholder = { Text(strings.harvestCropHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("harvest_crop_input"),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it; formError = null },
                        label = { Text(strings.harvestQtyLabel) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("harvest_qty_input"),
                    )
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it },
                        label = { Text(strings.harvestPriceLabel) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (formError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    DangerBannerBox(text = formError!!)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        val qty = qtyText.replace(",", ".").toDoubleOrNull()
                        val price = priceText.replace(",", ".").toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                        if (crop.isBlank() || qty == null || qty <= 0) {
                            formError = strings.fillRequiredFields
                        } else {
                            scope.launch {
                                onAddHarvest(date, crop, qty, price)
                                qtyText = ""
                                priceText = ""
                                formError = null
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("add_harvest_btn"),
                ) {
                    Text(strings.btnAddHarvest)
                }
            }
        }

        if (entries.isEmpty()) {
            item {
                AqroCard {
                    EmptyStateMessage(icon = Icons.Filled.Spa, message = strings.harvestEmpty)
                }
            }
        } else {
            items(entries, key = { it.id }) { item ->
                val valueAzn = item.quantityKg * item.pricePerKg
                AqroCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.crop, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                text = "${item.date} • ${formatNumber(item.quantityKg, 1)} ${strings.kgUnit} • ${formatNumber(valueAzn, 2)} ${strings.aznCurrency}",
                                style = MaterialTheme.typography.bodySmall,
                                color = extra.muted,
                            )
                        }
                        IconButton(onClick = { pendingDelete = item }) {
                            Icon(Icons.Filled.Delete, contentDescription = strings.delete, tint = DangerRed)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LinksScreen(
    customLinks: List<CustomLink>,
    onAddCustomLink: suspend (String, String) -> Unit,
    onDeleteCustomLink: suspend (String) -> Unit,
    onBack: () -> Unit,
    isLocalOnly: Boolean = false,
) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var linkName by remember { mutableStateOf("") }
    var linkUrl by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<CustomLink?>(null) }

    val openUrl: (String) -> Unit = { url -> openExternalUrl(context, url) }

    ConfirmDeleteDialog(
        visible = pendingDelete != null,
        itemLabel = pendingDelete?.name.orEmpty(),
        onConfirm = {
            val target = pendingDelete
            pendingDelete = null
            if (target != null && target.id.isNotBlank()) {
                scope.launch { onDeleteCustomLink(target.id) }
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
            ScreenTopBar(title = strings.linksTitle, onBack = onBack)
        }

        item {
            AqroCard(containerColor = extra.pale) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.eagroTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(strings.eagroSub, style = MaterialTheme.typography.bodySmall, color = extra.muted)
                    }
                    Button(onClick = { openUrl("https://eagro.az/") }) {
                        Text(strings.eagroBtn)
                        Spacer(modifier = Modifier.size(4.dp))
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        item {
            val telegramBlue = Color(0xFF0088CC)
            AqroCard(borderColor = telegramBlue.copy(alpha = 0.4f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(strings.telegramBotTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(strings.telegramBotSub, style = MaterialTheme.typography.bodySmall, color = extra.muted)
                    }
                    Button(
                        onClick = { openUrl(KTN_TELEGRAM_BOT_URL) },
                        colors = ButtonDefaults.buttonColors(containerColor = telegramBlue),
                        modifier = Modifier.testTag("links_telegram_bot_btn"),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(strings.telegramBotBtn)
                    }
                }
            }
        }

        item {
            Text(strings.officialLinksTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        items(strings.officialLinks) { link ->
            AqroCard(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.large)
                    .clickable { openUrl(link.url) },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(link.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(link.description, style = MaterialTheme.typography.bodySmall, color = extra.muted)
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = strings.open,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        if (!isLocalOnly) {
            item {
                AqroCard {
                    Text(strings.addCustomLinkTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(strings.addCustomLinkSub, style = MaterialTheme.typography.bodySmall, color = extra.muted)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = linkName,
                        onValueChange = { linkName = it; formError = null },
                        label = { Text(strings.customLinkNameLabel) },
                        placeholder = { Text(strings.customLinkNameHint) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = linkUrl,
                        onValueChange = { linkUrl = it; formError = null },
                        label = { Text(strings.customLinkUrlLabel) },
                        placeholder = { Text(strings.customLinkUrlHint) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (formError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        DangerBannerBox(text = formError!!)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (linkName.isBlank() || linkUrl.isBlank()) {
                                formError = strings.fillRequiredFields
                            } else {
                                scope.launch {
                                    onAddCustomLink(linkName, linkUrl)
                                    linkName = ""
                                    linkUrl = ""
                                    formError = null
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("save_custom_link_btn"),
                    ) {
                        Text(strings.btnSaveLink)
                    }
                }
            }

            item {
                Text(strings.myCustomLinksTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            if (customLinks.isEmpty()) {
                item {
                    AqroCard {
                        EmptyStateMessage(icon = Icons.Filled.Link, message = strings.customLinksEmpty)
                    }
                }
            } else {
                items(customLinks, key = { it.id }) { link ->
                    AqroCard(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.large)
                            .clickable { openUrl(link.url) },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(link.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(link.url, style = MaterialTheme.typography.bodySmall, color = extra.muted)
                            }
                            IconButton(onClick = { pendingDelete = link }) {
                                Icon(Icons.Filled.Delete, contentDescription = strings.delete, tint = DangerRed)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(
    language: AppLanguage,
    themePreference: ThemePreference,
    profile: FarmerProfile?,
    userEmail: String?,
    onSelectLanguage: (AppLanguage) -> Unit,
    onSelectTheme: (ThemePreference) -> Unit,
    onSaveProfile: suspend (String, String, String) -> Unit,
    onBack: () -> Unit,
    isLocalOnly: Boolean = false,
    onOpenFirebaseSetup: (() -> Unit)? = null,
) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var fullName by remember(profile?.fullName) { mutableStateOf(profile?.fullName.orEmpty()) }
    var phone by remember(profile?.phone) { mutableStateOf(profile?.phone.orEmpty()) }
    var region by remember(profile?.place) { mutableStateOf(profile?.place.orEmpty()) }
    var savedMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(profile) {
        if (profile != null) {
            if (fullName.isBlank()) fullName = profile.fullName
            if (phone.isBlank()) phone = profile.phone
            if (region.isBlank()) region = profile.place
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            ScreenTopBar(title = strings.profileTitle, onBack = onBack)
        }

        if (!isLocalOnly) {
            // Personal Profile Form
            item {
            AqroCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(strings.profileInfoTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (!userEmail.isNullOrBlank()) {
                            Text(userEmail, style = MaterialTheme.typography.labelSmall, color = extra.muted)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(strings.profileInfoSub, style = MaterialTheme.typography.bodySmall, color = extra.muted)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it; savedMsg = null },
                    label = { Text(strings.fullNameLabel) },
                    placeholder = { Text(strings.fullNameHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("profile_fullname_input"),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it; savedMsg = null },
                    label = { Text(strings.phoneLabel) },
                    placeholder = { Text(strings.phoneHint) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = region,
                    onValueChange = { region = it; savedMsg = null },
                    label = { Text(strings.regionLabel) },
                    placeholder = { Text(strings.regionHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (savedMsg != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(savedMsg!!, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                onSaveProfile(fullName, phone, region)
                                val firstName = fullName.trim().split(" ").firstOrNull().orEmpty()
                                savedMsg = if (firstName.isNotBlank()) {
                                    String.format(language.locale, strings.profileSavedNamed, firstName)
                                } else {
                                    strings.profileSavedGeneric
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("save_profile_btn"),
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(strings.btnSaveProfile)
                    }
                }
            }
        }

        // App Settings: Language & Theme
        item {
            AqroCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(strings.settingsTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(strings.languageLabel, style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))
                TabPillsRow(
                    tabs = AppLanguage.entries.map { it.nativeLabel },
                    selectedIndex = AppLanguage.entries.indexOf(language),
                    onSelect = { idx -> onSelectLanguage(AppLanguage.entries[idx]) },
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text(strings.themeModeLabel, style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))
                TabPillsRow(
                    tabs = listOf(strings.themeLight, strings.themeDark, strings.themeSystem),
                    selectedIndex = ThemePreference.entries.indexOf(themePreference),
                    onSelect = { idx -> onSelectTheme(ThemePreference.entries[idx]) },
                )
            }
        }

        // KTN 1652 Hotline Card
        item {
            AqroCard(containerColor = extra.pale) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.hotlineTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(strings.hotlineSub, style = MaterialTheme.typography.bodySmall, color = extra.muted)
                    }
                    Button(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:1652")))
                            }
                        },
                        modifier = Modifier.testTag("hotline_1652_btn"),
                    ) {
                        Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(strings.hotlineBtn)
                    }
                }
            }
        }

        // KTN ASI Telegram Bot Card
        item {
            val telegramBlue = Color(0xFF0088CC)
            AqroCard(borderColor = telegramBlue.copy(alpha = 0.35f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(strings.telegramBotTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(strings.telegramBotSub, style = MaterialTheme.typography.bodySmall, color = extra.muted)
                    }
                    Button(
                        onClick = { openExternalUrl(context, KTN_TELEGRAM_BOT_URL) },
                        colors = ButtonDefaults.buttonColors(containerColor = telegramBlue),
                        modifier = Modifier.testTag("profile_telegram_bot_btn"),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(strings.telegramBotBtn)
                    }
                }
            }
        }

        // Creator Attribution Card (Vaqif Qurbanov) & About App
        item {
            AqroCard(
                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Verified, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(
                            text = "${strings.creatorLabel}: ${strings.creatorName}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("creator_attribution_text"),
                        )
                        Text(strings.creatorSubtitle, style = MaterialTheme.typography.labelSmall, color = extra.muted)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(strings.aboutAppTitle, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(strings.aboutAppDesc, style = MaterialTheme.typography.bodySmall, color = extra.muted)
            }
        }

        // Firebase Setup Guide (Local Mode) OR Google Sign-Out Button (Cloud Mode)
        if (isLocalOnly) {
            if (onOpenFirebaseSetup != null) {
                item {
                    OutlinedButton(
                        onClick = onOpenFirebaseSetup,
                        modifier = Modifier.fillMaxWidth().testTag("open_firebase_setup_btn"),
                    ) {
                        Text(strings.localModeSetupAction, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            item {
                OutlinedButton(
                    onClick = {
                        signOutUser(credentialManager = credentialManager, scope = scope)
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                    border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().testTag("sign_out_button"),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(strings.signOut, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
