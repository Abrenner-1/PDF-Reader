package com.example.pdfreader

data class PdfFile(
    val uriString: String,
    val name: String,
    val dateOpened: Long,
    val sizeBytes: Long,
    val isStarred: Boolean = false
)
