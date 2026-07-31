package com.printready.app.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.printready.app.ui.theme.*
import com.printready.app.viewmodel.PrintReadyViewModel

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintPreviewScreen(
    viewModel: PrintReadyViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.workspaceState.collectAsState()
    val context = LocalContext.current

    // Trigger PDF generation on first load
    LaunchedEffect(Unit) {
        if (state.pdfFile == null && !state.pdfGenerating) {
            viewModel.generatePdf()
        }
    }

    // Show toast when PDF ready
    LaunchedEffect(state.pdfFile) {
        state.pdfFile?.let {
            Toast.makeText(context, "PDF saved: ${it.name}", Toast.LENGTH_SHORT).show()
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
                    Text("Print Preview", style = MaterialTheme.typography.headlineMedium, color = Primary)
                },
                actions = {
                    TextButton(onClick = {
                        state.pdfFile?.let { file ->
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share PDF"))
                        }
                    }) {
                        Text("Export", color = Primary, style = MaterialTheme.typography.headlineSmall)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        },
        containerColor = Color(0xFFF7FAFC)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))

            // A4 paper preview
            Box(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth(0.9f)
                    .aspectRatio(1f / 1.414f)
                    .shadow(12.dp, RoundedCornerShape(2.dp))
                    .clip(RoundedCornerShape(2.dp))
                    .background(SurfaceContainerLowest),
                contentAlignment = Alignment.TopStart
            ) {
                // A4 badge
                Text(
                    "A4",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant,
                    modifier = Modifier
                        .padding(6.dp)
                        .background(SurfaceContainer, RoundedCornerShape(2.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )

                // Dashed margin guide
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Content area placeholder
                    if (state.pdfGenerating) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Primary, strokeWidth = 2.dp)
                        }
                    } else {
                        state.pdfFile?.let { file ->
                            PdfThumbnail(
                                file = file,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Spec grid
            val docType = state.selectedDocType
            if (docType != null) {
                SpecGrid(
                    pageSize = "A4 (210 × 297 mm)",
                    quality = "High (300 DPI)",
                    scale = "100% Actual Size",
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceContainerLowest)
                        .padding(16.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            // Warning banner
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
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

            // Action buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.9f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = OnPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Print", style = MaterialTheme.typography.headlineSmall, color = OnPrimary)
                    }
                }

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

            Spacer(Modifier.height(32.dp))
        }
    }
}

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

@Composable
fun PdfThumbnail(file: File, modifier: Modifier = Modifier) {
    var bitmap by remember(file) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            try {
                val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fileDescriptor)

                if (renderer.pageCount > 0) {
                    val page = renderer.openPage(0)
                    // Render for display (e.g. 800 width maintaining A4 ratio)
                    val bmp = Bitmap.createBitmap(800, (800 * 1.414).toInt(), Bitmap.Config.ARGB_8888)

                    // PDFs often have transparent backgrounds, fill with white first
                    val canvas = Canvas(bmp)
                    canvas.drawColor(android.graphics.Color.WHITE)

                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bitmap = bmp
                }
                renderer.close()
                fileDescriptor.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    bitmap?.let { bmp ->
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "PDF Preview",
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    }
}
