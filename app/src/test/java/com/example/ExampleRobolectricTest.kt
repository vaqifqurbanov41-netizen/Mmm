package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.i18n.StringsAz
import com.example.i18n.StringsEn
import com.example.i18n.StringsRu
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

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
    }
}
