package com.example.serverapp

import org.junit.Assert.assertEquals
import org.junit.Assert.*
import org.junit.Test

object SettingsHelper {
    private const val DEFAULT_IP = "127.0.0.1"
    private const val DEFAULT_MAX_LENGTH = 60

    fun formatIp(ip: String?): String {
        return if (ip.isNullOrBlank() || ip == DEFAULT_IP) {
            "Не задано"
        } else {
            ip
        }
    }

    fun formatMaxLength(length: Int?): String {
        val len = length ?: DEFAULT_MAX_LENGTH
        return "${len} сек."
    }
}

class SettingsHelperTest {
    @Test
    fun `formatIp returns not set when null`() {
        assertEquals("Не задано", SettingsHelper.formatIp(null))
    }

    @Test
    fun `formatIp returns not set when blank`() {
        assertEquals("Не задано", SettingsHelper.formatIp(""))
    }

    @Test
    fun `formatIp returns not set when default IP`() {
        assertEquals("Не задано", SettingsHelper.formatIp("127.0.0.1"))
    }

    @Test
    fun `formatIp returns custom IP`() {
        assertEquals("192.168.0.5", SettingsHelper.formatIp("192.168.0.5"))
    }

    @Test
    fun `formatMaxLength returns default when null`() {
        assertEquals("60 сек.", SettingsHelper.formatMaxLength(null))
    }

    @Test
    fun `formatMaxLength returns default when zero`() {
        assertEquals("0 сек.", SettingsHelper.formatMaxLength(0))
    }

    @Test
    fun `formatMaxLength returns custom length`() {
        assertEquals("120 сек.", SettingsHelper.formatMaxLength(120))
    }
}

object ScanUtils {
    /**
     * Проверяет, превышает ли длительность видео максимальную длину (в секундах).
     */
    fun isVideoTooLong(durationMs: Long, maxLengthSec: Int): Boolean {
        return (durationMs / 1000) > maxLengthSec
    }

    /**
     * Генерирует корректное имя файла на основе raw-пути и mime-типа.
     * Если raw содержит расширение, возвращает raw, иначе добавляет расширение из mime.
     * Для неподдерживаемых image MIME-типов (не начинающихся с "image/") по умолчанию добавляет ".jpg",
     * для видео MIME-типов — ".mp4".
     */
    fun extractFilename(rawSegment: String?, mime: String, isVideo: Boolean): String {
        val raw = rawSegment
            ?.substringAfterLast('/')
            ?.substringBefore('?')
            ?: "upload"
        if ("." in raw) return raw

        val ext = when {
            isVideo -> "mp4"
            mime.startsWith("image/") -> mime.substringAfterLast('/').takeIf { it.isNotBlank() }
            else -> null
        } ?: if (isVideo) "mp4" else "jpg"

        return "${raw}.${ext}"
    }
}

object HistoryUtils {
    /**
     * Определяет, является ли файл видео на основании расширения имени,
     * обрезая query-параметры.
     */
    fun isVideoFile(filename: String): Boolean {
        // Убираем query-параметры и фрагменты
        val cleanName = filename.substringBefore('?').substringBefore('#').trim().lowercase()
        return cleanName.endsWith(".mp4")
    }/**
     * Форматирует текст статистики для заданного ярлыка и количества.
     */
    fun formatStat(label: String, count: Int): String {
        return "$label: $count"
    }
}

class ScanUtilsTest {
    @Test
    fun `isVideoTooLong returns false when equal to max`() {
        assertFalse(ScanUtils.isVideoTooLong(durationMs = 60000, maxLengthSec = 60))
    }

    @Test
    fun `isVideoTooLong returns true when one second over`() {
        assertTrue(ScanUtils.isVideoTooLong(durationMs = 61000, maxLengthSec = 60))
    }

    @Test
    fun `isVideoTooLong returns false when just under max`() {
        assertFalse(ScanUtils.isVideoTooLong(durationMs = 59999, maxLengthSec = 60))
    }

    @Test
    fun `isVideoTooLong handles zero and negative duration`() {
        assertFalse(ScanUtils.isVideoTooLong(durationMs = 0, maxLengthSec = 60))
        assertFalse(ScanUtils.isVideoTooLong(durationMs = -1000, maxLengthSec = 60))
    }

    @Test
    fun `extractFilename uses raw with extension`() {
        val raw = "file.name.png?token=123"
        val filename = ScanUtils.extractFilename(rawSegment = raw, mime = "image/png", isVideo = false)
        assertEquals("file.name.png", filename)
    }

    @Test
    fun `extractFilename adds extension from mime for image`() {
        val filename = ScanUtils.extractFilename(rawSegment = "file", mime = "image/jpeg", isVideo = false)
        assertEquals("file.jpeg", filename)
    }

    @Test
    fun `extractFilename adds default mp4 for video when mime missing`() {
        val filename = ScanUtils.extractFilename(rawSegment = "videoFile", mime = "", isVideo = true)
        assertEquals("videoFile.mp4", filename)
    }

    @Test
    fun `extractFilename uses upload when raw null`() {
        val filename = ScanUtils.extractFilename(rawSegment = null, mime = "image/png", isVideo = false)
        assertEquals("upload.png", filename)
    }

    @Test
    fun `extractFilename falls back to jpg for image when mime invalid`() {
        val filename = ScanUtils.extractFilename(rawSegment = "pic", mime = "application/octet-stream", isVideo = false)
        assertEquals("pic.jpg", filename)
    }
}

class HistoryUtilsTest {
    @Test
    fun `isVideoFile detects mp4`() {
        assertTrue(HistoryUtils.isVideoFile("movie.mp4"))
    }

    @Test
    fun `isVideoFile detects case insensitive`() {
        assertTrue(HistoryUtils.isVideoFile("CLIP.MP4"))
    }

    @Test
    fun `isVideoFile returns false for image`() {
        assertFalse(HistoryUtils.isVideoFile("photo.jpg"))
    }

    @Test
    fun `isVideoFile returns false for filenames containing mp4 but not as extension`() {
        assertFalse(HistoryUtils.isVideoFile("my_mp4_backup.txt"))
    }

    @Test
    fun `isVideoFile handles filenames with query params`() {
        assertTrue(HistoryUtils.isVideoFile("video.mp4?token=xyz"))
    }

    @Test
    fun `formatStat combines label and count`() {
        assertEquals("Bags: 5", HistoryUtils.formatStat("Bags", 5))
    }

    @Test
    fun `formatStat handles zero and negative counts`() {
        assertEquals("Items: 0", HistoryUtils.formatStat("Items", 0))
        assertEquals("Errors: -1", HistoryUtils.formatStat("Errors", -1))
    }
}
