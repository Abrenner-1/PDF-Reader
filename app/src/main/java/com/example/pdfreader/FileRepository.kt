package com.example.pdfreader

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class FileRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("pdf_reader_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val FILES_KEY = "recent_files"

    fun getFiles(): List<PdfFile> {
        val json = prefs.getString(FILES_KEY, null)
        if (json.isNullOrEmpty()) return emptyList()
        val type = object : TypeToken<List<PdfFile>>() {}.type
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addOrUpdateFile(newFile: PdfFile) {
        val currentFiles = getFiles().toMutableList()
        // Remove if exists to update its position and date
        val existingIndex = currentFiles.indexOfFirst { it.uriString == newFile.uriString }
        if (existingIndex != -1) {
            val existing = currentFiles[existingIndex]
            currentFiles.removeAt(existingIndex)
            // Preserve starred state if not explicitly passed differently
            currentFiles.add(0, newFile.copy(isStarred = existing.isStarred))
        } else {
            currentFiles.add(0, newFile)
        }
        
        saveFiles(currentFiles)
    }

    fun toggleStar(uriString: String) {
        val currentFiles = getFiles().toMutableList()
        val index = currentFiles.indexOfFirst { it.uriString == uriString }
        if (index != -1) {
            val existing = currentFiles[index]
            currentFiles[index] = existing.copy(isStarred = !existing.isStarred)
            saveFiles(currentFiles)
        }
    }

    fun removeFile(uriString: String) {
        val currentFiles = getFiles().toMutableList()
        currentFiles.removeAll { it.uriString == uriString }
        saveFiles(currentFiles)
    }

    private fun saveFiles(files: List<PdfFile>) {
        val json = gson.toJson(files)
        prefs.edit().putString(FILES_KEY, json).apply()
    }
}
