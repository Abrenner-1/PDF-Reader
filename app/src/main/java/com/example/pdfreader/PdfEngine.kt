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
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.graphics.blend.BlendMode
import com.tom_roush.pdfbox.rendering.PDFRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream

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

    suspend fun saveDocument(outputUri: Uri, annotations: List<TextAnnotation> = emptyList(), highlightAnnotations: List<HighlightAnnotation> = emptyList(), shapeAnnotations: List<ShapeAnnotation> = emptyList()) {
        withContext(Dispatchers.IO) {
            currentDocument?.let { doc ->
                // Group annotations by page
                val groupedAnnotations = annotations.groupBy { it.pageIndex }
                val groupedHighlights = highlightAnnotations.groupBy { it.pageIndex }
                val groupedShapes = shapeAnnotations.groupBy { it.pageIndex }
                
                val allPagesToModify = (groupedAnnotations.keys + groupedHighlights.keys + groupedShapes.keys).distinct()
                
                for (pageIndex in allPagesToModify) {
                    if (pageIndex in 0 until doc.numberOfPages) {
                        val page = doc.getPage(pageIndex)
                        val cropBox = page.cropBox
                        val pdfWidth = cropBox.width
                        val pdfHeight = cropBox.height
                        
                        val contentStream = PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)
                        
                        // 1. Draw Highlights first so they are under the text annotations
                        groupedHighlights[pageIndex]?.forEach { highlight ->
                            val r = (highlight.color.red * 255).toInt()
                            val g = (highlight.color.green * 255).toInt()
                            val b = (highlight.color.blue * 255).toInt()
                            
                            // Handle negative widths/heights
                            val renderX = if (highlight.width < 0) highlight.x + highlight.width else highlight.x
                            val renderY = if (highlight.height < 0) highlight.y + highlight.height else highlight.y
                            val renderWidth = kotlin.math.abs(highlight.width)
                            val renderHeight = kotlin.math.abs(highlight.height)
                            
                            val absoluteX = renderX * pdfWidth
                            // PDF coordinates: (0,0) is bottom-left, Compose is top-left
                            val absoluteY = pdfHeight - (renderY * pdfHeight) - (renderHeight * pdfHeight)
                            
                            val graphicsState = PDExtendedGraphicsState()
                            graphicsState.setBlendMode(BlendMode.MULTIPLY)
                            // Optional: set alpha if color has alpha
                            graphicsState.setNonStrokingAlphaConstant(highlight.color.alpha)
                            contentStream.setGraphicsStateParameters(graphicsState)
                            
                            contentStream.setNonStrokingColor(r, g, b)
                            contentStream.addRect(absoluteX, absoluteY, renderWidth * pdfWidth, renderHeight * pdfHeight)
                            contentStream.fill()
                        }
                        
                        // Reset Graphics State for text
                        contentStream.setGraphicsStateParameters(PDExtendedGraphicsState())
                        
                        // 2. Draw Text Annotations
                        groupedAnnotations[pageIndex]?.forEach { annotation ->
                            if (annotation.text.isNotEmpty()) {
                                val pdfFont = when (annotation.fontFamily) {
                                    "Times Roman" -> {
                                        if (annotation.isBold && annotation.isItalic) PDType1Font.TIMES_BOLD_ITALIC
                                        else if (annotation.isBold) PDType1Font.TIMES_BOLD
                                        else if (annotation.isItalic) PDType1Font.TIMES_ITALIC
                                        else PDType1Font.TIMES_ROMAN
                                    }
                                    "Courier" -> {
                                        if (annotation.isBold && annotation.isItalic) PDType1Font.COURIER_BOLD_OBLIQUE
                                        else if (annotation.isBold) PDType1Font.COURIER_BOLD
                                        else if (annotation.isItalic) PDType1Font.COURIER_OBLIQUE
                                        else PDType1Font.COURIER
                                    }
                                    else -> {
                                        if (annotation.isBold && annotation.isItalic) PDType1Font.HELVETICA_BOLD_OBLIQUE
                                        else if (annotation.isBold) PDType1Font.HELVETICA_BOLD
                                        else if (annotation.isItalic) PDType1Font.HELVETICA_OBLIQUE
                                        else PDType1Font.HELVETICA
                                    }
                                }
                                
                                val absoluteX = annotation.x * pdfWidth
                                val absoluteY = pdfHeight - (annotation.y * pdfHeight) - annotation.fontSize // Adjust for font baseline
                                
                                var textWidth = 0f
                                try {
                                    textWidth = (pdfFont.getStringWidth(annotation.text) / 1000f) * annotation.fontSize
                                } catch (e: Exception) {
                                    textWidth = annotation.fontSize * annotation.text.length * 0.5f // Fallback estimate
                                }
                                
                                // Draw background if present
                                if (annotation.backgroundColor != null) {
                                    val bg = annotation.backgroundColor!!
                                    val textHeight = annotation.fontSize * 1.2f
                                    
                                    contentStream.setNonStrokingColor((bg.red * 255).toInt(), (bg.green * 255).toInt(), (bg.blue * 255).toInt())
                                    contentStream.addRect(absoluteX - 2f, absoluteY - (textHeight * 0.2f), textWidth + 4f, textHeight)
                                    contentStream.fill()
                                }
                                
                                contentStream.beginText()
                                contentStream.setFont(pdfFont, annotation.fontSize)
                                
                                val r = (annotation.color.red * 255).toInt()
                                val g = (annotation.color.green * 255).toInt()
                                val b = (annotation.color.blue * 255).toInt()
                                contentStream.setNonStrokingColor(r, g, b)
                                
                                contentStream.newLineAtOffset(absoluteX, absoluteY)
                                contentStream.showText(annotation.text)
                                contentStream.endText()
                                
                                // Draw decorations (Underline / Strikethrough)
                                if (annotation.isUnderlined || annotation.isStrikethrough) {
                                    contentStream.setStrokingColor(r, g, b)
                                    contentStream.setLineWidth(annotation.fontSize * 0.08f)
                                    
                                    if (annotation.isUnderlined) {
                                        val lineY = absoluteY - (annotation.fontSize * 0.1f) // Slightly below baseline
                                        contentStream.moveTo(absoluteX, lineY)
                                        contentStream.lineTo(absoluteX + textWidth, lineY)
                                        contentStream.stroke()
                                    }
                                    
                                    if (annotation.isStrikethrough) {
                                        val lineY = absoluteY + (annotation.fontSize * 0.35f) // Above baseline, through text
                                        contentStream.moveTo(absoluteX, lineY)
                                        contentStream.lineTo(absoluteX + textWidth, lineY)
                                        contentStream.stroke()
                                    }
                                }
                            }
                        }
                        
                        // 3. Draw Shape Annotations
                        groupedShapes[pageIndex]?.forEach { shape ->
                            val startX = shape.startX * pdfWidth
                            val startY = pdfHeight - (shape.startY * pdfHeight)
                            val endX = shape.endX * pdfWidth
                            val endY = pdfHeight - (shape.endY * pdfHeight)
                            
                            val w = kotlin.math.abs(endX - startX)
                            val h = kotlin.math.abs(endY - startY)
                            val minX = minOf(startX, endX)
                            val minY = minOf(startY, endY) // Y goes up in PDF! So minY in PDF is visually the bottom of the shape
                            
                            val r = (shape.color.red * 255).toInt()
                            val g = (shape.color.green * 255).toInt()
                            val b = (shape.color.blue * 255).toInt()
                            
                            val graphicsState = PDExtendedGraphicsState()
                            graphicsState.setStrokingAlphaConstant(shape.alpha)
                            graphicsState.setNonStrokingAlphaConstant(shape.alpha)
                            contentStream.setGraphicsStateParameters(graphicsState)
                            
                            if (shape.isFilled) {
                                contentStream.setNonStrokingColor(r, g, b)
                            } else {
                                contentStream.setStrokingColor(r, g, b)
                                contentStream.setLineWidth(shape.strokeWidth)
                            }
                            
                            when (shape.type) {
                                ShapeType.RECTANGLE -> {
                                    contentStream.addRect(minX, minY, w, h)
                                    if (shape.isFilled) contentStream.fill() else contentStream.stroke()
                                }
                                ShapeType.OVAL -> {
                                    // Approximate ellipse with bezier curves
                                    val kappa = 0.5522848f
                                    val ox = (w / 2f) * kappa
                                    val oy = (h / 2f) * kappa
                                    val cx = minX + w / 2f
                                    val cy = minY + h / 2f
                                    
                                    contentStream.moveTo(cx - w/2f, cy)
                                    contentStream.curveTo(cx - w/2f, cy + oy, cx - ox, cy + h/2f, cx, cy + h/2f)
                                    contentStream.curveTo(cx + ox, cy + h/2f, cx + w/2f, cy + oy, cx + w/2f, cy)
                                    contentStream.curveTo(cx + w/2f, cy - oy, cx + ox, cy - h/2f, cx, cy - h/2f)
                                    contentStream.curveTo(cx - ox, cy - h/2f, cx - w/2f, cy - oy, cx - w/2f, cy)
                                    
                                    if (shape.isFilled) contentStream.fill() else contentStream.stroke()
                                }
                                ShapeType.LINE -> {
                                    contentStream.moveTo(startX, startY)
                                    contentStream.lineTo(endX, endY)
                                    contentStream.stroke()
                                }
                                ShapeType.ARROW -> {
                                    contentStream.moveTo(startX, startY)
                                    contentStream.lineTo(endX, endY)
                                    
                                    val angle = kotlin.math.atan2((endY - startY).toDouble(), (endX - startX).toDouble())
                                    val arrowSize = 15f
                                    val angle1 = angle + Math.PI * 0.8
                                    val angle2 = angle - Math.PI * 0.8
                                    
                                    val p1X = endX + arrowSize * kotlin.math.cos(angle1).toFloat()
                                    val p1Y = endY + arrowSize * kotlin.math.sin(angle1).toFloat()
                                    
                                    val p2X = endX + arrowSize * kotlin.math.cos(angle2).toFloat()
                                    val p2Y = endY + arrowSize * kotlin.math.sin(angle2).toFloat()
                                    
                                    contentStream.moveTo(endX, endY)
                                    contentStream.lineTo(p1X, p1Y)
                                    contentStream.moveTo(endX, endY)
                                    contentStream.lineTo(p2X, p2Y)
                                    
                                    contentStream.stroke()
                                }
                            }
                        }
                        
                        contentStream.close()
                    }
                }

                val pfd = context.contentResolver.openFileDescriptor(outputUri, "w")
                pfd?.use {
                    doc.save(FileOutputStream(it.fileDescriptor))
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
