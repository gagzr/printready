package com.printready.app.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.printready.app.domain.model.*
import com.printready.app.domain.usecase.GeneratePdfUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class WorkspaceUiState(
    val selectedDocType: DocumentType? = null,
    val items: List<CanvasItem> = emptyList(),
    val selectedItemId: String? = null,
    val marginPreset: MarginPreset = MarginPreset.NORMAL,
    val activeToolIndex: Int = 0,
    val isMultiCard: Boolean = false,
    val pdfGenerating: Boolean = false,
    val pdfFile: File? = null,
    val errorMessage: String? = null
)

data class DashboardUiState(
    val recentJobs: List<PrintJob> = emptyList(),
    val presets: List<DocumentType> = DefaultDocumentTypes.filter { it.category == DocumentCategory.ID }
)

class PrintReadyViewModel(application: Application) : AndroidViewModel(application) {

    private val generatePdf = GeneratePdfUseCase(application)

    private val _workspaceState = MutableStateFlow(WorkspaceUiState())
    val workspaceState: StateFlow<WorkspaceUiState> = _workspaceState.asStateFlow()

    private val _dashboardState = MutableStateFlow(DashboardUiState())
    val dashboardState: StateFlow<DashboardUiState> = _dashboardState.asStateFlow()

    private val _selectedDocType = MutableStateFlow<DocumentType?>(null)
    val selectedDocType: StateFlow<DocumentType?> = _selectedDocType.asStateFlow()

    fun clearWorkspace() {
        _workspaceState.value = WorkspaceUiState()
        _selectedDocType.value = null
    }

    private fun calculateSkeletonPosition(
        index: Int,
        totalSides: Int,
        docType: DocumentType,
        marginMm: Float
    ): Pair<Float, Float> {
        return when {
            // 2-side IDs (Aadhar Card, Voter ID, DL):
            // Side 0 (Front) -> Top Left (marginMm, marginMm)
            // Side 1 (Back)  -> Top Right (A4.WIDTH_MM - marginMm - widthMm, marginMm)
            totalSides == 2 -> {
                if (index == 0) {
                    Pair(marginMm, marginMm)
                } else {
                    val rightX = (A4.WIDTH_MM - marginMm - docType.widthMm).coerceAtLeast(marginMm)
                    Pair(rightX, marginMm)
                }
            }
            // 1-side IDs (PAN Card, Passport, etc.):
            // Top Center ((A4.WIDTH_MM - widthMm) / 2, marginMm)
            totalSides == 1 -> {
                val centerX = ((A4.WIDTH_MM - docType.widthMm) / 2f).coerceAtLeast(marginMm)
                Pair(centerX, marginMm)
            }
            // General fallback for multiple items / multi-card:
            else -> {
                val col = index % 2
                val row = index / 2
                val x = if (col == 0) marginMm else (A4.WIDTH_MM - marginMm - docType.widthMm).coerceAtLeast(marginMm)
                val y = marginMm + row * (docType.heightMm + 10f)
                Pair(x, y)
            }
        }
    }

    fun selectDocumentType(type: DocumentType) {
        _selectedDocType.value = type
        val currentMargin = _workspaceState.value.marginPreset.mm

        val initialItems = List(type.sides) { index ->
            val (startX, startY) = calculateSkeletonPosition(index, type.sides, type, currentMargin)
            CanvasItem(
                id = UUID.randomUUID().toString(),
                documentType = type,
                imageUri = null,
                offsetXMm = startX,
                offsetYMm = startY
            )
        }

        _workspaceState.update { it.copy(selectedDocType = type, items = initialItems, selectedItemId = null) }
    }

    fun selectItem(itemId: String?) {
        _workspaceState.update { it.copy(selectedItemId = itemId) }
    }

    fun addImageToCanvas(uri: Uri) {
        // If there's a selected item that is empty or we are replacing it, we could target it, but let's just find the first empty placeholder natively.
        // OR add it at the end if none are empty.
        _workspaceState.update { s ->
            val firstEmptyIndex = s.items.indexOfFirst { it.imageUri == null }
            if (firstEmptyIndex != -1) {
                val newItems = s.items.toMutableList()
                newItems[firstEmptyIndex] = newItems[firstEmptyIndex].copy(imageUri = uri)
                s.copy(items = newItems)
            } else {
                // All full, behavior: either append (multiCard) or replace first depending on logic.
                if (s.isMultiCard) {
                     val docType = s.selectedDocType ?: DefaultDocumentTypes.first()
                     val itemsCount = s.items.size
                     val (newX, newY) = calculateSkeletonPosition(itemsCount, itemsCount + 1, docType, s.marginPreset.mm)
                     val item = CanvasItem(
                         id = UUID.randomUUID().toString(),
                         documentType = docType,
                         imageUri = uri,
                         offsetXMm = newX,
                         offsetYMm = newY
                     )
                     s.copy(items = s.items + item)
                } else {
                     s // No room to add globally unless multiCard is true.
                }
            }
        }
    }

    fun updateItemPosition(itemId: String, offsetXMm: Float, offsetYMm: Float) {
        _workspaceState.update { state ->
            state.copy(items = state.items.map {
                if (it.id == itemId) it.copy(offsetXMm = offsetXMm, offsetYMm = offsetYMm) else it
            })
        }
    }

    fun setMargin(preset: MarginPreset) {
        _workspaceState.update { state ->
            val updatedItems = state.items.mapIndexed { index, item ->
                val (newX, newY) = calculateSkeletonPosition(index, state.items.size, item.documentType, preset.mm)
                item.copy(offsetXMm = newX, offsetYMm = newY)
            }
            state.copy(marginPreset = preset, items = updatedItems)
        }
    }

    fun setActiveTool(index: Int) {
        _workspaceState.update { it.copy(activeToolIndex = index) }
    }

    fun toggleMultiCard() {
        _workspaceState.update { it.copy(isMultiCard = !it.isMultiCard) }
    }

    fun generatePdf() {
        val state = _workspaceState.value
        if (state.items.isEmpty()) return

        _workspaceState.update { it.copy(pdfGenerating = true, errorMessage = null) }

        viewModelScope.launch(Dispatchers.IO) {
            val dir = File(getApplication<Application>().getExternalFilesDir(null), "Documents")
            dir.mkdirs()
            val outFile = File(dir, "printready_${System.currentTimeMillis()}.pdf")

            val job = PrintJob(
                id = UUID.randomUUID().toString(),
                title = state.selectedDocType?.name ?: "Document",
                documentType = state.selectedDocType ?: DefaultDocumentTypes.first(),
                items = state.items,
                marginMm = state.marginPreset.mm
            )

            val result = generatePdf.execute(job, outFile)

            _workspaceState.update { s ->
                result.fold(
                    onSuccess = { file ->
                        val updatedJob = job.copy(fileSizeBytes = file.length())
                        _dashboardState.update { ds -> ds.copy(recentJobs = listOf(updatedJob) + ds.recentJobs) }
                        s.copy(pdfGenerating = false, pdfFile = file)
                    },
                    onFailure = { e ->
                        s.copy(pdfGenerating = false, errorMessage = e.message ?: "PDF generation failed")
                    }
                )
            }
        }
    }

    fun clearPdfResult() {
        _workspaceState.update { it.copy(pdfFile = null, errorMessage = null) }
    }

    fun autoFitItems() {
        val docType = _workspaceState.value.selectedDocType ?: return
        val marginMm = _workspaceState.value.marginPreset.mm

        _workspaceState.update { state ->
            state.copy(items = state.items.mapIndexed { index, item ->
                val (newX, newY) = calculateSkeletonPosition(index, state.items.size, item.documentType, marginMm)
                item.copy(
                    scaleFactor = 1f,
                    offsetXMm = newX,
                    offsetYMm = newY
                )
            })
        }
    }

    fun updateItemUri(itemId: String, uri: Uri) {
        _workspaceState.update { state ->
            state.copy(items = state.items.map {
                if (it.id == itemId) it.copy(imageUri = uri) else it
            })
        }
    }
}
