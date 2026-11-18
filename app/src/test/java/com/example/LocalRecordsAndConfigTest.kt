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
import androidx.core.content.FileProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalRecordsAndConfigTest {

    @Before
    fun resetFileProviderCache() {
        // Each Robolectric @Test creates a fresh temporary dataDir, while FileProvider caches
        // resolved root paths in a static sCache map keyed by authority.
        runCatching {
            val cacheField = FileProvider::class.java.getDeclaredField("sCache")
            cacheField.isAccessible = true
            (cacheField.get(null) as? MutableMap<*, *>)?.clear()
        }
    }

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

    @Test
    fun kmzRoundTripAndGeoUtilsUtmDdmConversionsWorkAccurately() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pts = listOf(
            com.example.domain.LatLon(40.6780, 46.3600),
            com.example.domain.LatLon(40.6795, 46.3600),
            com.example.domain.LatLon(40.6795, 46.3620),
            com.example.domain.LatLon(40.6780, 46.3620),
        )
        val kmzBytes = com.example.domain.KmzUtils.buildKmzBytes(
            documentName = "Ganja_Test",
            placemarks = listOf(
                com.example.domain.KmzPlacemark(name = "Bugda 1", description = "2.8 ha", points = pts)
            ),
        )
        assertTrue(kmzBytes.size > 50)
        val parsed = com.example.domain.KmzUtils.parseKmzOrKml(kmzBytes)
        assertEquals("Ganja_Test", parsed.documentName)
        assertEquals(1, parsed.placemarks.size)
        assertEquals("Bugda 1", parsed.placemarks.first().name)
        assertEquals(4, parsed.placemarks.first().points.size)
        assertEquals(40.6780, parsed.placemarks.first().points.first().lat, 1e-4)
        assertEquals(46.3600, parsed.placemarks.first().points.first().lon, 1e-4)

        val (kmzFile, shareUri) = com.example.domain.KmzUtils.writeKmzToCacheFile(
            context = context,
            fileNameBase = "Bugda 1",
            placemarks = parsed.placemarks,
        )
        assertTrue(kmzFile.exists())
        assertNotNull(shareUri)

        // UTM Zone string parsing (Azerbaijan 38T / 38S / Europe 34U / South 38K)
        assertEquals(38 to false, com.example.domain.GeoUtils.parseUtmZone("38T"))
        assertEquals(38 to false, com.example.domain.GeoUtils.parseUtmZone("38S"))
        assertEquals(34 to false, com.example.domain.GeoUtils.parseUtmZone("34U"))
        assertEquals(38 to true, com.example.domain.GeoUtils.parseUtmZone("38K"))

        val utm = com.example.domain.GeoUtils.latLonToUtm(40.6782, 46.3606)
        assertEquals("38T", com.example.domain.GeoUtils.formatUtmZone(40.6782, utm.zone))
        val back = com.example.domain.GeoUtils.utmToLatLon(utm.easting, utm.northing, "38T")
        assertEquals(40.6782, back.lat, 0.001)
        assertEquals(46.3606, back.lon, 0.001)

        // DDM conversion round-trip
        val (deg, min, pos) = com.example.domain.GeoUtils.decimalToDdm(40.6782)
        assertTrue(pos)
        assertEquals(40, deg)
        assertEquals(40.6782, com.example.domain.GeoUtils.ddmToDecimal(deg.toDouble(), min, !pos), 1e-5)
    }

    @Test
    fun climateHistoryUtilsValidatesRangesSummarizesMonthlyMetricsAndBuildsCsv() {
        val (start2y, end2y) = com.example.domain.ClimateHistoryUtils.defaultDateRange(24)
        assertNotNull(com.example.domain.ClimateHistoryUtils.validateDateRange(start2y, end2y))
        val (start10y, end10y) = com.example.domain.ClimateHistoryUtils.defaultDateRange(120)
        assertNotNull(com.example.domain.ClimateHistoryUtils.validateDateRange(start10y, end10y))
        assertEquals(null, com.example.domain.ClimateHistoryUtils.validateDateRange("2010-01-01", "2026-01-01"))
        assertEquals(null, com.example.domain.ClimateHistoryUtils.validateDateRange("2025-05-01", "2024-05-01"))
        assertEquals(null, com.example.domain.ClimateHistoryUtils.validateDateRange("invalid", end2y))

        val mockResponse = com.example.data.remote.HistoricalClimateResponse(
            latitude = 40.6782,
            longitude = 46.3606,
            elevation = 412.0,
            daily = com.example.data.remote.HistoricalDailyDto(
                time = listOf("2024-01-15", "2024-01-16", "2024-07-20"),
                tempMax = listOf(4.0, 8.0, 37.5),
                tempMin = listOf(-2.0, 1.0, 22.0),
                tempMean = listOf(1.0, 4.5, 30.0),
                precipitationSum = listOf(22.4, 0.0, 2.6),
                et0Sum = listOf(1.1, 1.4, 6.5),
                windMax = listOf(14.0, 10.0, 42.0),
                radiationSum = listOf(6.2, 7.1, 24.5),
                humidityMean = listOf(84.0, 72.0, 40.0),
                rainSum = listOf(18.0, 0.0, 2.6),
                snowfallSum = listOf(4.4, 0.0, 0.0),
                windGustsMax = listOf(26.0, 18.0, 64.0),
                windDirectionDominant = listOf(90.0, 90.0, 90.0),
                sunshineDuration = listOf(7200.0, 14400.0, 39600.0),
                cloudCoverMean = listOf(85.0, 45.0, 10.0),
                pressureMslMean = listOf(1022.0, 1018.0, 1006.0),
                weatherCode = listOf(65, 2, 96),
            ),
        )
        val summary = com.example.domain.ClimateHistoryUtils.summarizeResponse(mockResponse)
        assertEquals(3, summary.daysCount)
        assertEquals(25.0, summary.totalPrecipMm, 1e-3)
        assertEquals(9.0, summary.totalEt0Mm, 1e-3)
        assertEquals(16.0, summary.waterBalanceMm, 1e-3)
        assertEquals(1, summary.frostDays)
        assertEquals(1, summary.hotDays)
        assertEquals(1, summary.heavyRainDays)
        assertEquals(1, summary.highWindDays)
        assertEquals(2, summary.stormHailDays)
        assertEquals(64.0, summary.maxGustKmh, 1e-3)
        assertEquals(90.0, summary.dominantWindDeg, 1e-1)
        assertEquals("E", com.example.domain.ClimateHistoryUtils.windCompassShort(summary.dominantWindDeg))
        assertEquals(17.0, summary.totalSunshineHours, 1e-2)
        assertEquals(1006.0, summary.minPressureHpa, 1e-2)
        assertEquals(1022.0, summary.maxPressureHpa, 1e-2)
        assertTrue(summary.extremeEvents.isNotEmpty())
        assertEquals(2, summary.monthlyChronological.size)
        assertEquals("2024-01", summary.monthlyChronological.first().yearMonth)
        assertEquals(1, summary.yearlySummaries.size)

        val csv = com.example.domain.ClimateHistoryUtils.buildClimateCsv("Gəncə", 40.6782, 46.3606, summary)
        assertTrue(csv.contains("Date,TempMin_C,TempMax_C,TempMean_C,Humidity_Pct"))
        assertTrue(csv.contains("Pressure_hPa,ET0_mm,WeatherCode"))
        assertTrue(csv.contains("2024-07-20"))
        assertTrue(csv.contains("1006.0"))
    }
}

