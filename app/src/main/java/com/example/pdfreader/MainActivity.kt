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

                    if (selectedUri == null) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Button(onClick = { filePicker.launch(arrayOf("application/pdf")) }) {
                                Text("Open PDF")
                            }
                        }
                    } else {
                        var refreshTrigger by remember { mutableStateOf(0) }
                        PdfViewerScreen(
                            pageCount = pageCount,
                            getPageBitmap = { index, scale -> pdfEngine.renderPageToBitmap(index, scale) },
                            onSaveRequested = { showSaveDialog = true },
                            onAddText = { index, text, x, y ->
                                scope.launch {
                                    pdfEngine.addTextToPage(index, text, x, y)
                                    refreshTrigger++ // Force re-render of bitmaps
                                }
                            },
                            refreshTrigger = refreshTrigger
                        )
                        
                        if (showSaveDialog) {
                            SaveDialog(
                                onDismiss = { showSaveDialog = false },
                                onOverwrite = {
                                    scope.launch {
                                        pdfEngine.saveDocument(selectedUri!!)
                                        showSaveDialog = false
                                    }
                                },
                                onSaveCopy = {
                                    // Note: In a real app, this would launch a CreateDocument intent
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
