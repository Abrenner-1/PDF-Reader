import re

with open('app/src/main/java/com/example/pdfreader/ui/PdfViewerScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Add onSearchRequested to signature
content = re.sub(
    r'onShareRequested: \(\) -> Unit',
    'onShareRequested: () -> Unit,\n    onSearchRequested: suspend (String) -> List<com.example.pdfreader.TextMatch>',
    content
)

# Add states
state_injection = """    var toolsVisible by remember { mutableStateOf(true) }
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<com.example.pdfreader.TextMatch>>(emptyList()) }
    var currentSearchIndex by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()"""
content = content.replace("    var toolsVisible by remember { mutableStateOf(true) }", state_injection)

# Add search canvas highlights
canvas_injection = """
                            // Draw search highlights
                            if (isSearching && searchResults.isNotEmpty()) {
                                Canvas(modifier = Modifier.fillMaxSize().zIndex(100f)) {
                                    val canvasWidth = size.width
                                    val canvasHeight = size.height
                                    
                                    searchResults.forEachIndexed { resIdx, match ->
                                        if (match.pageIndex == index) {
                                            val isCurrent = resIdx == currentSearchIndex
                                            val color = if (isCurrent) androidx.compose.ui.graphics.Color(1f, 0.5f, 0f, 0.5f) else androidx.compose.ui.graphics.Color(1f, 1f, 0f, 0.4f)
                                            drawRect(
                                                color = color,
                                                topLeft = androidx.compose.ui.geometry.Offset(match.x * canvasWidth, match.y * canvasHeight),
                                                size = androidx.compose.ui.geometry.Size(match.width * canvasWidth, match.height * canvasHeight)
                                            )
                                        }
                                    }
                                }
                            }
"""
content = re.sub(r'(Image\([\s\S]*?contentScale = ContentScale\.Fit\n\s*\)\n\s*\}(?: \?: CircularProgressIndicator\(\))?\n)', r'\1' + canvas_injection, content)

# Modify Top App Bar
top_app_bar_regex = r'Row\(\s*modifier = Modifier\.fillMaxSize\(\)\.padding\(horizontal = 4\.dp\),\s*verticalAlignment = Alignment\.CenterVertically,\s*horizontalArrangement = Arrangement\.SpaceBetween\s*\) \{[\s\S]*?Row\(verticalAlignment = Alignment\.CenterVertically\) \{[\s\S]*?Icon\(Icons\.Filled\.Share, contentDescription = "Share"\)\s*\}[\s\S]*?\}\s*\}'

new_top_bar = """Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (isSearching) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    IconButton(onClick = { isSearching = false; searchResults = emptyList() }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Close Search")
                                    }
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp).background(Color.LightGray, RoundedCornerShape(4.dp)).padding(8.dp),
                                        singleLine = true,
                                        decorationBox = { innerTextField ->
                                            if (searchQuery.isEmpty()) Text("Search...", color = Color.DarkGray)
                                            innerTextField()
                                        }
                                    )
                                    IconButton(onClick = {
                                        scope.launch {
                                            searchResults = onSearchRequested(searchQuery)
                                            currentSearchIndex = 0
                                            if (searchResults.isNotEmpty()) {
                                                listState.animateScrollToItem(searchResults[0].pageIndex)
                                            }
                                        }
                                    }) {
                                        Icon(Icons.Filled.Search, contentDescription = "Run Search")
                                    }
                                    Text("${if (searchResults.isNotEmpty()) currentSearchIndex + 1 else 0}/${searchResults.size}")
                                    IconButton(onClick = { 
                                        if (currentSearchIndex > 0) {
                                            currentSearchIndex-- 
                                            scope.launch { listState.animateScrollToItem(searchResults[currentSearchIndex].pageIndex) }
                                        }
                                    }) {
                                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Previous")
                                    }
                                    IconButton(onClick = { 
                                        if (currentSearchIndex < searchResults.size - 1) {
                                            currentSearchIndex++
                                            scope.launch { listState.animateScrollToItem(searchResults[currentSearchIndex].pageIndex) }
                                        }
                                    }) {
                                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Next")
                                    }
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = onCloseDocument) {
                                        Icon(Icons.Filled.ArrowBack, contentDescription = "Go Back")
                                    }
                                    IconButton(onClick = { sidebarOpen = !sidebarOpen }) {
                                        Icon(Icons.Filled.Menu, contentDescription = "Toggle Sidebar")
                                    }
                                }
                                
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { isSearching = true }) {
                                        Icon(Icons.Filled.Search, contentDescription = "Search")
                                    }
                                    IconButton(onClick = onShareRequested) {
                                        Icon(Icons.Filled.Share, contentDescription = "Share")
                                    }
                                    IconButton(onClick = { onSaveRequested(signatureAnnotations) }) {
                                        Icon(Icons.Filled.Save, contentDescription = "Save")
                                    }
                                }
                            }
                        }"""

content = re.sub(top_app_bar_regex, new_top_bar, content)

with open('app/src/main/java/com/example/pdfreader/ui/PdfViewerScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)
