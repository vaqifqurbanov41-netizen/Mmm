package com.example.domain

import com.example.data.model.FarmField
import com.example.data.remote.OpenMeteoResponse
import java.util.Locale
import kotlin.math.abs

enum class LocationSourceKind {
    FIELD,
    USER_GPS,
    REGISTERED_REGION,
}

enum class WeatherHazardKind {
    FROST,
    HEAT_STRESS,
    HEAVY_RAIN_LEACHING,
    HIGH_WIND_SPRAY,
}

data class MonitoredLocation(
    val id: String,
    val label: String,
    val sourceKind: LocationSourceKind,
    val lat: Double,
    val lon: Double,
    val cropNames: String = "",
)

data class WeatherHazardAlert(
    val id: String,
    val locationLabel: String,
    val sourceKind: LocationSourceKind,
    val kind: WeatherHazardKind,
    val date: String,
    val metricText: String,
    val cropNames: String = "",
)

object WeatherHazardMonitor {

    private data class RegionKeywordAlias(
        val presetId: String,
        val keywords: List<String>,
        val overrideLatLon: Pair<Double, Double>? = null,
    )

    private val regionAliases = listOf(
        RegionKeywordAlias("karabakh", listOf("berde", "bərdə", "terter", "tərtər", "agdam", "ağdam", "agcabedi", "ağcabədi", "fuzuli", "füzuli", "yevlax", "qarabag", "qarabağ", "karabakh")),
        RegionKeywordAlias("ganja", listOf("gence", "gəncə", "ganja", "goygol", "göygöl", "samux", "goranboy", "semkir", "şəmkir", "tovuz", "qazax", "agstafa", "ağstafa", "daskesen", "daşkəsən", "gedebey", "gədəbəy")),
        RegionKeywordAlias("aran", listOf("sabirabad", "saatli", "saatlı", "imisli", "imişli", "bilesuvar", "biləsuvar", "kurdemir", "kürdəmir", "ucar", "zerdab", "zərdab", "goycay", "göyçay", "agdas", "ağdaş", "salyan", "neftcala", "neftçala", "aran")),
        RegionKeywordAlias("quba", listOf("quba", "xacmaz", "xaçmaz", "khachmaz", "qusar", "sabran", "şabran", "siyezen", "siyəzən")),
        RegionKeywordAlias("shaki", listOf("seki", "şəki", "shaki", "zaqatala", "zagatala", "qax", "balaken", "balakən", "qebele", "qəbələ", "oguz", "oğuz")),
        RegionKeywordAlias("lankaran", listOf("lenkeran", "lənkəran", "lankaran", "astara", "masalli", "masallı", "celilabad", "cəlilabad", "lerik", "yardimli", "yardımlı")),
        RegionKeywordAlias("shirvan", listOf("sirvan", "şirvan", "shirvan", "samaxi", "şamaxı", "shamakhi", "ismayilli", "ismayıllı", "agsu", "ağsu", "qobustan")),
        RegionKeywordAlias("baku", listOf("baki", "bakı", "baku", "abseron", "abşeron", "absheron", "sumqayit", "sumqayıt")),
        RegionKeywordAlias("nakhchivan", listOf("naxcivan", "naxçıvan", "nakhchivan", "serur", "şərur", "ordubad", "culfa", "babek", "babək")),
    )

    fun resolveRegionPlaceCoordinates(place: String?): Pair<String, LatLon>? {
        val raw = place?.trim().orEmpty()
        if (raw.isBlank()) return null
        val lower = raw.lowercase(Locale.ROOT)
            .replace('ə', 'e')
            .replace('ı', 'i')
            .replace('ö', 'o')
            .replace('ü', 'u')
            .replace('ğ', 'g')
            .replace('ş', 's')
            .replace('ç', 'c')

        val presetMatch = ClimateHistoryUtils.regionPresets.firstOrNull { preset ->
            val parts = listOf(preset.id, preset.nameAz, preset.nameEn, preset.nameRu)
                .flatMap { it.lowercase(Locale.ROOT).split("/", "-", " ") }
                .map { it.trim() }
                .filter { it.length >= 3 }
            parts.any { token ->
                val asciiTok = token
                    .replace('ə', 'e').replace('ı', 'i').replace('ö', 'o')
                    .replace('ü', 'u').replace('ğ', 'g').replace('ş', 's').replace('ç', 'c')
                lower.contains(asciiTok)
            }
        }
        if (presetMatch != null) {
            return raw to LatLon(presetMatch.lat, presetMatch.lon)
        }

        val alias = regionAliases.firstOrNull { group ->
            group.keywords.any { kw -> lower.contains(kw) }
        }
        if (alias != null) {
            val preset = ClimateHistoryUtils.regionPresets.firstOrNull { it.id == alias.presetId }
            val coords = alias.overrideLatLon ?: preset?.let { it.lat to it.lon }
            if (coords != null) {
                return raw to LatLon(coords.first, coords.second)
            }
        }
        return null
    }

    fun collectMonitoredLocations(
        fields: List<FarmField>,
        userLat: Double?,
        userLon: Double?,
        profilePlace: String?,
        gpsDisplayLabel: String = "Cari GPS",
    ): List<MonitoredLocation> {
        val collected = mutableListOf<MonitoredLocation>()

        fun isDuplicateNear(lat: Double, lon: Double): Boolean =
            collected.any { existing ->
                abs(existing.lat - lat) < 0.12 && abs(existing.lon - lon) < 0.12
            }

        // 1. Saved farm fields (explicit coords or region location)
        fields.forEach { field ->
            val decoded = field.coords.mapNotNull { GeoUtils.decodeCoord(it) }
            val fieldLatLon = when {
                decoded.isNotEmpty() -> LatLon(decoded.map { it.lat }.average(), decoded.map { it.lon }.average())
                field.location.isNotBlank() -> resolveRegionPlaceCoordinates(field.location)?.second
                else -> null
            }
            if (fieldLatLon != null && !isDuplicateNear(fieldLatLon.lat, fieldLatLon.lon)) {
                val label = field.name.ifBlank { field.location }.ifBlank { "Sahə" }
                collected += MonitoredLocation(
                    id = "field_${field.id}",
                    label = label,
                    sourceKind = LocationSourceKind.FIELD,
                    lat = fieldLatLon.lat,
                    lon = fieldLatLon.lon,
                    cropNames = field.crop.trim(),
                )
            }
        }

        // 2. Person's own GPS / active coordinates
        if (userLat != null && userLon != null && !isDuplicateNear(userLat, userLon)) {
            collected += MonitoredLocation(
                id = "user_gps",
                label = gpsDisplayLabel,
                sourceKind = LocationSourceKind.USER_GPS,
                lat = userLat,
                lon = userLon,
            )
        }

        // 3. Registered profile area
        val resolvedPlace = resolveRegionPlaceCoordinates(profilePlace)
        if (resolvedPlace != null && !isDuplicateNear(resolvedPlace.second.lat, resolvedPlace.second.lon)) {
            collected += MonitoredLocation(
                id = "profile_region",
                label = resolvedPlace.first,
                sourceKind = LocationSourceKind.REGISTERED_REGION,
                lat = resolvedPlace.second.lat,
                lon = resolvedPlace.second.lon,
            )
        }

        return collected.take(5)
    }

    fun evaluateSevenDayHazards(
        location: MonitoredLocation,
        response: OpenMeteoResponse,
    ): List<WeatherHazardAlert> {
        val daily = response.daily ?: return emptyList()
        val daysCount = minOf(7, daily.time.size)
        if (daysCount <= 0) return emptyList()

        val foundByKind = linkedMapOf<WeatherHazardKind, WeatherHazardAlert>()

        fun putIfMoreSevere(kind: WeatherHazardKind, alert: WeatherHazardAlert) {
            if (!foundByKind.containsKey(kind)) {
                foundByKind[kind] = alert
            }
        }

        for (i in 0 until daysCount) {
            val date = daily.time.getOrElse(i) { "" }
            val tMin = daily.tempMin.getOrNull(i)
            val tMax = daily.tempMax.getOrNull(i)
            val precip = daily.precipitationSum.getOrNull(i) ?: 0.0
            val nextPrecip = if (i + 1 < daysCount) daily.precipitationSum.getOrNull(i + 1) ?: 0.0 else 0.0
            val twoDayPrecip = precip + nextPrecip
            val windMax = daily.windMax.getOrNull(i) ?: 0.0

            if (tMin != null && tMin <= 2.0) {
                putIfMoreSevere(
                    WeatherHazardKind.FROST,
                    WeatherHazardAlert(
                        id = "${location.id}_FROST_$date",
                        locationLabel = location.label,
                        sourceKind = location.sourceKind,
                        kind = WeatherHazardKind.FROST,
                        date = date,
                        metricText = String.format(Locale.US, "%.1f°C", tMin),
                        cropNames = location.cropNames,
                    ),
                )
            }

            if (tMax != null && tMax >= 35.0) {
                putIfMoreSevere(
                    WeatherHazardKind.HEAT_STRESS,
                    WeatherHazardAlert(
                        id = "${location.id}_HEAT_$date",
                        locationLabel = location.label,
                        sourceKind = location.sourceKind,
                        kind = WeatherHazardKind.HEAT_STRESS,
                        date = date,
                        metricText = String.format(Locale.US, "%.1f°C", tMax),
                        cropNames = location.cropNames,
                    ),
                )
            }

            if (precip >= 15.0 || twoDayPrecip >= 25.0) {
                val reportedVal = if (precip >= 15.0) precip else twoDayPrecip
                putIfMoreSevere(
                    WeatherHazardKind.HEAVY_RAIN_LEACHING,
                    WeatherHazardAlert(
                        id = "${location.id}_RAIN_$date",
                        locationLabel = location.label,
                        sourceKind = location.sourceKind,
                        kind = WeatherHazardKind.HEAVY_RAIN_LEACHING,
                        date = date,
                        metricText = String.format(Locale.US, "%.1f mm", reportedVal),
                        cropNames = location.cropNames,
                    ),
                )
            }

            val currentWind = if (i == 0) response.current?.windSpeed10m ?: 0.0 else 0.0
            val effectiveWind = maxOf(windMax, currentWind)
            if (effectiveWind >= 35.0) {
                putIfMoreSevere(
                    WeatherHazardKind.HIGH_WIND_SPRAY,
                    WeatherHazardAlert(
                        id = "${location.id}_WIND_$date",
                        locationLabel = location.label,
                        sourceKind = location.sourceKind,
                        kind = WeatherHazardKind.HIGH_WIND_SPRAY,
                        date = date,
                        metricText = String.format(Locale.US, "%.0f km/h", effectiveWind),
                        cropNames = location.cropNames,
                    ),
                )
            }
        }

        return foundByKind.values.toList()
    }
}
