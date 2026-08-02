package com.printready.app.ui.screens

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalPrintshop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.printready.app.domain.model.*
import com.printready.app.ui.theme.*
import com.printready.app.util.PdfPrintDocumentAdapter
import com.printready.app.viewmodel.PrintReadyViewModel
import com.printready.app.viewmodel.WorkspaceUiState
import com.yalantis.ucrop.UCrop
import java.io.File
import kotlin.math.roundToInt

private data class WorkspaceTool(val label: String, val icon: ImageVector)

private val WORKSPACE_TOOLS = listOf(
    WorkspaceTool("Auto-Fit", Icons.Default.Settings),
    WorkspaceTool("Add Document", Icons.Default.Add)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun A4WorkspaceScreen(
    viewModel: PrintReadyViewModel,
    multiCard: Boolean,
    sourceMode: String,
    onBack: () -> Unit
) {
    val state by viewModel.workspaceState.collectAsState()
    val tools = WORKSPACE_TOOLS
    val context = LocalContext.current

    var pendingActionItemId by remember { mutableStateOf<String?>(null) }
    var showPrintSheet by remember { mutableStateOf(false) }

    val pageLimitCount = state.selectedDocType?.sides ?: 1

    val scannerOptions = remember(pageLimitCount) {
        GmsDocumentScannerOptions.Builder()
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .setGalleryImportAllowed(true)
            .setPageLimit(pageLimitCount)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .build()
    }
    val scanner = remember(scannerOptions) { GmsDocumentScanning.getClient(scannerOptions) }

    val documentScannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            scanResult?.pages?.forEachIndexed { index, page ->
                if (index == 0 && pendingActionItemId != null) {
                    viewModel.updateItemUri(pendingActionItemId!!, page.imageUri)
                } else {
                    viewModel.addImageToCanvas(page.imageUri)
                }
            }
            pendingActionItemId = null
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        PickMultipleVisualMedia(maxItems = pageLimitCount.coerceAtLeast(2))
    ) { uris ->
        uris.forEachIndexed { index, uri ->
            if (index == 0 && pendingActionItemId != null) {
                viewModel.updateItemUri(pendingActionItemId!!, uri)
            } else {
                viewModel.addImageToCanvas(uri)
            }
        }
        pendingActionItemId = null
    }

    val startMlKitScan = {
        scanner.getStartScanIntent(context as Activity)
            .addOnSuccessListener { intentSender ->
                documentScannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
    }

    val startGalleryPick = {
        galleryLauncher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
    }

    val uCropLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val resultUri = UCrop.getOutput(result.data!!)
            val selectedId = state.selectedItemId
            if (resultUri != null && selectedId != null) {
                viewModel.updateItemUri(selectedId, resultUri)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (state.items.all { it.imageUri == null }) {
            if (sourceMode == "gallery") startGalleryPick() else startMlKitScan()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Primary)
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            state.selectedDocType?.name ?: "Workspace",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Primary
                        )
                        if (multiCard) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SecondaryContainer
                            ) {
                                Text(
                                    "Multi-Card",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = OnSecondaryContainer
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.undo() }, enabled = state.canUndo) {
                        Icon(
                            Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo",
                            tint = if (state.canUndo) Primary else OnSurfaceVariant.copy(alpha = 0.38f)
                        )
                    }
                    IconButton(onClick = { viewModel.redo() }, enabled = state.canRedo) {
                        Icon(
                            Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Redo",
                            tint = if (state.canRedo) Primary else OnSurfaceVariant.copy(alpha = 0.38f)
                        )
                    }
                    IconButton(onClick = { viewModel.resetLayout() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset Layout", tint = Primary)
                    }
                    TextButton(onClick = { showPrintSheet = true }) {
                        Text("Print & Save", color = Primary, style = MaterialTheme.typography.headlineSmall)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface.copy(alpha = 0.9f)),
                modifier = Modifier.shadow(elevation = 2.dp, spotColor = Primary.copy(alpha = 0.1f))
            )
        },
        containerColor = SurfaceContainerLow
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            var zoomScale by remember { mutableFloatStateOf(1f) }
            var panOffset by remember { mutableStateOf(Offset.Zero) }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(SurfaceContainerLow)
                    .drawAlignmentGrid()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            zoomScale = (zoomScale * zoom).coerceIn(0.5f, 5f)
                            panOffset += pan
                        }
                    }
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = { viewModel.selectItem(null) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.graphicsLayer {
                        scaleX = zoomScale
                        scaleY = zoomScale
                        translationX = panOffset.x
                        translationY = panOffset.y
                    }
                ) {
                    A4CanvasSheet(
                        items = state.items,
                        selectedItemId = state.selectedItemId,
                        canvasGrayscale = state.canvasGrayscale,
                        onDragStarted = { viewModel.pushUndoSnapshotForDrag() },
                        onItemMoved = { id, dx, dy -> viewModel.updateItemScaleAndPosition(id, 1f, dx, dy) },
                        onItemScaled = { id, scale, x, y, w, h -> viewModel.updateItemScaleAndPosition(id, scale, x, y, w, h) },
                        onItemSelected = { id ->
                            val item = state.items.find { it.id == id }
                            if (item?.imageUri == null) {
                                pendingActionItemId = id
                                if (sourceMode == "gallery") startGalleryPick() else startMlKitScan()
                            } else {
                                viewModel.selectItem(id)
                            }
                        },
                        onCropClicked = { _, _ -> }
                    )
                }
            }

            val handleCropClick: (String, Uri) -> Unit = { id, uri ->
                val item = state.items.find { it.id == id }
                val destUri = Uri.fromFile(File(context.cacheDir, "crop_${System.currentTimeMillis()}.jpg"))
                val isDoc = item?.documentType?.category == DocumentCategory.DOCUMENT ||
                        item?.documentType?.category == DocumentCategory.CUSTOM
                val uCropIntent = UCrop.of(uri, destUri)
                    .withOptions(UCrop.Options().apply {
                        if (item != null && item.documentType.widthMm > 0f && item.documentType.heightMm > 0f && !isDoc) {
                            withAspectRatio(item.documentType.widthMm, item.documentType.heightMm)
                            setFreeStyleCropEnabled(false)
                        } else {
                            setFreeStyleCropEnabled(true)
                            setAspectRatioOptions(
                                0,
                                com.yalantis.ucrop.model.AspectRatio("Custom", 0f, 0f),
                                com.yalantis.ucrop.model.AspectRatio("1:1", 1f, 1f),
                                com.yalantis.ucrop.model.AspectRatio("3:4", 3f, 4f),
                                com.yalantis.ucrop.model.AspectRatio("3:2", 3f, 2f),
                                com.yalantis.ucrop.model.AspectRatio("16:9", 16f, 9f)
                            )
                        }
                        setHideBottomControls(false)
                        setToolbarTitle("Crop Image")
                        setCompressionQuality(100)
                    })
                    .getIntent(context)
                viewModel.selectItem(id)
                uCropLauncher.launch(uCropIntent)
            }

            Surface(shadowElevation = 4.dp, color = Surface) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Adjust panel — shown when a filled item is selected
                    val selectedItem = state.items.find { it.id == state.selectedItemId && it.imageUri != null }
                    if (selectedItem != null) {
                        HorizontalDivider(color = OutlineVariant.copy(alpha = 0.5f))
                        AdjustPanel(
                            item = selectedItem,
                            onAdjust = { b, c, g ->
                                viewModel.updateItemAdjustments(selectedItem.id, b, c, g)
                            },
                            onCropClick = {
                                selectedItem.imageUri?.let { uri ->
                                    handleCropClick(selectedItem.id, uri)
                                }
                            },
                            onDeleteClick = {
                                viewModel.removeItem(selectedItem.id)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                    HorizontalDivider(color = OutlineVariant.copy(alpha = 0.5f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Print in Grayscale",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurface
                        )
                        androidx.compose.material3.Switch(
                            checked = state.canvasGrayscale,
                            onCheckedChange = { viewModel.toggleCanvasGrayscale() }
                        )
                    }
                    HorizontalDivider(color = OutlineVariant.copy(alpha = 0.5f))
                    MarginSegmentedControl(
                        selected = state.marginPreset,
                        onSelect = { viewModel.setMargin(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    HorizontalDivider(color = OutlineVariant.copy(alpha = 0.5f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        tools.forEachIndexed { i, tool ->
                            ToolButton(
                                label = tool.label,
                                icon = tool.icon,
                                isActive = state.activeToolIndex == i,
                                onClick = {
                                    viewModel.setActiveTool(i)
                                    when (i) {
                                        0 -> viewModel.autoFitItems()
                                        1 -> if (sourceMode == "gallery") startGalleryPick() else startMlKitScan()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPrintSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showPrintSheet = false
                viewModel.clearPdfResult()
            },
            containerColor = Surface
        ) {
            PrintExportSheet(
                state = state,
                context = context,
                onGenerate = { viewModel.generatePdf() },
                onDismiss = {
                    showPrintSheet = false
                    viewModel.clearPdfResult()
                }
            )
        }
    }
}

@Composable
private fun PrintExportSheet(
    state: WorkspaceUiState,
    context: android.content.Context,
    onGenerate: () -> Unit,
    onDismiss: () -> Unit
) {
    val hasImages = state.items.any { it.imageUri != null }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Print & Export PDF",
            style = MaterialTheme.typography.headlineMedium,
            color = Primary
        )

        Spacer(Modifier.height(20.dp))

        SpecGrid(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceContainerLowest)
                .padding(16.dp)
        )

        Spacer(Modifier.height(16.dp))

        when {
            // ── ERROR ──────────────────────────────────────────────────────────
            state.errorMessage != null -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ErrorContainer.copy(alpha = 0.2f))
                        .border(1.dp, ErrorContainer, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, null, tint = Error, modifier = Modifier.size(20.dp))
                    Text(
                        state.errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnErrorContainer
                    )
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onGenerate,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Retry", style = MaterialTheme.typography.labelLarge)
                }
            }

            // ── GENERATING ────────────────────────────────────────────────────
            state.pdfGenerating -> {
                Spacer(Modifier.height(8.dp))
                CircularProgressIndicator(color = Primary, strokeWidth = 3.dp)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Generating high-resolution PDF…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Rendering at 300 DPI — this may take a moment",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            // ── DONE ──────────────────────────────────────────────────────────
            state.pdfFile != null -> {
                val file = state.pdfFile

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SecondaryContainer.copy(alpha = 0.4f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, null, tint = Secondary, modifier = Modifier.size(18.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            file.name,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = OnSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${formatPdfSize(file.length())} · A4 · 300 DPI",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ErrorContainer.copy(alpha = 0.15f))
                        .border(1.dp, ErrorContainer.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, null, tint = Error, modifier = Modifier.size(16.dp))
                    Text(
                        "Print at Actual Size — do not scale to fit page",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnErrorContainer
                    )
                }

                Spacer(Modifier.height(20.dp))

                // Print via system print dialog
                Button(
                    onClick = {
                        val fileUri = FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", file
                        )
                        val printManager = context.getSystemService(android.content.Context.PRINT_SERVICE) as PrintManager
                        printManager.print(
                            file.nameWithoutExtension,
                            PdfPrintDocumentAdapter(context, fileUri),
                            PrintAttributes.Builder()
                                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                                .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
                                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                                .build()
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Icon(Icons.Default.LocalPrintshop, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Print", style = MaterialTheme.typography.labelLarge, color = OnPrimary)
                }

                Spacer(Modifier.height(10.dp))

                // Save to Downloads
                OutlinedButton(
                    onClick = {
                        val saved = savePdfToDownloads(context, file)
                        Toast.makeText(
                            context,
                            if (saved) "Saved to Downloads" else "Could not save to Downloads",
                            Toast.LENGTH_SHORT
                        ).show()
                        if (saved) onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Secondary)
                ) {
                    Icon(Icons.Default.FileDownload, null, tint = Secondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save to Downloads", style = MaterialTheme.typography.labelLarge, color = Secondary)
                }

                Spacer(Modifier.height(10.dp))

                // Share
                TextButton(
                    onClick = {
                        val uri = FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", file
                        )
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share PDF"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Share PDF", color = OnSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                }
            }

            // ── IDLE ──────────────────────────────────────────────────────────
            else -> {
                if (!hasImages) {
                    Text(
                        "Add at least one image to the canvas before exporting.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                }
                Button(
                    onClick = onGenerate,
                    enabled = hasImages,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text("Generate PDF", style = MaterialTheme.typography.labelLarge, color = OnPrimary)
                }
            }
        }
    }
}

private fun buildPreviewColorFilter(brightness: Float, contrast: Float, grayscale: Boolean): ColorFilter? {
    val isNeutral = brightness == 0f && contrast == 1f && !grayscale
    if (isNeutral) return null

    val c = contrast
    val t = (1f - c) * 128f
    val b = brightness * 255f
    val offset = t + b

    val matrix = if (grayscale) {
        val rw = 0.299f
        val gw = 0.587f
        val bw = 0.114f
        floatArrayOf(
            c * rw, c * gw, c * bw, 0f, offset,
            c * rw, c * gw, c * bw, 0f, offset,
            c * rw, c * gw, c * bw, 0f, offset,
            0f, 0f, 0f, 1f, 0f
        )
    } else {
        floatArrayOf(
            c, 0f, 0f, 0f, offset,
            0f, c, 0f, 0f, offset,
            0f, 0f, c, 0f, offset,
            0f, 0f, 0f, 1f, 0f
        )
    }

    return ColorFilter.colorMatrix(ColorMatrix(matrix))
}

@Composable
private fun AdjustPanel(
    item: CanvasItem,
    onAdjust: (brightness: Float, contrast: Float, grayscale: Boolean) -> Unit,
    onCropClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Adjust",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = OnSurface
            )
            Spacer(Modifier.weight(1f))
            
            IconButton(onClick = onCropClick) {
                Icon(Icons.Default.Crop, contentDescription = "Crop Image", tint = Primary)
            }
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Image", tint = MaterialTheme.colorScheme.error)
            }

            FilterChip(
                selected = item.grayscale,
                onClick = { onAdjust(item.brightness, item.contrast, !item.grayscale) },
                label = { Text("Grayscale", style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PrimaryContainer,
                    selectedLabelColor = OnPrimaryContainer
                )
            )
        }

        SliderRow(
            label = "Brightness",
            value = item.brightness,
            valueRange = -1f..1f,
            onValueChange = { onAdjust(it, item.contrast, item.grayscale) }
        )
        SliderRow(
            label = "Contrast",
            value = item.contrast,
            valueRange = 0.5f..2f,
            onValueChange = { onAdjust(item.brightness, it, item.grayscale) }
        )
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = OnSurfaceVariant,
            modifier = Modifier.width(70.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Primary,
                activeTrackColor = Primary,
                inactiveTrackColor = SurfaceContainerHigh
            )
        )
    }
}

private fun savePdfToDownloads(context: android.content.Context, file: File): Boolean = runCatching {
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, file.name)
        put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
        put(MediaStore.Downloads.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
    resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
    values.clear()
    values.put(MediaStore.Downloads.IS_PENDING, 0)
    resolver.update(uri, values, null, null)
    true
}.getOrDefault(false)

private fun formatPdfSize(bytes: Long): String {
    if (bytes == 0L) return "--"
    return if (bytes < 1024 * 1024) "${bytes / 1024} KB" else "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

@Composable
private fun A4CanvasSheet(
    items: List<CanvasItem>,
    selectedItemId: String?,
    canvasGrayscale: Boolean,
    onDragStarted: () -> Unit,
    onItemMoved: (String, Float, Float) -> Unit,
    onItemScaled: (String, Float, Float, Float, Float?, Float?) -> Unit,
    onItemSelected: (String) -> Unit,
    onCropClicked: (String, Uri) -> Unit
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .widthIn(max = 340.dp)
            .aspectRatio(1f / 1.414f)
            .shadow(8.dp, RoundedCornerShape(2.dp))
            .clip(RoundedCornerShape(2.dp))
            .background(SurfaceContainerLowest)
            .onSizeChanged { canvasSize = it }
    ) {
        Text(
            "A4",
            style = MaterialTheme.typography.labelSmall,
            color = OnSurfaceVariant,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .background(SurfaceContainer, RoundedCornerShape(2.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )

        DashedMarginGuide()

        val density = LocalDensity.current
        val totalItems = items.size
        val scaleW = if (canvasSize.width > 0) canvasSize.width / A4.WIDTH_MM else 1f
        val scaleH = if (canvasSize.height > 0) canvasSize.height / A4.HEIGHT_MM else 1f

        items.forEachIndexed { index, item ->
            DraggableCardItem(
                item = item,
                itemIndex = index,
                totalItems = totalItems,
                isSelected = item.id == selectedItemId,
                canvasGrayscale = canvasGrayscale,
                scaleW = scaleW,
                scaleH = scaleH,
                canvasSize = canvasSize,
                density = density,
                onDragStarted = onDragStarted,
                onItemMoved = onItemMoved,
                onItemScaled = onItemScaled,
                onSelect = { onItemSelected(item.id) },
                onCropClicked = { uri -> onCropClicked(item.id, uri) }
            )
        }
    }
}

@Composable
private fun DraggableCardItem(
    item: CanvasItem,
    itemIndex: Int,
    totalItems: Int,
    isSelected: Boolean,
    canvasGrayscale: Boolean,
    scaleW: Float,
    scaleH: Float,
    canvasSize: IntSize,
    density: androidx.compose.ui.unit.Density,
    modifier: Modifier = Modifier,
    onDragStarted: () -> Unit,
    onItemMoved: (String, Float, Float) -> Unit,
    onItemScaled: (String, Float, Float, Float, Float?, Float?) -> Unit,
    onSelect: () -> Unit,
    onCropClicked: (Uri) -> Unit
) {
    var localXMm by remember(item.id, item.offsetXMm) { mutableFloatStateOf(item.offsetXMm) }
    var localYMm by remember(item.id, item.offsetYMm) { mutableFloatStateOf(item.offsetYMm) }

    val initialEffectiveAspectRatio = item.overrideAspectRatio
        ?: (item.documentType.widthMm / item.documentType.heightMm.coerceAtLeast(1f))
    val initialHeightMm = item.documentType.widthMm / initialEffectiveAspectRatio

    var localWidthMm by remember(item.id, item.overrideWidthMm, item.scaleFactor) {
        mutableFloatStateOf(item.overrideWidthMm ?: (item.documentType.widthMm * item.scaleFactor))
    }
    var localHeightMm by remember(item.id, item.overrideHeightMm, item.scaleFactor) {
        mutableFloatStateOf(item.overrideHeightMm ?: (initialHeightMm * item.scaleFactor))
    }

    val effectiveAspectRatio = localWidthMm / localHeightMm.coerceAtLeast(1f)
    val rawW = (localWidthMm * scaleW).roundToInt()
    val itemWidthDp = with(density) { rawW.toDp() }
    val xPx = (localXMm * scaleW).roundToInt()
    val yPx = (localYMm * scaleH).roundToInt()

    Box(
        modifier = modifier
            .offset { IntOffset(xPx, yPx) }
            .width(itemWidthDp)
            .aspectRatio(effectiveAspectRatio)
            .clip(RoundedCornerShape(2.dp))
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) Secondary else Color.Transparent,
                shape = RoundedCornerShape(2.dp)
            )
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onSelect
            )
            .pointerInput(item.id, canvasSize) {
                detectDragGestures(
                    onDragStart = { onDragStarted(); onSelect() },
                    onDragEnd = { onItemMoved(item.id, localXMm, localYMm) },
                    onDragCancel = { onItemMoved(item.id, localXMm, localYMm) },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (canvasSize.width > 0 && canvasSize.height > 0) {
                            val dxMm = (dragAmount.x / canvasSize.width) * A4.WIDTH_MM
                            val dyMm = (dragAmount.y / canvasSize.height) * A4.HEIGHT_MM
                            val maxXMm = (A4.WIDTH_MM - localWidthMm).coerceAtLeast(0f)
                            val maxYMm = (A4.HEIGHT_MM - localHeightMm).coerceAtLeast(0f)
                            localXMm = (localXMm + dxMm).coerceIn(0f, maxXMm)
                            localYMm = (localYMm + dyMm).coerceIn(0f, maxYMm)
                        }
                    }
                )
            }
    ) {
        if (item.imageUri != null) {
            AsyncImage(
                model = item.imageUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = buildPreviewColorFilter(item.brightness, item.contrast, item.grayscale || canvasGrayscale),
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val labelText = when {
                totalItems == 2 && itemIndex == 0 -> "Front (Top Left)"
                totalItems == 2 && itemIndex == 1 -> "Back (Top Right)"
                totalItems == 1 -> "Front (Top Center)"
                else -> "Side ${itemIndex + 1}"
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SurfaceContainerHighest)
                    .drawBehind {
                        drawRect(
                            color = OutlineVariant,
                            style = Stroke(
                                width = 4f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                            )
                        )
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add image",
                    tint = Primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = labelText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
                Text(
                    text = "Tap to capture",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant
                )
            }
        }

        if (isSelected) {
            CornerHandles(
                onScaleStart = { onDragStarted() },
                onScaleDelta = { corner, dragAmountPx ->
                    val dxMm = dragAmountPx.x / scaleW
                    val dyMm = dragAmountPx.y / scaleH
                    var dw: Float
                    var dh: Float
                    var moveX: Float
                    var moveY: Float
                    when (corner) {
                        Corner.TopLeft -> { dw = -dxMm; dh = -dyMm; moveX = dxMm; moveY = dyMm }
                        Corner.TopRight -> { dw = dxMm; dh = -dyMm; moveY = dyMm; moveX = 0f }
                        Corner.BottomLeft -> { dw = -dxMm; dh = dyMm; moveX = dxMm; moveY = 0f }
                        Corner.BottomRight -> { dw = dxMm; dh = dyMm; moveX = 0f; moveY = 0f }
                    }
                    if (localWidthMm + dw >= 10f && localHeightMm + dh >= 10f) {
                        localWidthMm += dw; localHeightMm += dh
                        localXMm += moveX; localYMm += moveY
                    }
                },
                onScaleEnd = {
                    onItemScaled(item.id, 1f, localXMm, localYMm, localWidthMm, localHeightMm)
                }
            )

        }
    }
}

enum class Corner { TopLeft, TopRight, BottomLeft, BottomRight }

@Composable
private fun CornerHandles(
    onScaleStart: () -> Unit,
    onScaleDelta: (Corner, Offset) -> Unit,
    onScaleEnd: () -> Unit
) {
    val handleTouchSize = 28.dp
    val handleVisualSize = 12.dp

    @Composable
    fun BoxScope.CornerHandleBox(alignment: Alignment, corner: Corner) {
        Box(
            modifier = Modifier
                .align(alignment)
                .size(handleTouchSize)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { onScaleStart() },
                        onDragEnd = { onScaleEnd() },
                        onDragCancel = { onScaleEnd() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onScaleDelta(corner, dragAmount)
                        }
                    )
                },
            contentAlignment = alignment
        ) {
            Box(
                modifier = Modifier
                    .size(handleVisualSize)
                    .clip(CircleShape)
                    .background(SurfaceContainerLowest)
                    .border(2.dp, Secondary, CircleShape)
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CornerHandleBox(Alignment.TopStart, Corner.TopLeft)
        CornerHandleBox(Alignment.TopEnd, Corner.TopRight)
        CornerHandleBox(Alignment.BottomStart, Corner.BottomLeft)
        CornerHandleBox(Alignment.BottomEnd, Corner.BottomRight)
    }
}

@Composable
private fun DashedMarginGuide() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .drawBehind {
                drawRect(
                    color = OutlineVariant.copy(alpha = 0.6f),
                    style = Stroke(
                        width = 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    )
                )
            }
    )
}

@Composable
private fun MarginSegmentedControl(
    selected: MarginPreset,
    onSelect: (MarginPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(SurfaceContainerLow, RoundedCornerShape(4.dp))
            .border(1.dp, OutlineVariant, RoundedCornerShape(4.dp))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        MarginPreset.entries.forEach { preset ->
            val isSelected = preset == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isSelected) PrimaryContainer else Color.Transparent)
                    .clickable { onSelect(preset) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    preset.label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = if (isSelected) OnPrimaryContainer else OnSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ToolButton(label: String, icon: ImageVector, isActive: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .widthIn(min = 72.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (isActive) PrimaryFixed else SurfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (isActive) Primary else OnSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) Primary else OnSurfaceVariant
        )
    }
}

private fun Modifier.drawAlignmentGrid(): Modifier = this.drawBehind {
    val step = size.width * 0.1f
    var x = 0f
    while (x <= size.width) {
        drawLine(Color(0xFF43474E).copy(alpha = 0.08f), Offset(x, 0f), Offset(x, size.height), 1f)
        x += step
    }
    var y = 0f
    while (y <= size.height) {
        drawLine(Color(0xFF43474E).copy(alpha = 0.08f), Offset(0f, y), Offset(size.width, y), 1f)
        y += step
    }
}

@Composable
private fun SpecGrid(modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.SpaceEvenly) {
        SpecCell(label = "PAGE SIZE", value = "A4 (210 × 297 mm)")
        VerticalDivider(color = OutlineVariant, modifier = Modifier.height(48.dp))
        SpecCell(label = "QUALITY", value = "High (300 DPI)")
        VerticalDivider(color = OutlineVariant, modifier = Modifier.height(48.dp))
        SpecCell(label = "SCALE", value = "100% Actual Size")
    }
}

@Composable
private fun SpecCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
    }
}
