package com.example.pdfreader

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.pdfreader.ui.PdfViewerScreen
import com.example.pdfreader.ui.SaveDialog
import com.example.pdfreader.ui.theme.PDFReaderTheme
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var pdfEngine: PdfEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize PDFBox-Android
        PDFBoxResourceLoader.init(applicationContext)
        pdfEngine = PdfEngine(this)

        setContent {
            PDFReaderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var selectedUri by remember { mutableStateOf<Uri?>(null) }
                    var pageCount by remember { mutableStateOf(0) }
                    val scope = rememberCoroutineScope()
                    var showSaveDialog by remember { mutableStateOf(false) }

                    val filePicker = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocument()
                    ) { uri: Uri? ->
                        uri?.let {
                            scope.launch {
                                pdfEngine.loadPdf(it)
                                selectedUri = it
                                pageCount = pdfEngine.getPageCount()
                            }
                        }
                    }

                    // Auto-launch the file picker when there's no document selected
                    LaunchedEffect(selectedUri) {
                        if (selectedUri == null) {
                            filePicker.launch(arrayOf("application/pdf"))
                        }
                    }

                    if (selectedUri == null) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Button(onClick = { filePicker.launch(arrayOf("application/pdf")) }) {
                                Text("Open PDF")
                            }
                        }
                    } else {
                        var currentAnnotations by remember { mutableStateOf<List<TextAnnotation>>(emptyList()) }
                        var currentHighlights by remember { mutableStateOf<List<HighlightAnnotation>>(emptyList()) }
                        var currentShapes by remember { mutableStateOf<List<ShapeAnnotation>>(emptyList()) }
                        
                        val saveAsPicker = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.CreateDocument("application/pdf")
                        ) { uri: Uri? ->
                            uri?.let {
                                scope.launch {
                                    pdfEngine.saveDocument(it, currentAnnotations, currentHighlights, currentShapes)
                                }
                            }
                        }
                        
                        PdfViewerScreen(
                            pageCount = pageCount,
                            getPageBitmap = { index, scale -> pdfEngine.renderPageToBitmap(index, scale) },
                            onSaveRequested = { annotations, highlights, shapes -> 
                                currentAnnotations = annotations
                                currentHighlights = highlights
                                currentShapes = shapes
                                showSaveDialog = true 
                            },
                            onCloseDocument = {
                                selectedUri = null
                                pageCount = 0
                            }
                        )
                        
                        if (showSaveDialog) {
                            SaveDialog(
                                onDismiss = { showSaveDialog = false },
                                onOverwrite = {
                                    scope.launch {
                                        pdfEngine.saveDocument(selectedUri!!, currentAnnotations, currentHighlights)
                                        showSaveDialog = false
                                    }
                                },
                                onSaveCopy = {
                                    saveAsPicker.launch("Document_Copy.pdf")
                                    showSaveDialog = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // We should close the document when the activity is destroyed
        // though typically this should be in a ViewModel
    }
}
