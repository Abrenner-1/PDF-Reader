# Interactive Annotation System Implementation Plan

This plan addresses the need to make text annotations interactive (movable, editable, formatable, and deletable) rather than immediately stamping them permanently into the PDF.

## Goal Description
Currently, text is burned permanently into the PDF using `PDPageContentStream` the moment it is submitted. To allow interactions like dragging, editing, or deleting, we must decouple the UI rendering from the PDF Engine. We will achieve this by creating an **Annotation Overlay Layer** in Jetpack Compose. Text will exist as draggable UI components on screen until the user explicitly hits the "Save" button, at which point all annotations will be permanently stamped into the PDF file.

> [!WARNING]  
> ## User Review Required
> This fundamentally changes how the app works. Any edits you make on screen will exist *only in the app's memory* until you press "Save". If the app closes before you hit Save, your unsaved text boxes will be lost. Do you approve of this approach?

## Proposed Changes

### 1. Data Models (State Management)
We will introduce a data structure to track text boxes in memory.

#### [NEW] `AnnotationState.kt`
- Create a `TextAnnotation` data class containing:
  - `id`: Unique identifier.
  - `pageIndex`: Which page the text belongs to.
  - `text`: The string content.
  - `x, y`: The coordinates on the page.
  - `fontSize`: Text size formatting.
  - `color`: Text color formatting.

### 2. UI Updates (`PdfViewerScreen.kt`)
We will modify the screen to render the text annotations *on top* of the PDF bitmaps.

#### [MODIFY] `PdfViewerScreen.kt`
- **State Holders**: Add a `mutableStateListOf<TextAnnotation>()` to track all active text boxes.
- **Draggable Overlays**: Over the PDF `Image` inside the `Box`, we will render a `BasicTextField` for every annotation belonging to that page.
- **Drag Gestures**: Add `pointerInput(detectDragGestures)` to allow users to move the text boxes around the page.
- **Selection/Formatting**: When a text box is tapped/selected, show a small formatting toolbar (Size +, Size -, Change Color, Delete).

### 3. PDF Engine (`PdfEngine.kt`)
We will update the engine to stamp all annotations at once during the save process.

#### [MODIFY] `PdfEngine.kt`
- Modify the `saveDocument` function to accept a `List<TextAnnotation>`.
- Before writing the file to disk, loop through the list, open a `PDPageContentStream` for the appropriate pages, and apply all text, fonts, sizes, and colors exactly where the user positioned them on screen.

## Verification Plan
### Manual Verification
1. Launch app and open a PDF.
2. Tap to add a text box.
3. **Verify Dragging**: Ensure the user can drag the text box around the page smoothly.
4. **Verify Formatting**: Ensure clicking the text box opens formatting tools to change color/size or delete it.
5. **Verify Saving**: Click "Save" and open the newly generated PDF in an external viewer to ensure the text was permanently burned into the correct position.
