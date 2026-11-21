package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.ai.OfflineAgronomistAdvisor
import com.example.data.ai.ReferenceImageMatcher
import com.example.data.local.LocalRecordsStore
import com.example.data.local.PreferencesStore
import com.example.data.local.collectEquipmentAlerts
import com.example.data.local.evaluateEquipmentStatus
import com.example.data.remote.OpenMeteoService
import com.example.i18n.AppLanguage
import com.example.i18n.LocalStrings
import com.example.notifications.EquipmentReminderManager
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.unit.sp
import com.example.ui.components.AqroCard
import com.example.ui.components.AqroTopHeader
import com.example.ui.components.BotanicalCornerBackdrop
import com.example.ui.components.GreenCameraScannerNavButton
import com.example.ui.components.KTN_TELEGRAM_BOT_URL
import com.example.ui.components.ModuleIconMedallion
import com.example.ui.components.ModuleVisibilityDialog
import com.example.ui.components.NotificationsDialog
import com.example.ui.components.formatNumber
import com.example.ui.components.openExternalUrl
import com.example.ui.navigation.NavRoutes
import com.example.ui.theme.DangerBorder
import com.example.ui.theme.DangerRed
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.LocalAqroColors
import com.example.ui.theme.PhosphorusAmber
import com.example.ui.theme.PotassiumPlum
import com.example.ui.theme.ThemePreference
import com.example.ui.theme.WarningAmber
import com.example.domain.ClimateHistoryUtils
import com.example.domain.WeatherHazardMonitor
import com.example.ui.viewmodel.ChatMessageUi
import com.example.ui.viewmodel.ClimateHistoryUiState
import com.example.ui.viewmodel.EquipmentAlert
import com.example.ui.viewmodel.EquipmentStatus
import com.example.ui.viewmodel.WeatherUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun LocalToolsApp(
    language: AppLanguage,
    themePreference: ThemePreference,
    onSelectLanguage: (AppLanguage) -> Unit,
    onSelectTheme: (ThemePreference) -> Unit,
    onToggleDark: (Boolean) -> Unit,
    onOpenFirebaseSetup: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferencesStore = remember(context) { PreferencesStore(context.applicationContext) }
    val localRecordsStore = remember(context) { LocalRecordsStore(context.applicationContext) }
    val weatherService = remember { OpenMeteoService.create() }

    val localEquipment by localRecordsStore.equipment.collectAsStateWithLifecycle()
    val localFinance by localRecordsStore.finance.collectAsStateWithLifecycle()
    val reminderDays by preferencesStore.equipmentReminderDaysFlow.collectAsStateWithLifecycle(initialValue = 7)
    val hiddenModules by preferencesStore.hiddenModulesFlow.collectAsStateWithLifecycle(initialValue = emptySet())
    var offlineChatMessages by remember { mutableStateOf<List<ChatMessageUi>>(emptyList()) }
    var offlineScannerBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var offlineScannerResult by remember { mutableStateOf("") }
    var isOfflineScannerLoading by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }
    val strings = LocalStrings.current
    val equipmentAlerts = remember(localEquipment, reminderDays) {
        collectEquipmentAlerts(localEquipment, reminderDays)
    }

    LaunchedEffect(localEquipment, reminderDays, language) {
        EquipmentReminderManager.syncEquipmentReminders(
            context = context,
            items = localEquipment,
            warningDays = reminderDays,
            strings = strings,
        )
    }

    fun sendOfflineAiQuestion(question: String, lang: AppLanguage) {
        val trimmed = question.trim()
        if (trimmed.isBlank()) return
        val userMsg = ChatMessageUi(
            id = "loc_u_${System.currentTimeMillis()}",
            isUser = true,
            text = trimmed,
        )
        val botMsg = ChatMessageUi(
            id = "loc_b_${System.currentTimeMillis() + 1}",
            isUser = false,
            text = OfflineAgronomistAdvisor.answerOffline(trimmed, lang),
            isStreaming = false,
        )
        offlineChatMessages = offlineChatMessages + userMsg + botMsg
    }

    var weatherState by remember { mutableStateOf(WeatherUiState()) }
    var climateState by remember { mutableStateOf(ClimateHistoryUiState()) }

    fun fetchWeather(lat: Double, lon: Double) {
        scope.launch {
            weatherState = weatherState.copy(isLoading = true, error = null, lat = lat, lon = lon)
            try {
                preferencesStore.setCoordinates(lat, lon)
                val response = weatherService.getForecast(latitude = lat, longitude = lon)
                weatherState = WeatherUiState(
                    isLoading = false,
                    response = response,
                    error = null,
                    lat = lat,
                    lon = lon,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                weatherState = weatherState.copy(
                    isLoading = false,
                    error = e.localizedMessage ?: "Weather error",
                )
            }
        }
    }

    fun fetchClimateHistory(
        lat: Double,
        lon: Double,
        startDate: String,
        endDate: String,
        locationLabel: String,
    ) {
        scope.launch {
            climateState = climateState.copy(
                isLoading = true,
                lat = lat,
                lon = lon,
                startDate = startDate,
                endDate = endDate,
                locationLabel = locationLabel,
                error = null,
            )
            try {
                val res = weatherService.getHistoricalClimate(
                    latitude = lat,
                    longitude = lon,
                    startDate = startDate,
                    endDate = endDate,
                )
                climateState = ClimateHistoryUiState(
                    isLoading = false,
                    locationLabel = locationLabel,
                    lat = res.latitude,
                    lon = res.longitude,
                    startDate = startDate,
                    endDate = endDate,
                    elevationM = res.elevation,
                    summary = ClimateHistoryUtils.summarizeResponse(res),
                    error = null,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                climateState = climateState.copy(
                    isLoading = false,
                    error = e.localizedMessage ?: "Climate history error",
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        val (lat, lon) = runCatching { preferencesStore.coordinatesFlow.first() }
            .getOrDefault(40.6782 to 46.3606)
        fetchWeather(lat, lon)
    }

    val extraColors = LocalAqroColors.current
    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: NavRoutes.HOME

    val navigateTo: (String) -> Unit = { route ->
        if (route == NavRoutes.HOME) {
            navController.popBackStack(NavRoutes.HOME, inclusive = false)
        } else if (currentRoute != route) {
            navController.navigate(route) {
                launchSingleTop = true
            }
        }
    }

    val weatherTempPill = weatherState.response?.current?.let {
        "${formatNumber(it.temperature2m, 0)}°C"
    }

    val localWeatherHazards = remember(weatherState.response, weatherState.lat, weatherState.lon, strings.notifSourceGps) {
        val resp = weatherState.response ?: return@remember emptyList()
        WeatherHazardMonitor.collectMonitoredLocations(
            fields = emptyList(),
            userLat = weatherState.lat,
            userLon = weatherState.lon,
            profilePlace = null,
            gpsDisplayLabel = strings.notifSourceGps,
        ).flatMap { loc ->
            WeatherHazardMonitor.evaluateSevenDayHazards(loc, resp)
        }
    }

    LaunchedEffect(localWeatherHazards, language) {
        EquipmentReminderManager.syncWeatherHazards(
            context = context,
            alerts = localWeatherHazards,
            strings = strings,
        )
    }

    val alertCount = localWeatherHazards.size + equipmentAlerts.size
    val hasAlertDot = localWeatherHazards.isNotEmpty() || weatherState.hasAnyHazard || equipmentAlerts.isNotEmpty()

    NotificationsDialog(
        visible = showNotificationsDialog,
        weatherHazards = localWeatherHazards,
        equipmentAlerts = equipmentAlerts,
        onOpenWeather = { navigateTo(NavRoutes.WEATHER) },
        onOpenEquipment = { navigateTo(NavRoutes.EQUIPMENT) },
        onDismiss = { showNotificationsDialog = false },
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AqroTopHeader(
                farmerFirstName = "",
                language = language,
                weatherTempText = weatherTempPill,
                hasAlertDot = hasAlertDot,
                isDark = extraColors.isDark,
                onWeatherClick = { navigateTo(NavRoutes.WEATHER) },
                onThemeToggle = { onToggleDark(extraColors.isDark) },
                onNotificationsClick = { showNotificationsDialog = true },
                alertCount = alertCount,
            )
        },
        bottomBar = {
            if (!isImeVisible) {
                LocalBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = navigateTo,
                )
            }
        },
        modifier = Modifier.testTag("local_tools_app"),
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
        ) {
            NavHost(
                navController = navController,
                startDestination = NavRoutes.HOME,
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxSize(),
            ) {
                composable(NavRoutes.HOME) {
                    LocalToolsHomeScreen(
                        hiddenModules = hiddenModules,
                        onToggleModuleVisible = { route, visible ->
                            scope.launch { preferencesStore.setModuleVisible(route, visible) }
                        },
                        onResetModules = {
                            scope.launch { preferencesStore.showAllModules() }
                        },
                        onNavigate = navigateTo,
                        onOpenFirebaseSetup = onOpenFirebaseSetup,
                    )
                }

                composable(NavRoutes.CHAT) {
                    ChatScreen(
                        language = language,
                        messages = offlineChatMessages,
                        isStreaming = false,
                        onSendQuestion = ::sendOfflineAiQuestion,
                        onClearChat = { offlineChatMessages = emptyList() },
                        onBack = { navController.popBackStack() },
                        isOfflineOnly = true,
                    )
                }

                composable(NavRoutes.SCANNER) {
                    ScannerScreen(
                        language = language,
                        selectedBitmap = offlineScannerBitmap,
                        diagnosisResult = offlineScannerResult,
                        isLoading = isOfflineScannerLoading,
                        errorMessage = null,
                        onSelectBitmap = {
                            offlineScannerBitmap = it
                            offlineScannerResult = ""
                        },
                        onStartDiagnosis = { note, lang ->
                            scope.launch {
                                isOfflineScannerLoading = true
                                try {
                                    val bmp = offlineScannerBitmap
                                    val realSims = if (bmp != null) {
                                        val preMatches = OfflineAgronomistAdvisor.matchThreatsWithVisualComparison(
                                            bitmap = bmp,
                                            note = note,
                                            limit = 10,
                                        )
                                        withTimeoutOrNull(3500L) {
                                            ReferenceImageMatcher.fetchAndCompareRealPhotos(
                                                bitmap = bmp,
                                                candidates = preMatches.map { it.threat },
                                                maxCandidates = 10,
                                            )
                                        }.orEmpty()
                                    } else {
                                        emptyMap()
                                    }
                                    offlineScannerResult = OfflineAgronomistAdvisor.diagnosePlantPhotoOffline(
                                        bitmap = bmp,
                                        note = note,
                                        language = lang,
                                        realPhotoSimilarities = realSims,
                                    )
                                } finally {
                                    isOfflineScannerLoading = false
                                }
                            }
                        },
                        onBack = { navController.popBackStack() },
                        onSaveCameraPhotoToGallery = { tempFile, cropName ->
                            localRecordsStore.saveScannerFileToGallery(
                                tempFile = tempFile,
                                cropHint = cropName,
                                language = language,
                            )
                        },
                        onRenameGalleryPhoto = localRecordsStore::renamePhoto,
                    )
                }

                composable(NavRoutes.NPK) {
                    NpkScreen(
                        language = language,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(NavRoutes.WEATHER) {
                    WeatherScreen(
                        weatherState = weatherState,
                        onRefreshWeather = { lat, lon -> fetchWeather(lat, lon) },
                        onBack = { navController.popBackStack() },
                        fields = emptyList(),
                        climateState = climateState,
                        onLoadClimateHistory = ::fetchClimateHistory,
                    )
                }

                composable(NavRoutes.PESTICIDE) {
                    PesticideScreen(
                        language = language,
                        customPesticides = emptyList(),
                        onAddCustomPesticide = { _, _, _, _, _, _ -> },
                        onDeleteCustomPesticide = {},
                        onBack = { navController.popBackStack() },
                        isLocalOnly = true,
                    )
                }

                composable(NavRoutes.SUBSIDY) {
                    SubsidyScreen(
                        language = language,
                        declarations = emptyList(),
                        onAddDeclaration = { _, _ -> },
                        onDeleteDeclaration = {},
                        onBack = { navController.popBackStack() },
                        isLocalOnly = true,
                    )
                }

                composable(NavRoutes.EQUIPMENT) {
                    EquipmentScreen(
                        equipment = localEquipment,
                        computeStatus = { date -> evaluateEquipmentStatus(date, reminderDays) },
                        onSaveEquipment = { existing, name, model, plate, insp, ins ->
                            localRecordsStore.saveEquipment(existing, name, model, plate, insp, ins)
                        },
                        onDeleteEquipment = { id -> localRecordsStore.deleteEquipment(id) },
                        onExportJson = { localRecordsStore.exportBackupJson() },
                        onImportJson = { json -> localRecordsStore.importBackupJson(json) },
                        onBack = { navController.popBackStack() },
                        reminderDays = reminderDays,
                        onSelectReminderDays = { days ->
                            scope.launch { preferencesStore.setEquipmentReminderDays(days) }
                        },
                    )
                }

                composable(NavRoutes.FINANCE) {
                    FinanceScreen(
                        language = language,
                        fields = emptyList(),
                        entries = localFinance,
                        onAddFinance = { type, date, field, amt, cat ->
                            localRecordsStore.addFinance(type, date, field, amt, cat)
                        },
                        onDeleteFinance = { id -> localRecordsStore.deleteFinance(id) },
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(NavRoutes.GALLERY) {
                    PhotoGalleryScreen(
                        language = language,
                        store = localRecordsStore,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(NavRoutes.SEEDS) {
                    SeedsScreen(
                        language = language,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(NavRoutes.AGRO_CALENDAR) {
                    AgroCalendarScreen(
                        language = language,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(NavRoutes.INSURANCE) {
                    InsuranceCalcScreen(
                        language = language,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(NavRoutes.STATIONS) {
                    StationsScreen(
                        language = language,
                        onNavigateToTool = navigateTo,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(NavRoutes.LINKS) {
                    LinksScreen(
                        customLinks = emptyList(),
                        onAddCustomLink = { _, _ -> },
                        onDeleteCustomLink = {},
                        onBack = { navController.popBackStack() },
                        isLocalOnly = true,
                    )
                }

                composable(NavRoutes.PROFILE) {
                    ProfileScreen(
                        language = language,
                        themePreference = themePreference,
                        profile = null,
                        userEmail = null,
                        onSelectLanguage = onSelectLanguage,
                        onSelectTheme = onSelectTheme,
                        onSaveProfile = { _, _, _ -> },
                        onBack = { navController.popBackStack() },
                        isLocalOnly = true,
                        onOpenFirebaseSetup = onOpenFirebaseSetup,
                    )
                }
            }
        }
    }
}

private data class LocalToolTile(
    val route: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val tag: String,
)

@Composable
private fun LocalToolsHomeScreen(
    hiddenModules: Set<String>,
    onToggleModuleVisible: (String, Boolean) -> Unit,
    onResetModules: () -> Unit,
    onNavigate: (String) -> Unit,
    onOpenFirebaseSetup: () -> Unit,
) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current
    val context = LocalContext.current
    var showCustomizeDialog by remember { mutableStateOf(false) }

    val allTiles = listOf(
        LocalToolTile(NavRoutes.NPK, strings.tileNpkTitle, strings.tileNpkSub, Icons.Filled.Grass, EmeraldPrimary, "local_tile_npk"),
        LocalToolTile(NavRoutes.WEATHER, strings.tileWeatherTitle, strings.tileWeatherSub, Icons.Filled.WbSunny, Color(0xFF2F80ED), "local_tile_weather"),
        LocalToolTile(NavRoutes.PESTICIDE, strings.tilePestTitle, strings.tilePestSub, Icons.Filled.Science, WarningAmber, "local_tile_pesticide"),
        LocalToolTile(NavRoutes.AGRO_CALENDAR, strings.tileCalendarTitle, strings.tileCalendarSub, Icons.Filled.CalendarMonth, Color(0xFF7E22CE), "local_tile_calendar"),
        LocalToolTile(NavRoutes.INSURANCE, strings.tileInsuranceTitle, strings.tileInsuranceSub, Icons.Filled.VerifiedUser, Color(0xFF0369A1), "local_tile_insurance"),
        LocalToolTile(NavRoutes.STATIONS, strings.tileStationsTitle, strings.tileStationsSub, Icons.Filled.Storefront, Color(0xFF0D9488), "local_tile_stations"),
        LocalToolTile(NavRoutes.SUBSIDY, strings.tileSubsidyTitle, strings.tileSubsidySub, Icons.Filled.RequestQuote, PhosphorusAmber, "local_tile_subsidy"),
        LocalToolTile(NavRoutes.EQUIPMENT, strings.tileEquipmentTitle, strings.tileEquipmentSub, Icons.Filled.Agriculture, PotassiumPlum, "local_tile_equipment"),
        LocalToolTile(NavRoutes.FINANCE, strings.tileFinanceTitle, strings.tileFinanceSub, Icons.Filled.AccountBalanceWallet, Color(0xFF0F766E), "local_tile_finance"),
        LocalToolTile(NavRoutes.GALLERY, strings.tileGalleryTitle, strings.tileGallerySub, Icons.Filled.PhotoCamera, Color(0xFF0284C7), "local_tile_gallery"),
        LocalToolTile(NavRoutes.SEEDS, strings.tileSeedsTitle, strings.tileSeedsSub, Icons.Filled.LocalFlorist, Color(0xFF15803D), "local_tile_seeds"),
        LocalToolTile(NavRoutes.LINKS, strings.tileLinksTitle, strings.tileLinksSub, Icons.Filled.Link, Color(0xFF4F46E5), "local_tile_links"),
    )
    val tiles = allTiles.filter { it.route !in hiddenModules }

    ModuleVisibilityDialog(
        visible = showCustomizeDialog,
        isLocalOnly = true,
        hiddenModules = hiddenModules,
        onToggleModule = onToggleModuleVisible,
        onShowAll = onResetModules,
        onDismiss = { showCustomizeDialog = false },
    )

    Box(modifier = Modifier.fillMaxSize()) {
        BotanicalCornerBackdrop(modifier = Modifier.fillMaxSize())

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().testTag("local_home_list"),
        ) {
            // Illustrated Hero Card
            item {
                HomeHeroBannerCard(
                    greeting = "${strings.greetingPrefix}, ${strings.defaultFarmerName}!",
                    subtitle = strings.heroSubtitle,
                    fieldsLabel = strings.myFieldsStat,
                    fieldsCount = "0",
                    areaLabel = strings.totalAreaStat,
                    areaValue = "0.00 ha",
                    onFieldsClick = onOpenFirebaseSetup,
                    showStats = false,
                )
            }

            // Illustrated Offline AI Agronomist Card
            item {
                HomeAssistantBannerCard(
                    title = strings.aiBannerTitle,
                    badgeLabel = strings.aiOfflineBadge,
                    subtitle = strings.aiBannerSub,
                    actionLabel = strings.askNowBtn,
                    tag = "local_home_ai_banner",
                    onClick = { onNavigate(NavRoutes.CHAT) },
                )
            }

            // KTN ASI Telegram Bot Card
            item {
                HomeTelegramBotCard(
                    title = strings.telegramBotTitle,
                    subtitle = strings.telegramBotSub,
                    actionLabel = strings.telegramBotBtn,
                    tag = "local_home_telegram_bot_card",
                    onClick = { openExternalUrl(context, KTN_TELEGRAM_BOT_URL) },
                )
            }

            // Local Mode Info Banner with clean full-width text layout + Setup Action
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = extra.warningContainer),
                    border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = WarningAmber,
                                modifier = Modifier.padding(top = 2.dp).size(22.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.localModeBannerTitle,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = strings.localModeBannerSub,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = extra.muted,
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            TextButton(
                                onClick = onOpenFirebaseSetup,
                                modifier = Modifier.testTag("local_mode_setup_btn"),
                            ) {
                                Text(
                                    text = strings.localModeSetupAction,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }

            // Modules Header + Customize Visibility Button
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = strings.modulesSectionTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    TextButton(
                        onClick = { showCustomizeDialog = true },
                        modifier = Modifier.testTag("customize_modules_btn"),
                    ) {
                        Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(strings.modulesCustomizeBtn, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Working Local Tool Tiles
            item {
                if (tiles.isEmpty()) {
                    AqroCard {
                        Text(
                            text = strings.modulesAllHiddenMsg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = extra.muted,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onResetModules,
                            modifier = Modifier.fillMaxWidth().testTag("show_all_modules_inline_btn"),
                        ) {
                            Text(strings.modulesShowAllBtn)
                        }
                    }
                } else {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth().testTag("local_tiles_grid")) {
                        val cols = if (maxWidth >= 600.dp) 4 else 3
                        val rows = tiles.chunked(cols)
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            rows.forEach { rowItems ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                                ) {
                                    rowItems.forEach { tile ->
                                        Card(
                                            shape = RoundedCornerShape(20.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                            border = BorderStroke(1.dp, extra.line),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(132.dp)
                                                .clip(RoundedCornerShape(20.dp))
                                                .clickable { onNavigate(tile.route) }
                                                .testTag(tile.tag),
                                        ) {
                                            Column(
                                                verticalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                            ) {
                                                ModuleIconMedallion(
                                                    route = tile.route,
                                                    fallbackIcon = tile.icon,
                                                    fallbackAccent = tile.accent,
                                                    size = 46.dp,
                                                )
                                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        modifier = Modifier.fillMaxWidth(),
                                                    ) {
                                                        Text(
                                                            text = tile.title,
                                                            style = MaterialTheme.typography.labelLarge.copy(
                                                                fontSize = 12.5.sp,
                                                                lineHeight = 15.sp,
                                                            ),
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f).padding(end = 2.dp),
                                                        )
                                                        Icon(
                                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                            contentDescription = null,
                                                            tint = extra.muted,
                                                            modifier = Modifier.size(16.dp),
                                                        )
                                                    }
                                                    Text(
                                                        text = tile.subtitle,
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontSize = 10.5.sp,
                                                            lineHeight = 13.sp,
                                                        ),
                                                        color = extra.muted,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    repeat(cols - rowItems.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalBottomBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current

    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, extra.line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            LocalNavTab(
                icon = Icons.Filled.Home,
                label = strings.navHome,
                selected = currentRoute == NavRoutes.HOME,
                tag = "local_nav_home",
                onClick = { onNavigate(NavRoutes.HOME) },
                modifier = Modifier.weight(1f),
            )
            LocalNavTab(
                icon = Icons.Filled.Build,
                label = strings.navTools,
                selected = currentRoute == NavRoutes.NPK ||
                    currentRoute == NavRoutes.WEATHER ||
                    currentRoute == NavRoutes.PESTICIDE ||
                    currentRoute == NavRoutes.SUBSIDY ||
                    currentRoute == NavRoutes.EQUIPMENT ||
                    currentRoute == NavRoutes.FINANCE ||
                    currentRoute == NavRoutes.GALLERY ||
                    currentRoute == NavRoutes.SEEDS ||
                    currentRoute == NavRoutes.AGRO_CALENDAR ||
                    currentRoute == NavRoutes.INSURANCE ||
                    currentRoute == NavRoutes.STATIONS,
                tag = "local_nav_tools",
                onClick = { onNavigate(NavRoutes.NPK) },
                modifier = Modifier.weight(1f),
            )
            GreenCameraScannerNavButton(
                selected = currentRoute == NavRoutes.SCANNER,
                contentDesc = strings.navScanner,
                tag = "local_nav_scanner",
                onClick = { onNavigate(NavRoutes.SCANNER) },
                modifier = Modifier.weight(1f),
            )
            LocalNavTab(
                icon = Icons.Filled.Link,
                label = strings.navLinks,
                selected = currentRoute == NavRoutes.LINKS,
                tag = "local_nav_links",
                onClick = { onNavigate(NavRoutes.LINKS) },
                modifier = Modifier.weight(1f),
            )
            LocalNavTab(
                icon = Icons.Filled.Person,
                label = strings.navProfile,
                selected = currentRoute == NavRoutes.PROFILE,
                tag = "local_nav_profile",
                onClick = { onNavigate(NavRoutes.PROFILE) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LocalNavTab(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extra = LocalAqroColors.current
    val tint = if (selected) MaterialTheme.colorScheme.primary else extra.muted
    val pillBg = if (selected) extra.pale else Color.Transparent

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .background(pillBg)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp)
            .testTag(tag),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
