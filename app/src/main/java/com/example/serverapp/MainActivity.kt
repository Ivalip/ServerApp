package com.example.serverapp

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class MainActivity : ComponentActivity() {
    private lateinit var getImageLauncher: ActivityResultLauncher<String>
    private lateinit var getVideoLauncher: ActivityResultLauncher<String>
    private lateinit var apiService: ImageUploadService

    private var imageUri: Uri? = null
    private var videoUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val baseUrlEditText = findViewById<EditText>(R.id.baseUrlEditText)
        val submitButton    = findViewById<Button>(R.id.submitButton)
        val btnChooseImage  = findViewById<Button>(R.id.btnChooseImage)
        val btnUploadImage  = findViewById<Button>(R.id.btnUploadImage)
        val btnChooseVideo  = findViewById<Button>(R.id.btnChooseVideo)
        val btnUploadVideo  = findViewById<Button>(R.id.btnUploadVideo)

        submitButton.setOnClickListener {
            val baseUrl = "http://" + baseUrlEditText.text.toString().trim() + ":5000"
            if (baseUrlEditText.text.isNotEmpty()) {
                val retrofit = RetrofitClient.getClient(baseUrl)
                apiService = retrofit.create(ImageUploadService::class.java)
                Toast.makeText(this, "Connected to $baseUrl", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Please enter a valid URL", Toast.LENGTH_SHORT).show()
            }
        }

        // Выбор изображения
        getImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                imageUri = it
                Toast.makeText(this, "Image selected", Toast.LENGTH_SHORT).show()
            }
        }
        btnChooseImage.setOnClickListener { getImageLauncher.launch("image/*") }

        // Выбор видео
        getVideoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                videoUri = it
                Toast.makeText(this, "Video selected", Toast.LENGTH_SHORT).show()
            }
        }
        btnChooseVideo.setOnClickListener { getVideoLauncher.launch("video/*") }

        // Загрузка изображения
        btnUploadImage.setOnClickListener {
            imageUri?.let {
                CoroutineScope(Dispatchers.IO).launch {
                    uploadMedia(it, isVideo = false)
                }
            } ?: runOnUiThread {
                Toast.makeText(this, "Select an image first", Toast.LENGTH_SHORT).show()
            }
        }

        // Загрузка видео
        btnUploadVideo.setOnClickListener {
            videoUri?.let {
                CoroutineScope(Dispatchers.IO).launch {
                    uploadMedia(it, isVideo = true)
                }
            } ?: runOnUiThread {
                Toast.makeText(this, "Select a video first", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun uploadMedia(uri: Uri, isVideo: Boolean) {
        // 1. Получаем код доступа
        val codeResp = apiService.getAccessCode()
        if (!codeResp.isSuccessful || codeResp.body() == null) {
            runOnUiThread {
                Toast.makeText(this, "Error getting access code", Toast.LENGTH_SHORT).show()
            }
            return
        }
        val code = codeResp.body()!!.code

        // 2. Читаем байты
        val stream = contentResolver.openInputStream(uri)
        val bytes  = stream?.readBytes()
        stream?.close()
        if (bytes == null) {
            runOnUiThread {
                Toast.makeText(this, "Error reading file", Toast.LENGTH_SHORT).show()
            }
            return
        }

        // 3. Определяем MIME и имя файла
        val mime = contentResolver.getType(uri) ?: if (isVideo) "video/mp4" else "image/jpeg"
        val rawName = uri.lastPathSegment ?: ""
        val filename = rawName
            .substringAfterLast('/')
            .substringBefore('?')
            .ifEmpty { if (isVideo) "upload.mp4" else "upload.jpg" }

        val requestBody = bytes.toRequestBody(mime.toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", filename, requestBody)

        // 4. Отправляем
        val resp = if (isVideo) {
            apiService.uploadVideo(code, part)
        } else {
            apiService.uploadImage(code, part)
        }
        if (!resp.isSuccessful) {
            runOnUiThread {
                Toast.makeText(this, "Upload failed (${resp.code()})", Toast.LENGTH_SHORT).show()
            }
            return
        }

        // 5. Получаем статус
        val statusResp = apiService.getStatus(code)
        if (statusResp.isSuccessful && statusResp.body() != null) {
            val status = statusResp.body()!!
            runOnUiThread {
                Toast.makeText(
                    this,
                    "Images: ${status.images.joinToString()} | Videos: ${status.videos.joinToString()}",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            runOnUiThread {
                Toast.makeText(this, "Error getting status", Toast.LENGTH_SHORT).show()
            }
        }
    }
}