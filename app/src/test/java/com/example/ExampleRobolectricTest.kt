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
import com.example.i18n.AppLanguage
import com.example.i18n.StringsAz
import com.example.i18n.StringsEn
import com.example.i18n.StringsRu
import com.example.ui.screens.AuthScreen
import com.example.ui.theme.AqroMeslehetTheme
import androidx.compose.runtime.CompositionLocalProvider
import com.example.i18n.LocalStrings
import org.junit.Assert.assertEquals
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
    fun `authScreen shows sign in, local mode button, and expandable sha1 troubleshooting card`() {
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
        composeTestRule.onNodeWithTag("auth_continue_local_btn").assertIsDisplayed()

        composeTestRule.onNodeWithTag("auth_troubleshoot_toggle_btn").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("auth_troubleshoot_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("copy_sha1_button").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("auth_update_google_services_btn").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("auth_continue_local_btn").performScrollTo().performClick()
        assertTrue(continuedLocal)
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
        composeTestRule.onNodeWithTag("eq_name_input").performTextInput("John Deere 6120M")
        composeTestRule.onNodeWithTag("save_equipment_btn").performScrollTo().performClick()
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
    }
}
