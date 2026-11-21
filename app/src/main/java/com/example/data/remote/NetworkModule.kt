package com.example.data.remote

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val userAgentInterceptor = Interceptor { chain ->
        val original = chain.request()
        val host = original.url.host
        val userAgent = if (host.contains("lrclib.net", ignoreCase = true)) {
            "Cadence/1.0 (Android Music Player)"
        } else {
            USER_AGENT
        }
        val builder = original.newBuilder()
            .header("User-Agent", userAgent)
            .header("Accept", "application/json, text/plain, */*")

        if (host.contains("jiosaavn.com", ignoreCase = true) ||
            host.contains("saavncdn.com", ignoreCase = true)
        ) {
            builder
                .header("Referer", "https://www.jiosaavn.com/")
                .header("Origin", "https://www.jiosaavn.com")
        }

        chain.proceed(builder.build())
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(userAgentInterceptor)
            .addInterceptor(loggingInterceptor)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    val json: Json by lazy {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
    }

    private val converterFactory by lazy {
        json.asConverterFactory("application/json".toMediaType())
    }

    private val jioSaavnRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(MusicApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(converterFactory)
            .build()
    }

    private val saavnMirrorRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(SaavnMirrorApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(converterFactory)
            .build()
    }

    private val saavnVercelMirrorRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(SaavnVercelMirrorApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(converterFactory)
            .build()
    }

    private val audiusRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(AudiusApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(converterFactory)
            .build()
    }

    private val lrcLibRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(LrcLibApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(converterFactory)
            .build()
    }

    val musicApiService: MusicApiService by lazy {
        jioSaavnRetrofit.create(MusicApiService::class.java)
    }

    val saavnMirrorApiService: SaavnMirrorApiService by lazy {
        saavnMirrorRetrofit.create(SaavnMirrorApiService::class.java)
    }

    val saavnVercelMirrorApiService: SaavnVercelMirrorApiService by lazy {
        saavnVercelMirrorRetrofit.create(SaavnVercelMirrorApiService::class.java)
    }

    val audiusApiService: AudiusApiService by lazy {
        audiusRetrofit.create(AudiusApiService::class.java)
    }

    val lrcLibApiService: LrcLibApiService by lazy {
        lrcLibRetrofit.create(LrcLibApiService::class.java)
    }
}
