package com.example.rokidphone.service.rag.network

import com.example.rokidphone.data.normalizeAnythingLlmApiKey
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object AnythingLlmApiFactory {

    fun create(
        serverUrl: String,
        apiKey: String,
    ): AnythingLlmApi {
        val normalizedServerUrl = normalizeServerUrl(serverUrl)
        val normalizedApiKey = normalizeAnythingLlmApiKey(apiKey)
        val httpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Authorization", "Bearer $normalizedApiKey")
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(normalizedServerUrl)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AnythingLlmApi::class.java)
    }

    fun normalizeServerUrl(serverUrl: String): String {
        val trimmed = serverUrl.trim().trimEnd('/')
        return "$trimmed/"
    }
}
