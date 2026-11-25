package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AdminContentItem
import com.example.i18n.AppLanguage
import com.example.i18n.LocalStrings
import com.example.ui.components.AqroCard
import com.example.ui.components.ScreenTopBar
import com.example.ui.components.openExternalUrl
import com.example.ui.navigation.NavRoutes
import com.example.ui.theme.LocalAqroColors

private data class StationServiceDef(
    val id: String,
    val titleAz: String,
    val titleEn: String,
    val titleRu: String,
    val summaryAz: String,
    val summaryEn: String,
    val summaryRu: String,
    val guideAz: String,
    val guideEn: String,
    val guideRu: String,
    val mapQueryAz: String,
    val portalUrl: String,
    val appToolRoute: String?,
    val icon: ImageVector,
    val accent: Color,
) {
    fun title(lang: AppLanguage): String = when (lang) {
        AppLanguage.AZ -> titleAz
        AppLanguage.EN -> titleEn
        AppLanguage.RU -> titleRu
    }

    fun summary(lang: AppLanguage): String = when (lang) {
        AppLanguage.AZ -> summaryAz
        AppLanguage.EN -> summaryEn
        AppLanguage.RU -> summaryRu
    }

    fun guide(lang: AppLanguage): String = when (lang) {
        AppLanguage.AZ -> guideAz
        AppLanguage.EN -> guideEn
        AppLanguage.RU -> guideRu
    }
}

private val districtPresets = listOf(
    "Ağcabədi", "Bərdə", "Gəncə", "Şəmkir", "Sabirabad", "Quba", "Lənkəran", "Şəki",
)

private val stationServices = listOf(
    StationServiceDef(
        id = "fertilizer",
        titleAz = "Gübrə satışı məntəqələri",
        titleEn = "Fertilizer Supply Centers",
        titleRu = "Пункты продажи удобрений",
        summaryAz = "Subsidiyalı mineral gübrələr (karbamid, ammonium şorası, ammofos, NPK)",
        summaryEn = "Subsidized mineral fertilizers (urea, ammonium nitrate, ammophos, NPK)",
        summaryRu = "Минеральные удобрения (карбамид, селитра, аммофос, NPK)",
        guideAz = "Gübrə alarkən Fermer Kartı (EKTİS) ilə dövlət güzəştindən istifadə edə bilərsiniz. Səpin və yemləmə normasını tətbiqdəki NPK hesablayıcısı ilə əvvəlcədən hesablayın.",
        guideEn = "Use your EKTIS Farmer Card for subsidized purchases and calculate exact field dosage using the app NPK calculator.",
        guideRu = "Используйте карту фермера EKTIS и заранее рассчитайте норму в калькуляторе NPK.",
        mapQueryAz = "Aqroservis gübrə satışı",
        portalUrl = "https://eagro.az/",
        appToolRoute = NavRoutes.NPK,
        icon = Icons.Filled.Grass,
        accent = Color(0xFF15803D),
    ),
    StationServiceDef(
        id = "pesticide",
        titleAz = "Pestisid və bitki mühafizəsi",
        titleEn = "Pesticide & Crop Protection",
        titleRu = "Пункты пестицидов и СЗР",
        summaryAz = "Herbisid, funqisid və insektisid satışı, rəsmi qeydiyyatlı preparatlar",
        summaryEn = "Registered herbicides, fungicides, and insecticides",
        summaryRu = "Зарегистрированные гербициды, фунгициды и инсектициды",
        guideAz = "Qadağan olunmuş və mənşəyi bilinməyən dərmanlardan istifadə etməyin. İstifadədən əvvəl Pestisid bölməsində preparatın dozasını və gözləmə müddətini yoxlayın.",
        guideEn = "Verify approved dosage and pre-harvest quarantine intervals in the Pesticide module before spraying.",
        guideRu = "Проверьте дозировку и срок ожидания в разделе Пестициды перед обработкой.",
        mapQueryAz = "bitki mühafizə vasitələri pestisid mağazası",
        portalUrl = "https://afsa.gov.az/",
        appToolRoute = NavRoutes.PESTICIDE,
        icon = Icons.Filled.BugReport,
        accent = Color(0xFFD97706),
    ),
    StationServiceDef(
        id = "seed",
        titleAz = "Toxum məntəqələri",
        titleEn = "Certified Seed Centers",
        titleRu = "Семенные центры",
        summaryAz = "Dövlət reyestrində olan R1 / R2 sertifikatlı taxıl, pambıq, yonca və tərəvəz toxumları",
        summaryEn = "State-certified R1/R2 wheat, barley, cotton, alfalfa, and vegetable seeds",
        summaryRu = "Сертифицированные семена R1/R2 зерновых, хлопка, люцерны и овощей",
        guideAz = "Sertifikatlı toxum istifadəsi subsidiyalı şərtlərə uyğundur və məhsuldarlığı 20–30% artırır. Bölgənizə uyğun sortları Toxum bölməsindən seçə bilərsiniz.",
        guideEn = "Certified seeds qualify for higher yield and support programs. Pick regionally adapted varieties in the Seed Advisor.",
        guideRu = "Сертифицированные семена повышают урожайность на 20–30%. Подберите сорт в разделе Семена.",
        mapQueryAz = "sertifikatlı toxum satışı",
        portalUrl = "https://eagro.az/",
        appToolRoute = NavRoutes.SEEDS,
        icon = Icons.Filled.Eco,
        accent = Color(0xFF0F766E),
    ),
    StationServiceDef(
        id = "sapling",
        titleAz = "Tinglər və şitil mərkəzləri",
        titleEn = "Orchard Saplings & Nurseries",
        titleRu = "Саженцы и питомники",
        summaryAz = "Meyvə ağacları (alma, nar, fındıq, xurma, badam) və üzüm tingləri",
        summaryEn = "Grafted fruit tree saplings (apple, pomegranate, hazelnut, persimmon, almond) & vines",
        summaryRu = "Привитые саженцы плодовых деревьев и винограда",
        guideAz = "Bağ salmazdan əvvəl tinglərin fitosanitar sertifikatını və calaqaltı növünü (M9, MM106 və s.) yoxlayın, payız və ya erkən yaz əkinini aqrotəqvimlə planlaşdırın.",
        guideEn = "Check phytosanitary certificates and rootstock type before planting orchards, guided by the Agro Calendar.",
        guideRu = "Проверьте фитосанитарный сертификат и тип подвоя перед закладкой сада.",
        mapQueryAz = "meyvə tingləri tingçilik təsərrüfatı",
        portalUrl = "https://agro.gov.az/",
        appToolRoute = NavRoutes.AGRO_CALENDAR,
        icon = Icons.Filled.Park,
        accent = Color(0xFF2E7D32),
    ),
    StationServiceDef(
        id = "vet",
        titleAz = "Baytarlıq xidməti (Baytar)",
        titleEn = "Veterinary Clinics & Stations",
        titleRu = "Ветеринарные пункты",
        summaryAz = "Heyvanların peyvəndlənməsi, müayinəsi, dərman təminatı və identifikasiya",
        summaryEn = "Livestock vaccination, diagnostics, veterinary medicines & EKTIS tagging",
        summaryRu = "Вакцинация скота, диагностика, ветпрепараты и учёт",
        guideAz = "İribuynuzlu və xırdabuynuzlu heyvanların planlı peyvənd və qan müayinəsi üçün rayon Dövlət Aqrar İnkişaf Mərkəzi (DAİM) və yerli baytarlıq məntəqəsinə müraciət edin.",
        guideEn = "Contact your district State Agrarian Development Center (DAIM) and certified local veterinarians for vaccination schedules.",
        guideRu = "Обратитесь в районный центр DAIM и местный ветеринарный пункт для плановой вакцинации.",
        mapQueryAz = "baytarlıq klinikası baytar",
        portalUrl = "https://afsa.gov.az/",
        appToolRoute = null,
        icon = Icons.Filled.MedicalServices,
        accent = Color(0xFF0284C7),
    ),
    StationServiceDef(
        id = "smm",
        titleAz = "SMM (Süni mayalanma məntəqəsi)",
        titleEn = "Artificial Insemination (SMM)",
        titleRu = "Пункты искусственного осеменения",
        summaryAz = "Yüksək məhsuldar cins maldarlıq və buzov üzrə subsidiya dəstəyi",
        summaryEn = "Pedigree livestock breeding & calf subsidy eligibility",
        summaryRu = "Племенное разведение КРС и оформление субсидий на телят",
        guideAz = "Süni mayalanma yolu ilə alınan hər sağlam buzova görə dövlət subsidiyası nəzərdə tutulur. Məntəqə qeydiyyatı EKTİS üzərindən aparılır.",
        guideEn = "Calves born via registered AI stations qualify for state livestock subsidies via EKTIS.",
        guideRu = "Телята от зарегистрированного ИО участвуют в программе государственных субсидий через EKTIS.",
        mapQueryAz = "süni mayalanma baytarlıq məntəqəsi",
        portalUrl = "https://agro.gov.az/",
        appToolRoute = null,
        icon = Icons.Filled.Biotech,
        accent = Color(0xFF7C3AED),
    ),
    StationServiceDef(
        id = "lab",
        titleAz = "Aqrokimyəvi laboratoriya",
        titleEn = "Agrochemical Soil Laboratory",
        titleRu = "Агрохимическая лаборатория",
        summaryAz = "Torpaq pH, humus, azot-fosfor-kalium, şorlaşma və su analizi",
        summaryEn = "Soil pH, organic matter, NPK nutrient, salinity & water testing",
        summaryRu = "Анализ почвы на pH, гумус, NPK, засоление и качество воды",
        guideAz = "Nümunə götürərkən 5–10 hektardan ziqzaq sxemilə 0–30 sm dərinlikdən qarışıq nümunə hazırlayın. Analiz nəticələrini birbaşa tətbiqin NPK hesablayıcısına daxil edin.",
        guideEn = "Collect zigzag composite samples (0–30 cm depth) across each 5–10 ha block and plug test results directly into NPK Calculator.",
        guideRu = "Отбирайте смешанный образец с глубины 0–30 см зигзагом и используйте данные в калькуляторе NPK.",
        mapQueryAz = "aqrokimya laboratoriyası aqrar xidmətlər",
        portalUrl = "https://afsa.gov.az/",
        appToolRoute = NavRoutes.NPK,
        icon = Icons.Filled.Science,
        accent = Color(0xFF4F46E5),
    ),
)

@Composable
fun StationsScreen(
    language: AppLanguage,
    adminStations: List<AdminContentItem> = emptyList(),
    onNavigateToTool: ((String) -> Unit)? = null,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current
    val context = LocalContext.current

    var districtInput by remember { mutableStateOf("Ağcabədi") }
    var selectedService by remember { mutableStateOf<StationServiceDef?>(null) }

    fun openMapsSearch(queryText: String) {
        val fullQuery = listOf(districtInput.trim(), queryText).filter { it.isNotBlank() }.joinToString(" ")
        val url = "https://www.google.com/maps/search/?api=1&query=${Uri.encode(fullQuery)}"
        openExternalUrl(context, url)
    }

    selectedService?.let { service ->
        AlertDialog(
            onDismissRequest = { selectedService = null },
            title = {
                Text(
                    text = service.title(language),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = service.summary(language),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = extra.pale,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = service.guide(language),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    Button(
                        onClick = { openMapsSearch(service.mapQueryAz) },
                        modifier = Modifier.fillMaxWidth().testTag("station_dialog_maps_btn"),
                    ) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.stationsSearchMapsBtn)
                    }
                    OutlinedButton(
                        onClick = { openExternalUrl(context, service.portalUrl) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.stationsOpenPortalBtn)
                    }
                    val route = service.appToolRoute
                    if (route != null && onNavigateToTool != null) {
                        OutlinedButton(
                            onClick = {
                                selectedService = null
                                onNavigateToTool(route)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(strings.stationsOpenToolBtn, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedService = null }) {
                    Text(strings.back)
                }
            },
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("stations_screen"),
    ) {
        item {
            ScreenTopBar(
                title = strings.stationsTitle,
                onBack = onBack,
            )
        }

        item {
            AqroCard {
                    Text(
                        text = strings.stationsSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = extra.muted,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = districtInput,
                        onValueChange = { districtInput = it },
                        label = { Text(strings.stationsDistrictHint) },
                        leadingIcon = {
                            Icon(Icons.Filled.LocationOn, contentDescription = null)
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("stations_district_input"),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        districtPresets.forEach { preset ->
                            FilterChip(
                                selected = districtInput.equals(preset, ignoreCase = true),
                                onClick = { districtInput = preset },
                                label = { Text(preset) },
                            )
                        }
                    }
                }
            }

            // 7 Service Cards matching Yonca Məntəqələr
            items(stationServices, key = { it.id }) { service ->
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, extra.line),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { selectedService = service }
                        .testTag("station_card_${service.id}"),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(14.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(service.accent.copy(alpha = 0.14f)),
                        ) {
                            Icon(
                                imageVector = service.icon,
                                contentDescription = service.title(language),
                                tint = service.accent,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = service.title(language),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = service.summary(language),
                                style = MaterialTheme.typography.bodySmall,
                                color = extra.muted,
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = strings.open,
                            tint = extra.muted,
                        )
                    }
                }
            }

        // Admin-published custom stations (if any)
        if (adminStations.isNotEmpty()) {
            items(adminStations, key = { "admin_station_${it.id}" }) { item ->
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = extra.pale),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth().testTag("admin_station_${item.id}"),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
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
                                text = item.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        if (item.region.isNotBlank()) {
                            Text(
                                text = item.region,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text(
                            text = item.details,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (item.contactOrUrl.isNotBlank()) {
                            val raw = item.contactOrUrl.trim()
                            val url = when {
                                raw.startsWith("http", true) || raw.startsWith("tel:", true) -> raw
                                raw.startsWith("+") || raw.all { it.isDigit() || it == ' ' || it == '-' } -> "tel:${raw.filterNot { it == ' ' }}"
                                else -> "https://www.google.com/maps/search/?api=1&query=${Uri.encode("${item.region} ${item.title}")}"
                            }
                            TextButton(
                                onClick = { openExternalUrl(context, url) },
                            ) {
                                Text(raw, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

