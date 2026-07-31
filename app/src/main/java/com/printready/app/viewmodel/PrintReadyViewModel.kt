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

    fun selectDocumentType(type: DocumentType) {
        _selectedDocType.value = type

        val centerX = (A4.WIDTH_MM - type.widthMm) / 2f
        val centerY = (A4.HEIGHT_MM - type.heightMm) / 2f
        val spacingY = type.heightMm + 10f
        val baseOffsetY = centerY - (spacingY / 2f)

        val initialItems = List(type.sides) { index ->
            val (startX, startY) = if (type.sides == 2) {
                 Pair(centerX, baseOffsetY + index * spacingY)
            } else {
                 Pair(centerX, centerY)
            }
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
                // Because users might scan a new side specifically via single-tap overlay, they'll use a specific action.
                // But if they just hit "Add Page" / "Scan" globally:
                if (s.isMultiCard) {
                     val docType = s.selectedDocType ?: DefaultDocumentTypes.first()
                     val itemsCount = s.items.size
                     val centerX = (A4.WIDTH_MM - docType.widthMm) / 2f
                     val centerY = (A4.HEIGHT_MM - docType.heightMm) / 2f
                     val item = CanvasItem(
                         id = UUID.randomUUID().toString(),
                         documentType = docType,
                         imageUri = uri,
                         offsetXMm = centerX + (itemsCount * 10f),
                         offsetYMm = centerY + (itemsCount * 10f)
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
        _workspaceState.update { it.copy(marginPreset = preset) }
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
        val availW = A4.WIDTH_MM - marginMm * 2
        val availH = A4.HEIGHT_MM - marginMm * 2
        val scaleW = availW / docType.widthMm
        val scaleH = availH / docType.heightMm
        val scale = minOf(scaleW, scaleH, 1f)

        _workspaceState.update { state ->
            state.copy(items = state.items.mapIndexed { i, item ->
                val col = i % 2
                val row = i / 2
                item.copy(
                    scaleFactor = scale,
                    offsetXMm = marginMm + col * (docType.widthMm * scale + 2f),
                    offsetYMm = marginMm + row * (docType.heightMm * scale + 2f)
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
