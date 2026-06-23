package com.aionyxe.filebridge.ui.logs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aionyxe.filebridge.R
import com.aionyxe.filebridge.data.logs.LogEntry
import com.aionyxe.filebridge.data.logs.LogType
import com.aionyxe.filebridge.ui.components.EmptyState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    viewModel: LogsViewModel = hiltViewModel(),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.logs_title)) },
                actions = {
                    IconButton(
                        onClick = { showClearDialog = true },
                        enabled = entries.isNotEmpty(),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.logs_clear),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                EmptyState(
                    icon = Icons.AutoMirrored.Outlined.List,
                    title = stringResource(R.string.logs_empty_title),
                    body = stringResource(R.string.logs_empty_body),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                // Reverse-chronological: newest at the top.
                val reversed = entries.asReversed()
                itemsIndexed(reversed, key = { i, _ -> reversed.size - i }) { index, entry ->
                    LogRow(entry = entry)
                    if (index < reversed.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 48.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.logs_clear_dialog_title)) },
            text = { Text(stringResource(R.string.logs_clear_dialog_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onClearConfirmed()
                    showClearDialog = false
                }) {
                    Text(stringResource(R.string.logs_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

@Composable
private fun LogRow(entry: LogEntry, modifier: Modifier = Modifier) {
    val accent = entry.type.accentColor()
    val isError = entry.type == LogType.TRANSFER_FAILED || entry.type == LogType.AUTH_FAILURE
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        // Semantic colored dot — the at-a-glance category indicator.
        Surface(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(8.dp),
            shape = CircleShape,
            color = accent,
            content = {},
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = timeFormat.format(Date(entry.timestamp)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 1.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LogType.accentColor(): Color = when (this) {
    LogType.SERVER_STARTED, LogType.CLIENT_CONNECTED -> MaterialTheme.colorScheme.tertiary
    LogType.SERVER_STOPPED, LogType.CLIENT_DISCONNECTED -> MaterialTheme.colorScheme.outline
    LogType.FILE_UPLOADED -> MaterialTheme.colorScheme.primary
    LogType.FILE_DOWNLOADED -> MaterialTheme.colorScheme.secondary
    LogType.TRANSFER_FAILED, LogType.AUTH_FAILURE -> MaterialTheme.colorScheme.error
}
