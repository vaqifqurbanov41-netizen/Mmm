package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.i18n.StringsAz
import com.example.i18n.StringsEn
import com.example.i18n.StringsRu
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
    fun `aqroRootApp renders missing firebase setup screen and opens local tools mode cleanly`() {
        composeTestRule.setContent {
            AqroRootApp(firebaseReadyOverride = false)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("firebase_missing_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("auth_lang_en").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("firebase_missing_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("open_local_tools_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("local_tools_app").assertIsDisplayed()
        composeTestRule.onNodeWithTag("local_home_telegram_bot_card").assertIsDisplayed()
    }
}
