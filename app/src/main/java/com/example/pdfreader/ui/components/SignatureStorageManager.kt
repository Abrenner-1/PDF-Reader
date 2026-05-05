package com.example.pdfreader.ui.components

import android.content.Context
import androidx.compose.ui.geometry.Offset
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

data class SavedSignature(
    val id: String,
    val strokes: List<List<FloatArray>>
) {
    fun toOffsetStrokes(): List<List<Offset>> {
        return strokes.map { stroke ->
            stroke.map { pt -> Offset(pt[0], pt[1]) }
        }
    }

    companion object {
        fun fromOffsetStrokes(strokes: List<List<Offset>>): SavedSignature {
            val floatStrokes = strokes.map { stroke ->
                stroke.map { pt -> floatArrayOf(pt.x, pt.y) }
            }
            return SavedSignature(id = UUID.randomUUID().toString(), strokes = floatStrokes)
        }
    }
}

class SignatureStorageManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("signatures_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveSignature(signature: SavedSignature) {
        val currentSignatures = getSignatures().toMutableList()
        currentSignatures.add(signature)
        
        val json = gson.toJson(currentSignatures)
        prefs.edit().putString("saved_signatures", json).apply()
    }

    fun deleteSignature(id: String) {
        val currentSignatures = getSignatures().toMutableList()
        currentSignatures.removeIf { it.id == id }
        
        val json = gson.toJson(currentSignatures)
        prefs.edit().putString("saved_signatures", json).apply()
    }

    fun getSignatures(): List<SavedSignature> {
        val json = prefs.getString("saved_signatures", null) ?: return emptyList()
        val type = object : TypeToken<List<SavedSignature>>() {}.type
        return try {
            val list: List<SavedSignature> = gson.fromJson(json, type) ?: emptyList()
            list.filter { sig ->
                sig.strokes.all { stroke ->
                    stroke.all { pt -> kotlin.math.abs(pt[0]) <= 2f && kotlin.math.abs(pt[1]) <= 2f }
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
