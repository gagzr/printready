package com.printready.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.printready.app.domain.model.DefaultDocumentTypes
import com.printready.app.domain.model.DocumentType
import com.printready.app.ui.theme.*
import com.printready.app.viewmodel.PrintReadyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectDocumentTypeScreen(
    viewModel: PrintReadyViewModel,
    onBack: () -> Unit,
    onDocTypeSelected: (DocumentType) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var showCustomDialog by remember { mutableStateOf(false) }

    val filtered = remember(query) {
        DefaultDocumentTypes.filter {
            it.name.contains(query, ignoreCase = true) ||
            it.category.tag.contains(query, ignoreCase = true)
        }
    }

    if (showCustomDialog) {
        CustomSizeDialog(
            onDismiss = { showCustomDialog = false },
            onConfirm = { widthMm, heightMm ->
                showCustomDialog = false
                val customType = DocumentType(
                    id = "custom_" + System.currentTimeMillis(),
                    name = "Custom Size",
                    category = com.printready.app.domain.model.DocumentCategory.CUSTOM,
                    widthMm = widthMm,
                    heightMm = heightMm
                )
                onDocTypeSelected(customType)
            }
        )
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
                    Text("PrintReady", style = MaterialTheme.typography.headlineMedium, color = Primary)
                },
                actions = {
                    TextButton(onClick = { }) {
                        Text("Export", color = Primary, style = MaterialTheme.typography.headlineSmall)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        },
        containerColor = Surface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            Text("Select Document Type", style = MaterialTheme.typography.headlineLarge, color = OnSurface)
            Spacer(Modifier.height(4.dp))
            Text("Choose a preset or enter custom dimensions", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
            Spacer(Modifier.height(16.dp))

            // Search bar
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                placeholder = { Text("Search document types…", color = Outline) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Outline, modifier = Modifier.size(20.dp)) },
                shape = RoundedCornerShape(4.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = OutlineVariant,
                    focusedContainerColor = SurfaceContainerLow,
                    unfocusedContainerColor = SurfaceContainerLow
                )
            )

            Spacer(Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(filtered.dropLast(1)) { docType ->
                    DocTypeCard(docType = docType, onClick = { onDocTypeSelected(docType) }) // Default to scan here for now, or might need to pass sourceMode to this screen
                }
                // Custom size — full width
                item(span = { GridItemSpan(2) }) {
                    CustomSizeCard(onClick = { showCustomDialog = true })
                }
            }
        }
    }
}

@Composable
private fun DocTypeCard(docType: DocumentType, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(128.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            // Category badge top-left
            Text(
                docType.category.tag,
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .background(SurfaceContainer, RoundedCornerShape(2.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )

            Column(
                modifier = Modifier.align(Alignment.BottomStart),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Icon circle
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SurfaceContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        docType.category.tag.take(1),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Primary
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    docType.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    docType.dimensionLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CustomSizeDialog(onDismiss: () -> Unit, onConfirm: (Float, Float) -> Unit) {
    var widthText by remember { mutableStateOf("") }
    var heightText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom Document Size") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = widthText,
                    onValueChange = { widthText = it },
                    label = { Text("Width (mm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )
                OutlinedTextField(
                    value = heightText,
                    onValueChange = { heightText = it },
                    label = { Text("Height (mm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val w = widthText.toFloatOrNull() ?: 0f
                val h = heightText.toFloatOrNull() ?: 0f
                if (w > 0 && h > 0) {
                    onConfirm(w, h)
                }
            }) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CustomSizeCard(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.5.dp, PrimaryContainer.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SurfaceContainer),
                contentAlignment = Alignment.Center
            ) {
                Text("?", style = MaterialTheme.typography.headlineSmall, color = Primary)
            }
            Column {
                Text("Custom Size", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
                Text("Enter your own dimensions", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
            }
        }
    }
}
