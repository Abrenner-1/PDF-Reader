package com.example.pdfreader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.rendering.PDFRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PdfEngine(private val context: Context) {

    private var currentDocument: PDDocument? = null

    suspend fun loadPdf(uri: Uri) {
        withContext(Dispatchers.IO) {
            val inputStream = context.contentResolver.openInputStream(uri)
            currentDocument?.close()
            currentDocument = PDDocument.load(inputStream)
        }
    }

    suspend fun closeDocument() {
        withContext(Dispatchers.IO) {
            currentDocument?.close()
            currentDocument = null
        }
    }

    fun getPageCount(): Int {
        return currentDocument?.numberOfPages ?: 0
    }

    suspend fun renderPageToBitmap(pageIndex: Int, scale: Float = 1f): Bitmap? {
        return withContext(Dispatchers.IO) {
            currentDocument?.let { doc ->
                if (pageIndex in 0 until doc.numberOfPages) {
                    val renderer = PDFRenderer(doc)
                    renderer.renderImage(pageIndex, scale, com.tom_roush.pdfbox.rendering.ImageType.ARGB)
                } else null
            }
        }
    }

    suspend fun saveDocument(uri: Uri) {
        withContext(Dispatchers.IO) {
            currentDocument?.let { doc ->
                val outputStream = context.contentResolver.openOutputStream(uri)
                if (outputStream != null) {
                    doc.save(outputStream)
                    outputStream.close()
                }
            }
        }
    }

    suspend fun addRectangle(pageIndex: Int, x: Float, y: Float, width: Float, height: Float, r: Int, g: Int, b: Int) {
        withContext(Dispatchers.IO) {
            currentDocument?.let { doc ->
                if (pageIndex in 0 until doc.numberOfPages) {
                    val page = doc.getPage(pageIndex)
                    val contentStream = PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)
                    contentStream.setNonStrokingColor(r, g, b)
                    contentStream.addRect(x, y, width, height)
                    contentStream.fill()
                    contentStream.close()
                }
            }
        }
    }

    suspend fun extractText(pageIndex: Int): String {
        return withContext(Dispatchers.IO) {
            currentDocument?.let { doc ->
                if (pageIndex in 0 until doc.numberOfPages) {
                    val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
                    stripper.startPage = pageIndex + 1
                    stripper.endPage = pageIndex + 1
                    stripper.getText(doc)
                } else ""
            } ?: ""
        }
    }

    suspend fun addHighlight(pageIndex: Int, x: Float, y: Float, width: Float, height: Float) {
        withContext(Dispatchers.IO) {
            currentDocument?.let { doc ->
                if (pageIndex in 0 until doc.numberOfPages) {
                    val page = doc.getPage(pageIndex)
                    val contentStream = PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)
                    // Basic yellow color, true highlight requires PDExtendedGraphicsState with blend mode Multiply
                    contentStream.setNonStrokingColor(255, 255, 0)
                    contentStream.addRect(x, y, width, height)
                    contentStream.fill()
                    contentStream.close()
                }
            }
        }
    }

    suspend fun drawArrow(pageIndex: Int, startX: Float, startY: Float, endX: Float, endY: Float) {
        withContext(Dispatchers.IO) {
            currentDocument?.let { doc ->
                if (pageIndex in 0 until doc.numberOfPages) {
                    val page = doc.getPage(pageIndex)
                    val contentStream = PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)
                    contentStream.setStrokingColor(255, 0, 0) // Red stroke
                    contentStream.setLineWidth(3f)
                    contentStream.moveTo(startX, startY)
                    contentStream.lineTo(endX, endY)
                    contentStream.stroke()
                    contentStream.close()
                }
            }
        }
    }

    suspend fun addTextToPage(pageIndex: Int, text: String, x: Float, y: Float) {
        withContext(Dispatchers.IO) {
            currentDocument?.let { doc ->
                if (pageIndex in 0 until doc.numberOfPages) {
                    val page = doc.getPage(pageIndex)
                    val contentStream = PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)
                    contentStream.beginText()
                    contentStream.setFont(PDType1Font.HELVETICA, 16f)
                    contentStream.setNonStrokingColor(0, 0, 0) // Black text
                    contentStream.newLineAtOffset(x, y)
                    contentStream.showText(text)
                    contentStream.endText()
                    contentStream.close()
                }
            }
        }
    }

    suspend fun createPdfFromImages(imageUris: List<Uri>, outputUri: Uri) {
        withContext(Dispatchers.IO) {
            val doc = PDDocument()
            for (uri in imageUris) {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (bitmap != null) {
                    val page = PDPage(PDRectangle(bitmap.width.toFloat(), bitmap.height.toFloat()))
                    doc.addPage(page)
                    val pdImage = JPEGFactory.createFromImage(doc, bitmap)
                    val contentStream = PDPageContentStream(doc, page)
                    contentStream.drawImage(pdImage, 0f, 0f)
                    contentStream.close()
                }
            }
            val outputStream = context.contentResolver.openOutputStream(outputUri)
            if (outputStream != null) {
                doc.save(outputStream)
                outputStream.close()
            }
            doc.close()
        }
    }

    suspend fun createPdfFromText(text: String, outputUri: Uri) {
        withContext(Dispatchers.IO) {
            val doc = PDDocument()
            val page = PDPage()
            doc.addPage(page)

            val contentStream = PDPageContentStream(doc, page)
            contentStream.beginText()
            // Using standard Helvetica font
            contentStream.setFont(PDType1Font.HELVETICA, 12f)
            contentStream.newLineAtOffset(50f, 700f)
            
            // A simple implementation that replaces newlines with spaces for now
            contentStream.showText(text.replace("\n", " ")) 
            
            contentStream.endText()
            contentStream.close()

            val outputStream = context.contentResolver.openOutputStream(outputUri)
            if (outputStream != null) {
                doc.save(outputStream)
                outputStream.close()
            }
            doc.close()
        }
    }
}
