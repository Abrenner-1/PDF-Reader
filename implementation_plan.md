# Android PDF Editor & Reader App

This is the finalized implementation plan for your feature-rich PDF Reader and Editor application for Android.

## Background Context
We will build a modern Android app using Kotlin and Jetpack Compose. To handle advanced PDF manipulation, we will utilize **PdfBox-Android** (a free, open-source port of Apache PDFBox). This provides the capabilities to parse, render, modify, and create PDF documents.

## Approved Features & Implementation Strategy

### 1. Navigation & UI
- **Continuous Scroll**: The main viewing area will be a vertical, continuously scrolling list of PDF pages.
- **Thumbnail Sidebar**: A collapsible sidebar or bottom sheet will show miniature thumbnails of each page for quick navigation.
- **Dark Mode**: Supports automatic system Dark Mode, plus a toggle to invert the colors of the PDF pages for night reading.

### 2. Save & Export
- **Save Prompt**: Whenever saving a document (after annotating, signing, etc.), the app will prompt the user to either "Overwrite Original" or "Save as Copy".
- **Share**: A standard Android Share button to send the document to other apps.

### 3. Annotation & Shapes
- **Add Signature**: A drawing pad to capture signatures, which will be stamped onto the PDF as an image annotation.
- **Highlight Text**: Text extraction to find words and draw highlight boxes over them.
- **Shapes**: Users can select drawing tools to add Shapes (Arrows, Clouds, Rectangles) directly onto the document.
- **Add Text Boxes**: Users can easily drop new text boxes over the page.

### 4. Convert to PDF
- **Image-to-PDF**: Select JPEG/PNG images from the device gallery and stitch them into a single PDF.
- **Text-to-PDF**: A simple text editor area to type or paste plain text and convert it into a basic PDF file.

## Proposed Architecture

- **UI Framework**: Jetpack Compose
- **Language**: Kotlin
- **PDF Engine**: `TomRoush:PdfBox-Android` (Free, Open-Source)
- **Architecture**: MVVM (Model-View-ViewModel)

### Core Components

#### app/build.gradle.kts
Dependencies for Compose, Coroutines, and `implementation("com.tomroush:pdfbox-android:2.0.27.0")`.

#### MainActivity.kt
Hosts the Compose UI and handles Android intents (file picking, sharing).

#### PdfEngine.kt
Abstracts `PdfBox` operations:
- `renderPageToBitmap(pageIndex)`
- `addSignature`, `addHighlight`, `addShapes(Arrow, Cloud)`
- `createPdfFromImages(imageUris)`, `createPdfFromText(string)`
- `saveDocument(overwriteBoolean)`

#### PdfViewerScreen.kt & SidebarComponent.kt
The Jetpack Compose UI:
- **Main Canvas**: Continuous scroll `LazyColumn` showing rendered bitmaps.
- **Sidebar**: A drawer showing thumbnails.
- **Dialogs**: Save confirmation prompt.

## Verification Plan
1. Initialize the Android project.
2. Verify continuous scroll and thumbnail sidebar UI.
3. Test drawing and saving shapes (Arrows/Clouds/Text boxes/Signatures).
4. Test the Save prompt functionality.
5. Verify Image/Text to PDF conversions.
