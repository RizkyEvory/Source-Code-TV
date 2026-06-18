package com.example.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.data.model.Channel
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object FileUtils {
    fun exportToM3u(context: Context, fileName: String, channels: List<Channel>): Boolean {
        val builder = java.lang.StringBuilder()
        builder.append("#EXTM3U\n")
        for (channel in channels) {
            builder.append("#EXTINF:-1 tvg-id=\"\" tvg-name=\"${channel.name}\" tvg-logo=\"${channel.logoUrl ?: ""}\" group-title=\"${channel.group}\",${channel.name}\n")
            builder.append("${channel.streamUrl}\n")
        }
        val m3uContent = builder.toString()

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "audio/x-mpegurl")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues) ?: return false
                val outputStream: OutputStream = resolver.openOutputStream(uri) ?: return false
                outputStream.use {
                    it.write(m3uContent.toByteArray())
                }
                true
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, fileName)
                val outputStream = FileOutputStream(file)
                outputStream.use {
                    it.write(m3uContent.toByteArray())
                }
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
