package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.i18n.AppLanguage
import com.example.i18n.LocalStrings
import com.example.ui.components.AqroCard
import com.example.ui.components.DangerBannerBox
import com.example.ui.components.ScreenTopBar
import com.example.ui.components.TabPillsRow
import com.example.ui.theme.LocalAqroColors
import com.example.ui.theme.NitrogenGreen
import com.example.ui.theme.PhosphorusAmber
import com.example.ui.theme.PotassiumPlum

@Composable
fun NpkScreen(
    language: AppLanguage,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current

    var areaText by remember { mutableStateOf("1") }
    var unitIndex by remember { mutableIntStateOf(0) } // 0=ha, 1=sot, 2=m2

    var reqNText by remember { mutableStateOf("") }
    var reqPText by remember { mutableStateOf("") }
    var reqKText by remember { mutableStateOf("") }

    var fertNName by remember { mutableStateOf("") }
    var fertNPct by remember { mutableStateOf("46") }
    var fertPName by remember { mutableStateOf("") }
    var fertPPct by remember { mutableStateOf("46") }
    var fertPNPct by remember { mutableStateOf("18") }
    var fertKName by remember { mutableStateOf("") }
    var fertKPct by remember { mutableStateOf("60") }

    var resultText by remember { mutableStateOf<String?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().testTag("npk_screen"),
    ) {
        item {
            ScreenTopBar(title = strings.npkTitle, onBack = onBack)
        }

        item {
            AqroCard {
                Text(strings.npkFieldSizeTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = areaText,
                    onValueChange = { areaText = it; errorText = null },
                    label = { Text(strings.npkFieldSizeTitle) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("npk_area_input"),
                )
                Spacer(modifier = Modifier.height(8.dp))
                TabPillsRow(
                    tabs = listOf(strings.haUnit, strings.sotUnit, strings.m2Unit),
                    selectedIndex = unitIndex,
                    onSelect = { unitIndex = it },
                )
            }
        }

        item {
            AqroCard {
                Text(strings.npkDemandTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = reqNText,
                    onValueChange = { reqNText = it },
                    label = { Text(strings.npkNitrogenLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("npk_req_n"),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = reqPText,
                    onValueChange = { reqPText = it },
                    label = { Text(strings.npkPhosphorusLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("npk_req_p"),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = reqKText,
                    onValueChange = { reqKText = it },
                    label = { Text(strings.npkPotassiumLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("npk_req_k"),
                )
            }
        }

        item {
            AqroCard {
                Text(strings.npkFertilizersTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))

                // N Fertilizer
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = NitrogenGreen.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, NitrogenGreen.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(10.dp),
                    ) {
                        OutlinedTextField(
                            value = fertNName,
                            onValueChange = { fertNName = it },
                            label = { Text(strings.npkFertNName) },
                            placeholder = { Text(strings.npkFertNNameHint) },
                            singleLine = true,
                            modifier = Modifier.weight(1.4f),
                        )
                        OutlinedTextField(
                            value = fertNPct,
                            onValueChange = { fertNPct = it },
                            label = { Text(strings.npkFertNPct) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // P Fertilizer
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = PhosphorusAmber.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, PhosphorusAmber.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(10.dp),
                    ) {
                        OutlinedTextField(
                            value = fertPName,
                            onValueChange = { fertPName = it },
                            label = { Text(strings.npkFertPName) },
                            placeholder = { Text(strings.npkFertPNameHint) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            OutlinedTextField(
                                value = fertPPct,
                                onValueChange = { fertPPct = it },
                                label = { Text(strings.npkFertPPct) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = fertPNPct,
                                onValueChange = { fertPNPct = it },
                                label = { Text(strings.npkFertPNPct) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // K Fertilizer
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = PotassiumPlum.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, PotassiumPlum.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(10.dp),
                    ) {
                        OutlinedTextField(
                            value = fertKName,
                            onValueChange = { fertKName = it },
                            label = { Text(strings.npkFertKName) },
                            placeholder = { Text(strings.npkFertKNameHint) },
                            singleLine = true,
                            modifier = Modifier.weight(1.4f),
                        )
                        OutlinedTextField(
                            value = fertKPct,
                            onValueChange = { fertKPct = it },
                            label = { Text(strings.npkFertKPct) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            val rawArea = areaText.replace(",", ".").toDoubleOrNull()
                            if (rawArea == null || rawArea <= 0) {
                                errorText = strings.invalidInput
                                return@Button
                            }
                            errorText = null
                            val areaHa = when (unitIndex) {
                                1 -> rawArea / 100.0
                                2 -> rawArea / 10000.0
                                else -> rawArea
                            }
                            val reqN = reqNText.replace(",", ".").toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                            val reqP = reqPText.replace(",", ".").toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                            val reqK = reqKText.replace(",", ".").toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0

                            val pPct = fertPPct.replace(",", ".").toDoubleOrNull() ?: 0.0
                            val pNPct = fertPNPct.replace(",", ".").toDoubleOrNull() ?: 0.0
                            val nPct = fertNPct.replace(",", ".").toDoubleOrNull() ?: 0.0
                            val kPct = fertKPct.replace(",", ".").toDoubleOrNull() ?: 0.0

                            val lines = mutableListOf(
                                String.format(language.locale, strings.npkAreaLine, areaHa)
                            )

                            var nSuppliedByP = 0.0
                            if (reqP > 0 && pPct > 0) {
                                val perHa = reqP / (pPct / 100.0)
                                nSuppliedByP = perHa * (pNPct / 100.0)
                                val name = fertPName.ifBlank { strings.npkDefaultPName }
                                lines.add(String.format(language.locale, strings.npkPhosphorusLine, name, perHa, areaHa, perHa * areaHa))
                            }
                            val remainingN = (reqN - nSuppliedByP).coerceAtLeast(0.0)
                            if (remainingN > 0 && nPct > 0) {
                                val perHa = remainingN / (nPct / 100.0)
                                val name = fertNName.ifBlank { strings.npkDefaultNName }
                                lines.add(String.format(language.locale, strings.npkNitrogenLine, name, perHa, areaHa, perHa * areaHa))
                            }
                            if (reqK > 0 && kPct > 0) {
                                val perHa = reqK / (kPct / 100.0)
                                val name = fertKName.ifBlank { strings.npkDefaultKName }
                                lines.add(String.format(language.locale, strings.npkPotassiumLine, name, perHa, areaHa, perHa * areaHa))
                            }
                            if (lines.size == 1) {
                                lines.add(strings.npkNoFertApplied)
                            }
                            resultText = lines.joinToString("\n\n")
                        },
                        modifier = Modifier.weight(1.4f).testTag("npk_calc_button"),
                    ) {
                        Text(strings.btnCalculateNpk)
                    }
                    OutlinedButton(
                        onClick = {
                            areaText = "1"
                            reqNText = ""
                            reqPText = ""
                            reqKText = ""
                            resultText = null
                            errorText = null
                        },
                        modifier = Modifier.weight(0.8f).testTag("npk_reset_button"),
                    ) {
                        Text(strings.reset)
                    }
                }
            }
        }

        if (errorText != null) {
            item { DangerBannerBox(text = errorText!!) }
        }

        item {
            AqroCard(containerColor = extra.pale) {
                Text(
                    text = resultText ?: strings.npkEmptyResult,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (resultText != null) FontWeight.Bold else FontWeight.Normal,
                    color = if (resultText != null) MaterialTheme.colorScheme.onSurface else extra.muted,
                    modifier = Modifier.testTag("npk_result_text"),
                )
            }
        }
    }
}
