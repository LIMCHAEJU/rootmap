package com.example.rootmap

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface TouristApiService {
    @GET("areaBasedList1")
    fun getTouristInfo(
        @Query("numOfRows") numOfRows: Int,
        @Query("pageNo") pageNo: Int,
        @Query("MobileOS") mobileOS: String,
        @Query("MobileApp") mobileApp: String,
        @Query("contentTypeId") contentTypeId: Int,
        @Query("areaCode") areaCode: Int,
        @Query("serviceKey") serviceKey: String
    ): Call<TouristResponse>

    @GET("detailIntro1")
    fun getTouristDetail(
        @Query("contentId") contentId: Int,
        @Query("contentTypeId") contentTypeId: Int,
        @Query("MobileOS") mobileOS: String,
        @Query("MobileApp") mobileApp: String,
        @Query("serviceKey") serviceKey: String
    ): Call<TouristDetailResponse>
}
