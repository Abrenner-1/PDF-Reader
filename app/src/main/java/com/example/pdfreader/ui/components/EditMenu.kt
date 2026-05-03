package com.example.pdfreader.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pdfreader.ShapeType
import com.example.pdfreader.TextAnnotation
import com.example.pdfreader.HighlightAnnotation
import com.example.pdfreader.ShapeAnnotation
import com.example.pdfreader.ui.ToolType
import androidx.compose.foundation.border

@Composable
fun EditSecondaryMenu(
    currentTool: ToolType,
    currentShapeType: ShapeType,
    onToolSelected: (ToolType) -> Unit,
    onShapeSelected: (ShapeType) -> Unit,
    onClose: () -> Unit,
    selectedTextAnnotationId: String?,
    textAnnotations: List<TextAnnotation>,
    onUpdateText: (TextAnnotation) -> Unit,
    onDeleteText: (String) -> Unit,
    selectedHighlightId: String?,
    highlightAnnotations: List<HighlightAnnotation>,
    onUpdateHighlight: (HighlightAnnotation) -> Unit,
    onDeleteHighlight: (String) -> Unit,
    selectedShapeId: String?,
    shapeAnnotations: List<ShapeAnnotation>,
    onUpdateShape: (ShapeAnnotation) -> Unit,
    onDeleteShape: (String) -> Unit
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
            
            // Add Text Tool
            IconButton(
                onClick = { onToolSelected(ToolType.ADD_TEXT) },
                modifier = Modifier.background(if (currentTool == ToolType.ADD_TEXT) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, CircleShape)
            ) { Text("T", fontWeight = FontWeight.Bold, fontSize = 20.sp) }
            
            // Highlight Tool
            IconButton(
                onClick = { onToolSelected(ToolType.HIGHLIGHT) },
                modifier = Modifier.background(if (currentTool == ToolType.HIGHLIGHT) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, CircleShape)
            ) { Icon(Icons.Filled.Create, contentDescription = "Highlight") }
            
            // Shape tools
            ShapeType.values().forEach { type ->
                val isSelected = currentShapeType == type && currentTool == ToolType.SHAPE
                IconButton(
                    onClick = { 
                        onToolSelected(ToolType.SHAPE)
                        onShapeSelected(type)
                    },
                    modifier = Modifier.background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, RoundedCornerShape(8.dp))
                ) {
                    val iconColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    Canvas(modifier = Modifier.size(20.dp)) {
                        val strokeW = 4f
                        val padding = 2f
                        when(type) {
                            ShapeType.RECTANGLE -> drawRect(color = iconColor, style = Stroke(width = strokeW), topLeft = Offset(padding, padding), size = androidx.compose.ui.geometry.Size(size.width - padding*2, size.height - padding*2))
                            ShapeType.OVAL -> drawOval(color = iconColor, style = Stroke(width = strokeW), topLeft = Offset(padding, padding), size = androidx.compose.ui.geometry.Size(size.width - padding*2, size.height - padding*2))
                            ShapeType.LINE -> drawLine(color = iconColor, strokeWidth = strokeW, start = Offset(padding, size.height - padding), end = Offset(size.width - padding, padding))
                            ShapeType.ARROW -> {
                                drawLine(color = iconColor, strokeWidth = strokeW, start = Offset(padding, size.height - padding), end = Offset(size.width - padding, padding))
                                drawLine(color = iconColor, strokeWidth = strokeW, start = Offset(size.width - padding, padding), end = Offset(size.width - padding - 10f, padding))
                                drawLine(color = iconColor, strokeWidth = strokeW, start = Offset(size.width - padding, padding), end = Offset(size.width - padding, padding + 10f))
                            }
                        }
                    }
                }
            }
            
            // Formatters
            if (selectedTextAnnotationId != null) {
                val selectedAnnotation = textAnnotations.find { it.id == selectedTextAnnotationId }
                if (selectedAnnotation != null) {
                    TextFormattingMenu(
                        annotation = selectedAnnotation,
                        onUpdate = onUpdateText,
                        onDelete = { onDeleteText(selectedTextAnnotationId) }
                    )
                }
            }
            if (selectedHighlightId != null) {
                val selectedHighlight = highlightAnnotations.find { it.id == selectedHighlightId }
                if (selectedHighlight != null) {
                    HighlightFormattingMenu(
                        annotation = selectedHighlight,
                        onUpdate = onUpdateHighlight,
                        onDelete = { onDeleteHighlight(selectedHighlightId) }
                    )
                }
            }
            if (selectedShapeId != null) {
                val selectedShape = shapeAnnotations.find { it.id == selectedShapeId }
                if (selectedShape != null) {
                    ShapeFormattingMenu(
                        annotation = selectedShape,
                        onUpdate = onUpdateShape,
                        onDelete = { onDeleteShape(selectedShapeId) }
                    )
                }
            }
        }
    }
}
@Composable
fun TextFormattingMenu(
    annotation: TextAnnotation,
    onUpdate: (TextAnnotation) -> Unit,
    onDelete: () -> Unit
) {
    val colors = listOf(Color.Black, Color.Red, Color.Blue, Color(0xFF009688), Color(0xFFFF9800))
    val bgColors = listOf(Color.Transparent, Color.Yellow.copy(alpha = 0.5f), Color.Green.copy(alpha = 0.3f), Color.LightGray.copy(alpha=0.5f))
    val fonts = listOf("Helvetica", "Times Roman", "Courier")
    
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Row: Font Controls & Delete
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Font Size Stepper
                Row(
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onUpdate(annotation.copy(fontSize = annotation.fontSize - 2f)) }, modifier = Modifier.size(32.dp)) { Text("-", fontWeight = FontWeight.Bold) }
                    Text("${annotation.fontSize.toInt()}", modifier = Modifier.padding(horizontal = 4.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    IconButton(onClick = { onUpdate(annotation.copy(fontSize = annotation.fontSize + 2f)) }, modifier = Modifier.size(32.dp)) { Text("+", fontWeight = FontWeight.Bold) }
                }
                
                // Font Family Dropdown
                var expanded by remember { mutableStateOf(false) }
                Box {
                    Button(
                        onClick = { expanded = true },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(annotation.fontFamily, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = "Select Font", modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        fonts.forEach { font ->
                            DropdownMenuItem(
                                text = { Text(font) },
                                onClick = { 
                                    onUpdate(annotation.copy(fontFamily = font))
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(4.dp))
                
                // Delete Button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.errorContainer, CircleShape)
                ) { 
                    Icon(Icons.Filled.Close, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
                }
            }
            
            // Middle Row: Styles & Text Color
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rich Text Toggles
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { onUpdate(annotation.copy(isBold = !annotation.isBold)) },
                        modifier = Modifier.size(32.dp).background(if(annotation.isBold) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                    ) { Text("B", fontWeight = FontWeight.ExtraBold, color = if(annotation.isBold) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface) }
                    
                    IconButton(
                        onClick = { onUpdate(annotation.copy(isItalic = !annotation.isItalic)) },
                        modifier = Modifier.size(32.dp).background(if(annotation.isItalic) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                    ) { Text("I", fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold, color = if(annotation.isItalic) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface) }
                    
                    IconButton(
                        onClick = { onUpdate(annotation.copy(isUnderlined = !annotation.isUnderlined)) },
                        modifier = Modifier.size(32.dp).background(if(annotation.isUnderlined) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                    ) { Text("U", textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Bold, color = if(annotation.isUnderlined) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface) }
                    
                    IconButton(
                        onClick = { onUpdate(annotation.copy(isStrikethrough = !annotation.isStrikethrough)) },
                        modifier = Modifier.size(32.dp).background(if(annotation.isStrikethrough) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                    ) { Text("S", textDecoration = TextDecoration.LineThrough, fontWeight = FontWeight.Bold, color = if(annotation.isStrikethrough) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface) }
                }
                
                // Text Color Picker
                var textColorExpanded by remember { mutableStateOf(false) }
                Box {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                            .clickable { textColorExpanded = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text("A", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                            Box(modifier = Modifier.height(3.dp).width(16.dp).background(annotation.color))
                        }
                    }
                    DropdownMenu(expanded = textColorExpanded, onDismissRequest = { textColorExpanded = false }) {
                        Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            colors.forEach { c ->
                                Box(modifier = Modifier.size(24.dp).background(c, CircleShape).clickable { onUpdate(annotation.copy(color = c)); textColorExpanded = false })
                            }
                        }
                    }
                }
                
                // Background Color Picker
                var bgColorExpanded by remember { mutableStateOf(false) }
                Box {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                            .clickable { bgColorExpanded = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(annotation.backgroundColor ?: Color.Transparent, RoundedCornerShape(4.dp))
                                .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.5f), RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (annotation.backgroundColor == null) {
                                Icon(Icons.Filled.Close, contentDescription = "None", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    DropdownMenu(expanded = bgColorExpanded, onDismissRequest = { bgColorExpanded = false }) {
                        Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            bgColors.forEach { c ->
                                val isTransparent = c == Color.Transparent
                                val modifier = if (isTransparent) Modifier.background(MaterialTheme.colorScheme.surface, CircleShape) else Modifier.background(c, CircleShape)
                                Box(
                                    modifier = Modifier.size(24.dp).then(modifier).clickable { 
                                        onUpdate(annotation.copy(backgroundColor = if(isTransparent) null else c))
                                        bgColorExpanded = false 
                                    },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isTransparent) Icon(Icons.Filled.Close, contentDescription = "None", modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HighlightFormattingMenu(
    annotation: HighlightAnnotation,
    onUpdate: (HighlightAnnotation) -> Unit,
    onDelete: () -> Unit
) {
    val colors = listOf(Color.Yellow, Color.Green, Color.Cyan, Color.Magenta, Color(0xFFFF9800), Color.Red)
    
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp).width(200.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Row: Colors & Delete
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Color Picker
                var colorExpanded by remember { mutableStateOf(false) }
                Box {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                            .clickable { colorExpanded = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(annotation.color, RoundedCornerShape(4.dp))
                                .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.5f), RoundedCornerShape(4.dp))
                        )
                    }
                    DropdownMenu(expanded = colorExpanded, onDismissRequest = { colorExpanded = false }) {
                        Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            colors.forEach { c ->
                                Box(
                                    modifier = Modifier.size(24.dp).background(c.copy(alpha = annotation.color.alpha), CircleShape)
                                        .clickable { 
                                            onUpdate(annotation.copy(color = c.copy(alpha = annotation.color.alpha)))
                                            colorExpanded = false 
                                        }
                                )
                            }
                        }
                    }
                }
                
                // Delete Button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.errorContainer, CircleShape)
                ) { 
                    Icon(Icons.Filled.Close, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
                }
            }
            
            // Bottom Row: Transparency Slider
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Alpha", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = annotation.color.alpha,
                    onValueChange = { newAlpha ->
                        onUpdate(annotation.copy(color = annotation.color.copy(alpha = newAlpha)))
                    },
                    valueRange = 0.1f..1.0f,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun ShapeFormattingMenu(
    annotation: ShapeAnnotation,
    onUpdate: (ShapeAnnotation) -> Unit,
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
                
                // Alpha Slider
                Slider(
                    value = annotation.alpha,
                    onValueChange = { onUpdate(annotation.copy(alpha = it)) },
                    valueRange = 0.1f..1f,
                    modifier = Modifier.width(100.dp)
                )
                
                // Stroke Width Slider
                Slider(
                    value = annotation.strokeWidth,
                    onValueChange = { onUpdate(annotation.copy(strokeWidth = it)) },
                    valueRange = 1f..20f,
                    modifier = Modifier.width(100.dp)
                )
                
                IconButton(onClick = onDelete, modifier = Modifier.background(MaterialTheme.colorScheme.errorContainer, CircleShape)) {
                    Icon(Icons.Filled.Close, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            if (annotation.type == ShapeType.RECTANGLE || annotation.type == ShapeType.OVAL) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Filled:")
                    Checkbox(checked = annotation.isFilled, onCheckedChange = { onUpdate(annotation.copy(isFilled = it)) })
                }
            }
        }
    }
}
