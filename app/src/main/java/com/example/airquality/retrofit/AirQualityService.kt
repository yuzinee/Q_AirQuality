package com.example.airquality.retrofit

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface AirQualityService {
    @GET("nearest_city")        // HTTP 메서드 종류, 상대 URL
    fun getAirQualityData(@Query("lat") lat : String, @Query("lon") lon : String,
        @Query("key") key : String) : Call<AirQualityResponse>
}