package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.local.PreferencesStore
import com.example.data.remote.OpenMeteoService
import com.example.i18n.AppLanguage
import com.example.i18n.LocalStrings
import com.example.ui.components.AqroTopHeader
import com.example.ui.components.KTN_TELEGRAM_BOT_URL
import com.example.ui.components.formatNumber
import com.example.ui.components.openExternalUrl
import com.example.ui.navigation.NavRoutes
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.LocalAqroColors
import com.example.ui.theme.PhosphorusAmber
import com.example.ui.theme.ThemePreference
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.WeatherUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
    val weatherService = remember { OpenMeteoService.create() }

    var weatherState by remember { mutableStateOf(WeatherUiState()) }

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

    LaunchedEffect(Unit) {
        val (lat, lon) = runCatching { preferencesStore.coordinatesFlow.first() }
            .getOrDefault(40.6782 to 46.3606)
        fetchWeather(lat, lon)
    }

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

    val weatherTempPill = weatherState.response?.current?.let {
        "${formatNumber(it.temperature2m, 0)}°C"
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AqroTopHeader(
                farmerFirstName = "",
                language = language,
                weatherTempText = weatherTempPill,
                hasAlertDot = weatherState.hasAnyHazard,
                isDark = extraColors.isDark,
                onWeatherClick = { navigateTo(NavRoutes.WEATHER) },
                onThemeToggle = { onToggleDark(extraColors.isDark) },
                onNotificationsClick = { navigateTo(NavRoutes.WEATHER) },
            )
        },
        bottomBar = {
            LocalBottomBar(
                currentRoute = currentRoute,
                onNavigate = navigateTo,
            )
        },
        modifier = Modifier.testTag("local_tools_app"),
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
                    LocalToolsHomeScreen(
                        onNavigate = navigateTo,
                        onOpenFirebaseSetup = onOpenFirebaseSetup,
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
    onNavigate: (String) -> Unit,
    onOpenFirebaseSetup: () -> Unit,
) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current
    val context = LocalContext.current

    val tiles = listOf(
        LocalToolTile(NavRoutes.NPK, strings.tileNpkTitle, strings.tileNpkSub, Icons.Filled.Grass, EmeraldPrimary, "local_tile_npk"),
        LocalToolTile(NavRoutes.WEATHER, strings.tileWeatherTitle, strings.tileWeatherSub, Icons.Filled.WbSunny, Color(0xFF2F80ED), "local_tile_weather"),
        LocalToolTile(NavRoutes.PESTICIDE, strings.tilePestTitle, strings.tilePestSub, Icons.Filled.Science, WarningAmber, "local_tile_pesticide"),
        LocalToolTile(NavRoutes.SUBSIDY, strings.tileSubsidyTitle, strings.tileSubsidySub, Icons.Filled.RequestQuote, PhosphorusAmber, "local_tile_subsidy"),
        LocalToolTile(NavRoutes.LINKS, strings.tileLinksTitle, strings.tileLinksSub, Icons.Filled.Link, Color(0xFF4F46E5), "local_tile_links"),
        LocalToolTile(NavRoutes.PROFILE, strings.navProfile, strings.settingsTitle, Icons.Filled.Person, EmeraldPrimary, "local_tile_profile"),
    )

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        // Hero Card
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(extra.heroStart, extra.heroEnd)))
                        .padding(18.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = strings.heroTitle,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text = strings.heroSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f),
                        )
                    }
                }
            }
        }

        // KTN ASI Telegram Bot Card
        item {
            val telegramBlue = Color(0xFF0088CC)
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, telegramBlue.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { openExternalUrl(context, KTN_TELEGRAM_BOT_URL) }
                    .testTag("local_home_telegram_bot_card"),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(telegramBlue.copy(alpha = 0.14f)),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = strings.telegramBotTitle,
                            tint = telegramBlue,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.telegramBotTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = strings.telegramBotSub,
                            style = MaterialTheme.typography.bodySmall,
                            color = extra.muted,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = telegramBlue.copy(alpha = 0.12f),
                    ) {
                        Text(
                            text = strings.telegramBotBtn,
                            style = MaterialTheme.typography.labelMedium,
                            color = telegramBlue,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }

        // Local Mode Info Banner with Setup Guide shortcut
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = extra.warningContainer),
                border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = WarningAmber,
                        modifier = Modifier.size(22.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.localModeBannerTitle,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = strings.localModeBannerSub,
                            style = MaterialTheme.typography.bodySmall,
                            color = extra.muted,
                        )
                    }
                    TextButton(onClick = onOpenFirebaseSetup) {
                        Text(
                            text = strings.localModeSetupAction,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // Working Local Tool Tiles
        item {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val cols = if (maxWidth >= 600.dp) 3 else 2
                val rows = tiles.chunked(cols)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rows.forEach { rowItems ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            rowItems.forEach { tile ->
                                Card(
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, extra.line),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(114.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .clickable { onNavigate(tile.route) }
                                        .testTag(tile.tag),
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 8.dp, vertical = 10.dp),
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(tile.accent.copy(alpha = 0.14f)),
                                        ) {
                                            Icon(
                                                imageVector = tile.icon,
                                                contentDescription = tile.title,
                                                tint = tile.accent,
                                                modifier = Modifier.size(22.dp),
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(7.dp))
                                        Text(
                                            text = tile.title,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = tile.subtitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = extra.muted,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
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

@Composable
private fun LocalBottomBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
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
                    currentRoute == NavRoutes.SUBSIDY,
                tag = "local_nav_tools",
                onClick = { onNavigate(NavRoutes.NPK) },
                modifier = Modifier.weight(1f),
            )
            LocalNavTab(
                icon = Icons.Filled.Link,
                label = strings.tileLinksTitle,
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
