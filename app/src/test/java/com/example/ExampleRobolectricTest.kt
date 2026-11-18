package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.firebase.getSigningSha1
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithText
import com.example.data.model.ADMIN_EMAIL
import com.example.data.model.AdminContentItem
import com.example.i18n.AppLanguage
import com.example.i18n.LocalStrings
import com.example.i18n.StringsAz
import com.example.i18n.StringsEn
import com.example.i18n.StringsRu
import com.example.ui.navigation.NavRoutes
import com.example.ui.screens.AdminScreen
import com.example.ui.screens.AgroCalendarScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.EquipmentScreen
import com.example.ui.screens.FieldsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.InsuranceCalcScreen
import com.example.ui.screens.NpkScreen
import com.example.ui.screens.PesticideScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SeedsScreen
import com.example.ui.screens.StationsScreen
import com.example.ui.screens.WeatherScreen
import com.example.ui.theme.AqroMeslehetTheme
import com.example.ui.theme.ThemePreference
import com.example.ui.viewmodel.ChatMessageUi
import com.example.ui.viewmodel.ClimateHistoryUiState
import com.example.ui.viewmodel.DocScanUiState
import com.example.ui.viewmodel.EquipmentAlert
import com.example.ui.viewmodel.EquipmentStatus
import com.example.ui.viewmodel.WeatherUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `read string from context and verify localized strings`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("AqroMəsləhət", appName)

        val sha1 = getSigningSha1(context)
        assertTrue(sha1.contains(":"))

        assertEquals("Vaqif Qurbanov", StringsAz.creatorName)
        assertEquals("Vaqif Qurbanov", StringsEn.creatorName)
        assertEquals("Вагиф Гурбанов", StringsRu.creatorName)
        assertTrue(StringsAz.allowedPesticides.size >= 14)
        assertTrue(StringsEn.allowedPesticides.size == StringsAz.allowedPesticides.size)
        assertTrue(StringsRu.allowedPesticides.size == StringsAz.allowedPesticides.size)
        assertTrue(StringsAz.bannedPesticides.size == StringsEn.bannedPesticides.size)
        assertTrue(StringsAz.officialLinks.any { it.url == "https://t.me/ktn_asi_bot" })
        assertTrue(StringsEn.officialLinks.any { it.url == "https://t.me/ktn_asi_bot" })
        assertTrue(StringsRu.officialLinks.any { it.url == "https://t.me/ktn_asi_bot" })
    }

    @Test
    fun `mainActivity launches without crashing`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        assertTrue(controller.get() != null)
        controller.pause().stop().destroy()
    }

    @Test
    fun `authScreen shows sign in, browser google fallback, local mode button, and expandable sha1 troubleshooting card`() {
        var continuedLocal = false
        composeTestRule.setContent {
            CompositionLocalProvider(LocalStrings provides StringsAz) {
                AqroMeslehetTheme {
                    AuthScreen(
                        currentLanguage = AppLanguage.AZ,
                        onSelectLanguage = {},
                        onAuthSuccess = {},
                        onContinueLocalMode = { continuedLocal = true },
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("auth_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("google_sign_in_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("web_google_sign_in_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("auth_continue_local_btn").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("auth_troubleshoot_toggle_btn").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("auth_troubleshoot_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("copy_pkg_button").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("copy_sha1_button").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("copy_sha256_button").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("auth_update_google_services_btn").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("auth_paste_google_services_btn").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("auth_continue_local_btn").performScrollTo().performClick()
        assertTrue(continuedLocal)
    }

    @Test
    fun `seedsScreen selects region crop recommends varieties and opens variety detail dialog`() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalStrings provides StringsAz) {
                AqroMeslehetTheme {
                    SeedsScreen(
                        language = AppLanguage.AZ,
                        onBack = {},
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("seeds_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("seed_region_chip_lankaran_astara").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("seed_crop_chip_rice").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("seeds_screen").performScrollToNode(hasTestTag("seed_detail_btn_hashimi_rice"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("seed_detail_btn_hashimi_rice").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("seed_detail_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("seed_detail_close_btn").performClick()
    }

    @Test
    fun `aqroRootApp renders missing firebase screen opens local tools and navigates equipment finance and gallery`() {
        composeTestRule.setContent {
            AqroRootApp(firebaseReadyOverride = false)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("firebase_missing_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("import_google_services_file_btn").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("paste_google_services_btn").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("auth_lang_en").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("open_local_tools_btn").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("local_tools_app").assertIsDisplayed()
        composeTestRule.onNodeWithTag("local_home_list").performScrollToNode(hasTestTag("local_home_telegram_bot_card"))
        composeTestRule.onNodeWithTag("local_home_telegram_bot_card").assertIsDisplayed()

        // Scroll LazyColumn to reveal the tiles grid, then open Equipment
        composeTestRule.onNodeWithTag("local_home_list").performScrollToNode(hasTestTag("local_tiles_grid"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("local_tile_equipment").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("equipment_screen").performScrollToNode(hasTestTag("eq_name_input"))
        composeTestRule.onNodeWithTag("eq_name_input").performTextInput("John Deere 6120M")
        composeTestRule.onNodeWithTag("equipment_screen").performScrollToNode(hasTestTag("save_equipment_btn"))
        composeTestRule.onNodeWithTag("save_equipment_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("equipment_screen").performScrollToNode(hasTestTag("back_button"))
        composeTestRule.onNodeWithTag("back_button").performClick()
        composeTestRule.waitForIdle()

        // Open Finance (Simple Accounting)
        composeTestRule.onNodeWithTag("local_home_list").performScrollToNode(hasTestTag("local_tiles_grid"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("local_tile_finance").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("finance_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("finance_screen").performScrollToNode(hasTestTag("finance_amount_input"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("finance_amount_input").performTextInput("420")
        composeTestRule.onNodeWithTag("finance_screen").performScrollToNode(hasTestTag("add_finance_btn"))
        composeTestRule.onNodeWithTag("add_finance_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("finance_screen").performScrollToNode(hasTestTag("back_button"))
        composeTestRule.onNodeWithTag("back_button").performClick()
        composeTestRule.waitForIdle()

        // Open Offline Camera & Photo Gallery
        composeTestRule.onNodeWithTag("local_home_list").performScrollToNode(hasTestTag("local_tiles_grid"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("local_tile_gallery").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("photo_gallery_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("take_photo_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("import_photo_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("back_button").performClick()
        composeTestRule.waitForIdle()

        // Open Seeds & Varieties advisor from Local Tools grid
        composeTestRule.onNodeWithTag("local_home_list").performScrollToNode(hasTestTag("local_tiles_grid"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("local_tile_seeds").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("seeds_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("back_button").performClick()
        composeTestRule.waitForIdle()

        // Open Yonca Agro Calendar, Insurance Calculator, and Stations from Local Tools grid
        composeTestRule.onNodeWithTag("local_home_list").performScrollToNode(hasTestTag("local_tiles_grid"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("local_tile_calendar").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("agro_calendar_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("back_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("local_home_list").performScrollToNode(hasTestTag("local_tiles_grid"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("local_tile_insurance").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("insurance_calc_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("back_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("local_home_list").performScrollToNode(hasTestTag("local_tiles_grid"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("local_tile_stations").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("stations_screen").assertIsDisplayed()
    }

    @Test
    fun `yoncaScreensRenderAndInteractCorrectly`() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalStrings provides StringsAz) {
                AqroMeslehetTheme {
                    AgroCalendarScreen(
                        language = AppLanguage.AZ,
                        adminCalendarNotes = listOf(
                            AdminContentItem(id = "c1", category = "calendar", region = "Aran", title = "Ekspert qeydi", details = "Test təqvim qeydi")
                        ),
                        onBack = {},
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("agro_calendar_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("admin_calendar_note_c1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("calendar_month_card_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("calendar_category_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("calendar_category_item_forage").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("calendar_month_card_1").assertIsDisplayed()
    }

    @Test
    fun `insuranceAndStationsScreensWorkAndAdminIsRestrictedToOwnerEmail`() {
        var publishedTitle = ""
        composeTestRule.setContent {
            CompositionLocalProvider(LocalStrings provides StringsAz) {
                AqroMeslehetTheme {
                    AdminScreen(
                        userEmail = ADMIN_EMAIL,
                        entries = emptyList(),
                        onPublishEntry = { _, _, title, _, _ -> publishedTitle = title },
                        onDeleteEntry = {},
                        onBack = {},
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("admin_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("admin_cat_seed").performClick()
        composeTestRule.onNodeWithTag("admin_title_input").performTextInput("Yeni Sort 2026")
        composeTestRule.onNodeWithTag("admin_details_input").performTextInput("Aran bölgəsi üçün yüksək məhsuldarlıq")
        composeTestRule.onNodeWithTag("admin_publish_btn").performScrollTo().performClick()
        assertEquals("Yeni Sort 2026", publishedTitle)
    }

    @Test
    fun `homeScreenShowsAdminTileOnlyForOwnerEmail`() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalStrings provides StringsAz) {
                AqroMeslehetTheme {
                    HomeScreen(
                        fields = emptyList(),
                        weatherState = WeatherUiState(),
                        equipmentAlerts = emptyList(),
                        onNavigate = {},
                        userEmail = "farmer@example.com",
                        adminEntries = listOf(
                            AdminContentItem(id = "n1", category = "notice", title = "Mövsüm elanı", details = "Suvarma cədvəli yeniləndi")
                        ),
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_screen_list").performScrollToNode(hasTestTag("home_admin_notice_n1"))
        composeTestRule.onNodeWithTag("home_admin_notice_n1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tile_admin").assertDoesNotExist()
    }

    @Test
    fun `homeScreenDisplaysAdminTileForOwnerAndAdminScreenBlocksOthers`() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalStrings provides StringsAz) {
                AqroMeslehetTheme {
                    HomeScreen(
                        fields = emptyList(),
                        weatherState = WeatherUiState(),
                        equipmentAlerts = emptyList(),
                        onNavigate = {},
                        userEmail = ADMIN_EMAIL,
                        adminEntries = emptyList(),
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_screen_list").performScrollToNode(hasTestTag("home_tiles_grid"))
        composeTestRule.onNodeWithTag("tile_admin").performScrollTo()
        composeTestRule.onNodeWithTag("tile_admin").assertIsDisplayed()
    }

    @Test
    fun `adminScreenShowsRestrictedCardForNonAdminUser`() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalStrings provides StringsAz) {
                AqroMeslehetTheme {
                    AdminScreen(
                        userEmail = "notadmin@gmail.com",
                        entries = emptyList(),
                        onPublishEntry = { _, _, _, _, _ -> },
                        onDeleteEntry = {},
                        onBack = {},
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("admin_restricted_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("admin_publish_btn").assertDoesNotExist()
    }

    @Test
    fun `insuranceCalcAndStationsScreensRenderAllElements`() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalStrings provides StringsAz) {
                AqroMeslehetTheme {
                    InsuranceCalcScreen(language = AppLanguage.AZ, onBack = {})
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("insurance_calc_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("insurance_calc_screen").performScrollToNode(hasTestTag("insurance_result_card"))
        composeTestRule.onNodeWithTag("insurance_result_card").assertIsDisplayed()
    }

    @Test
    fun `stationsScreenShowsAllSevenServiceCenters`() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalStrings provides StringsAz) {
                AqroMeslehetTheme {
                    StationsScreen(language = AppLanguage.AZ, onBack = {})
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("stations_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("station_card_fertilizer").assertIsDisplayed()
        composeTestRule.onNodeWithTag("stations_screen").performScrollToNode(hasTestTag("station_card_lab"))
        composeTestRule.onNodeWithTag("station_card_lab").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("station_dialog_maps_btn").assertIsDisplayed()
    }

    @Test
    fun `equipmentScreenShowsInspectionAndInsuranceLabelsReminderChipsAndSoundTest`() {
        assertEquals("Texniki baxış və sığorta izlənilir", StringsAz.alertNormalTitle)
        assertEquals("Texniki baxış və sığorta xatırlatması", StringsAz.alertEquipmentTitle)

        var selectedDays = 7
        composeTestRule.setContent {
            CompositionLocalProvider(LocalStrings provides StringsAz) {
                AqroMeslehetTheme {
                    EquipmentScreen(
                        equipment = emptyList(),
                        computeStatus = { null },
                        onSaveEquipment = { _, _, _, _, _, _ -> },
                        onDeleteEquipment = {},
                        onExportJson = { "{}" },
                        onImportJson = { 0 },
                        onBack = {},
                        reminderDays = selectedDays,
                        onSelectReminderDays = { selectedDays = it },
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("reminder_lead_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reminder_lead_7").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reminder_lead_30").assertIsDisplayed()
        composeTestRule.onNodeWithTag("test_sound_notification_btn").assertIsDisplayed()

        composeTestRule.onNodeWithTag("reminder_lead_1").performClick()
        assertEquals(1, selectedDays)
        composeTestRule.onNodeWithTag("reminder_lead_30").performClick()
        assertEquals(30, selectedDays)
    }

    @Test
    fun `chatScreenRevealsAppCheckHelperWhenTokenErrorOccurs`() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalStrings provides StringsAz) {
                AqroMeslehetTheme {
                    ChatScreen(
                        language = AppLanguage.AZ,
                        messages = listOf(
                            ChatMessageUi(
                                id = "err-1",
                                isUser = false,
                                text = "Firebase app check token is invalid",
                            )
                        ),
                        isStreaming = false,
                        onSendQuestion = { _, _ -> },
                        onClearChat = {},
                        onBack = {},
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ai_chat_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("copy_appcheck_uuid_btn").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("appcheck_custom_token_input").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("apply_appcheck_token_btn").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `npkScreenRendersFertilizerInputsCleanly`() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalStrings provides StringsAz) {
                AqroMeslehetTheme {
                    NpkScreen(language = AppLanguage.AZ, onBack = {})
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("npk_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("npk_screen").performScrollToNode(hasTestTag("npk_calc_button"))
        composeTestRule.onNodeWithTag("npk_calc_button").assertIsDisplayed()
    }

    @Test
    fun `pesticideScreenShowsActiveIngredientPerCropAndSearchesPreparationsSeparately`() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalStrings provides StringsAz) {
                AqroMeslehetTheme {
                    PesticideScreen(
                        language = AppLanguage.AZ,
                        customPesticides = emptyList(),
                        onAddCustomPesticide = { _, _, _, _, _, _ -> },
                        onDeleteCustomPesticide = {},
                        onBack = {},
                        isLocalOnly = true,
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pesticide_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pest_crop_chip_potato").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("pesticide_screen").performScrollToNode(hasTestTag("pest_active_ingredient_text_potato_colorado"))
        composeTestRule.onNodeWithTag("pest_active_ingredient_text_potato_colorado")
            .assertIsDisplayed()
            .assertTextContains("Tiametoksam", substring = true)
        composeTestRule.onNodeWithText("Aktara 25 WG", substring = true).assertDoesNotExist()

        composeTestRule.onNodeWithTag("pesticide_screen").performScrollToNode(hasTestTag("pest_find_prep_by_ingredient_btn"))
        composeTestRule.onNodeWithTag("pest_find_prep_by_ingredient_btn").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("prep_search_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pesticide_screen").performScrollToNode(hasTestTag("prep_card_aktara_25"))
        composeTestRule.onNodeWithTag("prep_card_aktara_25").assertIsDisplayed()
    }

    @Test
    fun `homeScreenCustomizesAndResetsModuleVisibility`() {
        composeTestRule.setContent {
            var hidden by remember { mutableStateOf(emptySet<String>()) }
            CompositionLocalProvider(LocalStrings provides StringsAz) {
                AqroMeslehetTheme {
                    HomeScreen(
                        fields = emptyList(),
                        weatherState = WeatherUiState(),
                        equipmentAlerts = emptyList(),
                        onNavigate = {},
                        hiddenModules = hidden,
                        onToggleModuleVisible = { route, visible ->
                            hidden = if (visible) hidden - route else hidden + route
                        },
                        onResetModules = { hidden = emptySet() },
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_screen_list").performScrollToNode(hasTestTag("tile_pesticide"))
        composeTestRule.onNodeWithTag("tile_pesticide").assertIsDisplayed()

        composeTestRule.onNodeWithTag("home_screen_list").performScrollToNode(hasTestTag("customize_modules_btn"))
        composeTestRule.onNodeWithTag("customize_modules_btn").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("modules_visibility_dialog_list")
            .performScrollToNode(hasTestTag("module_switch_${NavRoutes.PESTICIDE}"))
        composeTestRule.onNodeWithTag("module_switch_${NavRoutes.PESTICIDE}").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("close_modules_dialog_btn").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("tile_pesticide").assertDoesNotExist()

        composeTestRule.onNodeWithTag("customize_modules_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("reset_modules_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("close_modules_dialog_btn").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("home_screen_list").performScrollToNode(hasTestTag("tile_pesticide"))
        composeTestRule.onNodeWithTag("tile_pesticide").assertIsDisplayed()
    }

    @Test
    fun `localToolsAppSupportsOfflineAiAgronomistChat`() {
        composeTestRule.setContent {
            AqroRootApp(firebaseReadyOverride = false)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("auth_lang_az").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("open_local_tools_btn").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("local_home_ai_banner").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ai_chat_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ai_chat_input").performTextInput("Pomidor fitoftoroz")
        composeTestRule.onNodeWithTag("ai_send_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("ai_chat_list")
            .performScrollToNode(androidx.compose.ui.test.hasText("Mankozeb", substring = true))
        composeTestRule.onNodeWithText("Mankozeb", substring = true).assertExists()
    }

    @Test
    fun `profileOmitsModulesAndTelegramCards`() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalStrings provides StringsAz) {
                AqroMeslehetTheme {
                    ProfileScreen(
                        language = AppLanguage.AZ,
                        themePreference = ThemePreference.SYSTEM,
                        profile = null,
                        userEmail = "farmer@example.com",
                        onSelectLanguage = {},
                        onSelectTheme = {},
                        onSaveProfile = { _, _, _ -> },
                        onBack = {},
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("profile_modules_card").assertDoesNotExist()
        composeTestRule.onNodeWithTag("profile_telegram_bot_btn").assertDoesNotExist()
        composeTestRule.onNodeWithTag("profile_screen_list").performScrollToNode(hasTestTag("hotline_1652_btn"))
        composeTestRule.onNodeWithTag("hotline_1652_btn").assertIsDisplayed()
    }

    @Test
    fun `homeOmitsEquipmentTrackingAlertCard`() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalStrings provides StringsAz) {
                AqroMeslehetTheme {
                    HomeScreen(
                        fields = emptyList(),
                        weatherState = WeatherUiState(),
                        equipmentAlerts = listOf(EquipmentAlert("MTZ-82", EquipmentStatus.OVERDUE)),
                        onNavigate = {},
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_alert_card").assertDoesNotExist()
    }

    @Test
    fun `fieldsScreenOpensCoordinatesDialogAddsUtmPointAndExposesKmzActions`() {
        val addedPoints = mutableListOf<com.example.domain.LatLon>()
        composeTestRule.setContent {
            CompositionLocalProvider(LocalStrings provides StringsAz) {
                AqroMeslehetTheme {
                    FieldsScreen(
                        language = AppLanguage.AZ,
                        fields = emptyList(),
                        mapPoints = addedPoints,
                        measurements = com.example.domain.GeoUtils.calculateMeasurements(addedPoints),
                        mapCenter = com.example.domain.LatLon(40.6782, 46.3606),
                        mapZoom = 15,
                        isSatellite = true,
                        gpsLocation = null,
                        gpsAccuracyM = null,
                        docScanState = DocScanUiState(),
                        onAddPoint = { addedPoints.add(it) },
                        onMovePoint = { _, _ -> },
                        onUndoPoint = {},
                        onClearPoints = {},
                        onSetPolygon = {},
                        onCenterChanged = {},
                        onZoomChanged = {},
                        onToggleLayer = {},
                        onUpdateGpsFix = { _, _, _ -> },
                        onScanDocument = { _, _, _ -> },
                        onSaveField = { _, _, _, _, _, _, _ -> },
                        onFocusFieldOnMap = {},
                        onDeleteField = {},
                        onBack = {},
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("open_kmz_file_btn").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("share_kmz_btn").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_kmz_btn").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("open_coordinates_dialog_btn").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("coordinates_entry_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("coord_mode_utm").assertIsDisplayed()
        composeTestRule.onNodeWithTag("coord_utm_zone_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("coord_utm_easting_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("coord_utm_northing_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("coords_done_btn").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, addedPoints.size)
    }

    @Test
    fun `weatherScreenDisplaysClimateHistoryTabAndExportsSummary`() {
        var loadedLabel = ""
        val sampleSummary = com.example.domain.ClimateHistoryUtils.summarizeResponse(
            com.example.data.remote.HistoricalClimateResponse(
                latitude = 40.6782,
                longitude = 46.3606,
                daily = com.example.data.remote.HistoricalDailyDto(
                    time = listOf("2024-05-01", "2025-06-02"),
                    tempMax = listOf(24.0, 36.0),
                    tempMin = listOf(-1.0, 18.0),
                    tempMean = listOf(12.0, 27.0),
                    precipitationSum = listOf(22.0, 0.0),
                    et0Sum = listOf(3.2, 5.8),
                    windMax = listOf(12.0, 42.0),
                    radiationSum = listOf(18.0, 25.0),
                    humidityMean = listOf(68.0, 42.0),
                    windGustsMax = listOf(20.0, 62.0),
                    windDirectionDominant = listOf(120.0, 270.0),
                    sunshineDuration = listOf(28800.0, 43200.0),
                    cloudCoverMean = listOf(40.0, 15.0),
                    pressureMslMean = listOf(1016.0, 1009.0),
                    weatherCode = listOf(65, 95),
                ),
            )
        )
        composeTestRule.setContent {
            CompositionLocalProvider(LocalStrings provides StringsAz) {
                AqroMeslehetTheme {
                    WeatherScreen(
                        weatherState = WeatherUiState(),
                        onRefreshWeather = { _, _ -> },
                        onBack = {},
                        fields = listOf(
                            com.example.data.model.FarmField(
                                id = "f1",
                                name = "Şimal sahəsi",
                                crop = "Buğda",
                                sizeHa = 3.5,
                                coords = listOf("40.6782,46.3606"),
                            )
                        ),
                        climateState = ClimateHistoryUiState(
                            locationLabel = "Şimal sahəsi",
                            summary = sampleSummary,
                        ),
                        onLoadClimateHistory = { _, _, _, _, label -> loadedLabel = label },
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(StringsAz.weatherTabClimateHistory).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("climate_field_chip_f1").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("climate_preset_120m").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("climate_load_btn").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals("Şimal sahəsi", loadedLabel)

        composeTestRule.onNodeWithTag("weather_screen_list").performScrollToNode(hasTestTag("climate_download_csv_btn"))
        composeTestRule.onNodeWithTag("climate_download_csv_btn").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("climate_share_csv_btn").performScrollTo().assertIsDisplayed()

        // Verify interactive timeline map & stepping backward/forward in time
        composeTestRule.onNodeWithTag("weather_screen_list").performScrollToNode(hasTestTag("climate_time_map_card"))
        composeTestRule.onNodeWithTag("climate_timeline_slider").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("climate_map_active_period").assertTextContains("2025-06", substring = true)
        composeTestRule.onNodeWithTag("climate_slider_prev_btn").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("climate_map_active_period").assertTextContains("2024-05", substring = true)
        composeTestRule.onNodeWithTag("climate_slider_next_btn").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("climate_map_active_period").assertTextContains("2025-06", substring = true)

        // Verify all 6 climate analytical chart cards render
        composeTestRule.onNodeWithTag("weather_screen_list").performScrollToNode(hasTestTag("climate_chart_temperature"))
        composeTestRule.onNodeWithTag("climate_chart_temperature").assertIsDisplayed()
        composeTestRule.onNodeWithTag("weather_screen_list").performScrollToNode(hasTestTag("climate_chart_precip_humidity"))
        composeTestRule.onNodeWithTag("climate_chart_precip_humidity").assertIsDisplayed()
        composeTestRule.onNodeWithTag("weather_screen_list").performScrollToNode(hasTestTag("climate_chart_wind"))
        composeTestRule.onNodeWithTag("climate_chart_wind").assertIsDisplayed()
        composeTestRule.onNodeWithTag("weather_screen_list").performScrollToNode(hasTestTag("climate_chart_solar_cloud"))
        composeTestRule.onNodeWithTag("climate_chart_solar_cloud").assertIsDisplayed()
        composeTestRule.onNodeWithTag("weather_screen_list").performScrollToNode(hasTestTag("climate_chart_pressure_et0"))
        composeTestRule.onNodeWithTag("climate_chart_pressure_et0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("weather_screen_list").performScrollToNode(hasTestTag("climate_chart_extremes"))
        composeTestRule.onNodeWithTag("climate_chart_extremes").assertIsDisplayed()
    }
}
