package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.firebase.getRuntimeWebClientId
import com.example.data.firebase.saveAndInitializeFirebaseFromJson
import com.example.data.local.LocalRecordsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalRecordsAndConfigTest {

    @Test
    fun localRecordsStorePersistsEquipmentFinanceBackupAndOfflinePhotos() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = LocalRecordsStore(context)

        store.saveEquipment(null, "Belarus Traktor", "MTZ-82", "90-AA-100", "2026-12-30", "2026-12-31")
        assertEquals(1, store.equipment.value.size)
        val eq = store.equipment.value.first()
        assertEquals("Belarus Traktor", eq.name)

        store.addFinance("income", "2026-11-16", "Şimal sahəsi", 1250.0, "Məhsul satışı")
        store.addFinance("expense", "2026-11-16", "Şimal sahəsi", 250.0, "Gübrə")
        assertEquals(2, store.finance.value.size)

        val backup = store.exportBackupJson()
        assertTrue(backup.contains("Belarus Traktor"))
        assertTrue(backup.contains("Məhsul satışı"))

        store.deleteEquipment(eq.id)
        assertTrue(store.equipment.value.isEmpty())
        val restored = store.importBackupJson(backup)
        assertEquals(3, restored)
        assertEquals(1, store.equipment.value.size)

        val (uri, file) = store.createCameraPhotoTarget()
        assertNotNull(uri)
        file.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()))
        assertTrue(store.finalizeCapturedPhoto(file, "Yarpaq yoxlanışı"))
        assertEquals(1, store.photos.value.size)
        assertEquals("Yarpaq yoxlanışı", store.photos.value.first().caption)

        val shareUri = store.getShareUri(store.photos.value.first().file)
        assertNotNull(shareUri)

        store.updatePhotoCaption(file.name, "Yenilənmiş qeyd")
        assertEquals("Yenilənmiş qeyd", store.photos.value.first().caption)

        store.deletePhoto(file.name)
        assertTrue(store.photos.value.isEmpty())
    }

    @Test
    fun runtimeFirebaseJsonValidationRejectsInvalidAndSavesValidConfig() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(saveAndInitializeFirebaseFromJson(context, "not-valid-json").isFailure)

        val sampleJson = """
            {
              "project_info": {
                "project_number": "1234567890",
                "project_id": "test-agro-project",
                "storage_bucket": "test-agro-project.appspot.com"
              },
              "client": [
                {
                  "client_info": {
                    "mobilesdk_app_id": "1:1234567890:android:abcdef123456",
                    "android_client_info": {
                      "package_name": "${context.packageName}"
                    }
                  },
                  "oauth_client": [
                    {
                      "client_id": "1234567890-web.apps.googleusercontent.com",
                      "client_type": 3
                    }
                  ],
                  "api_key": [
                    {
                      "current_key": "fake-unit-test-key-value"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
        val result = saveAndInitializeFirebaseFromJson(context, sampleJson)
        assertTrue(result.isSuccess)
        assertEquals("test-agro-project", result.getOrNull())
        assertEquals("1234567890-web.apps.googleusercontent.com", getRuntimeWebClientId(context))
        assertFalse(getRuntimeWebClientId(context).isNullOrBlank())
    }
}
