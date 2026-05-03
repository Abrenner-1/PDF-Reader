package com.example.pdfreader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import java.util.UUID

data class TextAnnotation(
    val id: String = UUID.randomUUID().toString(),
    val pageIndex: Int,
    var text: String,
    var x: Float, // Relative X coordinate (0f to 1f) based on view width
    var y: Float, // Relative Y coordinate (0f to 1f) based on view height
    var fontSize: Float = 16f,
    var color: Color = Color.Black,
    var fontFamily: String = "Helvetica",
    var backgroundColor: Color? = null,
    var isBold: Boolean = false,
    var isItalic: Boolean = false,
    var isUnderlined: Boolean = false,
    var isStrikethrough: Boolean = false
)

data class HighlightAnnotation(
    val id: String = UUID.randomUUID().toString(),
    val pageIndex: Int,
    var x: Float, // Relative X coordinate (0f to 1f)
    var y: Float, // Relative Y coordinate (0f to 1f)
    var width: Float, // Relative width
    var height: Float, // Relative height
    var color: Color = Color.Yellow.copy(alpha = 0.4f)
)

enum class ShapeType { RECTANGLE, OVAL, LINE, ARROW }

data class ShapeAnnotation(
    val id: String = UUID.randomUUID().toString(),
    val pageIndex: Int,
    var type: ShapeType,
    var startX: Float,
    var startY: Float,
    var endX: Float,
    var endY: Float,
    var color: Color = Color.Red,
    var alpha: Float = 1f,
    var strokeWidth: Float = 3f,
    var isFilled: Boolean = false
)
data class SignatureAnnotation(
    val id: String = UUID.randomUUID().toString(),
    val pageIndex: Int,
    var strokes: List<List<Offset>>, // Normalized 0f..1f relative to its own bounding box
    var startX: Float,
    var startY: Float,
    var endX: Float,
    var endY: Float,
    var color: Color = Color.Black,
    var strokeWidth: Float = 3f
)
