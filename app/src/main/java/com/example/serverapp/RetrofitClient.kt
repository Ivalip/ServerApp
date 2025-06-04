package com.example.serverapp

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    // Храним полный базовый URL, или null, если не задан
    private var baseUrl: String? = "http://127.0.0.1:5000"
    private var retrofit: Retrofit? = null

    /**
     * Возвращает Retrofit-экземпляр, если baseUrl уже установлен,
     * иначе бросает IllegalStateException.
     */
    fun getClient(): Retrofit {
        val url = baseUrl
            ?: throw IllegalStateException("Base URL is not set. Please configure it in Settings.")
        if (retrofit != null) return retrofit!!

        retrofit = Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return retrofit!!
    }

    /**
     * Устанавливает новый IP-адрес (без протокола и порта).
     * Пересобирает Retrofit при следующем getClient().
     */
    fun setServerIp(ip: String) {
        // Собираем полный адрес
        baseUrl = "http://$ip:5000/"
        retrofit = null
    }

    /**
     * Возвращает текущий полный базовый URL, или null.
     */
    fun currentBaseUrl(): String? = baseUrl
}
