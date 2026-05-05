package com.example.pdfreader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
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
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            var isDarkMode by remember { mutableStateOf(systemDark) }

            PDFReaderTheme(darkTheme = isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var selectedUri by remember { mutableStateOf<Uri?>(null) }
                    var pageCount by remember { mutableStateOf(0) }
                    val scope = rememberCoroutineScope()
                    var showSaveDialog by remember { mutableStateOf(false) }
                    var pendingSignatures by remember { mutableStateOf<List<SignatureAnnotation>>(emptyList()) }
                    
                    val fileRepository = remember { FileRepository(this@MainActivity) }
                    var recentFiles by remember { mutableStateOf(fileRepository.getFiles()) }

                    val filePicker = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocument()
                    ) { uri: Uri? ->
                        uri?.let {
                            try {
                                contentResolver.takePersistableUriPermission(
                                    it,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            
                            scope.launch {
                                try {
                                    pdfEngine.loadPdf(it)
                                    selectedUri = it
                                    pageCount = pdfEngine.getPageCount()
                                    
                                    // Update history
                                    val (name, size) = getFileMetadata(this@MainActivity, it)
                                    val pdfFile = PdfFile(it.toString(), name, System.currentTimeMillis(), size)
                                    fileRepository.addOrUpdateFile(pdfFile)
                                    recentFiles = fileRepository.getFiles()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    Toast.makeText(this@MainActivity, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }

                    if (selectedUri == null) {
                        com.example.pdfreader.ui.HomeScreen(
                            isDarkMode = isDarkMode,
                            onToggleDarkMode = { isDarkMode = !isDarkMode },
                            onOpenPdfClick = { filePicker.launch(arrayOf("application/pdf")) },
                            onOpenFileUri = { uriStr -> 
                                val uri = Uri.parse(uriStr)
                                scope.launch {
                                    try {
                                        pdfEngine.loadPdf(uri)
                                        selectedUri = uri
                                        pageCount = pdfEngine.getPageCount()
                                        
                                        val (name, size) = getFileMetadata(this@MainActivity, uri)
                                        val pdfFile = PdfFile(uri.toString(), name, System.currentTimeMillis(), size)
                                        fileRepository.addOrUpdateFile(pdfFile)
                                        recentFiles = fileRepository.getFiles()
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        
                                        // If permission is denied or file is deleted, remove it from history
                                        fileRepository.removeFile(uriStr)
                                        recentFiles = fileRepository.getFiles()
                                        
                                        Toast.makeText(
                                            this@MainActivity, 
                                            "File access expired or file missing. Please open it from your device again.", 
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            },
                            recentFiles = recentFiles,
                            onToggleStar = { uriStr ->
                                fileRepository.toggleStar(uriStr)
                                recentFiles = fileRepository.getFiles()
                            }
                        )
                    } else {
                        PdfViewerScreen(
                            pageCount = pageCount,
                            getPageBitmap = { index, scale -> pdfEngine.renderPageToBitmap(index, scale) },
                            isDarkMode = isDarkMode,
                            onToggleDarkMode = { isDarkMode = !isDarkMode },
                            onCloseDocument = {
                                selectedUri = null
                                pageCount = 0
                            },
                            onSaveRequested = { signatures ->
                                pendingSignatures = signatures
                                showSaveDialog = true
                            },
                            onShareRequested = {
                                selectedUri?.let { uri ->
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    startActivity(Intent.createChooser(shareIntent, "Share PDF"))
                                }
                            },
                            onSearchRequested = { query ->
                                pdfEngine.searchDocument(query)
                            }
                        )
                        
                        if (showSaveDialog) {
                            com.example.pdfreader.ui.SaveDialog(
                                onDismiss = { showSaveDialog = false },
                                onOverwrite = {
                                    showSaveDialog = false
                                    scope.launch {
                                        val success = pdfEngine.saveDocument(selectedUri!!, signatures = pendingSignatures)
                                        if (success) {
                                            Toast.makeText(this@MainActivity, "Signatures saved", Toast.LENGTH_SHORT).show()
                                            pdfEngine.loadPdf(selectedUri!!)
                                        } else {
                                            Toast.makeText(this@MainActivity, "Failed to save signatures", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                onSaveCopy = {
                                    showSaveDialog = false
                                    // Normally we would launch an intent to create a new document
                                    // For now, we'll just show a toast and overwrite
                                    Toast.makeText(this@MainActivity, "Save Copy not fully implemented. Overwriting original.", Toast.LENGTH_SHORT).show()
                                    scope.launch {
                                        pdfEngine.saveDocument(selectedUri!!, signatures = pendingSignatures)
                                        pdfEngine.loadPdf(selectedUri!!)
                                    }
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
