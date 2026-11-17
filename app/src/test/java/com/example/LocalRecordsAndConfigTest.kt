package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.firebase.getRuntimeWebClientId
import com.example.data.firebase.getSigningSha1
import com.example.data.firebase.getSigningSha256
import com.example.data.firebase.inspectFirebaseJson
import com.example.data.firebase.sanitizeGoogleServicesJson
import com.example.data.firebase.saveAndInitializeFirebaseFromJson
import com.example.data.local.LocalRecordsStore
import com.example.ui.auth.isShaOrCertMismatchError
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

        val sha1Plain = getSigningSha1(context).replace(":", "").lowercase()
        assertTrue(getSigningSha256(context).contains(":"))
        assertTrue(isShaOrCertMismatchError("[ERROR_INVALID_CERT_HASH] There was an error while trying to get your package certificate hash."))
        assertTrue(isShaOrCertMismatchError("[16] Account reauth failed."))

        val sampleJson = """
            ${'\uFEFF'}{
              “project_info”: {
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
                      "client_id": "1234567890-android.apps.googleusercontent.com",
                      "client_type": 1,
                      "android_info": {
                        "package_name": "${context.packageName}",
                        "certificate_hash": "$sha1Plain"
                      }
                    },
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
        val diag = inspectFirebaseJson(context, sampleJson)
        assertNotNull(diag)
        assertTrue(diag!!.hasMatchingSha1)
        assertTrue(diag.hasWebClientId)
        assertTrue(diag.hasMatchingPackage)
        assertFalse(sanitizeGoogleServicesJson(sampleJson).contains("\u0000"))

        val result = saveAndInitializeFirebaseFromJson(context, sampleJson)
        assertTrue(result.isSuccess)
        assertEquals("test-agro-project", result.getOrNull())
        assertEquals("1234567890-web.apps.googleusercontent.com", getRuntimeWebClientId(context))
        assertFalse(getRuntimeWebClientId(context).isNullOrBlank())
    }

    @Test
    fun seedCatalogProvidesAdaptedVarietiesForEveryAzerbaijanRegion() {
        val regions = com.example.data.model.SeedCatalog.regions
        assertEquals(6, regions.size)
        regions.forEach { region ->
            val crops = com.example.data.model.SeedCatalog.cropsForRegion(region.id)
            assertTrue("Expected crops for ${region.id}", crops.isNotEmpty())
            crops.forEach { crop ->
                val list = com.example.data.model.SeedCatalog.varietiesFor(region.id, crop.id)
                assertTrue("Expected varieties for ${region.id} / ${crop.id}", list.isNotEmpty())
            }
        }
    }

    @Test
    fun adminEmailGateAndAgroCalendarCatalogWorkAsExpected() {
        assertTrue(com.example.data.model.isAdminEmail("vaqifqurbanov494@gmail.com"))
        assertTrue(com.example.data.model.isAdminEmail(" VaqifQurbanov494@GMAIL.com "))
        assertFalse(com.example.data.model.isAdminEmail("other@gmail.com"))
        assertFalse(com.example.data.model.isAdminEmail(null))
        assertFalse(com.example.data.model.isAdminEmail(""))

        val categories = com.example.data.model.AgroCalendarCatalog.categories
        assertEquals(5, categories.size)
        categories.forEach { cat ->
            val crops = com.example.data.model.AgroCalendarCatalog.cropsForCategory(cat.id)
            assertTrue("Expected crops for category ${cat.id}", crops.isNotEmpty())
            crops.forEach { crop ->
                assertEquals("Expected 12 months for ${crop.id}", 12, crop.months.size)
                crop.months.forEachIndexed { idx, month ->
                    assertEquals(idx + 1, month.monthNumber)
                    assertTrue(month.tasksAz.isNotEmpty())
                    assertTrue(month.tasksEn.isNotEmpty())
                    assertTrue(month.tasksRu.isNotEmpty())
                }
            }
        }
    }

    @Test
    fun equipmentStatusAndAlertsRespectWarningLeadDaysAndNormalizePreferences() {
        assertEquals(1, com.example.data.local.normalizeReminderDays(1))
        assertEquals(7, com.example.data.local.normalizeReminderDays(7))
        assertEquals(30, com.example.data.local.normalizeReminderDays(30))
        assertEquals(7, com.example.data.local.normalizeReminderDays(14))
        assertEquals(7, com.example.data.local.normalizeReminderDays(null))

        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val cal5 = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, 5) }
        val inFiveDays = fmt.format(cal5.time)

        val cal20 = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, 20) }
        val inTwentyDays = fmt.format(cal20.time)

        assertEquals(
            com.example.ui.viewmodel.EquipmentStatus.VALID,
            com.example.data.local.evaluateEquipmentStatus(inFiveDays, warningDays = 1),
        )
        assertEquals(
            com.example.ui.viewmodel.EquipmentStatus.SOON,
            com.example.data.local.evaluateEquipmentStatus(inFiveDays, warningDays = 7),
        )
        assertEquals(
            com.example.ui.viewmodel.EquipmentStatus.VALID,
            com.example.data.local.evaluateEquipmentStatus(inTwentyDays, warningDays = 7),
        )
        assertEquals(
            com.example.ui.viewmodel.EquipmentStatus.SOON,
            com.example.data.local.evaluateEquipmentStatus(inTwentyDays, warningDays = 30),
        )

        val eq = com.example.data.model.EquipmentItem(
            id = "eq-1",
            name = "MTZ-82",
            inspectionDate = inFiveDays,
            insuranceDate = inTwentyDays,
        )
        assertTrue(com.example.data.local.collectEquipmentAlerts(listOf(eq), warningDays = 1).isEmpty())
        assertEquals(1, com.example.data.local.collectEquipmentAlerts(listOf(eq), warningDays = 7).size)
        assertEquals(1, com.example.data.local.collectEquipmentAlerts(listOf(eq), warningDays = 30).size)
    }

    @Test
    fun appCheckHelpersDetectErrorsAndManageCustomDebugToken() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(com.example.data.firebase.isAppCheckTokenError("Firebase app check token is invalid."))
        assertTrue(com.example.data.firebase.isAppCheckTokenError("AppCheck attestation failed"))
        assertFalse(com.example.data.firebase.isAppCheckTokenError("Network timeout"))

        com.example.data.firebase.clearCustomAppCheckDebugToken(context)
        val uuid = com.example.data.firebase.getOrCreateAppCheckDebugUuid(context)
        assertTrue(uuid.length >= 32)
        assertEquals(uuid, com.example.data.firebase.getOrCreateAppCheckDebugUuid(context))

        com.example.data.firebase.applyCustomAppCheckDebugToken(context, "11111111-2222-3333-4444-555555555555")
        assertEquals(
            "11111111-2222-3333-4444-555555555555",
            com.example.data.firebase.getCustomAppCheckDebugToken(context),
        )
        com.example.data.firebase.clearCustomAppCheckDebugToken(context)
        assertEquals(null, com.example.data.firebase.getCustomAppCheckDebugToken(context))
    }

    @Test
    fun cropProtectionCatalogFiltersByCropExcludesTradeNamesAndSupportsOfflineAi() {
        val tradeNames = listOf(
            "Ridomil", "Mospilan", "Score", "Aktara", "Karate",
            "Chorus", "Amistar", "Confidor", "Proclaim", "Decis",
            "Tilt", "Topas", "Vertimec", "Envidor", "Roundup",
        )
        com.example.data.model.CropProtectionCatalog.crops.forEach { crop ->
            val cropThreats = com.example.data.model.CropProtectionCatalog.threatsForCrop(crop.id)
            assertTrue("Expected threats for crop ${crop.id}", cropThreats.isNotEmpty())
            assertTrue("Expected only ${crop.id} threats", cropThreats.all { it.cropId == crop.id })
            cropThreats.forEach { threat ->
                val ingredients = "${threat.activeIngredient.az} ${threat.activeIngredient.en} ${threat.activeIngredient.ru}"
                tradeNames.forEach { trade ->
                    assertFalse(
                        "Threat ${threat.id} activeIngredient must not leak commercial trade name $trade",
                        ingredients.contains(trade, ignoreCase = true),
                    )
                }
            }
        }

        val mancozebPreps = com.example.data.model.CropProtectionCatalog.searchPreparations("Mankozeb")
        assertTrue(mancozebPreps.any { it.tradeName.contains("Ridomil", ignoreCase = true) })
        val acetamipridPreps = com.example.data.model.CropProtectionCatalog.searchPreparations("Asetamiprid")
        assertTrue(acetamipridPreps.any { it.tradeName.contains("Mospilan", ignoreCase = true) })

        val azAnswer = com.example.data.ai.OfflineAgronomistAdvisor.answerOffline(
            question = "Pomidor yarpaqlarında fitoftoroz var, nə etməliyəm?",
            language = com.example.i18n.AppLanguage.AZ,
            aiFailureNote = "Offline timeout",
        )
        assertTrue(azAnswer.contains("Mankozeb"))
        assertTrue(azAnswer.contains("Offline timeout"))

        val enAnswer = com.example.data.ai.OfflineAgronomistAdvisor.answerOffline(
            question = "Colorado potato beetle damage on potato",
            language = com.example.i18n.AppLanguage.EN,
        )
        assertTrue(enAnswer.contains("Thiamethoxam"))

        val ruAnswer = com.example.data.ai.OfflineAgronomistAdvisor.answerOffline(
            question = "Ржавчина на пшенице",
            language = com.example.i18n.AppLanguage.RU,
        )
        assertTrue(ruAnswer.contains("Пропиконазол"))

        val bannedAnswer = com.example.data.ai.OfflineAgronomistAdvisor.answerOffline(
            question = "DDT istifadə edə bilərəm?",
            language = com.example.i18n.AppLanguage.AZ,
        )
        assertTrue(bannedAnswer.contains("Qadağan", ignoreCase = true) || bannedAnswer.contains("DDT", ignoreCase = true))
    }
}


