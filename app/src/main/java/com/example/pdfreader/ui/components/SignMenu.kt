package com.example.pdfreader.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import com.example.pdfreader.SignatureAnnotation
import androidx.compose.foundation.border

@Composable
fun SignSecondaryMenu(
    onClose: () -> Unit,
    savedSignatures: List<SavedSignature>,
    onSelectSignature: (SavedSignature) -> Unit,
    onDeleteSignature: (String) -> Unit,
    onCreateSignature: () -> Unit,
    selectedSignatureId: String?,
    signatureAnnotations: List<SignatureAnnotation>,
    onUpdateSignature: (SignatureAnnotation) -> Unit,
    onDeleteAnnotation: (String) -> Unit
) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close Tool")
            }
            Divider(modifier = Modifier.height(24.dp).width(1.dp).padding(horizontal = 4.dp))
            
            // Create New Signature Button
            IconButton(
                onClick = onCreateSignature,
                modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
            ) { 
                Icon(Icons.Filled.Add, contentDescription = "Create Signature", tint = MaterialTheme.colorScheme.onPrimaryContainer) 
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Saved Signatures List
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(savedSignatures) { sig ->
                    Surface(
                        modifier = Modifier
                            .size(60.dp, 40.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { onSelectSignature(sig) },
                                    onLongPress = { onDeleteSignature(sig.id) }
                                )
                            },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                            val path = Path()
                            val strokes = sig.toOffsetStrokes()
                            strokes.forEach { stroke ->
                                if (stroke.size > 1) {
                                    val startX = stroke.first().x * size.width
                                    val startY = stroke.first().y * size.height
                                    path.moveTo(startX, startY)
                                    for (i in 1 until stroke.size) {
                                        path.lineTo(stroke[i].x * size.width, stroke[i].y * size.height)
                                    }
                                } else if (stroke.size == 1) {
                                    drawCircle(Color.Black, radius = 1.5f, center = androidx.compose.ui.geometry.Offset(stroke.first().x * size.width, stroke.first().y * size.height))
                                }
                            }
                            drawPath(path, color = Color.Black, style = Stroke(width = 2f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                        }
                    }
                }
            }
            
            // Formatting Menu
            if (selectedSignatureId != null) {
                val selectedSignature = signatureAnnotations.find { it.id == selectedSignatureId }
                if (selectedSignature != null) {
                    SignatureFormattingMenu(
                        annotation = selectedSignature,
                        onUpdate = onUpdateSignature,
                        onDelete = { onDeleteAnnotation(selectedSignatureId) }
                    )
                }
            }
        }
    }
}
@Composable
fun SignatureFormattingMenu(
    annotation: SignatureAnnotation,
    onUpdate: (SignatureAnnotation) -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                // Color Picker
                val colors = listOf(Color.Red, Color.Blue, Color.Green, Color.Black, Color(1f, 1f, 0f), Color.White)
                var expanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { expanded = true }) {
                        Box(modifier = Modifier.size(24.dp).background(annotation.color.copy(alpha = 1f), CircleShape).border(1.dp, Color.Gray, CircleShape))
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        colors.forEach { c ->
                            DropdownMenuItem(
                                text = { Box(modifier = Modifier.size(24.dp).background(c, CircleShape).border(1.dp, Color.Gray, CircleShape)) },
                                onClick = { onUpdate(annotation.copy(color = c)); expanded = false }
                            )
                        }
                    }
                }
                
                // Stroke Width Slider
                Slider(
                    value = annotation.strokeWidth,
                    onValueChange = { onUpdate(annotation.copy(strokeWidth = it)) },
                    valueRange = 1f..10f,
                    modifier = Modifier.width(100.dp)
                )
                
                IconButton(onClick = onDelete, modifier = Modifier.background(MaterialTheme.colorScheme.errorContainer, CircleShape)) {
                    Icon(Icons.Filled.Close, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}
