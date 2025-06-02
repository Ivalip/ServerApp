package com.example.serverapp

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

data class CodeResponse(
    val code: String
)

data class StatusResponse(
    val images: List<String> = emptyList(),
    val videos: List<String> = emptyList()
)
data class ImageListResponse(val images: List<String> = emptyList())
data class VideoListResponse(val videos: List<String> = emptyList())

interface ImageUploadService {

    // 1. Получить код доступа
    @GET("/")
    suspend fun getAccessCode(): Response<CodeResponse>

    // 2. Загрузка изображения
    @Multipart
    @POST("/{code}/image")
    suspend fun uploadImage(
        @Path("code") code: String,
        @Part file: MultipartBody.Part
    ): Response<Unit>

    // 3. Загрузка видео
    @Multipart
    @POST("/{code}/video")
    suspend fun uploadVideo(
        @Path("code") code: String,
        @Part file: MultipartBody.Part
    ): Response<Unit>

    // 4. Получить статус (списки изображений и видео)
    @GET("/{code}")
    suspend fun getStatus(
        @Path("code") code: String
    ): Response<StatusResponse>

    // 5. (опционально) отдельные GET по типу контента
    @GET("/{code}/image")
    suspend fun getImageInfo(@Path("code") code: String): Response<ImageListResponse>

    @GET("/{code}/video")
    suspend fun getVideoInfo(@Path("code") code: String): Response<VideoListResponse>
}