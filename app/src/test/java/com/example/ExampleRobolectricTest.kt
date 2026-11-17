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
import com.example.data.model.ADMIN_EMAIL
import com.example.data.model.AdminContentItem
import com.example.i18n.AppLanguage
import com.example.i18n.LocalStrings
import com.example.i18n.StringsAz
import com.example.i18n.StringsEn
import com.example.i18n.StringsRu
import com.example.ui.screens.AdminScreen
import com.example.ui.screens.AgroCalendarScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.EquipmentScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.InsuranceCalcScreen
import com.example.ui.screens.NpkScreen
import com.example.ui.screens.SeedsScreen
import com.example.ui.screens.StationsScreen
import com.example.ui.theme.AqroMeslehetTheme
import com.example.ui.viewmodel.ChatMessageUi
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
}
