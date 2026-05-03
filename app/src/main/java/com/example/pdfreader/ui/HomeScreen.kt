package com.example.pdfreader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.example.pdfreader.PdfFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    onOpenPdfClick: () -> Unit,
    onOpenFileUri: (String) -> Unit,
    recentFiles: List<PdfFile>,
    onToggleStar: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedBottomTab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PDF Reader") },
                actions = {
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Settings")
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (isDarkMode) "Light Mode" else "Dark Mode") },
                            leadingIcon = { Icon(if (isDarkMode) Icons.Filled.LightMode else Icons.Filled.DarkMode, contentDescription = null) },
                            onClick = {
                                onToggleDarkMode()
                                expanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("About") },
                            onClick = {
                                // Placeholder for future About dialog
                                expanded = false
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    selected = selectedBottomTab == 0,
                    onClick = { selectedBottomTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Folder, contentDescription = "Files") },
                    label = { Text("Files") },
                    selected = selectedBottomTab == 1,
                    onClick = { selectedBottomTab = 1 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Build, contentDescription = "Tools") },
                    label = { Text("Tools") },
                    selected = selectedBottomTab == 2,
                    onClick = { selectedBottomTab = 2 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    selected = selectedBottomTab == 3,
                    onClick = { selectedBottomTab = 3 }
                )
            }
        },
        floatingActionButton = {
            if (selectedBottomTab == 0 || selectedBottomTab == 1) {
                FloatingActionButton(
                    onClick = onOpenPdfClick,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Open PDF")
                }
            }
        }
    ) { innerPadding ->
        if (selectedBottomTab == 0) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
            ) {
                // Quick Tools
                Text(
                    text = "Quick Tools",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    QuickToolItem(Icons.Filled.FolderOpen, "Open File", MaterialTheme.colorScheme.primary, onOpenPdfClick)
                    QuickToolItem(Icons.Filled.Edit, "Annotate", MaterialTheme.colorScheme.secondary, {})
                    QuickToolItem(Icons.Filled.Autorenew, "Convert", MaterialTheme.colorScheme.tertiary, {})
                    QuickToolItem(Icons.Filled.Assignment, "Fill Form", MaterialTheme.colorScheme.error, {})
                    QuickToolItem(Icons.Filled.BorderColor, "Sign", MaterialTheme.colorScheme.primary, {})
                    QuickToolItem(Icons.Filled.Scanner, "Scan", MaterialTheme.colorScheme.secondary, {})
                }
                
                Divider(modifier = Modifier.padding(vertical = 16.dp))
                
                var selectedListTab by remember { mutableStateOf(0) }
                
                // Recent / Starred Tabs
                Row(
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (selectedListTab == 0) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedListTab == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { selectedListTab = 0 }.padding(vertical = 8.dp)
                    )
                    Text(
                        text = "Starred",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (selectedListTab == 1) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedListTab == 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { selectedListTab = 1 }.padding(vertical = 8.dp)
                    )
                }
                
                val displayFiles = if (selectedListTab == 0) recentFiles else recentFiles.filter { it.isStarred }
                
                if (displayFiles.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No files found.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    displayFiles.forEach { file ->
                        RecentFileItem(
                            file = file,
                            onClick = { onOpenFileUri(file.uriString) },
                            onToggleStar = { onToggleStar(file.uriString) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(80.dp)) // FAB padding
            }
        } else {
            // Placeholder for other tabs
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("This tab is under construction.", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun QuickToolItem(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).width(72.dp)
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(16.dp),
            color = color.copy(alpha = 0.1f),
            contentColor = color
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.padding(16.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun RecentFileItem(file: PdfFile, onClick: () -> Unit, onToggleStar: () -> Unit) {
    val formatter = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val dateString = formatter.format(Date(file.dateOpened))
    val sizeString = if (file.sizeBytes > 1024 * 1024) {
        String.format(Locale.US, "%.1f MB", file.sizeBytes / (1024f * 1024f))
    } else {
        String.format(Locale.US, "%.1f KB", file.sizeBytes / 1024f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.error
        ) {
            Icon(
                imageVector = Icons.Filled.PictureAsPdf,
                contentDescription = "PDF Icon",
                modifier = Modifier.padding(12.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$dateString • $sizeString",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onToggleStar) {
            Icon(
                imageVector = if (file.isStarred) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = "Toggle Star",
                tint = if (file.isStarred) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
