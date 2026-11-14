package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class OpenMeteoResponse(
    val current: CurrentWeatherDto? = null,
    val daily: DailyWeatherDto? = null,
)

@JsonClass(generateAdapter = true)
data class CurrentWeatherDto(
    @Json(name = "temperature_2m") val temperature2m: Double = 0.0,
    @Json(name = "relative_humidity_2m") val humidity2m: Int = 0,
    val precipitation: Double = 0.0,
    @Json(name = "wind_speed_10m") val windSpeed10m: Double = 0.0,
)

@JsonClass(generateAdapter = true)
data class DailyWeatherDto(
    val time: List<String> = emptyList(),
    @Json(name = "temperature_2m_max") val tempMax: List<Double> = emptyList(),
    @Json(name = "temperature_2m_min") val tempMin: List<Double> = emptyList(),
    @Json(name = "precipitation_sum") val precipitationSum: List<Double> = emptyList(),
    @Json(name = "wind_speed_10m_max") val windMax: List<Double> = emptyList(),
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

    companion object {
        fun create(): OpenMeteoService {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
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
