package com.aionyxe.filebridge.ui.home

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aionyxe.filebridge.R
import com.aionyxe.filebridge.domain.model.ServerState
import com.aionyxe.filebridge.ui.components.QrCodeImage
import com.aionyxe.filebridge.ui.components.StatusCard
import com.aionyxe.filebridge.ui.components.WarningBanner
import com.aionyxe.filebridge.util.formatBytes
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    // Show transient error in Snackbar then clear it.
    LaunchedEffect(uiState.transientError) {
        val msg = uiState.transientError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.onErrorShown()
    }

    // Runtime storage permission state (re-evaluated on composition).
    val storageGranted = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    var showQrDialog by remember { mutableStateOf(false) }
    var showStopDialog by remember { mutableStateOf(false) }

    if (showQrDialog) {
        val url = uiState.connectionInfo?.url ?: ""
        QrFullscreenDialog(url = url, onDismiss = { showQrDialog = false })
    }

    if (showStopDialog) {
        StopConfirmDialog(
            clientCount = uiState.connectedClients,
            onConfirm = {
                viewModel.onStopClicked()
                showStopDialog = false
            },
            onDismiss = { showStopDialog = false },
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Crossfade(
            targetState = uiState.serverState is ServerState.Running,
            label = "server-state-crossfade",
        ) { isRunning ->
            if (isRunning) {
                ServerOnContent(
                    uiState = uiState,
                    onStopClicked = {
                        if (uiState.connectedClients > 0) showStopDialog = true
                        else viewModel.onStopClicked()
                    },
                    onCopyAddress = {
                        uiState.connectionInfo?.url?.let { url ->
                            clipboard.setText(AnnotatedString(url))
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.home_address_copied),
                                )
                            }
                        }
                    },
                    onTogglePassword = viewModel::onTogglePasswordVisibility,
                    onQrTapped = { showQrDialog = true },
                )
            } else {
                ServerOffContent(
                    uiState = uiState,
                    storageGranted = storageGranted,
                    onStartClicked = viewModel::onStartClicked,
                    onGrantPermission = {
                        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Intent(
                                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            )
                        } else {
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.parse("package:${context.packageName}"))
                        }
                        context.startActivity(intent)
                    },
                )
            }
        }

        // Translucent overlay while transitioning.
        val isTransitioning = uiState.serverState is ServerState.Starting ||
            uiState.serverState is ServerState.Stopping
        AnimatedVisibility(visible = isTransitioning, modifier = Modifier.align(Alignment.Center)) {
            Surface(
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

// ---- Server OFF state ----

@Composable
private fun ServerOffContent(
    uiState: HomeUiState,
    storageGranted: Boolean,
    onStartClicked: () -> Unit,
    onGrantPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        StatusCard(
            title = stringResource(R.string.home_status_stopped_title),
            subtitle = stringResource(R.string.home_status_stopped_subtitle),
            icon = Icons.Outlined.WifiOff,
            accentColor = MaterialTheme.colorScheme.secondary,
        )

        if (uiState.isAnonymous) {
            WarningBanner(message = stringResource(R.string.home_anonymous_warning))
        }

        if (!storageGranted) {
            WarningBanner(
                message = stringResource(R.string.home_storage_permission_rationale),
                actionLabel = stringResource(R.string.home_grant_permission),
                onAction = onGrantPermission,
            )
        }

        Spacer(Modifier.weight(1f))

        ExtendedFloatingActionButton(
            onClick = onStartClicked,
            icon = { Icon(Icons.Outlined.Wifi, contentDescription = null) },
            text = { Text(stringResource(R.string.home_start_server)) },
            modifier = Modifier.align(Alignment.CenterHorizontally),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        )

        Spacer(Modifier.height(8.dp))
    }
}

// ---- Server ON state ----

@Composable
private fun ServerOnContent(
    uiState: HomeUiState,
    onStopClicked: () -> Unit,
    onCopyAddress: () -> Unit,
    onTogglePassword: () -> Unit,
    onQrTapped: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        PulsingStatusCard(
            subtitle = stringResource(
                R.string.home_status_running_subtitle,
                uiState.connectedClients,
            ),
        )

        StatsCard(uiState = uiState)

        if (uiState.isAnonymous) {
            WarningBanner(message = stringResource(R.string.home_anonymous_warning))
        }

        // Connection details card.
        val info = uiState.connectionInfo
        if (info != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // URL row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = info.url,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(onClick = onCopyAddress) {
                            Icon(
                                Icons.Outlined.ContentCopy,
                                contentDescription = stringResource(R.string.home_copy_address),
                            )
                        }
                    }

                    // Username row (single-user mode only).
                    if (!uiState.isAnonymous && info.username != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = info.username,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // Password row.
                    if (!uiState.isAnonymous && info.password != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    Icons.Outlined.LockOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = if (uiState.isPasswordRevealed) {
                                        info.password
                                    } else {
                                        "•".repeat(info.password.length.coerceIn(6, 20))
                                    },
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = if (uiState.isPasswordRevealed) {
                                            FontFamily.Monospace
                                        } else {
                                            FontFamily.Default
                                        },
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = onTogglePassword) {
                                Icon(
                                    imageVector = if (uiState.isPasswordRevealed) {
                                        Icons.Outlined.VisibilityOff
                                    } else {
                                        Icons.Outlined.Visibility
                                    },
                                    contentDescription = stringResource(
                                        if (uiState.isPasswordRevealed) {
                                            R.string.home_hide_password
                                        } else {
                                            R.string.home_show_password
                                        },
                                    ),
                                )
                            }
                        }
                    }

                    // QR code.
                    Card(
                        onClick = onQrTapped,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        QrCodeImage(
                            content = info.url,
                            size = 180.dp,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Stop button — destructive tone.
        Button(
            onClick = onStopClicked,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
        ) {
            Text(stringResource(R.string.home_stop_server))
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ---- Live stats dashboard ----

@Composable
private fun StatsCard(uiState: HomeUiState, modifier: Modifier = Modifier) {
    val stats = uiState.stats
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatItem(
                icon = Icons.Outlined.Group,
                value = uiState.connectedClients.toString(),
                label = stringResource(R.string.home_stat_clients),
            )
            StatItem(
                icon = Icons.Outlined.SwapVert,
                value = stats.filesTransferred.toString(),
                label = stringResource(R.string.home_stat_files),
            )
            StatItem(
                icon = Icons.Outlined.DataUsage,
                value = formatBytes(stats.bytesTransferred),
                label = stringResource(R.string.home_stat_data),
            )
            StatItem(
                icon = Icons.Outlined.ErrorOutline,
                value = stats.failedTransfers.toString(),
                label = stringResource(R.string.home_stat_failed),
                highlight = stats.failedTransfers > 0,
            )
        }
    }
}

@Composable
private fun RowScope.StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    highlight: Boolean = false,
) {
    val accent = if (highlight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = if (highlight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

// ---- Pulsing status card ----

@Composable
private fun PulsingStatusCard(subtitle: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    StatusCard(
        title = stringResource(R.string.home_status_running_title),
        subtitle = subtitle,
        icon = Icons.Outlined.Wifi,
        accentColor = MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha),
    )
}

// ---- Dialogs ----

@Composable
private fun QrFullscreenDialog(url: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                QrCodeImage(content = url, size = 280.dp)
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    }
}

@Composable
private fun StopConfirmDialog(
    clientCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_stop_dialog_title)) },
        text = {
            Text(
                stringResource(R.string.home_stop_dialog_message, clientCount),
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(stringResource(R.string.home_stop_server))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
