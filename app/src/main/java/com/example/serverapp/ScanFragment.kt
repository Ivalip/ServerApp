package com.example.serverapp.fragments

import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
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

class ScanFragment : Fragment(R.layout.fragment_scan) {
    private lateinit var getImageLauncher: ActivityResultLauncher<String>
    private lateinit var getVideoLauncher: ActivityResultLauncher<String>
    private lateinit var apiService: ImageUploadService

    private var isVideo: Boolean = false
    private var selectedUri: Uri? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Инициализируем apiService
        val baseUrl = "http://192.168.0.107:5000/"
        apiService = RetrofitClient.getClient(baseUrl).create(ImageUploadService::class.java)

        val btnChooseImage = view.findViewById<Button>(R.id.btnChooseImage)
        val btnChooseVideo = view.findViewById<Button>(R.id.btnChooseVideo)
        val btnUpload      = view.findViewById<Button>(R.id.btnUpload)

        getImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                selectedUri = it
                isVideo = false
//                Toast.makeText( "Image selected", Toast.LENGTH_SHORT).show()
            }
        }
        btnChooseImage.setOnClickListener { getImageLauncher.launch("image/*") }
        getVideoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                selectedUri = it
                isVideo = true
//                Toast.makeText(this, "Video selected", Toast.LENGTH_SHORT).show()
            }
        }
        btnChooseVideo.setOnClickListener { getVideoLauncher.launch("video/*") }
        btnUpload.setOnClickListener {
            selectedUri?.let { uri ->
                lifecycleScope.launch(Dispatchers.IO) {
                    uploadMedia(uri, isVideo)
                }
            } ?: Toast.makeText(requireContext(), "Select a file first", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun uploadMedia(uri: Uri, isVideo: Boolean) {
        val resolver = requireContext().contentResolver

        val codeResp = apiService.getAccessCode()
        if (!codeResp.isSuccessful || codeResp.body() == null) {
            return
        }
        val code = codeResp.body()!!.code

        // 2. Читаем байты
        val stream = resolver.openInputStream(uri)
        val bytes  = stream?.readBytes()
        stream?.close()
        if (bytes == null) {
            return
        }

        // 3. Определяем MIME и имя файла
        val mime = resolver.getType(uri) ?: if (isVideo) "video/mp4" else "image/jpeg"
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
            return
        }

    }
}
