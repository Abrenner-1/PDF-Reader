package com.example.pdfreader.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.launch

enum class ToolType { SCROLL, ADD_TEXT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    pageCount: Int,
    getPageBitmap: suspend (Int, Float) -> Bitmap?,
    onSaveRequested: () -> Unit,
    onAddText: (Int, String, Float, Float) -> Unit,
    refreshTrigger: Int
) {
    var sidebarOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var currentTool by remember { mutableStateOf(ToolType.SCROLL) }
    
    // State for text dialog
    var showTextDialog by remember { mutableStateOf(false) }
    var textDialogPage by remember { mutableStateOf(0) }
    var textDialogOffset by remember { mutableStateOf(Offset.Zero) }
    var textDialogBoxSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

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

        Scaffold(
            modifier = Modifier.weight(1f),
            topBar = {
                TopAppBar(
                    title = { Text("PDF Reader") },
                    actions = {
                        Button(
                            onClick = { 
                                currentTool = if (currentTool == ToolType.SCROLL) ToolType.ADD_TEXT else ToolType.SCROLL 
                            },
                            modifier = Modifier.padding(end = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (currentTool == ToolType.ADD_TEXT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Text(if (currentTool == ToolType.ADD_TEXT) "Text Tool ON" else "Text Tool OFF")
                        }
                        Button(onClick = onSaveRequested, modifier = Modifier.padding(end = 8.dp)) {
                            Text("Save")
                        }
                        Button(onClick = { sidebarOpen = !sidebarOpen }) {
                            Text(if (sidebarOpen) "Hide Sidebar" else "Show Sidebar")
                        }
                    }
                )
            }
        ) { paddingValues ->
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color.Gray),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(pageCount) { index ->
                    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
                    
                    LaunchedEffect(index, refreshTrigger) {
                        bitmap = getPageBitmap(index, 1f)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.75f) // Approximate PDF aspect ratio A4
                            .background(Color.White)
                            .pointerInput(currentTool) {
                                if (currentTool == ToolType.ADD_TEXT) {
                                    detectTapGestures { offset ->
                                        textDialogPage = index
                                        textDialogOffset = offset
                                        textDialogBoxSize = size
                                        showTextDialog = true
                                    }
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
                    }
                }
            }
        }
    }

    if (showTextDialog) {
        var textInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            title = { Text("Add Text") },
            text = {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    label = { Text("Enter text") }
                )
            },
            confirmButton = {
                Button(onClick = {
                    // Map Box tap coordinate to PDF coordinate
                    val pdfWidth = 595f
                    val pdfHeight = 842f
                    val x = (textDialogOffset.x / textDialogBoxSize.width) * pdfWidth
                    val y = pdfHeight - ((textDialogOffset.y / textDialogBoxSize.height) * pdfHeight)
                    
                    onAddText(textDialogPage, textInput, x, y)
                    showTextDialog = false
                }) {
                    Text("Add")
                }
            },
            dismissButton = {
                Button(onClick = { showTextDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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
