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
import androidx.compose.ui.zIndex
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

import com.example.pdfreader.SignatureAnnotation

enum class ToolType { SCROLL, ADD_TEXT, HIGHLIGHT, SHAPE, SIGNATURE }

data class SignatureLocation(val pageIndex: Int, val relativeX: Float, val relativeY: Float)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    pageCount: Int,
    getPageBitmap: suspend (Int, Float) -> Bitmap?,
    onSaveRequested: (List<TextAnnotation>, List<HighlightAnnotation>, List<ShapeAnnotation>, List<SignatureAnnotation>) -> Unit,
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
    val signatureAnnotations = remember { mutableStateListOf<SignatureAnnotation>() }
    
    // Track which annotation is selected for formatting/deleting
    var selectedTextAnnotationId by remember { mutableStateOf<String?>(null) }
    var selectedHighlightId by remember { mutableStateOf<String?>(null) }
    var selectedShapeId by remember { mutableStateOf<String?>(null) }
    var selectedSignatureId by remember { mutableStateOf<String?>(null) }
    var currentShapeType by remember { mutableStateOf(ShapeType.RECTANGLE) }
    
    var showSignaturePad by remember { mutableStateOf(false) }
    var pendingSignatureLocation by remember { mutableStateOf<SignatureLocation?>(null) }
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

                    val isSelectedPage = (selectedSignatureId != null && signatureAnnotations.any { it.id == selectedSignatureId && it.pageIndex == index }) ||
                                         (selectedShapeId != null && shapeAnnotations.any { it.id == selectedShapeId && it.pageIndex == index })

                    BoxWithConstraints(
                        modifier = Modifier
                            .zIndex(if (isSelectedPage) 1f else 0f)
                            .fillMaxWidth()
                            .aspectRatio(0.75f) // Approximate PDF aspect ratio A4
                            .background(Color.White)
                            .pointerInput(currentTool, selectedTextAnnotationId, selectedSignatureId, selectedShapeId) {
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
                                                    
                                                    highlightAnnotations[idx] = current.copy(
                                                        width = newWidth,
                                                        height = newHeight
                                                    )
                                                }
                                            }
                                        }
                                    )
                                } else if (selectedSignatureId != null) {
                                    var isResizingStart = false
                                    var isResizingEnd = false
                                    var isMoving = false
                                    var activeSigId: String? = null
                                    
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            val relativeX = offset.x / size.width
                                            val relativeY = offset.y / size.height
                                            val pixelX = offset.x
                                            val pixelY = offset.y
                                            
                                            val sig = signatureAnnotations.find { it.id == selectedSignatureId && it.pageIndex == index }
                                            if (sig != null) {
                                                val shapeStartX = sig.startX * size.width
                                                val shapeStartY = sig.startY * size.height
                                                val shapeEndX = sig.endX * size.width
                                                val shapeEndY = sig.endY * size.height
                                                
                                                val distToStart = kotlin.math.hypot((pixelX - shapeStartX).toDouble(), (pixelY - shapeStartY).toDouble()).toFloat()
                                                val distToEnd = kotlin.math.hypot((pixelX - shapeEndX).toDouble(), (pixelY - shapeEndY).toDouble()).toFloat()
                                                
                                                val touchRadius = 120f
                                                
                                                if (distToEnd < touchRadius && distToEnd <= distToStart) {
                                                    isResizingEnd = true
                                                    activeSigId = sig.id
                                                    return@detectDragGestures
                                                } else if (distToStart < touchRadius) {
                                                    isResizingStart = true
                                                    activeSigId = sig.id
                                                    return@detectDragGestures
                                                }
                                                
                                                val minX = minOf(sig.startX, sig.endX) - 0.05f
                                                val maxX = maxOf(sig.startX, sig.endX) + 0.05f
                                                val minY = minOf(sig.startY, sig.endY) - 0.05f
                                                val maxY = maxOf(sig.startY, sig.endY) + 0.05f
                                                
                                                if (relativeX in minX..maxX && relativeY in minY..maxY) {
                                                    isMoving = true
                                                    activeSigId = sig.id
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            isResizingStart = false
                                            isResizingEnd = false
                                            isMoving = false
                                            activeSigId = null
                                        },
                                        onDrag = { change, dragAmount ->
                                            if (activeSigId != null) {
                                                change.consume()
                                                val sigId = activeSigId!!
                                                val idx = signatureAnnotations.indexOfFirst { it.id == sigId }
                                                if (idx != -1) {
                                                    val current = signatureAnnotations[idx]
                                                    val dx = dragAmount.x / size.width
                                                    val dy = dragAmount.y / size.height
                                                    
                                                    if (isResizingEnd) {
                                                        signatureAnnotations[idx] = current.copy(endX = current.endX + dx, endY = current.endY + dy)
                                                    } else if (isResizingStart) {
                                                        signatureAnnotations[idx] = current.copy(startX = current.startX + dx, startY = current.startY + dy)
                                                    } else if (isMoving) {
                                                        var newY = current.startY + dy
                                                        var newEndY = current.endY + dy
                                                        var newPage = current.pageIndex
                                                        
                                                        while (newY > 1.0f && newPage < pageCount - 1) {
                                                            newY -= 1.0f
                                                            newEndY -= 1.0f
                                                            newPage += 1
                                                        }
                                                        while (newY < 0.0f && newPage > 0) {
                                                            newY += 1.0f
                                                            newEndY += 1.0f
                                                            newPage -= 1
                                                        }
                                                        
                                                        signatureAnnotations[idx] = current.copy(
                                                            pageIndex = newPage,
                                                            startX = current.startX + dx,
                                                            startY = newY,
                                                            endX = current.endX + dx,
                                                            endY = newEndY
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    )
                                } else if (currentTool == ToolType.SHAPE || selectedShapeId != null) {
                                    var isResizingStart = false
                                    var isResizingEnd = false
                                    var isMoving = false
                                    var activeShapeId: String? = null
                                    
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            val relativeX = offset.x / size.width
                                            val relativeY = offset.y / size.height
                                            val pixelX = offset.x
                                            val pixelY = offset.y
                                            
                                            if (selectedShapeId != null) {
                                                val shape = shapeAnnotations.find { it.id == selectedShapeId && it.pageIndex == index }
                                                if (shape != null) {
                                                    val shapeStartX = shape.startX * size.width
                                                    val shapeStartY = shape.startY * size.height
                                                    val shapeEndX = shape.endX * size.width
                                                    val shapeEndY = shape.endY * size.height
                                                    
                                                    val distToStart = kotlin.math.hypot((pixelX - shapeStartX).toDouble(), (pixelY - shapeStartY).toDouble()).toFloat()
                                                    val distToEnd = kotlin.math.hypot((pixelX - shapeEndX).toDouble(), (pixelY - shapeEndY).toDouble()).toFloat()
                                                    
                                                    val touchRadius = 120f
                                                    
                                                    if (distToEnd < touchRadius && distToEnd <= distToStart) {
                                                        isResizingEnd = true
                                                        activeShapeId = shape.id
                                                        return@detectDragGestures
                                                    } else if (distToStart < touchRadius) {
                                                        isResizingStart = true
                                                        activeShapeId = shape.id
                                                        return@detectDragGestures
                                                    }
                                                    
                                                    val minX = minOf(shape.startX, shape.endX) - 0.05f
                                                    val maxX = maxOf(shape.startX, shape.endX) + 0.05f
                                                    val minY = minOf(shape.startY, shape.endY) - 0.05f
                                                    val maxY = maxOf(shape.startY, shape.endY) + 0.05f
                                                    
                                                    if (relativeX in minX..maxX && relativeY in minY..maxY) {
                                                        isMoving = true
                                                        activeShapeId = shape.id
                                                        return@detectDragGestures
                                                    }
                                                }
                                            }
                                            
                                            if (currentTool == ToolType.SHAPE) {
                                                val newShape = ShapeAnnotation(
                                                    pageIndex = index,
                                                    type = currentShapeType,
                                                    startX = relativeX,
                                                    startY = relativeY,
                                                    endX = relativeX,
                                                    endY = relativeY
                                                )
                                                shapeAnnotations.add(newShape)
                                                activeShapeId = newShape.id
                                                selectedShapeId = newShape.id
                                                isResizingEnd = true // Dragging draws the end point
                                            }
                                        },
                                        onDragEnd = {
                                            isResizingStart = false
                                            isResizingEnd = false
                                            isMoving = false
                                            activeShapeId = null
                                        },
                                        onDrag = { change, dragAmount ->
                                            if (activeShapeId != null) {
                                                change.consume()
                                                val shapeId = activeShapeId!!
                                                val idx = shapeAnnotations.indexOfFirst { it.id == shapeId }
                                                if (idx != -1) {
                                                    val current = shapeAnnotations[idx]
                                                    val dx = dragAmount.x / size.width
                                                    val dy = dragAmount.y / size.height
                                                    
                                                    if (isResizingEnd) {
                                                        shapeAnnotations[idx] = current.copy(endX = current.endX + dx, endY = current.endY + dy)
                                                    } else if (isResizingStart) {
                                                        shapeAnnotations[idx] = current.copy(startX = current.startX + dx, startY = current.startY + dy)
                                                    } else if (isMoving) {
                                                        var newY = current.startY + dy
                                                        var newEndY = current.endY + dy
                                                        var newPage = current.pageIndex
                                                        
                                                        while (newY > 1.0f && newPage < pageCount - 1) {
                                                            newY -= 1.0f
                                                            newEndY -= 1.0f
                                                            newPage += 1
                                                        }
                                                        while (newY < 0.0f && newPage > 0) {
                                                            newY += 1.0f
                                                            newEndY += 1.0f
                                                            newPage -= 1
                                                        }
                                                        
                                                        shapeAnnotations[idx] = current.copy(
                                                            pageIndex = newPage,
                                                            startX = current.startX + dx,
                                                            startY = newY,
                                                            endX = current.endX + dx,
                                                            endY = newEndY
                                                        )
                                                    }
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
                        
                        // Render Canvas Shapes and Signatures
                        Canvas(modifier = Modifier.fillMaxSize().pointerInput(shapeAnnotations, signatureAnnotations, currentTool) {
                            detectTapGestures { offset ->
                                val tapX = offset.x / size.width
                                val tapY = offset.y / size.height
                                
                                val clickedSig = signatureAnnotations.filter { it.pageIndex == index }.findLast { sig ->
                                    val minX = minOf(sig.startX, sig.endX) - 0.05f
                                    val maxX = maxOf(sig.startX, sig.endX) + 0.05f
                                    val minY = minOf(sig.startY, sig.endY) - 0.05f
                                    val maxY = maxOf(sig.startY, sig.endY) + 0.05f
                                    tapX in minX..maxX && tapY in minY..maxY
                                }
                                
                                val clickedShape = if (clickedSig == null) {
                                    shapeAnnotations.filter { it.pageIndex == index }.findLast { shape ->
                                        val minX = minOf(shape.startX, shape.endX) - 0.05f
                                        val maxX = maxOf(shape.startX, shape.endX) + 0.05f
                                        val minY = minOf(shape.startY, shape.endY) - 0.05f
                                        val maxY = maxOf(shape.startY, shape.endY) + 0.05f
                                        tapX in minX..maxX && tapY in minY..maxY
                                    }
                                } else null
                                
                                if (clickedSig != null) {
                                    selectedSignatureId = clickedSig.id
                                    selectedShapeId = null
                                    selectedTextAnnotationId = null
                                    selectedHighlightId = null
                                } else if (clickedShape != null) {
                                    selectedShapeId = clickedShape.id
                                    selectedSignatureId = null
                                    selectedTextAnnotationId = null
                                    selectedHighlightId = null
                                } else if (currentTool == ToolType.SIGNATURE) {
                                    pendingSignatureLocation = SignatureLocation(index, tapX, tapY)
                                    showSignaturePad = true
                                } else if (currentTool == ToolType.SCROLL || currentTool == ToolType.SHAPE) {
                                    selectedShapeId = null
                                    selectedSignatureId = null
                                    selectedTextAnnotationId = null
                                    selectedHighlightId = null
                                }
                            }
                        }) {
                            signatureAnnotations.filter { it.pageIndex == index }.forEach { sig ->
                                val sX = boxWidth.toPx() * sig.startX
                                val sY = boxHeight.toPx() * sig.startY
                                val eX = boxWidth.toPx() * sig.endX
                                val eY = boxHeight.toPx() * sig.endY
                                val w = eX - sX
                                val h = eY - sY
                                
                                val isSelected = selectedSignatureId == sig.id
                                if (isSelected) {
                                    drawRect(
                                        color = Color.Blue.copy(alpha = 0.3f),
                                        topLeft = Offset(minOf(sX, eX) - 10f, minOf(sY, eY) - 10f),
                                        size = androidx.compose.ui.geometry.Size(kotlin.math.abs(w) + 20f, kotlin.math.abs(h) + 20f),
                                        style = Stroke(width = 2f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                                    )
                                    drawCircle(color = Color.Blue, radius = 16f, center = Offset(sX, sY))
                                    drawCircle(color = Color.White, radius = 12f, center = Offset(sX, sY))
                                    drawCircle(color = Color.Blue, radius = 16f, center = Offset(eX, eY))
                                    drawCircle(color = Color.White, radius = 12f, center = Offset(eX, eY))
                                }
                                
                                sig.strokes.forEach { stroke ->
                                    if (stroke.size > 1) {
                                        val path = Path().apply {
                                            val startPtX = sX + (stroke.first().x * w)
                                            val startPtY = sY + (stroke.first().y * h)
                                            moveTo(startPtX, startPtY)
                                            for (i in 1 until stroke.size) {
                                                lineTo(sX + (stroke[i].x * w), sY + (stroke[i].y * h))
                                            }
                                        }
                                        drawPath(path, sig.color, style = Stroke(width = sig.strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                                    } else if (stroke.size == 1) {
                                        drawCircle(sig.color, radius = sig.strokeWidth / 2f, center = Offset(sX + (stroke.first().x * w), sY + (stroke.first().y * h)))
                                    }
                                }
                            }
                            
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
                                    // Draw visible resize grab handles
                                    drawCircle(color = Color.Blue, radius = 16f, center = Offset(sX, sY))
                                    drawCircle(color = Color.White, radius = 12f, center = Offset(sX, sY))
                                    drawCircle(color = Color.Blue, radius = 16f, center = Offset(eX, eY))
                                    drawCircle(color = Color.White, radius = 12f, center = Offset(eX, eY))
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
            } else if (selectedSignatureId != null) {
                val selectedSignature = signatureAnnotations.find { it.id == selectedSignatureId }
                if (selectedSignature != null) {
                    Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)) {
                        SignatureFormattingMenu(
                            annotation = selectedSignature,
                            onUpdate = { updatedAnnotation ->
                                val idx = signatureAnnotations.indexOfFirst { it.id == selectedSignature.id }
                                if (idx != -1) signatureAnnotations[idx] = updatedAnnotation
                            },
                            onDelete = { 
                                signatureAnnotations.removeIf { it.id == selectedSignature.id }
                                selectedSignatureId = null
                            }
                        )
                    }
                } else {
                    selectedSignatureId = null
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
                            val iconColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            
                            IconButton(
                                onClick = { currentShapeType = type },
                                modifier = Modifier.background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                            ) {
                                Canvas(modifier = Modifier.size(20.dp)) {
                                    val strokeW = 4f
                                    val padding = 2f
                                    when(type) {
                                        ShapeType.RECTANGLE -> {
                                            drawRect(color = iconColor, style = Stroke(width = strokeW), topLeft = Offset(padding, padding), size = androidx.compose.ui.geometry.Size(size.width - padding*2, size.height - padding*2))
                                        }
                                        ShapeType.OVAL -> {
                                            drawOval(color = iconColor, style = Stroke(width = strokeW), topLeft = Offset(padding, padding), size = androidx.compose.ui.geometry.Size(size.width - padding*2, size.height - padding*2))
                                        }
                                        ShapeType.LINE -> {
                                            drawLine(color = iconColor, strokeWidth = strokeW, start = Offset(padding, size.height - padding), end = Offset(size.width - padding, padding))
                                        }
                                        ShapeType.ARROW -> {
                                            drawLine(color = iconColor, strokeWidth = strokeW, start = Offset(padding, size.height - padding), end = Offset(size.width - padding, padding))
                                            drawLine(color = iconColor, strokeWidth = strokeW, start = Offset(size.width - padding, padding), end = Offset(size.width - padding - 10f, padding))
                                            drawLine(color = iconColor, strokeWidth = strokeW, start = Offset(size.width - padding, padding), end = Offset(size.width - padding, padding + 10f))
                                        }
                                    }
                                }
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
                            val iconColor = if (currentTool == ToolType.SHAPE) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            Canvas(modifier = Modifier.size(20.dp)) {
                                drawRect(
                                    color = iconColor,
                                    style = Stroke(width = 5f),
                                    topLeft = Offset(2f, 2f),
                                    size = androidx.compose.ui.geometry.Size(size.width - 4f, size.height - 4f)
                                )
                            }
                        }
                        
                        // Signature Tool
                        IconButton(
                            onClick = { currentTool = ToolType.SIGNATURE },
                            modifier = Modifier.background(
                                if (currentTool == ToolType.SIGNATURE) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                CircleShape
                            )
                        ) {
                            val iconColor = if (currentTool == ToolType.SIGNATURE) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            Canvas(modifier = Modifier.size(20.dp)) {
                                val path = Path().apply {
                                    // Quill pen tip
                                    moveTo(size.width * 0.1f, size.height * 0.9f)
                                    lineTo(size.width * 0.3f, size.height * 0.9f)
                                    lineTo(size.width * 0.2f, size.height * 0.7f)
                                    close()
                                    
                                    // Feather body
                                    moveTo(size.width * 0.2f, size.height * 0.7f)
                                    quadraticBezierTo(size.width * 0.1f, size.height * 0.2f, size.width * 0.9f, size.height * 0.1f)
                                    quadraticBezierTo(size.width * 0.8f, size.height * 0.6f, size.width * 0.2f, size.height * 0.7f)
                                    
                                    // Feather details
                                    moveTo(size.width * 0.5f, size.height * 0.3f)
                                    lineTo(size.width * 0.65f, size.height * 0.25f)
                                    moveTo(size.width * 0.4f, size.height * 0.45f)
                                    lineTo(size.width * 0.55f, size.height * 0.4f)
                                    moveTo(size.width * 0.3f, size.height * 0.6f)
                                    lineTo(size.width * 0.45f, size.height * 0.55f)
                                }
                                drawPath(path, color = iconColor, style = Stroke(width = 1.5f))
                            }
                        }
                        
                        Divider(modifier = Modifier.height(24.dp).width(1.dp))
                        
                        // Save Button
                        IconButton(
                            onClick = { onSaveRequested(textAnnotations, highlightAnnotations, shapeAnnotations, signatureAnnotations) }
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
    
    if (showSignaturePad) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showSignaturePad = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.5f),
                shape = RoundedCornerShape(16.dp),
                color = Color.White
            ) {
                var strokes by remember { mutableStateOf(listOf<List<androidx.compose.ui.geometry.Offset>>()) }
                var currentStroke by remember { mutableStateOf(listOf<androidx.compose.ui.geometry.Offset>()) }
                
                Column(modifier = Modifier.fillMaxSize()) {
                    Text("Draw Signature", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
                    
                    Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFFF5F5F5))) {
                        Canvas(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentStroke = listOf(offset)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentStroke = currentStroke + change.position
                                },
                                onDragEnd = {
                                    if (currentStroke.isNotEmpty()) {
                                        strokes = strokes + listOf(currentStroke)
                                        currentStroke = emptyList()
                                    }
                                }
                            )
                        }) {
                            val drawStrokes = strokes + if (currentStroke.isNotEmpty()) listOf(currentStroke) else emptyList()
                            drawStrokes.forEach { stroke ->
                                if (stroke.size > 1) {
                                    val path = Path().apply {
                                        moveTo(stroke.first().x, stroke.first().y)
                                        for (i in 1 until stroke.size) {
                                            lineTo(stroke[i].x, stroke[i].y)
                                        }
                                    }
                                    drawPath(path, Color.Black, style = Stroke(width = 5f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                                } else if (stroke.size == 1) {
                                    drawCircle(Color.Black, radius = 2.5f, center = stroke.first())
                                }
                            }
                        }
                    }
                    
                    Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { strokes = emptyList() }) {
                            Text("Clear")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = { 
                            showSignaturePad = false
                            pendingSignatureLocation = null
                        }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            if (strokes.isNotEmpty()) {
                                // Calculate bounding box of all strokes
                                var minX = Float.MAX_VALUE
                                var minY = Float.MAX_VALUE
                                var maxX = Float.MIN_VALUE
                                var maxY = Float.MIN_VALUE
                                
                                strokes.forEach { stroke ->
                                    stroke.forEach { pt ->
                                        if (pt.x < minX) minX = pt.x
                                        if (pt.x > maxX) maxX = pt.x
                                        if (pt.y < minY) minY = pt.y
                                        if (pt.y > maxY) maxY = pt.y
                                    }
                                }
                                
                                // Normalize strokes to 0f..1f relative to their bounding box
                                val width = maxX - minX
                                val height = maxY - minY
                                val normalizedStrokes = strokes.map { stroke ->
                                    stroke.map { pt ->
                                        androidx.compose.ui.geometry.Offset(
                                            x = if (width > 0) (pt.x - minX) / width else 0f,
                                            y = if (height > 0) (pt.y - minY) / height else 0f
                                        )
                                    }
                                }
                                
                                val sigAspect = if (height > 0) width / height else 2f
                                val sigWidth = 0.3f
                                val sigHeight = sigWidth / sigAspect
                                
                                val loc = pendingSignatureLocation
                                val targetPageIndex = loc?.pageIndex ?: listState.firstVisibleItemIndex
                                val targetStartX = loc?.relativeX ?: (0.5f - (sigWidth / 2f))
                                val targetStartY = loc?.relativeY ?: (0.5f - (sigHeight / 2f))
                                
                                val sig = SignatureAnnotation(
                                    pageIndex = targetPageIndex,
                                    strokes = normalizedStrokes,
                                    startX = targetStartX,
                                    startY = targetStartY,
                                    endX = targetStartX + sigWidth,
                                    endY = targetStartY + sigHeight,
                                    color = Color.Black,
                                    strokeWidth = 3f
                                )
                                signatureAnnotations.add(sig)
                                selectedSignatureId = sig.id
                                currentTool = ToolType.SCROLL
                            }
                            showSignaturePad = false
                            pendingSignatureLocation = null
                        }) {
                            Text("Done")
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
