package com.example.serverapp.fragments

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.webkit.MimeTypeMap
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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

    private lateinit var btnCapturePhoto: ImageView
    private lateinit var btnCaptureVideo: ImageView
    private lateinit var btnUpload: ImageView

    private var currentOutputUri: Uri? = null
    private val prefs by lazy {
        requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    }

    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                enableCameraButtons(true)
            } else {
                Toast.makeText(requireContext(),
                    "Без доступа к камере функция съёмки недоступна",
                    Toast.LENGTH_LONG).show()
                enableCameraButtons(false)
            }
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

        apiService = RetrofitClient.getClient(requireContext())
            .create(ImageUploadService::class.java)

        btnCapturePhoto = view.findViewById(R.id.btnChooseImage)
        btnCaptureVideo = view.findViewById(R.id.btnChooseVideo)
        btnUpload       = view.findViewById(R.id.btnUpload)

        takePhotoLauncher = registerForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { success ->
            if (success && currentOutputUri != null) {
                lifecycleScope.launch(Dispatchers.IO) {
                    uploadMedia(currentOutputUri!!, isVideo = false)
                }
            } else {
                showToast("Фото отменено")
            }
        }

        takeVideoLauncher = registerForActivityResult(
            ActivityResultContracts.CaptureVideo()
        ) { success ->
            if (success && currentOutputUri != null) {
                lifecycleScope.launch(Dispatchers.IO) {
                    uploadMedia(currentOutputUri!!, isVideo = true)
                }
            } else {
                showToast("Видео отменено")
            }
        }

        pickMediaLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                requireContext().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
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
        checkCameraPermission()
    }

    private fun checkCameraPermission() {
        val ctx = requireContext()
        when (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA)) {
            PackageManager.PERMISSION_GRANTED -> {
                enableCameraButtons(true)
            }
            else -> {
                enableCameraButtons(false)
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun enableCameraButtons(enabled: Boolean) {
        btnCapturePhoto.isEnabled = enabled
        btnCapturePhoto.alpha     = if (enabled) 1f else 0.5f
        btnCaptureVideo.isEnabled = enabled
        btnCaptureVideo.alpha     = if (enabled) 1f else 0.5f
    }

    private fun createTempFile(prefix: String, suffix: String): File =
        File.createTempFile(prefix, suffix, requireContext().cacheDir)

    private suspend fun uploadMedia(uri: Uri, isVideo: Boolean) {
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

        val resolver = requireContext().contentResolver
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
        if (bytes == null) {
            showToast("Не удалось прочитать файл")
            return
        }

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

        val part = MultipartBody.Part.createFormData(
            "file", filename, bytes.toRequestBody(mime.toMediaTypeOrNull())
        )

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
                requireActivity().runOnUiThread {
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, HistoryFragment())
                        .commit()
                }
            } else {
                showToast("Ошибка загрузки: ${resp.code()}")
            }
        } catch (e: Exception) {
            showToast("Ошибка сети")
        }
    }

    private fun showToast(msg: String) {
        requireActivity().runOnUiThread {
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
    }
}