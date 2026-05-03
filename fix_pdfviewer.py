import os

with open('app/src/main/java/com/example/pdfreader/ui/PdfViewerScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Replace the EditMenu/SignMenu block
start_str = 'if (activeBottomCategory == BottomCategory.EDIT) {'
end_str = 'NavigationBar(\n'

start_idx = content.find(start_str)
end_idx = content.find(end_idx_str := '                    NavigationBar(\n')

if start_idx != -1 and end_idx != -1:
    new_block = '''if (activeBottomCategory == BottomCategory.EDIT) {
                            EditSecondaryMenu(
                                currentTool = currentTool,
                                currentShapeType = currentShapeType,
                                onToolSelected = { currentTool = it },
                                onShapeSelected = { currentShapeType = it },
                                onClose = { activeBottomCategory = null; currentTool = ToolType.SCROLL },
                                textAnnotations = textAnnotations,
                                highlightAnnotations = highlightAnnotations,
                                shapeAnnotations = shapeAnnotations,
                                selectedTextAnnotationId = selectedTextAnnotationId,
                                selectedHighlightId = selectedHighlightId,
                                selectedShapeId = selectedShapeId,
                                onUpdateText = { updated -> val idx = textAnnotations.indexOfFirst { it.id == updated.id }; if(idx!=-1) textAnnotations[idx] = updated },
                                onDeleteText = { id -> textAnnotations.removeIf { it.id == id }; selectedTextAnnotationId = null },
                                onUpdateHighlight = { updated -> val idx = highlightAnnotations.indexOfFirst { it.id == updated.id }; if(idx!=-1) highlightAnnotations[idx] = updated },
                                onDeleteHighlight = { id -> highlightAnnotations.removeIf { it.id == id }; selectedHighlightId = null },
                                onUpdateShape = { updated -> val idx = shapeAnnotations.indexOfFirst { it.id == updated.id }; if(idx!=-1) shapeAnnotations[idx] = updated },
                                onDeleteShape = { id -> shapeAnnotations.removeIf { it.id == id }; selectedShapeId = null }
                            )
                        } else if (activeBottomCategory == BottomCategory.SIGN) {
                            SignSecondaryMenu(
                                onClose = { activeBottomCategory = null; currentTool = ToolType.SCROLL },
                                savedSignatures = savedSignatures,
                                onSelectSignature = { sig -> 
                                    val newSig = SignatureAnnotation(
                                        pageIndex = listState.firstVisibleItemIndex,
                                        strokes = sig.strokes,
                                        startX = 0.4f,
                                        startY = 0.4f,
                                        endX = 0.6f,
                                        endY = 0.6f,
                                        color = androidx.compose.ui.graphics.Color.Black,
                                        strokeWidth = 2f
                                    )
                                    signatureAnnotations.add(newSig)
                                    selectedSignatureId = newSig.id
                                    currentTool = ToolType.SCROLL
                                },
                                onDeleteSignature = { id ->
                                    sigManager.deleteSignature(id)
                                    savedSignatures = sigManager.getSignatures()
                                },
                                onCreateSignature = { showSignaturePad = true },
                                selectedSignatureId = selectedSignatureId,
                                signatureAnnotations = signatureAnnotations,
                                onUpdateSignature = { updated -> val idx = signatureAnnotations.indexOfFirst { it.id == updated.id }; if(idx!=-1) signatureAnnotations[idx] = updated },
                                onDeleteAnnotation = { id -> signatureAnnotations.removeIf { it.id == id }; selectedSignatureId = null }
                            )
                        }
                    }
                }
            } else {
                // Main Bottom Navigation
'''
    content = content[:start_idx] + new_block + content[end_idx + len(end_idx_str):]
    
# Inject sigManager at top
inject_str = 'var activeBottomCategory by remember { mutableStateOf<BottomCategory?>(null) }'
inject_idx = content.find(inject_str)
if inject_idx != -1:
    content = content[:inject_idx] + '''val context = androidx.compose.ui.platform.LocalContext.current
    val sigManager = remember { com.example.pdfreader.ui.components.SignatureStorageManager(context) }
    var savedSignatures by remember { mutableStateOf(sigManager.getSignatures()) }
    ''' + content[inject_idx:]
    
# Update Signature Pad to save to sigManager
sig_pad_str = 'signatureAnnotations.add(sig)\n                                  selectedSignatureId = sig.id'
sig_pad_idx = content.find(sig_pad_str)
if sig_pad_idx != -1:
    content = content[:sig_pad_idx] + '''signatureAnnotations.add(sig)
                                  val savedSig = com.example.pdfreader.ui.components.SavedSignature.fromOffsetStrokes(strokes)
                                  sigManager.saveSignature(savedSig)
                                  savedSignatures = sigManager.getSignatures()
                                  selectedSignatureId = sig.id''' + content[sig_pad_idx + len(sig_pad_str):]

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Updated PdfViewerScreen.kt")
