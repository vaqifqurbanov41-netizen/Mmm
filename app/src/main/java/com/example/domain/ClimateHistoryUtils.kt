package com.example.domain

import com.example.data.model.FarmField
import com.example.data.remote.HistoricalClimateResponse
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class ClimateRegionPreset(
    val id: String,
    val nameAz: String,
    val nameEn: String,
    val nameRu: String,
    val lat: Double,
    val lon: Double,
)

enum class ClimateExtremeType {
    FROST,
    HEATWAVE,
    HEAVY_RAIN,
    SEVERE_WIND,
    STORM_HAIL,
}

data class ClimateExtremeEvent(
    val date: String,
    val type: ClimateExtremeType,
    val metricValue: Double,
    val weatherCode: Int = 0,
)

data class ClimateDailyRecord(
    val date: String,
    val tempMax: Double,
    val tempMin: Double,
    val tempMean: Double,
    val precipitationMm: Double,
    val et0Mm: Double,
    val windMaxKmh: Double,
    val radiationMjM2: Double,
    val humidityMeanPct: Double = 60.0,
    val rainMm: Double = precipitationMm,
    val snowfallCm: Double = 0.0,
    val windGustsMaxKmh: Double = windMaxKmh * 1.35,
    val windDirectionDeg: Double = 0.0,
    val sunshineHours: Double = 0.0,
    val cloudCoverPct: Double = 0.0,
    val pressureHpa: Double = 1013.2,
    val weatherCode: Int = 0,
)

data class ClimateMonthlySummary(
    val yearMonth: String,
    val avgTempMean: Double,
    val minTemp: Double,
    val maxTemp: Double,
    val totalPrecipMm: Double,
    val totalEt0Mm: Double,
    val rainyDays: Int,
    val frostDays: Int,
    val avgTempMin: Double = minTemp,
    val avgTempMax: Double = maxTemp,
    val totalRainMm: Double = totalPrecipMm,
    val totalSnowCm: Double = 0.0,
    val avgHumidityPct: Double = 60.0,
    val avgWindMaxKmh: Double = 0.0,
    val maxGustKmh: Double = 0.0,
    val dominantWindDeg: Double = 0.0,
    val avgRadiationMjM2: Double = 0.0,
    val totalSunshineHours: Double = 0.0,
    val avgCloudCoverPct: Double = 0.0,
    val avgPressureHpa: Double = 1013.2,
    val heatDays: Int = 0,
    val heavyRainDays: Int = 0,
    val highWindDays: Int = 0,
    val stormDays: Int = 0,
)

data class ClimateYearlySummary(
    val year: String,
    val avgTempC: Double,
    val minTempC: Double,
    val maxTempC: Double,
    val totalPrecipMm: Double,
    val avgHumidityPct: Double,
    val totalEt0Mm: Double,
    val avgWindMaxKmh: Double,
    val maxGustKmh: Double,
    val avgRadiationMjM2: Double,
    val avgCloudCoverPct: Double,
    val avgPressureHpa: Double,
    val extremeDaysCount: Int,
)

data class ClimateHistorySummary(
    val daysCount: Int,
    val startDate: String,
    val endDate: String,
    val avgTempC: Double,
    val minTempC: Double,
    val maxTempC: Double,
    val totalPrecipMm: Double,
    val totalEt0Mm: Double,
    val waterBalanceMm: Double,
    val rainyDays: Int,
    val frostDays: Int,
    val hotDays: Int,
    val maxWindKmh: Double,
    val monthlyBreakdown: List<ClimateMonthlySummary>,
    val records: List<ClimateDailyRecord>,
    val monthlyChronological: List<ClimateMonthlySummary> = monthlyBreakdown.sortedBy { it.yearMonth },
    val yearlySummaries: List<ClimateYearlySummary> = emptyList(),
    val avgTempMinC: Double = minTempC,
    val avgTempMaxC: Double = maxTempC,
    val tempTrendDeltaC: Double = 0.0,
    val avgHumidityPct: Double = 60.0,
    val minHumidityPct: Double = 60.0,
    val maxHumidityPct: Double = 60.0,
    val totalRainMm: Double = totalPrecipMm,
    val totalSnowCm: Double = 0.0,
    val maxDailyPrecipMm: Double = 0.0,
    val avgWindMaxKmh: Double = maxWindKmh,
    val maxGustKmh: Double = maxWindKmh,
    val dominantWindDeg: Double = 0.0,
    val avgRadiationMjM2: Double = 0.0,
    val totalSunshineHours: Double = 0.0,
    val avgCloudCoverPct: Double = 0.0,
    val avgPressureHpa: Double = 1013.2,
    val minPressureHpa: Double = 1013.2,
    val maxPressureHpa: Double = 1013.2,
    val heavyRainDays: Int = 0,
    val highWindDays: Int = 0,
    val stormHailDays: Int = 0,
    val extremeEvents: List<ClimateExtremeEvent> = emptyList(),
)

object ClimateHistoryUtils {
    const val MAX_HISTORY_YEARS = 10
    private const val MAX_HISTORY_DAYS = 3665L

    val regionPresets = listOf(
        ClimateRegionPreset("ganja", "Gəncə-Daşkəsən", "Ganja-Dashkasan", "Гянджа-Дашкесан", 40.6782, 46.3606),
        ClimateRegionPreset("aran", "Aran / Sabirabad", "Aran / Sabirabad", "Аран / Сабирабад", 40.0087, 48.4770),
        ClimateRegionPreset("karabakh", "Qarabağ / Bərdə", "Karabakh / Barda", "Карабах / Барда", 40.3744, 47.1267),
        ClimateRegionPreset("quba", "Quba-Xaçmaz", "Quba-Khachmaz", "Губа-Хачмаз", 41.3611, 48.5134),
        ClimateRegionPreset("shaki", "Şəki-Zaqatala", "Shaki-Zagatala", "Шеки-Загатала", 41.1919, 47.1706),
        ClimateRegionPreset("lankaran", "Lənkəran-Astara", "Lankaran-Astara", "Лянкяран-Астара", 38.7543, 48.8506),
        ClimateRegionPreset("shirvan", "Şirvan / Şamaxı", "Shirvan / Shamakhi", "Ширван / Шемахы", 40.6314, 48.6414),
        ClimateRegionPreset("baku", "Bakı / Abşeron", "Baku / Absheron", "Баку / Апшерон", 40.4093, 49.8671),
        ClimateRegionPreset("nakhchivan", "Naxçıvan", "Nakhchivan", "Нахчыван", 39.2089, 45.4122),
    )

    fun maxArchiveEndDate(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -3) }
        return fmt.format(cal.time)
    }

    fun defaultDateRange(monthsBack: Int): Pair<String, String> {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val endCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -3) }
        val boundedMonths = monthsBack.coerceIn(1, MAX_HISTORY_YEARS * 12)
        val startCal = (endCal.clone() as Calendar).apply { add(Calendar.MONTH, -boundedMonths) }
        return fmt.format(startCal.time) to fmt.format(endCal.time)
    }

    fun validateDateRange(startRaw: String, endRaw: String): Pair<String, String>? {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        val start = runCatching { fmt.parse(startRaw.trim()) }.getOrNull() ?: return null
        val end = runCatching { fmt.parse(endRaw.trim()) }.getOrNull() ?: return null
        val maxEnd = runCatching { fmt.parse(maxArchiveEndDate()) }.getOrNull()
        val clampedEnd = if (maxEnd != null && end.after(maxEnd)) maxEnd else end
        if (start.after(clampedEnd)) return null
        val spanDays = TimeUnit.MILLISECONDS.toDays(clampedEnd.time - start.time)
        if (spanDays > MAX_HISTORY_DAYS) return null
        return fmt.format(start) to fmt.format(clampedEnd)
    }

    fun fieldCentroidOrMatch(field: FarmField, fallbackLat: Double, fallbackLon: Double): LatLon {
        val pts = field.coords.mapNotNull { GeoUtils.decodeCoord(it) }
        if (pts.isNotEmpty()) {
            return LatLon(pts.map { it.lat }.average(), pts.map { it.lon }.average())
        }
        val locLower = field.location.lowercase(Locale.US)
        val matched = regionPresets.firstOrNull { preset ->
            locLower.contains(preset.id) ||
                preset.nameAz.lowercase(Locale.US).split("/", "-").any { part ->
                    part.trim().length >= 4 && locLower.contains(part.trim())
                }
        }
        return if (matched != null) LatLon(matched.lat, matched.lon) else LatLon(fallbackLat, fallbackLon)
    }

    fun circularMeanDirection(anglesDeg: List<Double>): Double {
        if (anglesDeg.isEmpty()) return 0.0
        var sumSin = 0.0
        var sumCos = 0.0
        anglesDeg.forEach { deg ->
            val rad = Math.toRadians(deg)
            sumSin += sin(rad)
            sumCos += cos(rad)
        }
        val meanDeg = Math.toDegrees(atan2(sumSin, sumCos))
        return (meanDeg + 360.0) % 360.0
    }

    fun windCompassShort(deg: Double): String {
        val norm = ((deg % 360.0) + 360.0) % 360.0
        val dirs = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val idx = (((norm + 22.5) / 45.0).toInt()) % 8
        return dirs[idx]
    }

    private fun isStormOrHailWmo(code: Int): Boolean =
        code in 95..99 || code in listOf(65, 66, 67, 75, 82, 86)

    fun summarizeResponse(response: HistoricalClimateResponse): ClimateHistorySummary {
        val daily = response.daily ?: return emptySummary()

        val records = daily.time.mapIndexedNotNull { idx, date ->
            val tMax = daily.tempMax.getOrNull(idx)
            val tMin = daily.tempMin.getOrNull(idx)
            val tMean = daily.tempMean.getOrNull(idx)
            if (tMax == null && tMin == null && tMean == null) return@mapIndexedNotNull null
            val resolvedMax = tMax ?: tMean ?: tMin ?: 0.0
            val resolvedMin = tMin ?: tMean ?: tMax ?: 0.0
            val resolvedMean = tMean ?: ((resolvedMax + resolvedMin) / 2.0)
            val precip = daily.precipitationSum.getOrNull(idx) ?: 0.0
            val rain = daily.rainSum.getOrNull(idx) ?: precip
            val snow = daily.snowfallSum.getOrNull(idx) ?: 0.0
            val windMax = daily.windMax.getOrNull(idx) ?: 0.0
            val gustsMax = daily.windGustsMax.getOrNull(idx) ?: (windMax * 1.35)
            val windDir = daily.windDirectionDominant.getOrNull(idx) ?: 0.0
            val sunshineSec = daily.sunshineDuration.getOrNull(idx) ?: 0.0
            val pressure = daily.pressureMslMean.getOrNull(idx) ?: 1013.2
            val humidity = daily.humidityMean.getOrNull(idx) ?: 60.0
            val cloud = daily.cloudCoverMean.getOrNull(idx) ?: 35.0
            val wmo = daily.weatherCode.getOrNull(idx) ?: 0

            ClimateDailyRecord(
                date = date,
                tempMax = resolvedMax,
                tempMin = resolvedMin,
                tempMean = resolvedMean,
                precipitationMm = precip,
                et0Mm = daily.et0Sum.getOrNull(idx) ?: 0.0,
                windMaxKmh = windMax,
                radiationMjM2 = daily.radiationSum.getOrNull(idx) ?: 0.0,
                humidityMeanPct = humidity.coerceIn(0.0, 100.0),
                rainMm = rain,
                snowfallCm = snow,
                windGustsMaxKmh = maxOf(windMax, gustsMax),
                windDirectionDeg = windDir,
                sunshineHours = (sunshineSec / 3600.0).coerceIn(0.0, 24.0),
                cloudCoverPct = cloud.coerceIn(0.0, 100.0),
                pressureHpa = pressure,
                weatherCode = wmo,
            )
        }

        if (records.isEmpty()) return emptySummary()

        val sortedRecords = records.sortedBy { it.date }
        val totalPrecip = sortedRecords.sumOf { it.precipitationMm }
        val totalRain = sortedRecords.sumOf { it.rainMm }
        val totalSnow = sortedRecords.sumOf { it.snowfallCm }
        val totalEt0 = sortedRecords.sumOf { it.et0Mm }

        val half = (sortedRecords.size / 2).coerceAtLeast(1)
        val firstHalfAvg = sortedRecords.take(half).map { it.tempMean }.average()
        val secondHalfAvg = sortedRecords.takeLast(sortedRecords.size - half).takeIf { it.isNotEmpty() }
            ?.map { it.tempMean }?.average() ?: firstHalfAvg
        val trendDelta = if (sortedRecords.size >= 60) secondHalfAvg - firstHalfAvg else 0.0

        val monthlyChronological = sortedRecords.groupBy { it.date.take(7) }
            .entries
            .sortedBy { it.key }
            .map { (ym, rows) ->
                ClimateMonthlySummary(
                    yearMonth = ym,
                    avgTempMean = rows.map { it.tempMean }.average(),
                    minTemp = rows.minOf { it.tempMin },
                    maxTemp = rows.maxOf { it.tempMax },
                    totalPrecipMm = rows.sumOf { it.precipitationMm },
                    totalEt0Mm = rows.sumOf { it.et0Mm },
                    rainyDays = rows.count { it.precipitationMm >= 1.0 },
                    frostDays = rows.count { it.tempMin <= 0.0 },
                    avgTempMin = rows.map { it.tempMin }.average(),
                    avgTempMax = rows.map { it.tempMax }.average(),
                    totalRainMm = rows.sumOf { it.rainMm },
                    totalSnowCm = rows.sumOf { it.snowfallCm },
                    avgHumidityPct = rows.map { it.humidityMeanPct }.average(),
                    avgWindMaxKmh = rows.map { it.windMaxKmh }.average(),
                    maxGustKmh = rows.maxOf { it.windGustsMaxKmh },
                    dominantWindDeg = circularMeanDirection(rows.map { it.windDirectionDeg }),
                    avgRadiationMjM2 = rows.map { it.radiationMjM2 }.average(),
                    totalSunshineHours = rows.sumOf { it.sunshineHours },
                    avgCloudCoverPct = rows.map { it.cloudCoverPct }.average(),
                    avgPressureHpa = rows.map { it.pressureHpa }.average(),
                    heatDays = rows.count { it.tempMax >= 35.0 },
                    heavyRainDays = rows.count { it.precipitationMm >= 20.0 },
                    highWindDays = rows.count { it.windMaxKmh >= 40.0 || it.windGustsMaxKmh >= 55.0 },
                    stormDays = rows.count { isStormOrHailWmo(it.weatherCode) || it.snowfallCm >= 5.0 },
                )
            }
        val monthlyBreakdown = monthlyChronological.reversed()

        val yearlySummaries = sortedRecords.groupBy { it.date.take(4) }
            .entries
            .sortedBy { it.key }
            .map { (yr, rows) ->
                ClimateYearlySummary(
                    year = yr,
                    avgTempC = rows.map { it.tempMean }.average(),
                    minTempC = rows.minOf { it.tempMin },
                    maxTempC = rows.maxOf { it.tempMax },
                    totalPrecipMm = rows.sumOf { it.precipitationMm },
                    avgHumidityPct = rows.map { it.humidityMeanPct }.average(),
                    totalEt0Mm = rows.sumOf { it.et0Mm },
                    avgWindMaxKmh = rows.map { it.windMaxKmh }.average(),
                    maxGustKmh = rows.maxOf { it.windGustsMaxKmh },
                    avgRadiationMjM2 = rows.map { it.radiationMjM2 }.average(),
                    avgCloudCoverPct = rows.map { it.cloudCoverPct }.average(),
                    avgPressureHpa = rows.map { it.pressureHpa }.average(),
                    extremeDaysCount = rows.count { rec ->
                        rec.tempMin <= 0.0 ||
                            rec.tempMax >= 35.0 ||
                            rec.precipitationMm >= 20.0 ||
                            rec.windMaxKmh >= 40.0 ||
                            rec.windGustsMaxKmh >= 55.0 ||
                            isStormOrHailWmo(rec.weatherCode)
                    },
                )
            }

        val extremeEvents = buildList {
            sortedRecords.forEach { rec ->
                when {
                    isStormOrHailWmo(rec.weatherCode) ->
                        add(ClimateExtremeEvent(rec.date, ClimateExtremeType.STORM_HAIL, rec.precipitationMm, rec.weatherCode))
                    rec.precipitationMm >= 20.0 ->
                        add(ClimateExtremeEvent(rec.date, ClimateExtremeType.HEAVY_RAIN, rec.precipitationMm, rec.weatherCode))
                    rec.windGustsMaxKmh >= 55.0 || rec.windMaxKmh >= 40.0 ->
                        add(ClimateExtremeEvent(rec.date, ClimateExtremeType.SEVERE_WIND, rec.windGustsMaxKmh, rec.weatherCode))
                    rec.tempMax >= 35.0 ->
                        add(ClimateExtremeEvent(rec.date, ClimateExtremeType.HEATWAVE, rec.tempMax, rec.weatherCode))
                    rec.tempMin <= 0.0 ->
                        add(ClimateExtremeEvent(rec.date, ClimateExtremeType.FROST, rec.tempMin, rec.weatherCode))
                }
            }
        }
            .sortedWith(
                compareBy<ClimateExtremeEvent> {
                    when (it.type) {
                        ClimateExtremeType.STORM_HAIL -> 0
                        ClimateExtremeType.HEAVY_RAIN -> 1
                        ClimateExtremeType.SEVERE_WIND -> 2
                        ClimateExtremeType.HEATWAVE -> 3
                        ClimateExtremeType.FROST -> 4
                    }
                }.thenByDescending { kotlin.math.abs(it.metricValue) }
            )
            .take(15)

        return ClimateHistorySummary(
            daysCount = sortedRecords.size,
            startDate = sortedRecords.first().date,
            endDate = sortedRecords.last().date,
            avgTempC = sortedRecords.map { it.tempMean }.average(),
            minTempC = sortedRecords.minOf { it.tempMin },
            maxTempC = sortedRecords.maxOf { it.tempMax },
            totalPrecipMm = totalPrecip,
            totalEt0Mm = totalEt0,
            waterBalanceMm = totalPrecip - totalEt0,
            rainyDays = sortedRecords.count { it.precipitationMm >= 1.0 },
            frostDays = sortedRecords.count { it.tempMin <= 0.0 },
            hotDays = sortedRecords.count { it.tempMax >= 35.0 },
            maxWindKmh = sortedRecords.maxOf { it.windMaxKmh },
            monthlyBreakdown = monthlyBreakdown,
            records = sortedRecords,
            monthlyChronological = monthlyChronological,
            yearlySummaries = yearlySummaries,
            avgTempMinC = sortedRecords.map { it.tempMin }.average(),
            avgTempMaxC = sortedRecords.map { it.tempMax }.average(),
            tempTrendDeltaC = trendDelta,
            avgHumidityPct = sortedRecords.map { it.humidityMeanPct }.average(),
            minHumidityPct = sortedRecords.minOf { it.humidityMeanPct },
            maxHumidityPct = sortedRecords.maxOf { it.humidityMeanPct },
            totalRainMm = totalRain,
            totalSnowCm = totalSnow,
            maxDailyPrecipMm = sortedRecords.maxOf { it.precipitationMm },
            avgWindMaxKmh = sortedRecords.map { it.windMaxKmh }.average(),
            maxGustKmh = sortedRecords.maxOf { it.windGustsMaxKmh },
            dominantWindDeg = circularMeanDirection(sortedRecords.map { it.windDirectionDeg }),
            avgRadiationMjM2 = sortedRecords.map { it.radiationMjM2 }.average(),
            totalSunshineHours = sortedRecords.sumOf { it.sunshineHours },
            avgCloudCoverPct = sortedRecords.map { it.cloudCoverPct }.average(),
            avgPressureHpa = sortedRecords.map { it.pressureHpa }.average(),
            minPressureHpa = sortedRecords.minOf { it.pressureHpa },
            maxPressureHpa = sortedRecords.maxOf { it.pressureHpa },
            heavyRainDays = sortedRecords.count { it.precipitationMm >= 20.0 },
            highWindDays = sortedRecords.count { it.windMaxKmh >= 40.0 || it.windGustsMaxKmh >= 55.0 },
            stormHailDays = sortedRecords.count { isStormOrHailWmo(it.weatherCode) || it.snowfallCm >= 5.0 },
            extremeEvents = extremeEvents,
        )
    }

    private fun emptySummary() = ClimateHistorySummary(
        daysCount = 0,
        startDate = "",
        endDate = "",
        avgTempC = 0.0,
        minTempC = 0.0,
        maxTempC = 0.0,
        totalPrecipMm = 0.0,
        totalEt0Mm = 0.0,
        waterBalanceMm = 0.0,
        rainyDays = 0,
        frostDays = 0,
        hotDays = 0,
        maxWindKmh = 0.0,
        monthlyBreakdown = emptyList(),
        records = emptyList(),
    )

    fun buildClimateCsv(
        locationLabel: String,
        lat: Double,
        lon: Double,
        summary: ClimateHistorySummary,
    ): String {
        val sb = StringBuilder("\uFEFF")
        val cleanLoc = locationLabel.replace(",", " ").ifBlank { "Location" }
        sb.append("# AqroMeslehet Historical Climate Report,$cleanLoc,")
        sb.append(String.format(Locale.US, "%.4f,%.4f,%s,%s\n", lat, lon, summary.startDate, summary.endDate))
        sb.append("Date,TempMin_C,TempMax_C,TempMean_C,Humidity_Pct,Precipitation_mm,Rain_mm,Snowfall_cm,WindMax_kmh,WindGusts_kmh,WindDir_deg,SolarRadiation_MJ_m2,Sunshine_h,CloudCover_Pct,Pressure_hPa,ET0_mm,WeatherCode\n")
        summary.records.forEach { rec ->
            sb.append(
                String.format(
                    Locale.US,
                    "%s,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.0f,%.2f,%.2f,%.1f,%.1f,%.2f,%d\n",
                    rec.date,
                    rec.tempMin,
                    rec.tempMax,
                    rec.tempMean,
                    rec.humidityMeanPct,
                    rec.precipitationMm,
                    rec.rainMm,
                    rec.snowfallCm,
                    rec.windMaxKmh,
                    rec.windGustsMaxKmh,
                    rec.windDirectionDeg,
                    rec.radiationMjM2,
                    rec.sunshineHours,
                    rec.cloudCoverPct,
                    rec.pressureHpa,
                    rec.et0Mm,
                    rec.weatherCode,
                )
            )
        }
        return sb.toString()
    }
}
