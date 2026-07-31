package com.printready.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import com.printready.app.domain.model.*
import com.printready.app.ui.theme.*
import com.printready.app.viewmodel.PrintReadyViewModel

import android.app.Activity
import androidx.activity.result.IntentSenderRequest
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import android.content.Intent
import com.yalantis.ucrop.UCrop
import java.io.File

import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Refresh
import android.widget.Toast
import androidx.core.content.FileProvider
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.filled.Info
import androidx.compose.foundation.BorderStroke
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

    LaunchedEffect(showPrintSheet) {
        if (showPrintSheet && state.pdfFile == null && !state.pdfGenerating) {
            viewModel.generatePdf()
        }
    }


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

    val startMlKitScan = {
        scanner.getStartScanIntent(context as Activity)
            .addOnSuccessListener { intentSender ->
                documentScannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
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

    // Auto-launch scanner if coming from dashboard fresh
    LaunchedEffect(Unit) {
        if (state.items.all { it.imageUri == null }) {
            startMlKitScan()
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
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = state.canUndo
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo",
                            tint = if (state.canUndo) Primary else OnSurfaceVariant.copy(alpha = 0.38f)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = state.canRedo
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Redo",
                            tint = if (state.canRedo) Primary else OnSurfaceVariant.copy(alpha = 0.38f)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.resetLayout() }
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reset Layout",
                            tint = Primary
                        )
                    }
                    TextButton(onClick = { showPrintSheet = true }) {
                        Text("Print & Save", color = Primary, style = MaterialTheme.typography.headlineSmall)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        },
        containerColor = SurfaceContainerLow
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Canvas area — takes all remaining space
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(SurfaceContainerLow)
                    .drawAlignmentGrid()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = { viewModel.selectItem(null) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                A4CanvasSheet(
                    items = state.items,
                    selectedItemId = state.selectedItemId,
                    multiCard = multiCard,
                    onDragStarted = { viewModel.pushUndoSnapshotForDrag() },
                    onItemMoved = { id, dx, dy -> viewModel.updateItemPosition(id, dx, dy) },
                    onItemSelected = { id ->
                        val item = state.items.find { it.id == id }
                        if (item?.imageUri == null) {
                            pendingActionItemId = id
                            startMlKitScan()
                        } else {
                            viewModel.selectItem(id)
                        }
                    },
                    onAddImage = { startMlKitScan() },
                    onCropClicked = { id, uri ->
                        val destUri = Uri.fromFile(File(context.cacheDir, "crop_${System.currentTimeMillis()}.jpg"))
                        val uCropIntent = UCrop.of(uri, destUri)
                            .withOptions(UCrop.Options().apply {
                                setFreeStyleCropEnabled(true)
                                setHideBottomControls(false)
                                setToolbarTitle("Crop Image")
                                setCompressionQuality(100)
                            })
                            .getIntent(context)
                        viewModel.selectItem(id)
                        uCropLauncher.launch(uCropIntent)
                    }
                )
            }

            // Bottom toolbar
            Surface(
                shadowElevation = 4.dp,
                color = Surface
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Margin segmented control
                    MarginSegmentedControl(
                        selected = state.marginPreset,
                        onSelect = { viewModel.setMargin(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    HorizontalDivider(color = OutlineVariant.copy(alpha = 0.5f))

                    // Tool scroll row
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
                                        1 -> startMlKitScan()
                                        else -> {}
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
            onDismissRequest = { showPrintSheet = false },
            containerColor = Surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Print & Save PDF", style = MaterialTheme.typography.headlineMedium, color = Primary)
                Spacer(Modifier.height(16.dp))
                
                val docType = state.selectedDocType
                if (docType != null) {
                    SpecGrid(
                        pageSize = "A4 (210 × 297 mm)",
                        quality = "High (300 DPI)",
                        scale = "100% Actual Size",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceContainerLowest)
                            .padding(16.dp)
                    )
                }
                
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ErrorContainer.copy(alpha = 0.2f))
                        .border(1.dp, ErrorContainer, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Info, null, tint = Error, modifier = Modifier.size(20.dp))
                    Column {
                        Text(
                            "Printer scale check required",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = OnErrorContainer
                        )
                        Text(
                            "Set your printer to print at Actual Size — do not scale to fit.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnErrorContainer
                        )
                    }
                }
                
                Spacer(Modifier.height(24.dp))
                
                Button(
                    onClick = {
                        state.pdfFile?.let { file ->
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/pdf")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryContainer),
                    enabled = state.pdfFile != null && !state.pdfGenerating
                ) {
                    if (state.pdfGenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = OnPrimary, strokeWidth = 2.dp)
                    } else {
                        Text("Print", style = MaterialTheme.typography.headlineSmall, color = OnPrimary)
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        state.pdfFile?.let { file ->
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Save PDF"))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Secondary),
                    enabled = state.pdfFile != null
                ) {
                    Text("Save PDF", style = MaterialTheme.typography.headlineSmall, color = Secondary)
                }
            }
        }
    }
}

@Composable
private fun A4CanvasSheet(
    items: List<CanvasItem>,
    selectedItemId: String?,
    multiCard: Boolean,
    onDragStarted: () -> Unit,
    onItemMoved: (String, Float, Float) -> Unit,
    onItemSelected: (String) -> Unit,
    onAddImage: () -> Unit,
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
        // A4 badge
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

        // Dashed margin guide
        DashedMarginGuide()

        val density = LocalDensity.current
        val totalItems = items.size
        val scaleW = if (canvasSize.width > 0) canvasSize.width / A4.WIDTH_MM else 1f
        val scaleH = if (canvasSize.height > 0) canvasSize.height / A4.HEIGHT_MM else 1f

        items.forEachIndexed { index, item ->
            val rawW = (item.documentType.widthMm * item.scaleFactor * scaleW).roundToInt()
            val itemWidthDp = with(density) { rawW.toDp() }

            DraggableCardItem(
                item = item,
                itemIndex = index,
                totalItems = totalItems,
                isSelected = item.id == selectedItemId,
                scaleW = scaleW,
                scaleH = scaleH,
                canvasSize = canvasSize,
                itemWidthDp = itemWidthDp,
                onDragStarted = onDragStarted,
                onItemMoved = onItemMoved,
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
    scaleW: Float,
    scaleH: Float,
    canvasSize: IntSize,
    itemWidthDp: Dp,
    modifier: Modifier = Modifier,
    onDragStarted: () -> Unit,
    onItemMoved: (String, Float, Float) -> Unit,
    onSelect: () -> Unit,
    onCropClicked: (Uri) -> Unit
) {
    var localXMm by remember(item.id, item.offsetXMm) { mutableFloatStateOf(item.offsetXMm) }
    var localYMm by remember(item.id, item.offsetYMm) { mutableFloatStateOf(item.offsetYMm) }

    val xPx = (localXMm * scaleW).roundToInt()
    val yPx = (localYMm * scaleH).roundToInt()

    Box(
        modifier = modifier
            .offset { IntOffset(xPx, yPx) }
            .width(itemWidthDp)
            .aspectRatio(item.documentType.widthMm / item.documentType.heightMm.coerceAtLeast(1f))
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
                    onDragStart = {
                        onDragStarted()
                        onSelect()
                    },
                    onDragEnd = {
                        onItemMoved(item.id, localXMm, localYMm)
                    },
                    onDragCancel = {
                        onItemMoved(item.id, localXMm, localYMm)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (canvasSize.width > 0 && canvasSize.height > 0) {
                            val dxMm = (dragAmount.x / canvasSize.width) * A4.WIDTH_MM
                            val dyMm = (dragAmount.y / canvasSize.height) * A4.HEIGHT_MM
                            val maxXMm = (A4.WIDTH_MM - item.documentType.widthMm).coerceAtLeast(0f)
                            val maxYMm = (A4.HEIGHT_MM - item.documentType.heightMm).coerceAtLeast(0f)
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
                        val stroke = Stroke(
                            width = 4f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                        )
                        drawRect(color = OutlineVariant, style = stroke)
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
                androidx.compose.material3.Text(
                    text = labelText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
                androidx.compose.material3.Text(
                    text = "Tap to capture",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant
                )
            }
        }
        // Corner handles when selected
        if (isSelected) {
            CornerHandles()
            if (item.imageUri != null) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    SmallFloatingActionButton(
                        onClick = { onCropClicked(item.imageUri) },
                        containerColor = Primary,
                        contentColor = OnPrimary,
                        modifier = Modifier.size(32.dp).offset(y = 16.dp)
                    ) {
                                                androidx.compose.material3.Text("Edit", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun CornerHandles() {
    val handleMod = Modifier
        .size(12.dp)
        .clip(CircleShape)
        .background(SurfaceContainerLowest)
        .border(2.dp, Secondary, CircleShape)

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = handleMod.align(Alignment.TopStart))
        Box(modifier = handleMod.align(Alignment.TopEnd))
        Box(modifier = handleMod.align(Alignment.BottomStart))
        Box(modifier = handleMod.align(Alignment.BottomEnd))
    }
}

@Composable
private fun DashedMarginGuide() {
    val color = OutlineVariant.copy(alpha = 0.6f)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .drawBehind {
                val stroke = Stroke(
                    width = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                )
                drawRect(color = color, style = stroke)
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
                    .clickableNoPull { onSelect(preset) }
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
            .clickableNoPull(onClick = onClick),
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
    val paint = androidx.compose.ui.graphics.Paint().also {
        it.color = Color(0xFF43474E).copy(alpha = 0.08f)
    }
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

// Simple clickable alias
private fun Modifier.clickableNoPull(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

@Composable
private fun SpecGrid(
    pageSize: String,
    quality: String,
    scale: String,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.SpaceEvenly) {
        SpecCell(label = "PAGE SIZE", value = pageSize)
        VerticalDivider(color = OutlineVariant, modifier = Modifier.height(48.dp))
        SpecCell(label = "QUALITY", value = quality)
        VerticalDivider(color = OutlineVariant, modifier = Modifier.height(48.dp))
        SpecCell(label = "SCALE", value = scale)
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
