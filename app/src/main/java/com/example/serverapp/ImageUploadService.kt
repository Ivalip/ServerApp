package com.example.serverapp

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

data class CodeResponse(
    val code: String
)

// Теперь в тело ответа GET /{code} добавлены поля bags, luggages и backpacks
data class StatusResponse(
    val images: List<String>    = emptyList(),
    val videos: List<String>    = emptyList(),
    val bags: String            = "0",
    val luggages: String        = "0",
    val backpacks: String       = "0"
)

data class ImageListResponse(val images: List<String> = emptyList())
data class VideoListResponse(val videos: List<String> = emptyList())

interface ImageUploadService {

    // 1. Получить код доступа
    @GET("/")
    suspend fun getAccessCode(): Response<CodeResponse>

    // Загрузка и скачивание
    @Multipart
    @POST("/{code}/image")
    suspend fun uploadImage(
        @Path("code") code: String,
        @Part file: MultipartBody.Part
    ): Response<Unit>

    @Multipart
    @POST("/{code}/video")
    suspend fun uploadVideo(
        @Path("code") code: String,
        @Part file: MultipartBody.Part
    ): Response<Unit>

    @GET("/{code}/download")
    @Streaming
    suspend fun downloadFile(@Path("code") code: String): Response<ResponseBody>

    // 4. Получить статус (списки + конкретные поля)
    @GET("/{code}")
    suspend fun getStatus(@Path("code") code: String): Response<StatusResponse>

    // Опционально: отдельные списки
    @GET("/{code}/image")
    suspend fun getImageInfo(@Path("code") code: String): Response<ImageListResponse>

    @GET("/{code}/video")
    suspend fun getVideoInfo(@Path("code") code: String): Response<VideoListResponse>
}
