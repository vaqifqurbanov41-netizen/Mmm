package com.example

import com.example.data.remote.OpenMeteoResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun openMeteoMoshi_parsesWithoutKsp() {
    val json = """
      {
        "current": {
          "temperature_2m": 22.5,
          "relative_humidity_2m": 60,
          "precipitation": 0.0,
          "wind_speed_10m": 12.3
        },
        "daily": {
          "time": ["2026-11-15"],
          "temperature_2m_max": [24.0],
          "temperature_2m_min": [11.0],
          "precipitation_sum": [1.5],
          "wind_speed_10m_max": [18.2]
        }
      }
    """.trimIndent()
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val parsed = moshi.adapter(OpenMeteoResponse::class.java).fromJson(json)
    assertNotNull(parsed)
    assertEquals(22.5, parsed!!.current!!.temperature2m, 0.01)
    assertEquals(60, parsed.current!!.humidity2m)
    assertEquals(18.2, parsed.daily!!.windMax.first(), 0.01)
  }
}
