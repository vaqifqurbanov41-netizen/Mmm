package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.i18n.AppLanguage
import com.example.i18n.LocalStrings
import com.example.ui.components.AqroCard
import com.example.ui.components.ScreenTopBar
import com.example.ui.components.formatNumber
import com.example.ui.components.openExternalUrl
import com.example.ui.theme.LocalAqroColors
import java.util.Locale

private data class InsuranceRegionDef(
    val id: String,
    val nameAz: String,
    val nameEn: String,
    val nameRu: String,
    val riskFactor: Double,
) {
    fun label(lang: AppLanguage): String = when (lang) {
        AppLanguage.AZ -> nameAz
        AppLanguage.EN -> nameEn
        AppLanguage.RU -> nameRu
    }
}

private data class InsuranceCropDef(
    val id: String,
    val nameAz: String,
    val nameEn: String,
    val nameRu: String,
    val defaultYieldTonHa: Double,
    val defaultPriceAznTon: Double,
    val basicRatePct: Double,
    val extendedRatePct: Double,
) {
    fun label(lang: AppLanguage): String = when (lang) {
        AppLanguage.AZ -> nameAz
        AppLanguage.EN -> nameEn
        AppLanguage.RU -> nameRu
    }
}

private val insuranceRegions = listOf(
    InsuranceRegionDef("aran", "Aran (Ağcabədi, Bərdə, Sabirabad)", "Aran (Aghjabadi, Barda)", "Аран (Агджабеди, Барда)", 1.00),
    InsuranceRegionDef("ganja", "Gəncə-Daşkəsən", "Ganja-Dashkasan", "Гянджа-Дашкесан", 1.08),
    InsuranceRegionDef("quba", "Quba-Xaçmaz", "Guba-Khachmaz", "Губа-Хачмаз", 1.05),
    InsuranceRegionDef("sheki", "Şəki-Zaqatala", "Shaki-Zagatala", "Шеки-Загатала", 1.12),
    InsuranceRegionDef("lankaran", "Lənkəran-Astara", "Lankaran-Astara", "Ленкорань-Астара", 1.06),
    InsuranceRegionDef("karabakh", "Qarabağ və Şərqi Zəngəzur", "Karabakh & E. Zangazur", "Карабах и В. Зангезур", 0.96),
)

private val insuranceCrops = listOf(
    InsuranceCropDef("wheat", "Buğda", "Wheat", "Пшеница", 3.8, 450.0, 2.8, 4.2),
    InsuranceCropDef("barley", "Arpa", "Barley", "Ячмень", 3.4, 390.0, 2.8, 4.1),
    InsuranceCropDef("corn", "Qarğıdalı", "Corn", "Кукуруза", 6.5, 480.0, 3.1, 4.6),
    InsuranceCropDef("cotton", "Pambıq", "Cotton", "Хлопок", 3.2, 950.0, 3.4, 5.0),
    InsuranceCropDef("potato", "Kartof", "Potato", "Картофель", 22.0, 550.0, 3.6, 5.4),
    InsuranceCropDef("tomato", "Pomidor", "Tomato", "Томат", 30.0, 600.0, 3.9, 5.8),
    InsuranceCropDef("alfalfa", "Yonca", "Alfalfa", "Люцерна", 9.0, 260.0, 2.5, 3.7),
    InsuranceCropDef("grape", "Üzüm", "Grape", "Виноград", 12.0, 850.0, 4.0, 6.0),
    InsuranceCropDef("orchard", "Meyvə bağları", "Fruit Orchards", "Плодовые сады", 14.0, 900.0, 4.2, 6.4),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InsuranceCalcScreen(
    language: AppLanguage,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current
    val context = LocalContext.current

    var selectedRegionId by remember { mutableStateOf(insuranceRegions.first().id) }
    var selectedCropId by remember { mutableStateOf(insuranceCrops.first().id) }

    val selectedRegion = remember(selectedRegionId) {
        insuranceRegions.first { it.id == selectedRegionId }
    }
    val selectedCrop = remember(selectedCropId) {
        insuranceCrops.first { it.id == selectedCropId }
    }

    var areaText by remember { mutableStateOf("5") }
    var yieldText by remember { mutableStateOf(formatNumber(insuranceCrops.first().defaultYieldTonHa, 1)) }
    var priceText by remember { mutableStateOf(formatNumber(insuranceCrops.first().defaultPriceAznTon, 0)) }
    var packageIndex by remember { mutableIntStateOf(0) } // 0 = basic, 1 = extended
    var applyStateSubsidy by remember { mutableStateOf(true) }

    val area = areaText.replace(",", ".").toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val expectedYield = yieldText.replace(",", ".").toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val marketPrice = priceText.replace(",", ".").toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0

    val tariffPct = (if (packageIndex == 0) selectedCrop.basicRatePct else selectedCrop.extendedRatePct) *
        selectedRegion.riskFactor
    val totalInsuredSum = area * expectedYield * marketPrice
    val grossPremium = totalInsuredSum * (tariffPct / 100.0)
    val stateSubsidyAmount = if (applyStateSubsidy) grossPremium * 0.50 else 0.0
    val farmerPays = (grossPremium - stateSubsidyAmount).coerceAtLeast(0.0)
    val perHectareCost = if (area > 0.0) farmerPays / area else 0.0

    fun shareEstimate() {
        val text = buildString {
            appendLine("${strings.appName} — ${strings.insuranceTitle}")
            appendLine("${strings.insuranceRegionLabel}: ${selectedRegion.label(language)}")
            appendLine("${strings.insuranceCropLabel}: ${selectedCrop.label(language)}")
            appendLine("${strings.insuranceAreaLabel}: ${formatNumber(area, 2)} ha")
            appendLine("${strings.insuranceTotalSumLabel}: ${formatNumber(totalInsuredSum, 2)} AZN")
            appendLine("${strings.insuranceGrossPremiumLabel}: ${formatNumber(grossPremium, 2)} AZN")
            appendLine("${strings.insuranceStateSupportLabel}: -${formatNumber(stateSubsidyAmount, 2)} AZN")
            append("${strings.insuranceFarmerPaysLabel}: ${formatNumber(farmerPays, 2)} AZN")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, strings.insuranceTitle)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, strings.insuranceTitle))
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("insurance_calc_screen"),
    ) {
        item {
            ScreenTopBar(title = strings.insuranceTitle, onBack = onBack)
        }

        item {
            AqroCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.VerifiedUser,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = strings.insuranceSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = extra.muted,
                    )
                }
            }
        }

        item {
            AqroCard {
                Text(
                    text = strings.insuranceRegionLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    insuranceRegions.forEach { region ->
                        val selected = region.id == selectedRegionId
                        FilterChip(
                            selected = selected,
                            onClick = { selectedRegionId = region.id },
                            label = { Text(region.label(language)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            modifier = Modifier.testTag("insurance_region_${region.id}"),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = strings.insuranceCropLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    insuranceCrops.forEach { crop ->
                        val selected = crop.id == selectedCropId
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedCropId = crop.id
                                yieldText = formatNumber(crop.defaultYieldTonHa, 1)
                                priceText = formatNumber(crop.defaultPriceAznTon, 0)
                            },
                            label = { Text(crop.label(language)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            modifier = Modifier.testTag("insurance_crop_${crop.id}"),
                        )
                    }
                }
            }
        }

        item {
            AqroCard {
                OutlinedTextField(
                    value = areaText,
                    onValueChange = { areaText = it },
                    label = { Text(strings.insuranceAreaLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("insurance_area_input"),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                ) {
                    OutlinedTextField(
                        value = yieldText,
                        onValueChange = { yieldText = it },
                        label = { Text(strings.insuranceYieldLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).fillMaxHeight().testTag("insurance_yield_input"),
                    )
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it },
                        label = { Text(strings.insurancePriceLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).fillMaxHeight().testTag("insurance_price_input"),
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = strings.insurancePackageLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = packageIndex == 0,
                        onClick = { packageIndex = 0 },
                        label = { Text(strings.insurancePkgBasic) },
                        modifier = Modifier.fillMaxWidth().testTag("insurance_pkg_basic"),
                    )
                    FilterChip(
                        selected = packageIndex == 1,
                        onClick = { packageIndex = 1 },
                        label = { Text(strings.insurancePkgExtended) },
                        modifier = Modifier.fillMaxWidth().testTag("insurance_pkg_extended"),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = strings.insuranceSubsidyToggle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = applyStateSubsidy,
                        onCheckedChange = { applyStateSubsidy = it },
                        modifier = Modifier.testTag("insurance_subsidy_switch"),
                    )
                }
            }
        }

        // Instant Insurance Summary Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = extra.pale),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth().testTag("insurance_result_card"),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    InsuranceStatRow(
                        label = strings.insuranceTotalSumLabel,
                        value = "${formatNumber(totalInsuredSum, 2)} AZN",
                    )
                    InsuranceStatRow(
                        label = "${strings.insuranceGrossPremiumLabel} (${formatNumber(tariffPct, 2)}%)",
                        value = "${formatNumber(grossPremium, 2)} AZN",
                    )
                    InsuranceStatRow(
                        label = strings.insuranceStateSupportLabel,
                        value = "-${formatNumber(stateSubsidyAmount, 2)} AZN",
                        valueColor = MaterialTheme.colorScheme.primary,
                    )
                    HorizontalDivider(color = extra.line)
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = strings.insuranceFarmerPaysLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                        )
                        Text(
                            text = "${formatNumber(farmerPays, 2)} AZN",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            softWrap = false,
                            maxLines = 1,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, extra.line),
                    ) {
                        Text(
                            text = String.format(Locale.US, strings.insurancePerHaFormat, perHectareCost),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                    ) {
                        OutlinedButton(
                            onClick = ::shareEstimate,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .heightIn(min = 48.dp)
                                .testTag("insurance_share_btn"),
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = strings.shareShort,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Button(
                            onClick = { openExternalUrl(context, "https://asf.gov.az/") },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .heightIn(min = 48.dp)
                                .testTag("insurance_official_btn"),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = strings.open,
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

@Composable
private fun InsuranceStatRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    val extra = LocalAqroColors.current
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = extra.muted,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            softWrap = false,
            maxLines = 1,
        )
    }
}
