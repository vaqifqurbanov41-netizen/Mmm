package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.ai.FirebaseAiRepository
import com.example.data.firebase.configureAppCheck
import com.example.data.firebase.ensureFirebaseInitialized
import com.example.data.firebase.resolveFirestoreDatabaseId
import com.example.data.local.LocalRecordsStore
import com.example.data.local.PreferencesStore
import com.example.data.remote.OpenMeteoService
import com.example.data.repository.FarmRepository
import com.example.i18n.AppLanguage
import com.example.i18n.LocalStrings
import com.example.i18n.StringsAz
import com.example.i18n.StringsEn
import com.example.i18n.StringsRu
import com.example.notifications.EquipmentReminderManager
import com.example.ui.auth.authStateFlow
import com.example.ui.components.AqroBottomBar
import com.example.ui.components.AqroTopHeader
import com.example.ui.components.NotificationsDialog
import com.example.ui.components.formatNumber
import com.example.ui.navigation.NavRoutes
import com.example.ui.screens.AdminScreen
import com.example.ui.screens.AgroCalendarScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.EquipmentScreen
import com.example.ui.screens.FieldsScreen
import com.example.ui.screens.FinanceScreen
import com.example.ui.screens.FirebaseConfigMissingScreen
import com.example.ui.screens.HarvestScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.InsuranceCalcScreen
import com.example.ui.screens.LinksScreen
import com.example.ui.screens.LocalToolsApp
import com.example.ui.screens.NpkScreen
import com.example.ui.screens.PesticideScreen
import com.example.ui.screens.PhotoGalleryScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ScannerScreen
import com.example.ui.screens.SeedsScreen
import com.example.ui.screens.StationsScreen
import com.example.ui.screens.SubsidyScreen
import com.example.ui.screens.WeatherScreen
import com.example.ui.theme.AqroMeslehetTheme
import com.example.ui.theme.LocalAqroColors
import com.aistudio.texnonezaret.ui.TexnonezaretRoot
import com.aistudio.testbaza.core.ui.TestBazaRoot
import com.example.ui.theme.ThemePreference
import com.example.ui.viewmodel.FarmViewModel
import com.example.ui.viewmodel.SettingsViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (BuildConfig.PREVIEW_TESTBAZA) {
            setContent {
                TestBazaRoot()
            }
        } else if (BuildConfig.PREVIEW_TEXNONEZARET) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
            setContent {
                TexnonezaretRoot()
            }
        } else {
            configureAppCheck(this, intent)
            setContent {
                AqroRootApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!BuildConfig.PREVIEW_TEXNONEZARET && !BuildConfig.PREVIEW_TESTBAZA) {
            configureAppCheck(this, intent)
        }
    }
}

@Composable
fun AqroRootApp(
    authOverride: FirebaseAuth? = null,
    firebaseReadyOverride: Boolean? = null,
) {
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                SettingsViewModel(PreferencesStore(app.applicationContext))
            }
        }
    )

    val language by settingsViewModel.language.collectAsStateWithLifecycle()
    val themePref by settingsViewModel.themePreference.collectAsStateWithLifecycle()
    val hiddenModules by settingsViewModel.hiddenModules.collectAsStateWithLifecycle()

    val strings = when (language) {
        AppLanguage.AZ -> StringsAz
        AppLanguage.EN -> StringsEn
        AppLanguage.RU -> StringsRu
    }

    val context = LocalContext.current
    val preferencesStore = remember(context) { PreferencesStore(context.applicationContext) }
    StartupPermissionsEffect(preferencesStore = preferencesStore)

    var isFirebaseReady by remember(context, authOverride, firebaseReadyOverride) {
        mutableStateOf(
            firebaseReadyOverride ?: (authOverride != null || ensureFirebaseInitialized(context))
        )
    }

    CompositionLocalProvider(LocalStrings provides strings) {
        AqroMeslehetTheme(themePreference = themePref) {
            var continueInLocalMode by remember { mutableStateOf(false) }

            if (continueInLocalMode) {
                LocalToolsApp(
                    language = language,
                    themePreference = themePref,
                    onSelectLanguage = settingsViewModel::setLanguage,
                    onSelectTheme = settingsViewModel::setThemePreference,
                    onToggleDark = settingsViewModel::toggleLightDark,
                    onOpenFirebaseSetup = { continueInLocalMode = false },
                    canUseCloudAi = isFirebaseReady,
                )
            } else if (!isFirebaseReady) {
                FirebaseConfigMissingScreen(
                    currentLanguage = language,
                    onSelectLanguage = settingsViewModel::setLanguage,
                    onContinueLocalMode = { continueInLocalMode = true },
                    onFirebaseActivated = {
                        configureAppCheck(context, null)
                        isFirebaseReady = ensureFirebaseInitialized(context)
                    },
                )
            } else {
                val auth = remember(authOverride) { authOverride ?: Firebase.auth }
                val currentUser by auth.authStateFlow()
                    .collectAsStateWithLifecycle(initialValue = auth.currentUser)

                val user = currentUser
                if (user == null) {
                    AuthScreen(
                        currentLanguage = language,
                        onSelectLanguage = settingsViewModel::setLanguage,
                        onAuthSuccess = {},
                        onContinueLocalMode = { continueInLocalMode = true },
                    )
                } else {
                    key(user.uid) {
                        AuthenticatedMainApp(
                            currentUserId = user.uid,
                            userEmail = user.email,
                            userDisplayName = user.displayName,
                            language = language,
                            themePreference = themePref,
                            onSelectLanguage = settingsViewModel::setLanguage,
                            onSelectTheme = settingsViewModel::setThemePreference,
                            onToggleDark = settingsViewModel::toggleLightDark,
                            hiddenModules = hiddenModules,
                            onToggleModuleVisible = settingsViewModel::setModuleVisible,
                            onResetModules = settingsViewModel::showAllModules,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthenticatedMainApp(
    currentUserId: String,
    userEmail: String?,
    userDisplayName: String?,
    language: AppLanguage,
    themePreference: ThemePreference,
    onSelectLanguage: (AppLanguage) -> Unit,
    onSelectTheme: (ThemePreference) -> Unit,
    onToggleDark: (Boolean) -> Unit,
    hiddenModules: Set<String>,
    onToggleModuleVisible: (String, Boolean) -> Unit,
    onResetModules: () -> Unit,
) {
    val context = LocalContext.current
    val localRecordsStore = remember(context) { LocalRecordsStore(context.applicationContext) }
    val localPhotos by localRecordsStore.photos.collectAsStateWithLifecycle()
    val viewModel: FarmViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = resolveFirestoreDatabaseId(app.applicationContext)
                val db = FirebaseFirestore.getInstance(databaseId)
                FarmViewModel(
                    repository = FarmRepository(db, Firebase.auth),
                    preferencesStore = PreferencesStore(app.applicationContext),
                    weatherService = OpenMeteoService.create(),
                    aiRepository = FirebaseAiRepository(),
                    currentUserId = currentUserId,
                )
            }
        }
    )

    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val fields by viewModel.fields.collectAsStateWithLifecycle()
    val equipment by viewModel.equipment.collectAsStateWithLifecycle()
    val finance by viewModel.finance.collectAsStateWithLifecycle()
    val harvests by viewModel.harvests.collectAsStateWithLifecycle()
    val declarations by viewModel.declarations.collectAsStateWithLifecycle()
    val customPesticides by viewModel.customPesticides.collectAsStateWithLifecycle()
    val customLinks by viewModel.customLinks.collectAsStateWithLifecycle()
    val adminEntries by viewModel.adminEntries.collectAsStateWithLifecycle()
    val weatherState by viewModel.weatherState.collectAsStateWithLifecycle()
    val weatherHazards by viewModel.weatherHazards.collectAsStateWithLifecycle()
    val climateHistoryState by viewModel.climateHistoryState.collectAsStateWithLifecycle()

    val mapPoints by viewModel.mapPoints.collectAsStateWithLifecycle()
    val measurements by viewModel.measurements.collectAsStateWithLifecycle()
    val mapCenter by viewModel.mapCenter.collectAsStateWithLifecycle()
    val mapZoom by viewModel.mapZoom.collectAsStateWithLifecycle()
    val isSatellite by viewModel.isSatellite.collectAsStateWithLifecycle()
    val gpsLocation by viewModel.gpsLocation.collectAsStateWithLifecycle()
    val gpsAccuracyM by viewModel.gpsAccuracyM.collectAsStateWithLifecycle()
    val docScanState by viewModel.docScanState.collectAsStateWithLifecycle()

    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isChatStreaming by viewModel.isChatStreaming.collectAsStateWithLifecycle()
    val scannerBitmap by viewModel.scannerBitmap.collectAsStateWithLifecycle()
    val scannerResult by viewModel.scannerResult.collectAsStateWithLifecycle()
    val isScannerLoading by viewModel.isScannerLoading.collectAsStateWithLifecycle()
    val scannerError by viewModel.scannerError.collectAsStateWithLifecycle()
    val equipmentReminderDays by viewModel.equipmentReminderDays.collectAsStateWithLifecycle()
    val preferencesStore = remember(context) { PreferencesStore(context.applicationContext) }
    val seenNotifSignature by preferencesStore.seenNotifSignatureFlow.collectAsStateWithLifecycle(initialValue = "")
    val dismissedNotifKeys by preferencesStore.dismissedNotifKeysFlow.collectAsStateWithLifecycle(initialValue = emptySet())
    val scope = rememberCoroutineScope()
    val strings = LocalStrings.current
    var showNotificationsDialog by remember { mutableStateOf(false) }

    val equipmentAlerts = remember(equipment, equipmentReminderDays) {
        viewModel.getEquipmentAlerts(equipment, equipmentReminderDays)
    }
    LaunchedEffect(equipment, equipmentReminderDays, language) {
        EquipmentReminderManager.syncEquipmentReminders(
            context = context,
            items = equipment,
            warningDays = equipmentReminderDays,
            strings = strings,
        )
    }
    LaunchedEffect(weatherHazards, language) {
        EquipmentReminderManager.syncWeatherHazards(
            context = context,
            alerts = weatherHazards,
            strings = strings,
        )
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

    val firstName = profile?.fullName
        ?.trim()
        ?.split(Regex("\\s+"))
        ?.firstOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: userDisplayName
            ?.trim()
            ?.split(Regex("\\s+"))
            ?.firstOrNull()
            .orEmpty()

    val regionLabel = profile?.place?.trim()?.takeIf { it.isNotBlank() }
        ?: fields.firstOrNull()?.location?.trim()?.takeIf { it.isNotBlank() }
        ?: ""

    val weatherTempPill = weatherState.response?.current?.let {
        "${formatNumber(it.temperature2m, 0)}°C"
    }

    val visibleWeatherHazards = remember(weatherHazards, dismissedNotifKeys) {
        weatherHazards.filter { EquipmentReminderManager.weatherAlertKey(it) !in dismissedNotifKeys }
    }
    val visibleEquipmentAlerts = remember(equipmentAlerts, dismissedNotifKeys) {
        equipmentAlerts.filter { EquipmentReminderManager.equipmentAlertKey(it) !in dismissedNotifKeys }
    }
    val activeNotifSignature = remember(visibleWeatherHazards, visibleEquipmentAlerts) {
        EquipmentReminderManager.buildNotificationSignature(visibleWeatherHazards, visibleEquipmentAlerts)
    }
    val hasUnseenAlerts = activeNotifSignature.isNotBlank() && activeNotifSignature != seenNotifSignature
    val alertCount = if (hasUnseenAlerts) visibleWeatherHazards.size + visibleEquipmentAlerts.size else 0
    val hasAlertDot = hasUnseenAlerts

    NotificationsDialog(
        visible = showNotificationsDialog,
        weatherHazards = visibleWeatherHazards,
        equipmentAlerts = visibleEquipmentAlerts,
        onOpenWeather = { navigateTo(NavRoutes.WEATHER) },
        onOpenEquipment = { navigateTo(NavRoutes.EQUIPMENT) },
        onDismiss = { showNotificationsDialog = false },
        onClearAll = {
            val allKeys = visibleWeatherHazards.map { EquipmentReminderManager.weatherAlertKey(it) } +
                visibleEquipmentAlerts.map { EquipmentReminderManager.equipmentAlertKey(it) }
            scope.launch {
                preferencesStore.dismissNotifications(allKeys)
                preferencesStore.markNotificationsViewed("")
            }
        },
        onDismissWeather = { alert ->
            scope.launch {
                preferencesStore.dismissNotifications(listOf(EquipmentReminderManager.weatherAlertKey(alert)))
            }
        },
        onDismissEquipment = { eqAlert ->
            scope.launch {
                preferencesStore.dismissNotifications(listOf(EquipmentReminderManager.equipmentAlertKey(eqAlert)))
            }
        },
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (currentRoute != NavRoutes.CHAT) {
                AqroTopHeader(
                    farmerFirstName = firstName,
                    language = language,
                    weatherTempText = weatherTempPill,
                    hasAlertDot = hasAlertDot,
                    isDark = extraColors.isDark,
                    onWeatherClick = { navigateTo(NavRoutes.WEATHER) },
                    onThemeToggle = { onToggleDark(extraColors.isDark) },
                    onNotificationsClick = {
                        scope.launch { preferencesStore.markNotificationsViewed(activeNotifSignature) }
                        showNotificationsDialog = true
                    },
                    weatherRegionName = regionLabel,
                    alertCount = alertCount,
                )
            }
        },
        bottomBar = {
            if (!isImeVisible) {
                AqroBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = navigateTo,
                )
            }
        },
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
                    HomeScreen(
                        fields = fields,
                        weatherState = weatherState,
                        equipmentAlerts = equipmentAlerts,
                        onNavigate = navigateTo,
                        userEmail = userEmail,
                        adminEntries = adminEntries,
                        hiddenModules = hiddenModules,
                        onToggleModuleVisible = onToggleModuleVisible,
                        onResetModules = onResetModules,
                        farmerFirstName = firstName,
                        weatherHazards = weatherHazards,
                    )
                }

                composable(NavRoutes.FIELDS) {
                    FieldsScreen(
                        language = language,
                        fields = fields,
                        mapPoints = mapPoints,
                        measurements = measurements,
                        mapCenter = mapCenter,
                        mapZoom = mapZoom,
                        isSatellite = isSatellite,
                        gpsLocation = gpsLocation,
                        gpsAccuracyM = gpsAccuracyM,
                        docScanState = docScanState,
                        onAddPoint = viewModel::addMapPoint,
                        onMovePoint = viewModel::updateMapPoint,
                        onUndoPoint = viewModel::undoMapPoint,
                        onClearPoints = viewModel::clearMapPoints,
                        onSetPolygon = viewModel::setMapPolygon,
                        onCenterChanged = viewModel::setMapCenter,
                        onZoomChanged = viewModel::setMapZoom,
                        onToggleLayer = viewModel::toggleMapLayer,
                        onUpdateGpsFix = viewModel::updateGpsFix,
                        onScanDocument = viewModel::scanFieldDocument,
                        onSaveField = viewModel::addField,
                        onFocusFieldOnMap = viewModel::focusFieldOnMap,
                        onDeleteField = viewModel::deleteField,
                        onBack = { navController.popBackStack() },
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
                        onRefreshWeather = viewModel::loadWeather,
                        onBack = { navController.popBackStack() },
                        fields = fields,
                        climateState = climateHistoryState,
                        onLoadClimateHistory = viewModel::loadClimateHistory,
                    )
                }

                composable(NavRoutes.CHAT) {
                    ChatScreen(
                        language = language,
                        messages = chatMessages,
                        isStreaming = isChatStreaming,
                        onSendQuestion = { q, lang, bmp ->
                            viewModel.sendAiQuestion(q, lang, bmp)
                        },
                        onClearChat = viewModel::clearChat,
                        onBack = { navController.popBackStack() },
                        galleryPhotos = localPhotos,
                    )
                }

                composable(NavRoutes.SCANNER) {
                    ScannerScreen(
                        language = language,
                        selectedBitmap = scannerBitmap,
                        diagnosisResult = scannerResult,
                        isLoading = isScannerLoading,
                        errorMessage = scannerError,
                        onSelectBitmap = viewModel::setScannerBitmap,
                        onStartDiagnosis = viewModel::analyzePlantPhoto,
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

                composable(NavRoutes.PESTICIDE) {
                    PesticideScreen(
                        language = language,
                        customPesticides = customPesticides,
                        onAddCustomPesticide = viewModel::addCustomPesticide,
                        onDeleteCustomPesticide = viewModel::deleteCustomPesticide,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(NavRoutes.SUBSIDY) {
                    SubsidyScreen(
                        language = language,
                        declarations = declarations,
                        onAddDeclaration = viewModel::addDeclaration,
                        onDeleteDeclaration = viewModel::deleteDeclaration,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(NavRoutes.EQUIPMENT) {
                    EquipmentScreen(
                        equipment = equipment,
                        computeStatus = { date -> viewModel.computeEquipmentStatus(date, equipmentReminderDays) },
                        onSaveEquipment = viewModel::saveEquipment,
                        onDeleteEquipment = viewModel::deleteEquipment,
                        onExportJson = viewModel::exportBackupJson,
                        onImportJson = viewModel::importBackupJson,
                        onBack = { navController.popBackStack() },
                        reminderDays = equipmentReminderDays,
                        onSelectReminderDays = viewModel::setEquipmentReminderDays,
                    )
                }

                composable(NavRoutes.FINANCE) {
                    FinanceScreen(
                        language = language,
                        fields = fields,
                        entries = finance,
                        onAddFinance = viewModel::addFinance,
                        onDeleteFinance = viewModel::deleteFinance,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(NavRoutes.HARVEST) {
                    HarvestScreen(
                        language = language,
                        fields = fields,
                        entries = harvests,
                        onAddHarvest = viewModel::addHarvest,
                        onDeleteHarvest = viewModel::deleteHarvest,
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
                        adminSeedEntries = adminEntries.filter { it.category == "seed" },
                        onBack = { navController.popBackStack() },
                        onAskAiAboutVariety = { prompt ->
                            navigateTo(NavRoutes.CHAT)
                            viewModel.sendAiQuestion(prompt, language)
                        },
                    )
                }

                composable(NavRoutes.AGRO_CALENDAR) {
                    AgroCalendarScreen(
                        language = language,
                        adminCalendarNotes = adminEntries.filter { it.category == "calendar" },
                        onBack = { navController.popBackStack() },
                        onAskAiAboutMonth = { prompt ->
                            navigateTo(NavRoutes.CHAT)
                            viewModel.sendAiQuestion(prompt, language)
                        },
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
                        adminStations = adminEntries.filter { it.category == "station" },
                        onNavigateToTool = navigateTo,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(NavRoutes.ADMIN) {
                    AdminScreen(
                        userEmail = userEmail,
                        entries = adminEntries,
                        onPublishEntry = viewModel::addAdminEntry,
                        onDeleteEntry = viewModel::deleteAdminEntry,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(NavRoutes.LINKS) {
                    LinksScreen(
                        customLinks = customLinks,
                        onAddCustomLink = viewModel::addCustomLink,
                        onDeleteCustomLink = viewModel::deleteCustomLink,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(NavRoutes.PROFILE) {
                    ProfileScreen(
                        language = language,
                        themePreference = themePreference,
                        profile = profile,
                        userEmail = userEmail,
                        onSelectLanguage = onSelectLanguage,
                        onSelectTheme = onSelectTheme,
                        onSaveProfile = viewModel::saveProfile,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

fun collectMissingStartupPermissions(context: Context): List<String> {
    val candidates = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    return candidates.filter { perm ->
        ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
    }
}

@Composable
private fun StartupPermissionsEffect(preferencesStore: PreferencesStore) {
    val context = LocalContext.current
    val prompted by preferencesStore.initialPermissionsPromptedFlow.collectAsStateWithLifecycle(initialValue = null)
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    LaunchedEffect(prompted) {
        if (prompted == false) {
            preferencesStore.markInitialPermissionsPrompted()
            val missing = collectMissingStartupPermissions(context)
            if (missing.isNotEmpty()) {
                runCatching {
                    launcher.launch(missing.toTypedArray())
                }
            }
        }
    }
}

