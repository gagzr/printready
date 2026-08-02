package com.printready.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Image
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import android.widget.Toast
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.printready.app.domain.model.DocumentType
import com.printready.app.domain.model.PrintJob
import com.printready.app.ui.theme.*
import com.printready.app.viewmodel.PrintReadyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: PrintReadyViewModel,
    onNewScan: () -> Unit,
    onNewGallery: () -> Unit,
    onPresetSelected: (DocumentType) -> Unit,
    onRecentJobClick: (PrintJob) -> Unit
) {
    val dashState by viewModel.dashboardState.collectAsState()
    val context = LocalContext.current

    var showSourceSheet by remember { mutableStateOf(false) }
    var renamingJob by remember { mutableStateOf<PrintJob?>(null) }
    var renameText by remember { mutableStateOf("") }

    val handlePrintShare: (PrintJob) -> Unit = { job ->
        val filePath = job.pdfFilePath
        if (filePath != null && File(filePath).exists()) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(filePath))
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share/Print PDF"))
        } else {
            Toast.makeText(context, "PDF file not found. Open in workspace to regenerate.", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "PrintReady",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = Primary
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Surface.copy(alpha = 0.9f),
                    scrolledContainerColor = Surface
                ),
                modifier = Modifier.shadow(elevation = 2.dp, spotColor = Primary.copy(alpha = 0.1f))
            )
        },
        containerColor = Surface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(24.dp))

            // Hero
            Text(
                "Welcome back!",
                style = MaterialTheme.typography.headlineLarge,
                color = OnSurface
            )
            Text(
                "Print documents at exact physical dimensions.",
                style = MaterialTheme.typography.bodyLarge,
                color = OnSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))

            // Bento grid
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BentoCard(
                    modifier = Modifier.weight(1f),
                    iconBg = Primary,
                    iconLabel = "scan_doc",
                    title = "Scan Document",
                    subtitle = "Use camera with edge detection",
                    onClick = onNewScan
                )
                BentoCard(
                    modifier = Modifier.weight(1f),
                    iconBg = Secondary,
                    iconLabel = "gallery",
                    title = "Upload from Gallery",
                    subtitle = "Pick images from your phone",
                    onClick = onNewGallery
                )
            }

            Spacer(Modifier.height(24.dp))

            // Common Presets
            Text(
                "Common Presets",
                style = MaterialTheme.typography.headlineSmall,
                color = OnSurface
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                dashState.presets.forEach { preset ->
                    PresetChip(
                        label = preset.name,
                        onClick = { onPresetSelected(preset) }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Recent prints
            if (dashState.recentJobs.isNotEmpty()) {
                Text(
                    "Recent Prints",
                    style = MaterialTheme.typography.headlineSmall,
                    color = OnSurface
                )
                Spacer(Modifier.height(8.dp))
                dashState.recentJobs.forEach { job ->
                    RecentPrintItem(
                        job = job,
                        onEdit = { onRecentJobClick(job) },
                        onPrintShare = { handlePrintShare(job) },
                        onRename = {
                            renamingJob = job
                            renameText = job.title
                        },
                        onDelete = { viewModel.deleteRecentJob(job.id) }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            renamingJob?.let { job ->
                AlertDialog(
                    onDismissRequest = { renamingJob = null },
                    title = { Text("Rename Print Job", style = MaterialTheme.typography.headlineSmall) },
                    text = {
                        OutlinedTextField(
                            value = renameText,
                            onValueChange = { renameText = it },
                            label = { Text("Title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (renameText.isNotBlank()) {
                                    viewModel.renameRecentJob(job.id, renameText.trim())
                                }
                                renamingJob = null
                            }
                        ) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { renamingJob = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun BentoCard(
    modifier: Modifier = Modifier,
    iconBg: Color,
    iconLabel: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val icon: ImageVector = when (iconLabel) {
        "gallery" -> Icons.Default.Image
        "scan_doc" -> Icons.Default.Camera
        else -> Icons.Default.Add
    }
    Card(
        modifier = modifier
            .heightIn(min = 140.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(iconBg.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconBg,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = OnSurface)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
        }
    }
}

@Composable
private fun PresetChip(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp), // More modern
        color = SurfaceContainerLowest,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, OutlineVariant.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = OnSurface)
        }
    }
}

@Composable
private fun RecentPrintItem(
    job: PrintJob,
    onEdit: () -> Unit,
    onPrintShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceContainerLowest)
            .border(1.dp, OutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .clickable(onClick = onEdit)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // A4 thumbnail
        Box(
            modifier = Modifier
                .width(54.dp)
                .height(72.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceContainer)
                .border(1.dp, OutlineVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.TopStart
        ) {
            Text(
                "A4",
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariant,
                modifier = Modifier
                    .background(SurfaceContainerHigh, RoundedCornerShape(topStart = 8.dp, bottomEnd = 8.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                job.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${job.pageCount} page · ${formatSize(job.fileSizeBytes)} · ${formatDate(job.createdAt)}",
                style = MaterialTheme.typography.labelMedium,
                color = OnSurfaceVariant
            )
        }

        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = OnSurfaceVariant)
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                    onClick = {
                        menuExpanded = false
                        onEdit()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Print / Share") },
                    leadingIcon = { Icon(Icons.Default.Share, null) },
                    onClick = {
                        menuExpanded = false
                        onPrintShare()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Default.Create, null) },
                    onClick = {
                        menuExpanded = false
                        onRename()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = Error) },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = Error) },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    }
                )
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes == 0L) return "--"
    return if (bytes < 1024 * 1024) "${bytes / 1024} KB" else "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

private fun formatDate(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}
