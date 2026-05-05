import re

with open('app/src/main/java/com/example/pdfreader/ui/PdfViewerScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Remove imports
content = content.replace("import com.example.pdfreader.TextAnnotation\n", "")
content = content.replace("import com.example.pdfreader.HighlightAnnotation\n", "")
content = content.replace("import com.example.pdfreader.ShapeAnnotation\n", "")
content = content.replace("import com.example.pdfreader.ShapeType\n", "")
content = content.replace("import com.example.pdfreader.ui.components.EditSecondaryMenu\n", "")
content = content.replace("import com.example.pdfreader.ui.components.MoreToolsMenu\n", "")

# Change signature
content = re.sub(
    r'onSaveRequested: \(List<TextAnnotation>, List<HighlightAnnotation>, List<ShapeAnnotation>, List<SignatureAnnotation>\) -> Unit',
    'onSaveRequested: (List<SignatureAnnotation>) -> Unit,\n    onShareRequested: () -> Unit',
    content
)

# Remove tool types
content = content.replace("enum class ToolType { SCROLL, ADD_TEXT, HIGHLIGHT, SHAPE, SIGNATURE }", "enum class ToolType { SCROLL, SIGNATURE }")

# Change onSaveRequested call
content = content.replace(
    "onSaveRequested(textAnnotations, highlightAnnotations, shapeAnnotations, signatureAnnotations)",
    "onSaveRequested(signatureAnnotations)"
)

# Replace the variables with nothing or commented out lines
replacements = [
    ("val textAnnotations = remember { mutableStateListOf<TextAnnotation>() }", ""),
    ("val highlightAnnotations = remember { mutableStateListOf<HighlightAnnotation>() }", ""),
    ("val shapeAnnotations = remember { mutableStateListOf<ShapeAnnotation>() }", ""),
    ("var selectedTextAnnotationId by remember { mutableStateOf<String?>(null) }", ""),
    ("var isEditingText by remember { mutableStateOf(false) }", ""),
    ("var selectedHighlightId by remember { mutableStateOf<String?>(null) }", ""),
    ("var selectedShapeId by remember { mutableStateOf<String?>(null) }", ""),
    ("var showMoreToolsSheet by remember { mutableStateOf(false) }", "")
]
for old, new in replacements:
    content = content.replace(old, new)

# Comment out the rendering loops to avoid syntax errors
content = re.sub(r'(textAnnotations\.filter\s*\{.*?\n.*?\}\n\s*\}\n)', '/* \\1 */\n', content, flags=re.DOTALL)
content = re.sub(r'(highlightAnnotations\.filter\s*\{.*?\n.*?\}\n\s*\}\n)', '/* \\1 */\n', content, flags=re.DOTALL)
content = re.sub(r'(shapeAnnotations\.filter\s*\{.*?\n.*?\}\n\s*\}\n)', '/* \\1 */\n', content, flags=re.DOTALL)

# Delete bottom nav items
content = re.sub(r'NavigationBarItem\(\n\s*selected = activeMenu == "Edit",.*?label = \{ Text\("Edit"\) \}\n\s*\)', '', content, flags=re.DOTALL)
content = re.sub(r'NavigationBarItem\(\n\s*selected = activeMenu == "More Tools",.*?label = \{ Text\("More Tools"\) \}\n\s*\)', '', content, flags=re.DOTALL)

# Remove EditSecondaryMenu usage
content = re.sub(r'if \(activeMenu == "Edit"\) \{\n\s*EditSecondaryMenu\(.*?\}\n\s*\}', '', content, flags=re.DOTALL)

# Remove MoreToolsSheet usage
content = re.sub(r'if \(showMoreToolsSheet\) \{\n\s*MoreToolsMenu\(.*?\}\n\s*\}', '', content, flags=re.DOTALL)

# Remove pointerInput references to textAnnotations, shapeAnnotations, etc.
# Just rewrite the pointerInput signature for the Canvas
content = re.sub(r'pointerInput\(shapeAnnotations, signatureAnnotations, currentTool\)', 'pointerInput(signatureAnnotations, currentTool)', content)
content = re.sub(r'pointerInput\(currentTool, selectedTextAnnotationId, selectedSignatureId, selectedShapeId\)', 'pointerInput(currentTool, selectedSignatureId)', content)

# Remove "selectedTextAnnotationId != null || selectedHighlightId != null || selectedShapeId != null" conditions
content = re.sub(r'if \(selectedTextAnnotationId != null \|\| selectedHighlightId != null \|\| selectedShapeId != null \|\| selectedSignatureId != null\)', 'if (selectedSignatureId != null)', content)
content = re.sub(r'if \(selectedShapeId != null \|\| selectedSignatureId != null \|\| selectedTextAnnotationId != null \|\| selectedHighlightId != null\)', 'if (selectedSignatureId != null)', content)
content = re.sub(r'selectedTextAnnotationId = null', '', content)
content = re.sub(r'selectedHighlightId = null', '', content)
content = re.sub(r'selectedShapeId = null', '', content)

with open('app/src/main/java/com/example/pdfreader/ui/PdfViewerScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)
