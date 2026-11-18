package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

data class OpenMeteoResponse(
    val current: CurrentWeatherDto? = null,
    val daily: DailyWeatherDto? = null,
)

data class CurrentWeatherDto(
    @param:Json(name = "temperature_2m") val temperature2m: Double = 0.0,
    @param:Json(name = "relative_humidity_2m") val humidity2m: Int = 0,
    val precipitation: Double = 0.0,
    @param:Json(name = "wind_speed_10m") val windSpeed10m: Double = 0.0,
)

data class DailyWeatherDto(
    val time: List<String> = emptyList(),
    @param:Json(name = "temperature_2m_max") val tempMax: List<Double> = emptyList(),
    @param:Json(name = "temperature_2m_min") val tempMin: List<Double> = emptyList(),
    @param:Json(name = "precipitation_sum") val precipitationSum: List<Double> = emptyList(),
    @param:Json(name = "wind_speed_10m_max") val windMax: List<Double> = emptyList(),
)

data class HistoricalClimateResponse(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val elevation: Double? = null,
    val daily: HistoricalDailyDto? = null,
)

data class HistoricalDailyDto(
    val time: List<String> = emptyList(),
    @param:Json(name = "temperature_2m_max") val tempMax: List<Double?> = emptyList(),
    @param:Json(name = "temperature_2m_min") val tempMin: List<Double?> = emptyList(),
    @param:Json(name = "temperature_2m_mean") val tempMean: List<Double?> = emptyList(),
    @param:Json(name = "precipitation_sum") val precipitationSum: List<Double?> = emptyList(),
    @param:Json(name = "et0_fao_evapotranspiration") val et0Sum: List<Double?> = emptyList(),
    @param:Json(name = "wind_speed_10m_max") val windMax: List<Double?> = emptyList(),
    @param:Json(name = "shortwave_radiation_sum") val radiationSum: List<Double?> = emptyList(),
    @param:Json(name = "relative_humidity_2m_mean") val humidityMean: List<Double?> = emptyList(),
    @param:Json(name = "rain_sum") val rainSum: List<Double?> = emptyList(),
    @param:Json(name = "snowfall_sum") val snowfallSum: List<Double?> = emptyList(),
    @param:Json(name = "wind_gusts_10m_max") val windGustsMax: List<Double?> = emptyList(),
    @param:Json(name = "wind_direction_10m_dominant") val windDirectionDominant: List<Double?> = emptyList(),
    @param:Json(name = "sunshine_duration") val sunshineDuration: List<Double?> = emptyList(),
    @param:Json(name = "cloud_cover_mean") val cloudCoverMean: List<Double?> = emptyList(),
    @param:Json(name = "pressure_msl_mean") val pressureMslMean: List<Double?> = emptyList(),
    @param:Json(name = "weather_code") val weatherCode: List<Int?> = emptyList(),
)

interface OpenMeteoService {
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "temperature_2m,relative_humidity_2m,precipitation,wind_speed_10m",
        @Query("daily") daily: String = "temperature_2m_max,temperature_2m_min,precipitation_sum,wind_speed_10m_max",
        @Query("timezone") timezone: String = "auto",
    ): OpenMeteoResponse

    @GET("https://archive-api.open-meteo.com/v1/archive")
    suspend fun getHistoricalClimate(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("daily") daily: String = "weather_code,temperature_2m_max,temperature_2m_min,temperature_2m_mean,precipitation_sum,rain_sum,snowfall_sum,relative_humidity_2m_mean,pressure_msl_mean,cloud_cover_mean,wind_speed_10m_max,wind_gusts_10m_max,wind_direction_10m_dominant,shortwave_radiation_sum,sunshine_duration,et0_fao_evapotranspiration",
        @Query("timezone") timezone: String = "auto",
    ): HistoricalClimateResponse

    companion object {
        fun create(): OpenMeteoService {
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .callTimeout(60, TimeUnit.SECONDS)
                .build()
            val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()
            return Retrofit.Builder()
                .baseUrl("https://api.open-meteo.com/")
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(OpenMeteoService::class.java)
        }
    }
}
