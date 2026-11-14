package com.example.domain

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan

data class LatLon(
    val lat: Double,
    val lon: Double,
)

data class UtmCoord(
    val zone: Int,
    val easting: Double,
    val northing: Double,
)

data class FieldMeasurements(
    val areaHa: Double,
    val areaSot: Double,
    val areaM2: Double,
    val widthM: Double,
    val lengthM: Double,
    val perimeterM: Double,
)

object GeoUtils {
    private const val EARTH_RADIUS_M = 6371000.0
    private const val SPHERICAL_RADIUS_M = 6378137.0

    fun haversineDistanceM(p1: LatLon, p2: LatLon): Double {
        val toRad = { d: Double -> d * PI / 180.0 }
        val dLat = toRad(p2.lat - p1.lat)
        val dLon = toRad(p2.lon - p1.lon)
        val a = sin(dLat / 2.0).pow(2.0) +
            cos(toRad(p1.lat)) * cos(toRad(p2.lat)) * sin(dLon / 2.0).pow(2.0)
        return 2.0 * EARTH_RADIUS_M * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    fun calculateMeasurements(points: List<LatLon>): FieldMeasurements {
        if (points.size < 2) {
            return FieldMeasurements(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        }

        var perim = 0.0
        val sideLengths = mutableListOf<Double>()
        for (i in points.indices) {
            val p1 = points[i]
            val p2 = points[(i + 1) % points.size]
            val d = haversineDistanceM(p1, p2)
            sideLengths.add(d)
            if (i < points.size - 1 || points.size >= 3) {
                perim += d
            }
        }

        var areaM2 = 0.0
        if (points.size >= 3) {
            val toRad = { d: Double -> d * PI / 180.0 }
            var total = 0.0
            for (i in points.indices) {
                val p1 = points[i]
                val p2 = points[(i + 1) % points.size]
                total += toRad(p2.lon - p1.lon) * (2.0 + sin(toRad(p1.lat)) + sin(toRad(p2.lat)))
            }
            areaM2 = kotlin.math.abs(total * SPHERICAL_RADIUS_M * SPHERICAL_RADIUS_M / 2.0)
        }

        val widthM: Double
        val lengthM: Double
        if (points.size == 4) {
            val s1 = (sideLengths[0] + sideLengths[2]) / 2.0
            val s2 = (sideLengths[1] + sideLengths[3]) / 2.0
            widthM = min(s1, s2)
            lengthM = max(s1, s2)
        } else {
            val minLat = points.minOf { it.lat }
            val maxLat = points.maxOf { it.lat }
            val minLon = points.minOf { it.lon }
            val maxLon = points.maxOf { it.lon }
            val midLat = (minLat + maxLat) / 2.0
            val midLon = (minLon + maxLon) / 2.0
            val nsSpan = haversineDistanceM(LatLon(minLat, midLon), LatLon(maxLat, midLon))
            val ewSpan = haversineDistanceM(LatLon(midLat, minLon), LatLon(midLat, maxLon))
            widthM = min(nsSpan, ewSpan)
            lengthM = max(nsSpan, ewSpan)
        }

        val ha = areaM2 / 10000.0
        val sot = areaM2 / 100.0
        return FieldMeasurements(
            areaHa = ha,
            areaSot = sot,
            areaM2 = areaM2,
            widthM = widthM,
            lengthM = lengthM,
            perimeterM = perim,
        )
    }

    fun latLonToUtm(lat: Double, lon: Double, forcedZone: Int? = null): UtmCoord {
        val a = 6378137.0
        val f = 1.0 / 298.257223563
        val k0 = 0.9996
        val e2 = f * (2.0 - f)
        val ep2 = e2 / (1.0 - e2)

        val zone = forcedZone ?: (kotlin.math.floor((lon + 180.0) / 6.0).toInt() + 1)
        val lon0 = ((zone - 1) * 6.0 - 180.0 + 3.0) * PI / 180.0
        val phi = lat * PI / 180.0
        val lam = lon * PI / 180.0

        val n = a / sqrt(1.0 - e2 * sin(phi) * sin(phi))
        val t = tan(phi) * tan(phi)
        val c = ep2 * cos(phi) * cos(phi)
        val aa = cos(phi) * (lam - lon0)

        val m = a * (
            (1.0 - e2 / 4.0 - 3.0 * e2 * e2 / 64.0 - 5.0 * e2 * e2 * e2 / 256.0) * phi -
                (3.0 * e2 / 8.0 + 3.0 * e2 * e2 / 32.0 + 45.0 * e2 * e2 * e2 / 1024.0) * sin(2.0 * phi) +
                (15.0 * e2 * e2 / 256.0 + 45.0 * e2 * e2 * e2 / 1024.0) * sin(4.0 * phi) -
                (35.0 * e2 * e2 * e2 / 3072.0) * sin(6.0 * phi)
            )

        val easting = k0 * n * (
            aa + (1.0 - t + c) * aa.pow(3) / 6.0 +
                (5.0 - 18.0 * t + t * t + 72.0 * c - 58.0 * ep2) * aa.pow(5) / 120.0
            ) + 500000.0

        var northing = k0 * (
            m + n * tan(phi) * (
                aa * aa / 2.0 +
                    (5.0 - t + 9.0 * c + 4.0 * c * c) * aa.pow(4) / 24.0 +
                    (61.0 - 58.0 * t + t * t + 600.0 * c - 330.0 * ep2) * aa.pow(6) / 720.0
                )
            )
        if (lat < 0) northing += 10000000.0

        return UtmCoord(
            zone = zone,
            easting = (easting * 100.0).roundToInt() / 100.0,
            northing = (northing * 100.0).roundToInt() / 100.0,
        )
    }

    fun utmToLatLon(easting: Double, northing: Double, zone: Int = 38): LatLon {
        val a = 6378137.0
        val f = 1.0 / 298.257223563
        val k0 = 0.9996
        val e2 = f * (2.0 - f)
        val ep2 = e2 / (1.0 - e2)
        val e1 = (1.0 - sqrt(1.0 - e2)) / (1.0 + sqrt(1.0 - e2))

        val x = easting - 500000.0
        val y = northing
        val m = y / k0
        val mu = m / (a * (1.0 - e2 / 4.0 - 3.0 * e2 * e2 / 64.0 - 5.0 * e2 * e2 * e2 / 256.0))

        val phi1 = mu +
            (3.0 * e1 / 2.0 - 27.0 * e1.pow(3) / 32.0) * sin(2.0 * mu) +
            (21.0 * e1 * e1 / 16.0 - 55.0 * e1.pow(4) / 32.0) * sin(4.0 * mu) +
            (151.0 * e1.pow(3) / 96.0) * sin(6.0 * mu) +
            (1097.0 * e1.pow(4) / 512.0) * sin(8.0 * mu)

        val c1 = ep2 * cos(phi1) * cos(phi1)
        val t1 = tan(phi1) * tan(phi1)
        val n1 = a / sqrt(1.0 - e2 * sin(phi1) * sin(phi1))
        val r1 = a * (1.0 - e2) / (1.0 - e2 * sin(phi1) * sin(phi1)).pow(1.5)
        val d = x / (n1 * k0)

        val lat = phi1 - (n1 * tan(phi1) / r1) * (
            d * d / 2.0 -
                (5.0 + 3.0 * t1 + 10.0 * c1 - 4.0 * c1 * c1 - 9.0 * ep2) * d.pow(4) / 24.0 +
                (61.0 + 90.0 * t1 + 298.0 * c1 + 45.0 * t1 * t1 - 252.0 * ep2 - 3.0 * c1 * c1) * d.pow(6) / 720.0
            )

        val lon0 = ((zone - 1) * 6.0 - 180.0 + 3.0) * PI / 180.0
        val lon = lon0 + (
            d - (1.0 + 2.0 * t1 + c1) * d.pow(3) / 6.0 +
                (5.0 - 2.0 * c1 + 28.0 * t1 - 3.0 * c1 * c1 + 8.0 * ep2 + 24.0 * t1 * t1) * d.pow(5) / 120.0
            ) / cos(phi1)

        return LatLon(lat * 180.0 / PI, lon * 180.0 / PI)
    }

    // Web Mercator projection helpers for slippy map tiles (256px tiles)
    fun latLonToWorldPx(lat: Double, lon: Double, zoom: Int): Pair<Double, Double> {
        val scale = (1 shl zoom) * 256.0
        val x = (lon + 180.0) / 360.0 * scale
        val sinLat = sin(lat.coerceIn(-85.0511, 85.0511) * PI / 180.0)
        val y = (0.5 - ln((1.0 + sinLat) / (1.0 - sinLat)) / (4.0 * PI)) * scale
        return x to y
    }

    fun worldPxToLatLon(wx: Double, wy: Double, zoom: Int): LatLon {
        val scale = (1 shl zoom) * 256.0
        val lon = (wx / scale) * 360.0 - 180.0
        val n = PI - 2.0 * PI * (wy / scale)
        val lat = atan2(sinh(n), 1.0) * 180.0 / PI
        return LatLon(lat.coerceIn(-85.0511, 85.0511), ((lon + 540.0) % 360.0) - 180.0)
    }

    fun encodeCoord(p: LatLon): String = String.format(java.util.Locale.US, "%.6f,%.6f", p.lat, p.lon)

    fun decodeCoord(raw: String): LatLon? {
        val parts = raw.split(",")
        if (parts.size < 2) return null
        val lat = parts[0].trim().toDoubleOrNull() ?: return null
        val lon = parts[1].trim().toDoubleOrNull() ?: return null
        return LatLon(lat, lon)
    }
}
