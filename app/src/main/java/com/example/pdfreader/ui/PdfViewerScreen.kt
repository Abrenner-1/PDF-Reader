package com.example.pdfreader.ui
}
    }
        }


    com.example.pdfreader.ui.components.MoreToolsMenu(
        visible = showMoreToolsSheet,
        isDarkMode = isDarkMode,
        onDismiss = { showMoreToolsSheet = false },
        onToggleDarkMode = onToggleDarkMode
    )
}


@Composable
fun SidebarComponent(
    pageCount: Int,
    getPageBitmap: suspend (Int) -> Bitmap?,
    onPageSelected: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxHeight()
            .width(100.dp)
            .background(Color.DarkGray)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(pageCount) { index ->
            var bitmap by remember { mutableStateOf<Bitmap?>(null) }

            LaunchedEffect(index) {
                bitmap = getPageBitmap(index) 
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
                    .background(Color.White)
                    .clickable { onPageSelected(index) },
                contentAlignment = Alignment.Center
            ) {
                bitmap?.let { b ->
                    Image(
                        bitmap = b.asImageBitmap(),
                        contentDescription = "Thumbnail $index",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } ?: Text("${index + 1}", color = Color.Black)
            }
        }
    }
}

@Composable
fun SaveDialog(
    onDismiss: () -> Unit,
    onOverwrite: () -> Unit,
    onSaveCopy: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Document") },
        text = { Text("Do you want to overwrite the original or save as a copy?") },
        confirmButton = {
            Button(onClick = onOverwrite) {
                Text("Overwrite")
            }
        },
        dismissButton = {
            Button(onClick = onSaveCopy) {
                Text("Save Copy")
            }
        }
    )
}

