package com.example.serverapp

import android.content.Context
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val PREFS_NAME = "app_prefs"
    private const val KEY_IP     = "server_ip"
    private const val DEFAULT_IP = "127.0.0.1"
    private const val PORT       = 5000

    @Volatile
    private var retrofit: Retrofit? = null

    /**
     * Возвращает сконфигурированный Retrofit, читая IP из SharedPreferences.
     * Если в prefs некорректный или пустой IP, используем DEFAULT_IP.
     */
    fun getClient(context: Context): Retrofit {
        // Читаем IP
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawIp = prefs.getString(KEY_IP, "")?.trim().orEmpty()
        val ip = if (isValidIp(rawIp)) rawIp else DEFAULT_IP

        // Собираем базовый URL
        val baseUrl = "http://$ip:$PORT/"

        // Если Retrofit еще не создан или URL поменялся — пересоздать
        synchronized(this) {
            if (retrofit == null || retrofit?.baseUrl().toString() != baseUrl) {
                retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
            }
            return retrofit!!
        }
    }

    /**
     * Простая проверка формата IPv4: четыре числа 0..255
     * (можно расширить под hostname или IPv6).
     */
    private fun isValidIp(ip: String): Boolean {
        val parts = ip.split('.')
        if (parts.size != 4) return false
        return parts.all {
            it.toIntOrNull()?.let { num -> num in 0..255 } ?: false
        }
    }

    /** Возвращает текущий базовый URL (для логирования и ошибок). */
    fun currentBaseUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ip = prefs.getString(KEY_IP, DEFAULT_IP) ?: DEFAULT_IP
        return "http://$ip:$PORT/"
    }
}
