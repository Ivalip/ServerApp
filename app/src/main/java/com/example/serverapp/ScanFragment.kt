// ScanFragment.kt
package com.example.serverapp.fragments

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.webkit.MimeTypeMap
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.serverapp.ImageUploadService
import com.example.serverapp.R
import com.example.serverapp.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class ScanFragment : Fragment(R.layout.fragment_scan) {
    private lateinit var takePhotoLauncher: androidx.activity.result.ActivityResultLauncher<Uri>
    private lateinit var takeVideoLauncher: androidx.activity.result.ActivityResultLauncher<Uri>
    private lateinit var pickMediaLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
    private lateinit var apiService: ImageUploadService

    private var currentOutputUri: Uri? = null
    private val prefs by lazy {
        requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    }

    companion object {
        private const val KEY_LAST_FILENAME = "last_filename"
        private const val KEY_LAST_CODE     = "last_code"
        private const val KEY_LAST_PATH     = "last_filepath"
        private const val KEY_MAX_LENGTH    = "video_max_length"
        private const val DEFAULT_MAX_LENGTH = 60
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        apiService = RetrofitClient.getClient().create(ImageUploadService::class.java)

        val btnCapturePhoto = view.findViewById<ImageView>(R.id.btnChooseImage)
        val btnCaptureVideo = view.findViewById<ImageView>(R.id.btnChooseVideo)
        val btnUpload       = view.findViewById<ImageView>(R.id.btnUpload)

        // Лаунчер для съёмки фото
        takePhotoLauncher = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.TakePicture()
        ) { success ->
            if (success && currentOutputUri != null) {
                lifecycleScope.launch(Dispatchers.IO) {
                    uploadMedia(currentOutputUri!!, isVideo = false)
                }
            } else {
                showToast("Фото отменено")
            }
        }

        // Лаунчер для записи видео
        takeVideoLauncher = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.CaptureVideo()
        ) { success ->
            if (success && currentOutputUri != null) {
                lifecycleScope.launch(Dispatchers.IO) {
                    uploadMedia(currentOutputUri!!, isVideo = true)
                }
            } else {
                showToast("Видео отменено")
            }
        }

        // Лаунчер для выбора из памяти
        pickMediaLauncher = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                // персистентное право читать URI
                requireContext().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                // определяем по MIME
                val mime = requireContext().contentResolver.getType(uri) ?: ""
                val isVideo = mime.startsWith("video")
                lifecycleScope.launch(Dispatchers.IO) {
                    uploadMedia(uri, isVideo)
                }
            } else {
                showToast("Файл не выбран")
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
            pickMediaLauncher.launch(arrayOf("image/*", "video/*"))
        }
    }

    private fun createTempFile(prefix: String, suffix: String): File =
        File.createTempFile(prefix, suffix, requireContext().cacheDir)

    private suspend fun uploadMedia(uri: Uri, isVideo: Boolean) {
        // 1) проверка длины видео
        if (isVideo) {
            val maxLen = prefs.getInt(KEY_MAX_LENGTH, DEFAULT_MAX_LENGTH)
            val retriever = MediaMetadataRetriever().apply { setDataSource(requireContext(), uri) }
            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L
            retriever.release()
            if (durationMs / 1000 > maxLen) {
                showToast("Длина видео ${durationMs / 1000}s > $maxLen s")
                return
            }
        }

        // 2) получение кода
        val code = try {
            val resp = apiService.getAccessCode()
            if (!resp.isSuccessful || resp.body() == null) {
                showToast("Ошибка получения кода: ${resp.code()}")
                return
            }
            resp.body()!!.code.also {
                prefs.edit().putString(KEY_LAST_CODE, it).apply()
            }
        } catch (e: Exception) {
            showToast("Сервер недоступен")
            return
        }

        // 3) чтение байт
        val resolver = requireContext().contentResolver
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
        if (bytes == null) {
            showToast("Не удалось прочитать файл")
            return
        }

        // 4) определяем mime и filename с гарантией расширения
        val mime = resolver.getType(uri) ?: if (isVideo) "video/mp4" else "image/jpeg"
        val raw = uri.lastPathSegment?.substringAfterLast('/')?.substringBefore('?') ?: "upload"
        val filename = if (raw.contains('.')) {
            raw
        } else {
            val ext = MimeTypeMap.getSingleton()
                .getExtensionFromMimeType(mime)
                ?: if (isVideo) "mp4" else "jpg"
            "$raw.$ext"
        }

        // 5) формируем multipart
        val part = MultipartBody.Part.createFormData(
            "file", filename, bytes.toRequestBody(mime.toMediaTypeOrNull())
        )

        // 6) отправка + кеширование
        try {
            val resp = if (isVideo)
                apiService.uploadVideo(code, part)
            else
                apiService.uploadImage(code, part)

            if (resp.isSuccessful) {
                showToast(if (isVideo) "Видео отправлено" else "Фото отправлено")

                val cacheFile = File(requireContext().cacheDir, filename)
                cacheFile.outputStream().use { it.write(bytes) }
                prefs.edit()
                    .putString(KEY_LAST_FILENAME, filename)
                    .putString(KEY_LAST_PATH, cacheFile.absolutePath)
                    .apply()
            } else {
                showToast("Ошибка загрузки: ${resp.code()}")
            }
        } catch (e: Exception) {
            showToast("Ошибка сети")
        }
    }

    private fun showToast(msg: String) {
        requireActivity().runOnUiThread {
            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
        }
    }
}
