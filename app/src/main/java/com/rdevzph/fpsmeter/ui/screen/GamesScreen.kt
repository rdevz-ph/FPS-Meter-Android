package com.rdevzph.fpsmeter.ui.screen

import androidx.compose.ui.res.stringResource
import com.rdevzph.fpsmeter.R

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rdevzph.fpsmeter.viewmodel.FpsViewModel
import com.rdevzph.fpsmeter.viewmodel.OverlaySettings

/**
 * Dedicated Material 3 full-page screen for managing Auto-Start apps,
 * background detection permissions, and configuring per-game FPS recording.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesScreen(
    settings: OverlaySettings,
    installedApps: List<FpsViewModel.AppInfo>,
    isLoading: Boolean = false,
    accessibilityEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onToggleAutoStart: (Boolean) -> Unit,
    onToggleAutoRecordAll: (Boolean) -> Unit,
    onTogglePackage: (String) -> Unit,
    onSetRecordingPackage: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf(0) } // 0: All, 1: User Apps, 2: Auto-Start, 3: Recording
    var appToConfirmRecording by remember { mutableStateOf<FpsViewModel.AppInfo?>(null) }
    var showAutoRecordAllDialog by remember { mutableStateOf(false) }

    val filteredApps = remember(searchQuery, installedApps, filterMode, settings.autoStartPackages, settings.recordingPackages, settings.autoRecordAll) {
        installedApps.filter { app ->
            val matchesSearch = searchQuery.isBlank() ||
                    app.appName.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (filterMode) {
                1 -> !app.isSystemApp // User apps only
                2 -> settings.autoStartPackages.contains(app.packageName) // Auto-Start target apps
                3 -> settings.autoRecordAll || settings.recordingPackages.contains(app.packageName) // Recording enabled
                else -> true
            }
            matchesSearch && matchesFilter
        }
    }

    // Confirmation Warning Dialog for FPS Recording
    if (appToConfirmRecording != null) {
        val targetApp = appToConfirmRecording!!
        AlertDialog(
            onDismissRequest = { appToConfirmRecording = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.confirm_enable_recording_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.confirm_enable_recording_msg, targetApp.appName),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.notice_monitor),
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Text(
                        text = stringResource(R.string.rec_aggregate_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSetRecordingPackage(targetApp.packageName, true)
                        appToConfirmRecording = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.common_confirm_enable))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { appToConfirmRecording = null },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // Confirmation Warning Dialog for Auto-Recording All Apps/Games
    if (showAutoRecordAllDialog) {
        AlertDialog(
            onDismissRequest = { showAutoRecordAllDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.confirm_auto_record_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.confirm_auto_record_msg),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.notice_auto_record),
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Text(
                        text = stringResource(R.string.per_game_hidden),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onToggleAutoRecordAll(true)
                        showAutoRecordAllDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.common_confirm_enable))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showAutoRecordAllDialog = false },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Auto-Start Service Control Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(
                1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Accessibility Service status warning if disabled
                if (!accessibilityEnabled) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.accessibility_required),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    stringResource(R.string.accessibility_required_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = onOpenAccessibilitySettings,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(stringResource(R.string.common_enable), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // Auto-Start master toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.auto_start_target),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            stringResource(R.string.auto_start_target_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = settings.autoStartEnabled,
                        onCheckedChange = onToggleAutoStart,
                        enabled = accessibilityEnabled
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Auto-Record All master toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.auto_record_all),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            stringResource(R.string.auto_record_all_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = settings.autoRecordAll,
                        onCheckedChange = { checked ->
                            if (checked) {
                                showAutoRecordAllDialog = true
                            } else {
                                onToggleAutoRecordAll(false)
                            }
                        }
                    )
                }
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(stringResource(R.string.search_apps)) },
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = null)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )

        // Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = filterMode == 0,
                onClick = { filterMode = 0 },
                label = { Text(stringResource(R.string.filter_all), style = MaterialTheme.typography.labelSmall) }
            )
            FilterChip(
                selected = filterMode == 1,
                onClick = { filterMode = 1 },
                label = { Text(stringResource(R.string.filter_user_apps), style = MaterialTheme.typography.labelSmall) }
            )
            FilterChip(
                selected = filterMode == 2,
                onClick = { filterMode = 2 },
                label = { Text(stringResource(R.string.filter_auto, settings.autoStartPackages.size), style = MaterialTheme.typography.labelSmall) }
            )
            FilterChip(
                selected = filterMode == 3,
                onClick = { filterMode = 3 },
                label = {
                    Text(
                        if (settings.autoRecordAll) stringResource(R.string.filter_rec_all) else stringResource(R.string.filter_rec, settings.recordingPackages.size),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )
        }

        // Target Apps List Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.target_apps, filteredApps.size),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = if (settings.autoRecordAll) stringResource(R.string.target_apps_hint_auto) else stringResource(R.string.target_apps_hint_rec),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // List of Apps
        if (isLoading) {
            val shimmerBrush = rememberShimmerBrush()
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(7) {
                    AppCardSkeleton(shimmerBrush)
                }
            }
        } else if (filteredApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.no_apps_match),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    val isAutoStart = settings.autoStartPackages.contains(app.packageName)
                    val isRecording = settings.recordingPackages.contains(app.packageName)

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isAutoStart || (!settings.autoRecordAll && isRecording)) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTogglePackage(app.packageName) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isAutoStart,
                                onCheckedChange = { onTogglePackage(app.packageName) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = app.appName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isAutoStart) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (!app.isSystemApp) {
                                        Spacer(Modifier.width(6.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                stringResource(R.string.badge_user),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(Modifier.width(4.dp))

                            // Launch game button
                            IconButton(
                                onClick = {
                                    val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                    if (launchIntent != null) {
                                        context.startActivity(launchIntent)
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.no_launcher_activity, app.appName), Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = stringResource(R.string.content_desc_launch, app.appName),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Per-game recording toggle button (hidden when Auto-Record All is enabled)
                            if (!settings.autoRecordAll) {
                                FilledTonalIconButton(
                                    onClick = {
                                        if (isRecording) {
                                            onSetRecordingPackage(app.packageName, false)
                                        } else {
                                            appToConfirmRecording = app
                                        }
                                    },
                                    modifier = Modifier.size(38.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = if (isRecording) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        contentColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Icon(
                                        imageVector = if (isRecording) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = if (isRecording) stringResource(R.string.content_desc_rec_enabled, app.appName) else stringResource(R.string.content_desc_rec_disabled, app.appName),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmerTransition")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    val isDark = isSystemInDarkTheme()
    val shimmerColors = if (isDark) {
        listOf(
            Color(0xFF24272B),
            Color(0xFF383C42),
            Color(0xFF24272B)
        )
    } else {
        listOf(
            Color(0xFFE2E5E9),
            Color(0xFFF2F4F7),
            Color(0xFFE2E5E9)
        )
    }

    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - 250f, translateAnim - 250f),
        end = Offset(translateAnim + 250f, translateAnim + 250f)
    )
}

@Composable
fun AppCardSkeleton(brush: Brush) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox placeholder
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(brush)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                // App title line placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(15.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
                Spacer(Modifier.height(8.dp))
                // Package name line placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(11.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
            }
            Spacer(Modifier.width(12.dp))
            // Launch/Record button placeholder
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(brush)
            )
        }
    }
}

