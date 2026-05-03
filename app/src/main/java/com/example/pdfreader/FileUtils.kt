package com.example.pdfreader

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

fun getFileMetadata(context: Context, uri: Uri): Pair<String, Long> {
    var name = "Unknown PDF"
    var size = 0L
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) name = cursor.getString(nameIndex) ?: "Unknown PDF"
                
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return Pair(name, size)
}
