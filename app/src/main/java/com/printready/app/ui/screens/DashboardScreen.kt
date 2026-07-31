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
    onPresetSelected: (DocumentType) -> Unit,
    onRecentJobClick: (PrintJob) -> Unit
) {
    val dashState by viewModel.dashboardState.collectAsState()
    val context = LocalContext.current

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
            TopAppBar(
                title = {
                    Text(
                        "PrintReady",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Primary
                    )
                },
                actions = {
                    // Removed unused profile icon placeholder
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        },
        // bottomBar removed as it was non-functional and redundant
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewScan,
                containerColor = Primary,
                contentColor = OnPrimary,
                shape = CircleShape,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.Add, "New scan", modifier = Modifier.size(24.dp))
            }
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
                    title = "Scan or Add Document",
                    subtitle = "Use camera or select from gallery",
                    onClick = onNewScan
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
    Card(
        modifier = modifier
            .heightIn(min = 140.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = OnPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, color = OnSurface)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
        }
    }
}

@Composable
private fun PresetChip(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = SurfaceContainerLowest,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, OutlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Removed hardcoded Settings icon here
            Text(label, style = MaterialTheme.typography.labelMedium, color = OnSurface)
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
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceContainerLowest)
            .border(1.dp, OutlineVariant, RoundedCornerShape(8.dp))
            .clickable(onClick = onEdit)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // A4 thumbnail
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(64.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(SurfaceContainerLowest)
                .border(1.dp, OutlineVariant, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.TopStart
        ) {
            Text(
                "A4",
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariant,
                modifier = Modifier
                    .background(SurfaceContainer, RoundedCornerShape(2.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                job.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${job.pageCount} page · ${formatSize(job.fileSizeBytes)}",
                style = MaterialTheme.typography.labelSmall,
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
