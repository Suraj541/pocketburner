package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Url

data class M5BurnerFileDto(
    @Json(name = "address") val address: String? = null,
    @Json(name = "offset") val offset: String? = null,
    @Json(name = "path") val path: String? = null,
    @Json(name = "filename") val filename: String? = null,
)

data class M5BurnerFirmwareDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "fid") val fid: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "author") val author: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "version") val version: String? = null,
    @Json(name = "framework") val framework: String? = null,
    @Json(name = "cover") val cover: String? = null,
    @Json(name = "download_url") val downloadUrl: String? = null,
    @Json(name = "url") val url: String? = null,
    @Json(name = "download") val download: String? = null,
    @Json(name = "offset") val offset: String? = null,
    @Json(name = "default_baud") val defaultBaud: Int? = null,
    @Json(name = "sha256") val sha256: String? = null,
    @Json(name = "official") val official: Boolean? = null,
    @Json(name = "files") val files: List<M5BurnerFileDto>? = null,
)

/**
 * Retrofit service interface for fetching remote firmware & OS repository feeds,
 * anchored to M5Burner's HK CDN API (`http://m5burner-api-fc-hk-cdn.m5stack.com/api/firmware`)
 * and supporting dynamic repository index URLs (Raspberry Pi Imager, Arduino/ESP indexes, GitHub releases).
 */
interface FirmwareCatalogApiService {

    @Headers(
        "Accept: application/json",
        "User-Agent: PocketBurner-Android/1.0",
    )
    @GET("api/firmware")
    suspend fun fetchM5BurnerCatalogRaw(): Response<ResponseBody>

    @Headers(
        "Accept: application/json",
        "User-Agent: PocketBurner-Android/1.0",
    )
    @GET
    suspend fun fetchFeedRaw(@Url feedUrl: String): Response<ResponseBody>

    companion object {
        const val M5BURNER_BASE_URL = "http://m5burner-api-fc-hk-cdn.m5stack.com/"

        fun create(
            client: OkHttpClient,
            baseUrl: String = M5BURNER_BASE_URL,
        ): FirmwareCatalogApiService {
            val normalizedBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()
            return Retrofit.Builder()
                .baseUrl(normalizedBase)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(FirmwareCatalogApiService::class.java)
        }
    }
}
