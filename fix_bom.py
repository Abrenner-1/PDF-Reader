import os
path = 'app/src/main/java/com/example/pdfreader/ui/PdfViewerScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# Fix BOM
content = content.replace('\ufeff// Top App Bar', '// Top App Bar')

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
print("BOM Fixed")
