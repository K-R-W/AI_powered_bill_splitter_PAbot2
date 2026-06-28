package com.example.pa_bot2.ocr

import android.net.Uri
import com.example.pa_bot2.model.BillItem

data class ExtractionResult(
    val items: List<BillItem>,
    val suggestedTitle: String? = null
)

interface BillExtractor {
    suspend fun extractItems(imageUri: Uri): List<BillItem> {
        return extract(imageUri).items
    }
    
    suspend fun extract(imageUri: Uri): ExtractionResult
}
