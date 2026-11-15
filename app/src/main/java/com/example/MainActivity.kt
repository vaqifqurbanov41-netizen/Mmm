package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
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
import com.example.data.local.PreferencesStore
import com.example.data.remote.OpenMeteoService
import com.example.data.repository.FarmRepository
import com.example.i18n.AppLanguage
import com.example.i18n.LocalStrings
import com.example.i18n.StringsAz
import com.example.i18n.StringsEn
import com.example.i18n.StringsRu
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.authStateFlow
import com.example.ui.components.AqroBottomBar
import com.example.ui.components.AqroTopHeader
import com.example.ui.components.formatNumber
import com.example.ui.navigation.NavRoutes
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.EquipmentScreen
import com.example.ui.screens.FieldsScreen
import com.example.ui.screens.FinanceScreen
import com.example.ui.screens.FirebaseConfigMissingScreen
import com.example.ui.screens.HarvestScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LinksScreen
import com.example.ui.screens.LocalToolsApp
import com.example.ui.screens.NpkScreen
import com.example.ui.screens.PesticideScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ScannerScreen
import com.example.ui.screens.SubsidyScreen
import com.example.ui.screens.WeatherScreen
import com.example.ui.theme.AqroMeslehetTheme
import com.example.ui.theme.LocalAqroColors
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
        configureAppCheck(this, intent)

        setContent {
            AqroRootApp()
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

    val strings = when (language) {
        AppLanguage.AZ -> StringsAz
        AppLanguage.EN -> StringsEn
        AppLanguage.RU -> StringsRu
    }

    val context = LocalContext.current
    val isFirebaseReady = remember(context, authOverride, firebaseReadyOverride) {
        firebaseReadyOverride ?: (authOverride != null || ensureFirebaseInitialized(context))
    }

    CompositionLocalProvider(LocalStrings provides strings) {
        AqroMeslehetTheme(themePreference = themePref) {
            if (!isFirebaseReady) {
                var continueInLocalMode by remember { mutableStateOf(false) }
                if (continueInLocalMode) {
                    LocalToolsApp(
                        language = language,
                        themePreference = themePref,
                        onSelectLanguage = settingsViewModel::setLanguage,
                        onSelectTheme = settingsViewModel::setThemePreference,
                        onToggleDark = settingsViewModel::toggleLightDark,
                        onOpenFirebaseSetup = { continueInLocalMode = false },
                    )
                } else {
                    FirebaseConfigMissingScreen(
                        currentLanguage = language,
                        onSelectLanguage = settingsViewModel::setLanguage,
                        onContinueLocalMode = { continueInLocalMode = true },
                    )
                }
            } else {
                val scope = rememberCoroutineScope()
                val credentialManager = remember(context) { CredentialManager.create(context) }
                val auth = remember(authOverride) { authOverride ?: Firebase.auth }
                val currentUser by auth.authStateFlow()
                    .collectAsStateWithLifecycle(initialValue = auth.currentUser)

                LaunchedEffect(auth) {
                    if (auth.currentUser == null) {
                        attemptAutoSignIn(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {},
                            onUnauthenticated = {},
                            scope = scope,
                        )
                    }
                }

                val user = currentUser
                if (user == null) {
                    AuthScreen(
                        currentLanguage = language,
                        onSelectLanguage = settingsViewModel::setLanguage,
                        onAuthSuccess = {},
                    )
                } else {
                    key(user.uid) {
                        AuthenticatedMainApp(
                            currentUserId = user.uid,
                            userEmail = user.email,
                            language = language,
                            themePreference = themePref,
                            onSelectLanguage = settingsViewModel::setLanguage,
                            onSelectTheme = settingsViewModel::setThemePreference,
                            onToggleDark = settingsViewModel::toggleLightDark,
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
    language: AppLanguage,
    themePreference: ThemePreference,
    onSelectLanguage: (AppLanguage) -> Unit,
    onSelectTheme: (ThemePreference) -> Unit,
    onToggleDark: (Boolean) -> Unit,
) {
    val viewModel: FarmViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
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
    val weatherState by viewModel.weatherState.collectAsStateWithLifecycle()

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

    val equipmentAlerts = remember(equipment) { viewModel.getEquipmentAlerts(equipment) }
    val extraColors = LocalAqroColors.current

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
        .orEmpty()

    val weatherTempPill = weatherState.response?.current?.let {
        "${formatNumber(it.temperature2m, 0)}°C"
    }

    val hasAlertDot = weatherState.hasAnyHazard || equipmentAlerts.isNotEmpty()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AqroTopHeader(
                farmerFirstName = firstName,
                language = language,
                weatherTempText = weatherTempPill,
                hasAlertDot = hasAlertDot,
                isDark = extraColors.isDark,
                onWeatherClick = { navigateTo(NavRoutes.WEATHER) },
                onThemeToggle = { onToggleDark(extraColors.isDark) },
                onNotificationsClick = {
                    val dest = if (weatherState.hasAnyHazard) NavRoutes.WEATHER else NavRoutes.EQUIPMENT
                    navigateTo(dest)
                },
            )
        },
        bottomBar = {
            AqroBottomBar(
                currentRoute = currentRoute,
                onNavigate = navigateTo,
            )
        },
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
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
                    )
                }

                composable(NavRoutes.CHAT) {
                    ChatScreen(
                        language = language,
                        messages = chatMessages,
                        isStreaming = isChatStreaming,
                        onSendQuestion = viewModel::sendAiQuestion,
                        onClearChat = viewModel::clearChat,
                        onBack = { navController.popBackStack() },
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
                        computeStatus = viewModel::computeEquipmentStatus,
                        onSaveEquipment = viewModel::saveEquipment,
                        onDeleteEquipment = viewModel::deleteEquipment,
                        onExportJson = viewModel::exportBackupJson,
                        onImportJson = viewModel::importBackupJson,
                        onBack = { navController.popBackStack() },
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
