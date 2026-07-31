package com.printready.app.domain.model

import android.net.Uri

data class DocumentType(
    val id: String,
    val name: String,
    val category: DocumentCategory,
    val widthMm: Float,
    val heightMm: Float,
    val description: String = "",
    val sides: Int = 1
) {
    val dimensionLabel: String get() = "%.1f × %.1f mm".format(widthMm, heightMm)
}

enum class DocumentCategory(val tag: String) {
    ID("ID"),
    DOCUMENT("DOC"),
    FINANCIAL("FIN"),
    CUSTOM("CUST")
}

val DefaultDocumentTypes = listOf(
    DocumentType("aadhar", "Aadhar Card", DocumentCategory.ID, 85.6f, 53.98f, sides = 2),
    DocumentType("pan", "PAN Card", DocumentCategory.ID, 85.6f, 53.98f, sides = 1),
    DocumentType("voter_id", "Voter ID", DocumentCategory.ID, 85.6f, 53.98f, sides = 2),
    DocumentType("dl", "Driving Licence", DocumentCategory.ID, 85.6f, 53.98f, sides = 2),
    DocumentType("passport_page", "Passport (Data Page)", DocumentCategory.ID, 125f, 88f, sides = 1),
    DocumentType("cheque_sbi", "Cheque – SBI", DocumentCategory.FINANCIAL, 175f, 78f, sides = 1),
    DocumentType("cheque_hdfc", "Cheque – HDFC/ICICI", DocumentCategory.FINANCIAL, 190f, 85f, sides = 1),
    DocumentType("passbook", "Passbook Page", DocumentCategory.DOCUMENT, 90f, 140f, sides = 1),
    DocumentType("a4_full", "Full A4 Document", DocumentCategory.DOCUMENT, 210f, 297f, sides = 1),
    DocumentType("custom", "Custom Size", DocumentCategory.CUSTOM, 0f, 0f, "Enter your own dimensions", sides = 1)
)

data class CanvasItem(
    val id: String,
    val documentType: DocumentType,
    val imageUri: Uri?,
    val offsetXMm: Float = 0f,
    val offsetYMm: Float = 0f,
    val scaleFactor: Float = 1f,
    val rotationDeg: Float = 0f,
    val overrideAspectRatio: Float? = null,
    val overrideWidthMm: Float? = null,
    val overrideHeightMm: Float? = null
)

data class PrintJob(
    val id: String,
    val title: String,
    val documentType: DocumentType,
    val items: List<CanvasItem>,
    val marginMm: Float = 10f,
    val createdAt: Long = System.currentTimeMillis(),
    val pageCount: Int = 1,
    val fileSizeBytes: Long = 0L,
    val pdfFilePath: String? = null
)

enum class MarginPreset(val label: String, val mm: Float) {
    NARROW("Narrow", 5f),
    NORMAL("Normal", 10f),
    WIDE("Wide", 15f)
}

// A4 constants (mm and 300 DPI px)
object A4 {
    const val WIDTH_MM = 210f
    const val HEIGHT_MM = 297f
    const val DPI = 300
    const val WIDTH_PX = 2480
    const val HEIGHT_PX = 3508

    fun mmToPx(mm: Float): Float = (mm / 25.4f) * DPI
}
