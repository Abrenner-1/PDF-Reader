package com.example.pdfreader.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.window.Popup
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pdfreader.TextAnnotation
import com.example.pdfreader.HighlightAnnotation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.material3.Slider
import com.example.pdfreader.ShapeAnnotation
import com.example.pdfreader.ShapeType
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas

enum class ToolType { SCROLL, ADD_TEXT, HIGHLIGHT, SHAPE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    pageCount: Int,
    getPageBitmap: suspend (Int, Float) -> Bitmap?,
    onSaveRequested: (List<TextAnnotation>, List<HighlightAnnotation>, List<ShapeAnnotation>) -> Unit,
    onCloseDocument: () -> Unit
) {
    var sidebarOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var currentTool by remember { mutableStateOf(ToolType.SCROLL) }
    
    // Store our interactive annotations
    val textAnnotations = remember { mutableStateListOf<TextAnnotation>() }
    val highlightAnnotations = remember { mutableStateListOf<HighlightAnnotation>() }
    val shapeAnnotations = remember { mutableStateListOf<ShapeAnnotation>() }
    
    // Track which annotation is selected for formatting/deleting
    var selectedTextAnnotationId by remember { mutableStateOf<String?>(null) }
    var selectedHighlightId by remember { mutableStateOf<String?>(null) }
    var selectedShapeId by remember { mutableStateOf<String?>(null) }
    var currentShapeType by remember { mutableStateOf(ShapeType.RECTANGLE) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(selectedTextAnnotationId) {
        if (selectedTextAnnotationId == null) {
            focusManager.clearFocus()
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        if (sidebarOpen) {
            SidebarComponent(
                pageCount = pageCount,
                getPageBitmap = { getPageBitmap(it, 0.2f) },
                onPageSelected = { index ->
                    coroutineScope.launch {
                        listState.animateScrollToItem(index)
                    }
                }
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.DarkGray)
                    // If user clicks outside a text box, deselect it
                    .pointerInput(Unit) {
                        detectTapGestures { 
                            selectedTextAnnotationId = null 
                            selectedHighlightId = null
                            selectedShapeId = null
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(pageCount) { index ->
                    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
                    
                    LaunchedEffect(index) {
                        bitmap = getPageBitmap(index, 1f)
                    }

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.75f) // Approximate PDF aspect ratio A4
                            .background(Color.White)
                            .pointerInput(currentTool, selectedTextAnnotationId) {
                                if (currentTool == ToolType.ADD_TEXT || selectedTextAnnotationId != null) {
                                    detectTapGestures { offset ->
                                        if (selectedTextAnnotationId != null) {
                                            selectedTextAnnotationId = null
                                        } else if (currentTool == ToolType.ADD_TEXT) {
                                            val relativeX = offset.x / size.width
                                            val relativeY = offset.y / size.height
                                            val newAnnotation = TextAnnotation(
                                                pageIndex = index,
                                                text = "",
                                                x = relativeX,
                                                y = relativeY
                                            )
                                            textAnnotations.add(newAnnotation)
                                            selectedTextAnnotationId = newAnnotation.id
                                        }
                                    }
                                } else if (currentTool == ToolType.HIGHLIGHT) {
                                    var currentHighlight: HighlightAnnotation? = null
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            val relativeX = offset.x / size.width
                                            val relativeY = offset.y / size.height
                                            val newHighlight = HighlightAnnotation(
                                                pageIndex = index,
                                                x = relativeX,
                                                y = relativeY,
                                                width = 0f,
                                                height = 0f
                                            )
                                            highlightAnnotations.add(newHighlight)
                                            currentHighlight = newHighlight
                                            selectedHighlightId = newHighlight.id
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            currentHighlight?.let { highlight ->
                                                val idx = highlightAnnotations.indexOfFirst { it.id == highlight.id }
                                                if (idx != -1) {
                                                    val current = highlightAnnotations[idx]
                                                    val newWidth = current.width + (dragAmount.x / size.width)
                                                    val newHeight = current.height + (dragAmount.y / size.height)
                                                    
                                                    // Allow drawing backwards by adjusting x/y and making width/height positive
                                                    highlightAnnotations[idx] = current.copy(
                                                        width = newWidth,
                                                        height = newHeight
                                                    )
                                                }
                                            }
                                        }
                                    )
                                } else if (currentTool == ToolType.SHAPE) {
                                    var currentShape: ShapeAnnotation? = null
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            val relativeX = offset.x / size.width
                                            val relativeY = offset.y / size.height
                                            val newShape = ShapeAnnotation(
                                                pageIndex = index,
                                                type = currentShapeType,
                                                startX = relativeX,
                                                startY = relativeY,
                                                endX = relativeX,
                                                endY = relativeY
                                            )
                                            shapeAnnotations.add(newShape)
                                            currentShape = newShape
                                            selectedShapeId = newShape.id
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            currentShape?.let { shape ->
                                                val idx = shapeAnnotations.indexOfFirst { it.id == shape.id }
                                                if (idx != -1) {
                                                    val current = shapeAnnotations[idx]
                                                    val newEndX = current.endX + (dragAmount.x / size.width)
                                                    val newEndY = current.endY + (dragAmount.y / size.height)
                                                    shapeAnnotations[idx] = current.copy(
                                                        endX = newEndX,
                                                        endY = newEndY
                                                    )
                                                }
                                            }
                                        }
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        bitmap?.let { b ->
                            Image(
                                bitmap = b.asImageBitmap(),
                                contentDescription = "Page $index",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } ?: CircularProgressIndicator()
                        
                        val boxWidth = maxWidth
                        val boxHeight = maxHeight
                        
                        // Render Canvas Shapes
                        Canvas(modifier = Modifier.fillMaxSize().pointerInput(shapeAnnotations) {
                            detectTapGestures { offset ->
                                val tapX = offset.x / size.width
                                val tapY = offset.y / size.height
                                
                                // Find if tap is within any shape bounds
                                val clickedShape = shapeAnnotations.filter { it.pageIndex == index }.findLast { shape ->
                                    val minX = minOf(shape.startX, shape.endX) - 0.05f
                                    val maxX = maxOf(shape.startX, shape.endX) + 0.05f
                                    val minY = minOf(shape.startY, shape.endY) - 0.05f
                                    val maxY = maxOf(shape.startY, shape.endY) + 0.05f
                                    tapX in minX..maxX && tapY in minY..maxY
                                }
                                
                                if (clickedShape != null) {
                                    selectedShapeId = clickedShape.id
                                    selectedTextAnnotationId = null
                                    selectedHighlightId = null
                                } else if (currentTool == ToolType.SCROLL) {
                                    selectedShapeId = null
                                    selectedTextAnnotationId = null
                                    selectedHighlightId = null
                                }
                            }
                        }) {
                            shapeAnnotations.filter { it.pageIndex == index }.forEach { shape ->
                                val sX = boxWidth.toPx() * shape.startX
                                val sY = boxHeight.toPx() * shape.startY
                                val eX = boxWidth.toPx() * shape.endX
                                val eY = boxHeight.toPx() * shape.endY
                                val w = eX - sX
                                val h = eY - sY
                                
                                val rectStyle = if (shape.isFilled) Fill else Stroke(width = shape.strokeWidth)
                                val colorWithAlpha = shape.color.copy(alpha = shape.alpha)
                                val isSelected = selectedShapeId == shape.id
                                
                                // Draw a selection bounding box if selected
                                if (isSelected) {
                                    drawRect(
                                        color = Color.Blue.copy(alpha = 0.3f),
                                        topLeft = Offset(minOf(sX, eX) - 10f, minOf(sY, eY) - 10f),
                                        size = androidx.compose.ui.geometry.Size(kotlin.math.abs(w) + 20f, kotlin.math.abs(h) + 20f),
                                        style = Stroke(width = 2f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                                    )
                                }
                                
                                when (shape.type) {
                                    ShapeType.RECTANGLE -> {
                                        drawRect(
                                            color = colorWithAlpha,
                                            topLeft = Offset(minOf(sX, eX), minOf(sY, eY)),
                                            size = androidx.compose.ui.geometry.Size(kotlin.math.abs(w), kotlin.math.abs(h)),
                                            style = rectStyle
                                        )
                                    }
                                    ShapeType.OVAL -> {
                                        drawOval(
                                            color = colorWithAlpha,
                                            topLeft = Offset(minOf(sX, eX), minOf(sY, eY)),
                                            size = androidx.compose.ui.geometry.Size(kotlin.math.abs(w), kotlin.math.abs(h)),
                                            style = rectStyle
                                        )
                                    }
                                    ShapeType.LINE -> {
                                        drawLine(
                                            color = colorWithAlpha,
                                            start = Offset(sX, sY),
                                            end = Offset(eX, eY),
                                            strokeWidth = shape.strokeWidth
                                        )
                                    }
                                    ShapeType.ARROW -> {
                                        drawLine(
                                            color = colorWithAlpha,
                                            start = Offset(sX, sY),
                                            end = Offset(eX, eY),
                                            strokeWidth = shape.strokeWidth
                                        )
                                        val angle = kotlin.math.atan2((eY - sY).toDouble(), (eX - sX).toDouble())
                                        val arrowSize = 25f
                                        val angle1 = angle + Math.PI * 0.8
                                        val angle2 = angle - Math.PI * 0.8
                                        
                                        val p1X = eX + arrowSize * kotlin.math.cos(angle1).toFloat()
                                        val p1Y = eY + arrowSize * kotlin.math.sin(angle1).toFloat()
                                        
                                        val p2X = eX + arrowSize * kotlin.math.cos(angle2).toFloat()
                                        val p2Y = eY + arrowSize * kotlin.math.sin(angle2).toFloat()
                                        
                                        val path = Path().apply {
                                            moveTo(eX, eY)
                                            lineTo(p1X, p1Y)
                                            moveTo(eX, eY)
                                            lineTo(p2X, p2Y)
                                        }
                                        drawPath(
                                            path = path,
                                            color = colorWithAlpha,
                                            style = Stroke(width = shape.strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                                        )
                                    }
                                }
                            }
                        }

                        // Render Highlights (below text)
                        highlightAnnotations.filter { it.pageIndex == index }.forEach { highlight ->
                            val isSelected = selectedHighlightId == highlight.id
                            
                            // Handle negative widths/heights if user dragged backwards
                            val renderX = if (highlight.width < 0) highlight.x + highlight.width else highlight.x
                            val renderY = if (highlight.height < 0) highlight.y + highlight.height else highlight.y
                            val renderWidth = kotlin.math.abs(highlight.width)
                            val renderHeight = kotlin.math.abs(highlight.height)
                            
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .offset(
                                        x = boxWidth * renderX,
                                        y = boxHeight * renderY
                                    )
                                    .size(
                                        width = boxWidth * renderWidth,
                                        height = boxHeight * renderHeight
                                    )
                                    .pointerInput(highlight.id) {
                                        detectTapGestures { 
                                            selectedHighlightId = highlight.id 
                                            selectedTextAnnotationId = null
                                        }
                                    }
                                    .background(highlight.color)
                            ) {
                                if (isSelected) {
                                    Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.3f)))
                                }
                            }
                        }

                        // Render our interactive text overlays for this page
                        textAnnotations.filter { it.pageIndex == index }.forEach { annotation ->
                            key(annotation.id) {
                                val isSelected = selectedTextAnnotationId == annotation.id
                                val focusRequester = remember { FocusRequester() }
                                
                                LaunchedEffect(isSelected) {
                                    if (isSelected) {
                                        try { focusRequester.requestFocus() } catch (e: Exception) {}
                                    } else if (annotation.text.isBlank()) {
                                        textAnnotations.removeIf { it.id == annotation.id }
                                    }
                                }
                                
                                Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .offset(
                                        x = boxWidth * annotation.x,
                                        y = boxHeight * annotation.y
                                    )
                                    // Pointer input to handle dragging and selecting
                                    .pointerInput(annotation.id) {
                                        detectDragGestures(
                                            onDragStart = { 
                                                selectedTextAnnotationId = annotation.id 
                                                selectedHighlightId = null
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val idx = textAnnotations.indexOfFirst { it.id == annotation.id }
                                                if (idx != -1) {
                                                    val current = textAnnotations[idx]
                                                    textAnnotations[idx] = current.copy(
                                                        x = current.x + (dragAmount.x / constraints.maxWidth.toFloat()),
                                                        y = current.y + (dragAmount.y / constraints.maxHeight.toFloat())
                                                    )
                                                }
                                            }
                                        )
                                    }
                                    .pointerInput(annotation.id) {
                                        detectTapGestures { 
                                            selectedTextAnnotationId = annotation.id 
                                            selectedHighlightId = null
                                        }
                                    }
                                    .background(if (isSelected) Color.LightGray.copy(alpha = 0.3f) else Color.Transparent)
                            ) {
                                Column {
                                    val composeFontFamily = when(annotation.fontFamily) {
                                        "Times Roman" -> FontFamily.Serif
                                        "Courier" -> FontFamily.Monospace
                                        else -> FontFamily.SansSerif
                                    }
                                    
                                    val composeFontWeight = if (annotation.isBold) FontWeight.Bold else FontWeight.Normal
                                    val composeDecoration = if (annotation.isUnderlined && annotation.isStrikethrough) {
                                        TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
                                    } else if (annotation.isUnderlined) {
                                        TextDecoration.Underline
                                    } else if (annotation.isStrikethrough) {
                                        TextDecoration.LineThrough
                                    } else {
                                        TextDecoration.None
                                    }
                                    
                                    BasicTextField(
                                        value = annotation.text,
                                        onValueChange = { newText ->
                                            val idx = textAnnotations.indexOfFirst { it.id == annotation.id }
                                            if (idx != -1) {
                                                textAnnotations[idx] = annotation.copy(text = newText)
                                            }
                                        },
                                        textStyle = TextStyle(
                                            color = annotation.color, 
                                            fontSize = annotation.fontSize.sp,
                                            fontFamily = composeFontFamily,
                                            fontWeight = composeFontWeight,
                                            fontStyle = if (annotation.isItalic) FontStyle.Italic else FontStyle.Normal,
                                            textDecoration = composeDecoration
                                        ),
                                        modifier = Modifier
                                            .focusRequester(focusRequester)
                                            .background(annotation.backgroundColor ?: Color.Transparent)
                                            .padding(4.dp)
                                    )
                                }
                            }
                            }
                        }
                    }
                }
            }

            // Top Left: Sidebar Toggle
            Surface(
                modifier = Modifier.padding(16.dp).align(Alignment.TopStart).size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                shadowElevation = 4.dp
            ) {
                IconButton(onClick = { sidebarOpen = !sidebarOpen }) {
                    Icon(Icons.Filled.Menu, contentDescription = "Toggle Sidebar")
                }
            }
            
            // Top Right: Close Document
            Surface(
                modifier = Modifier.padding(16.dp).align(Alignment.TopEnd).size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                shadowElevation = 4.dp
            ) {
                IconButton(onClick = onCloseDocument) {
                    Icon(Icons.Filled.Close, contentDescription = "Close Document")
                }
            }
            
            // Bottom Center: Floating Pill Toolbar
            // Bottom Toolbar / Contextual Menu
            if (selectedTextAnnotationId != null) {
                val selectedAnnotation = textAnnotations.find { it.id == selectedTextAnnotationId }
                if (selectedAnnotation != null) {
                    Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)) {
                        TextFormattingMenu(
                            annotation = selectedAnnotation,
                            onUpdate = { updatedAnnotation ->
                                val idx = textAnnotations.indexOfFirst { it.id == selectedAnnotation.id }
                                if (idx != -1) textAnnotations[idx] = updatedAnnotation
                            },
                            onDelete = { 
                                textAnnotations.removeIf { it.id == selectedAnnotation.id }
                                selectedTextAnnotationId = null
                            }
                        )
                    }
                } else {
                    selectedTextAnnotationId = null
                }
            } else if (selectedHighlightId != null) {
                val selectedHighlight = highlightAnnotations.find { it.id == selectedHighlightId }
                if (selectedHighlight != null) {
                    Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)) {
                        HighlightFormattingMenu(
                            annotation = selectedHighlight,
                            onUpdate = { updatedAnnotation ->
                                val idx = highlightAnnotations.indexOfFirst { it.id == selectedHighlight.id }
                                if (idx != -1) highlightAnnotations[idx] = updatedAnnotation
                            },
                            onDelete = { 
                                highlightAnnotations.removeIf { it.id == selectedHighlight.id }
                                selectedHighlightId = null
                            }
                        )
                    }
                } else {
                    selectedHighlightId = null
                }
            } else if (selectedShapeId != null) {
                val selectedShape = shapeAnnotations.find { it.id == selectedShapeId }
                if (selectedShape != null) {
                    Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)) {
                        ShapeFormattingMenu(
                            annotation = selectedShape,
                            onUpdate = { updatedAnnotation ->
                                val idx = shapeAnnotations.indexOfFirst { it.id == selectedShape.id }
                                if (idx != -1) shapeAnnotations[idx] = updatedAnnotation
                            },
                            onDelete = { 
                                shapeAnnotations.removeIf { it.id == selectedShape.id }
                                selectedShapeId = null
                            }
                        )
                    }
                } else {
                    selectedShapeId = null
                }
            } else if (currentTool == ToolType.SHAPE) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp),
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { currentTool = ToolType.SCROLL }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close Shape Tool")
                        }
                        Divider(modifier = Modifier.height(24.dp).width(1.dp))
                        
                        ShapeType.values().forEach { type ->
                            val isSelected = currentShapeType == type
                            val iconName = when(type) {
                                ShapeType.RECTANGLE -> "[]"
                                ShapeType.OVAL -> "O"
                                ShapeType.LINE -> "/"
                                ShapeType.ARROW -> "->"
                            }
                            IconButton(
                                onClick = { currentShapeType = type },
                                modifier = Modifier.background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                            ) {
                                Text(iconName, fontWeight = FontWeight.Bold, color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            } else {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp),
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Scroll Tool
                        IconButton(
                            onClick = { currentTool = ToolType.SCROLL },
                            modifier = Modifier.background(
                                if (currentTool == ToolType.SCROLL) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                CircleShape
                            )
                        ) {
                            Icon(Icons.Filled.Menu, contentDescription = "Scroll")
                        }
                        
                        // Text Tool
                        IconButton(
                            onClick = { currentTool = ToolType.ADD_TEXT },
                            modifier = Modifier.background(
                                if (currentTool == ToolType.ADD_TEXT) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                CircleShape
                            )
                        ) {
                            Text("T", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                        
                        // Highlight Tool
                        IconButton(
                            onClick = { currentTool = ToolType.HIGHLIGHT },
                            modifier = Modifier.background(
                                if (currentTool == ToolType.HIGHLIGHT) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                CircleShape
                            )
                        ) {
                            Icon(Icons.Filled.Create, contentDescription = "Highlight")
                        }
                        
                        // Shape Tool
                        IconButton(
                            onClick = { currentTool = ToolType.SHAPE },
                            modifier = Modifier.background(
                                if (currentTool == ToolType.SHAPE) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                CircleShape
                            )
                        ) {
                            Text("S", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                        
                        Divider(modifier = Modifier.height(24.dp).width(1.dp))
                        
                        // Save Button
                        IconButton(
                            onClick = { onSaveRequested(textAnnotations, highlightAnnotations, shapeAnnotations) }
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SidebarComponent(
    pageCount: Int,
    getPageBitmap: suspend (Int) -> Bitmap?,
    onPageSelected: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxHeight()
            .width(100.dp)
            .background(Color.DarkGray)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(pageCount) { index ->
            var bitmap by remember { mutableStateOf<Bitmap?>(null) }

            LaunchedEffect(index) {
                bitmap = getPageBitmap(index) 
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
                    .background(Color.White)
                    .clickable { onPageSelected(index) },
                contentAlignment = Alignment.Center
            ) {
                bitmap?.let { b ->
                    Image(
                        bitmap = b.asImageBitmap(),
                        contentDescription = "Thumbnail $index",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } ?: Text("${index + 1}", color = Color.Black)
            }
        }
    }
}

@Composable
fun SaveDialog(
    onDismiss: () -> Unit,
    onOverwrite: () -> Unit,
    onSaveCopy: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Document") },
        text = { Text("Do you want to overwrite the original or save as a copy?") },
        confirmButton = {
            Button(onClick = onOverwrite) {
                Text("Overwrite")
            }
        },
        dismissButton = {
            Button(onClick = onSaveCopy) {
                Text("Save Copy")
            }
        }
    )
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
                                    if (isTransparent) Icon(Icons.Filled.Clear, contentDescription = "None", modifier = Modifier.size(14.dp))
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
