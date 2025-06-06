package com.example.serverapp.fragments

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.serverapp.ImageUploadService
import com.example.serverapp.RetrofitClient
import com.example.serverapp.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class ScanFragment : Fragment(R.layout.fragment_scan) {
    private lateinit var takePhotoLauncher: androidx.activity.result.ActivityResultLauncher<Uri>
    private lateinit var takeVideoLauncher: androidx.activity.result.ActivityResultLauncher<Uri>
    private lateinit var apiService: ImageUploadService

    private var currentOutputUri: Uri? = null
    private var isVideo: Boolean = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        apiService = RetrofitClient.getClient().create(ImageUploadService::class.java)

        val btnCapturePhoto = view.findViewById<ImageView>(R.id.btnChooseImage)
        val btnCaptureVideo = view.findViewById<ImageView>(R.id.btnChooseVideo)
        val btnUpload       = view.findViewById<ImageView>(R.id.btnUpload)

        takePhotoLauncher = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.TakePicture()
        ) { success: Boolean ->
            if (success && currentOutputUri != null) {
                isVideo = false
                Toast.makeText(requireContext(), "Фото готово", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Фото отменено", Toast.LENGTH_SHORT).show()
            }
        }
        takeVideoLauncher = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.CaptureVideo()
        ) { success: Boolean ->
            if (success && currentOutputUri != null) {
                isVideo = true
                Toast.makeText(requireContext(), "Видео готово", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Видео отменено", Toast.LENGTH_SHORT).show()
            }
        }

        btnCapturePhoto.setOnClickListener {
            val photoFile = createTempFile("photo_", ".jpg")
            currentOutputUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                photoFile
            )
            takePhotoLauncher.launch(currentOutputUri)
        }
        btnCaptureVideo.setOnClickListener {
            val videoFile = createTempFile("video_", ".mp4")
            currentOutputUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                videoFile
            )
            takeVideoLauncher.launch(currentOutputUri)
        }

        btnUpload.setOnClickListener {
            val uri = currentOutputUri
            if (uri != null) {
                lifecycleScope.launch(Dispatchers.IO) {
                    uploadMedia(uri, isVideo)
                }
            } else {
                Toast.makeText(requireContext(), "Сначала сделайте фото или видео", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createTempFile(prefix: String, suffix: String): File {
        val dir = requireContext().cacheDir
        val file = File.createTempFile(prefix, suffix, dir)
        return file
    }

    private suspend fun uploadMedia(uri: Uri, isVideo: Boolean) {
        val resolver = requireContext().contentResolver

        // 1. GET-код с сетевой защитой
        val code: String = try {
            val codeResp = apiService.getAccessCode()
            if (!codeResp.isSuccessful || codeResp.body() == null) {
                showToast("Ошибка получения кода: ${codeResp.code()}")
                return
            }
            codeResp.body()!!.code
        } catch (e: java.net.ConnectException) {
            showToast("Сервер недоступен (не могу подключиться к ${RetrofitClient.currentBaseUrl()})")
            return
        } catch (e: Exception) {
            showToast("Ошибка сети: ${e.localizedMessage}")
            return
        }

        // 2. Читаем байты
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
        if (bytes == null) {
            showToast("Не удалось прочитать файл")
            return
        }

        // 3. MIME + имя файла
        val mime = resolver.getType(uri) ?: if (isVideo) "video/mp4" else "image/jpeg"
        val filename = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.substringBefore('?')
            ?: if (isVideo) "upload.mp4" else "upload.jpg"

        val part = try {
            val requestBody = bytes.toRequestBody(mime.toMediaTypeOrNull())
            MultipartBody.Part.createFormData("file", filename, requestBody)
        } catch (e: Exception) {
            showToast("Ошибка подготовки файла: ${e.localizedMessage}")
            return
        }

        // 4. POST
        try {
            val resp = if (isVideo) {
                apiService.uploadVideo(code, part)
            } else {
                apiService.uploadImage(code, part)
            }
            if (!resp.isSuccessful) {
                showToast("Ошибка загрузки: ${resp.code()}")
                return
            }
        } catch (e: java.net.ConnectException) {
            showToast("Сервер недоступен (⌛ нет соединения)")
            return
        } catch (e: Exception) {
            showToast("Ошибка сети: ${e.localizedMessage}")
            return
        }
        showToast("Файл отправлен, код: $code")
    }

    private fun showToast(text: String) {
        requireActivity().runOnUiThread {
            Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show()
        }
    }
}