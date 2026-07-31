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

private val WORKSPACE_TOOLS_SINGLE = listOf("Auto-Fit", "Add Page", "Scan Document", "Margins", "Align Top", "Center")
private val WORKSPACE_TOOLS_MULTI = listOf("Auto-Fit", "Add Page", "Scan Document", "Margins", "Align Top", "Center", "Duplicate", "Auto-Arrange")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun A4WorkspaceScreen(
    viewModel: PrintReadyViewModel,
    multiCard: Boolean,
    sourceMode: String,
    onBack: () -> Unit,
    onExport: () -> Unit
) {
    val state by viewModel.workspaceState.collectAsState()
    val tools = if (multiCard) WORKSPACE_TOOLS_MULTI else WORKSPACE_TOOLS_SINGLE
    val context = LocalContext.current

    var pendingActionItemId by remember { mutableStateOf<String?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            if (pendingActionItemId != null) {
                viewModel.updateItemUri(pendingActionItemId!!, it)
                pendingActionItemId = null
            } else {
                viewModel.addImageToCanvas(it)
            }
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
            scanResult?.pages?.forEach { page ->
                viewModel.addImageToCanvas(page.imageUri)
            }
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

    // Auto-launch the appropriate picker if coming from dashboard fresh
    LaunchedEffect(Unit) {
        if (state.items.isEmpty()) {
            if (sourceMode == "upload") {
                imagePickerLauncher.launch("image/*")
            } else if (sourceMode == "scan") {
                startMlKitScan()
            }
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
                    TextButton(onClick = onExport) {
                        Text("Export", color = Primary, style = MaterialTheme.typography.headlineSmall)
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
                    onItemMoved = { id, dx, dy -> viewModel.updateItemPosition(id, dx, dy) },
                    onItemSelected = { id -> viewModel.selectItem(id) },
                    onAddImage = { startMlKitScan() },
                    onCropClicked = { id, uri ->
                        val destUri = Uri.fromFile(File(context.cacheDir, "crop_${System.currentTimeMillis()}.jpg"))
                        val uCropIntent = UCrop.of(uri, destUri)
                            .withOptions(UCrop.Options().apply {
                                setFreeStyleCropEnabled(true)
                                setHideBottomControls(false)
                                setToolbarTitle("Crop Image")
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
                                label = tool,
                                isActive = state.activeToolIndex == i,
                                onClick = {
                                    viewModel.setActiveTool(i)
                                    when (i) {
                                        0 -> viewModel.autoFitItems()
                                        1 -> imagePickerLauncher.launch("image/*")
                                        2 -> startMlKitScan()
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
}

@Composable
private fun A4CanvasSheet(
    items: List<CanvasItem>,
    selectedItemId: String?,
    multiCard: Boolean,
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

        if (items.isEmpty()) {
            // Empty state — tap to add
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .clickableNoPull(onClick = onAddImage)
            ) {
                Icon(Icons.Default.Add, null, tint = OnSurfaceVariant, modifier = Modifier.size(32.dp))
                Text("Tap to add image", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
            }
        }

        val density = LocalDensity.current
        items.forEach { item ->
            val scaleW = canvasSize.width / A4.WIDTH_MM
            val scaleH = canvasSize.height / A4.HEIGHT_MM
            val xPx = (item.offsetXMm * scaleW).roundToInt()
            val yPx = (item.offsetYMm * scaleH).roundToInt()
            val rawW = (item.documentType.widthMm * item.scaleFactor * scaleW).roundToInt()

            val itemWidthDp = with(density) { rawW.toDp() }

                        DraggableCardItem(
                item = item,
                isSelected = item.id == selectedItemId,
                modifier = Modifier
                    .offset { IntOffset(xPx, yPx) }
                    .width(itemWidthDp)
                    .aspectRatio(item.documentType.widthMm / item.documentType.heightMm.coerceAtLeast(1f)),
                onMoved = { dx, dy ->
                    val dxMm = (dx / canvasSize.width) * A4.WIDTH_MM
                    val dyMm = (dy / canvasSize.height) * A4.HEIGHT_MM
                    val newX = item.offsetXMm + dxMm
                    val newY = item.offsetYMm + dyMm
                    onItemMoved(item.id, newX, newY)
                },
                onSelect = { onItemSelected(item.id) },
                onCropClicked = { uri -> onCropClicked(item.id, uri) }
            )
        }
    }
}

@Composable
private fun DraggableCardItem(
    item: CanvasItem,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onMoved: (Float, Float) -> Unit,
    onSelect: () -> Unit,
    onCropClicked: (Uri) -> Unit
) {
    Box(
        modifier = modifier
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
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onSelect() },
                    onDragEnd = { },
                    onDrag = { _, dragAmount ->
                        onMoved(dragAmount.x, dragAmount.y)
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
            Box(Modifier.fillMaxSize().background(SurfaceVariant))
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
private fun ToolButton(label: String, isActive: Boolean, onClick: () -> Unit) {
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
                Icons.Default.Add,
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
