package com.aionyxe.filebridge.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aionyxe.filebridge.BuildConfig
import com.aionyxe.filebridge.R
import com.aionyxe.filebridge.domain.model.AccessMode
import com.aionyxe.filebridge.domain.model.AuthMode
import com.aionyxe.filebridge.domain.model.CertificateInfo
import com.aionyxe.filebridge.domain.model.Protocol
import com.aionyxe.filebridge.domain.model.ThemeMode
import com.aionyxe.filebridge.ui.components.LabeledSwitch
import com.aionyxe.filebridge.ui.components.PortInputField
import com.aionyxe.filebridge.ui.components.SectionHeader
import com.aionyxe.filebridge.ui.components.WarningBanner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Directory picker launcher
    val dirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        uri?.let { chosen ->
            // Persist the grant so the app can access this tree on next cold start.
            context.contentResolver.takePersistableUriPermission(
                chosen,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            viewModel.onRootDirSelected(chosen.toString())
        }
    }

    var showCertDialog by remember { mutableStateOf(false) }
    var showRegenCertDialog by remember { mutableStateOf(false) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // ---- Server running warning ----
        if (uiState.serverRunning) {
            item {
                WarningBanner(
                    message = stringResource(R.string.settings_server_running_warning),
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        // ══════════════════════════ SERVER ══════════════════════════
        item { SectionHeader(title = stringResource(R.string.settings_section_server)) }

        // Protocol
        item {
            SettingsItemRow(label = stringResource(R.string.settings_protocol)) {
                SingleChoiceSegmentedButtonRow {
                    Protocol.entries.forEachIndexed { index, proto ->
                        SegmentedButton(
                            selected = uiState.settings.protocol == proto,
                            onClick = { viewModel.onProtocolChanged(proto) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = Protocol.entries.size,
                            ),
                            enabled = !uiState.serverRunning,
                            label = { Text(proto.name) },
                        )
                    }
                }
            }
        }

        // FTP port
        item {
            val portError = uiState.validationErrors[SettingsField.FTP_PORT]
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                PortInputField(
                    value = uiState.ftpPortStr,
                    onValueChange = viewModel::onFtpPortChanged,
                    label = stringResource(R.string.settings_ftp_port),
                    isError = portError != null,
                    errorText = portError?.let { stringResource(it) },
                    enabled = !uiState.serverRunning,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Passive port range
        item {
            val minError = uiState.validationErrors[SettingsField.PASV_MIN]
            val maxError = uiState.validationErrors[SettingsField.PASV_MAX]
            val errorText = (minError ?: maxError)?.let { stringResource(it) }
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PortInputField(
                    value = uiState.pasvMinStr,
                    onValueChange = viewModel::onPasvMinChanged,
                    label = stringResource(R.string.settings_pasv_min),
                    isError = minError != null,
                    errorText = errorText,
                    enabled = !uiState.serverRunning,
                    modifier = Modifier.weight(1f),
                )
                PortInputField(
                    value = uiState.pasvMaxStr,
                    onValueChange = viewModel::onPasvMaxChanged,
                    label = stringResource(R.string.settings_pasv_max),
                    isError = maxError != null,
                    enabled = !uiState.serverRunning,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item { SectionDivider() }

        // ══════════════════════════ AUTHENTICATION ══════════════════════════
        item { SectionHeader(title = stringResource(R.string.settings_section_auth)) }

        // Auth mode
        item {
            SettingsItemRow(label = stringResource(R.string.settings_auth_mode)) {
                SingleChoiceSegmentedButtonRow {
                    AuthMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = uiState.settings.authMode == mode,
                            onClick = { viewModel.onAuthModeChanged(mode) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = AuthMode.entries.size,
                            ),
                            enabled = !uiState.serverRunning,
                            label = {
                                Text(
                                    when (mode) {
                                        AuthMode.ANONYMOUS -> stringResource(R.string.auth_anonymous)
                                        AuthMode.SINGLE_USER -> stringResource(R.string.auth_single_user)
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }

        // Username + Password — only shown in SINGLE_USER mode
        if (uiState.settings.authMode == AuthMode.SINGLE_USER) {
            item {
                val usernameError = uiState.validationErrors[SettingsField.USERNAME]
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    OutlinedTextField(
                        value = uiState.settings.username,
                        onValueChange = viewModel::onUsernameChanged,
                        label = { Text(stringResource(R.string.settings_username)) },
                        isError = usernameError != null,
                        supportingText = usernameError?.let {
                            { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                        },
                        singleLine = true,
                        enabled = !uiState.serverRunning,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                val passwordError = uiState.validationErrors[SettingsField.PASSWORD]
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    OutlinedTextField(
                        value = uiState.passwordPlaintext,
                        onValueChange = viewModel::onPasswordChanged,
                        label = { Text(stringResource(R.string.settings_password)) },
                        isError = passwordError != null,
                        supportingText = passwordError?.let {
                            { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                        },
                        visualTransformation = if (uiState.isPasswordRevealed) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = viewModel::onTogglePasswordVisibility) {
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
                        },
                        singleLine = true,
                        enabled = !uiState.serverRunning,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        item { SectionDivider() }

        // ══════════════════════════ STORAGE ══════════════════════════
        item { SectionHeader(title = stringResource(R.string.settings_section_storage)) }

        // Root directory picker
        item {
            val rootDisplay = uiState.settings.rootDirUri
                .takeIf { it.isNotEmpty() }
                ?.let { Uri.parse(it).lastPathSegment ?: it }
                ?: stringResource(R.string.settings_root_not_set)

            ClickableSettingsRow(
                title = stringResource(R.string.settings_root_dir),
                subtitle = rootDisplay,
                onClick = { dirPickerLauncher.launch(null) },
            )
        }

        // Access mode
        item {
            LabeledSwitch(
                title = stringResource(R.string.settings_read_only),
                subtitle = stringResource(R.string.settings_read_only_subtitle),
                checked = uiState.settings.accessMode == AccessMode.READ_ONLY,
                onCheckedChange = { readOnly ->
                    viewModel.onAccessModeChanged(
                        if (readOnly) AccessMode.READ_ONLY else AccessMode.READ_WRITE,
                    )
                },
                enabled = !uiState.serverRunning,
            )
        }

        item { SectionDivider() }

        // ══════════════════════════ BEHAVIOR ══════════════════════════
        item { SectionHeader(title = stringResource(R.string.settings_section_behavior)) }

        item {
            LabeledSwitch(
                title = stringResource(R.string.settings_start_on_launch),
                checked = uiState.settings.startOnAppLaunch,
                onCheckedChange = viewModel::onStartOnAppLaunchChanged,
            )
        }
        item {
            LabeledSwitch(
                title = stringResource(R.string.settings_start_on_boot),
                subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    stringResource(R.string.settings_start_on_boot_hint)
                } else {
                    null
                },
                checked = uiState.settings.startOnBoot,
                onCheckedChange = viewModel::onStartOnBootChanged,
            )
        }
        item {
            LabeledSwitch(
                title = stringResource(R.string.settings_keep_screen_on),
                checked = uiState.settings.keepScreenOn,
                onCheckedChange = viewModel::onKeepScreenOnChanged,
            )
        }
        item {
            InfoRow(message = stringResource(R.string.settings_notification_info))
        }

        item { SectionDivider() }

        // ══════════════════════════ SECURITY ══════════════════════════
        item { SectionHeader(title = stringResource(R.string.settings_section_security)) }

        item {
            if (uiState.isRegeneratingCert) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.settings_cert_generating),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            } else {
                val certInfo = uiState.certInfo
                ClickableSettingsRow(
                    title = stringResource(R.string.settings_cert_title),
                    subtitle = certInfo?.shortFingerprint()
                        ?: stringResource(R.string.settings_cert_not_generated),
                    onClick = { if (certInfo != null) showCertDialog = true },
                )
            }
        }

        item {
            ClickableSettingsRow(
                title = stringResource(R.string.settings_cert_regenerate),
                subtitle = null,
                onClick = {
                    if (uiState.serverRunning && uiState.settings.protocol == Protocol.FTPS) {
                        // Refuse — server is actively using FTPS.
                        // The WarningBanner at the top already explains this.
                    } else {
                        showRegenCertDialog = true
                    }
                },
            )
        }

        item { SectionDivider() }

        // ══════════════════════════ APPEARANCE ══════════════════════════
        item { SectionHeader(title = stringResource(R.string.settings_section_appearance)) }

        item {
            SettingsItemRow(label = stringResource(R.string.settings_theme)) {
                SingleChoiceSegmentedButtonRow {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = uiState.settings.themeMode == mode,
                            onClick = { viewModel.onThemeModeChanged(mode) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = ThemeMode.entries.size,
                            ),
                            label = {
                                Text(
                                    when (mode) {
                                        ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                                        ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                                        ThemeMode.DARK -> stringResource(R.string.theme_dark)
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }

        item { SectionDivider() }

        // ══════════════════════════ ABOUT ══════════════════════════
        item { SectionHeader(title = stringResource(R.string.settings_section_about)) }

        item {
            InfoRow(message = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME))
        }

        item {
            val ctx = LocalContext.current
            ClickableSettingsRow(
                title = stringResource(R.string.settings_github),
                subtitle = null,
                onClick = {
                    ctx.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.GITHUB_URL)),
                    )
                },
            )
        }

        item {
            val ctx = LocalContext.current
            ClickableSettingsRow(
                title = stringResource(R.string.settings_translate),
                subtitle = null,
                onClick = {
                    ctx.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.TRANSLATION_URL)),
                    )
                },
            )
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }

    // ---- Certificate detail dialog ----
    uiState.certInfo?.let { cert ->
        if (showCertDialog) {
            CertificateDetailDialog(
                cert = cert,
                onDismiss = { showCertDialog = false },
            )
        }
    }

    // ---- Regenerate cert confirmation dialog ----
    if (showRegenCertDialog) {
        AlertDialog(
            onDismissRequest = { showRegenCertDialog = false },
            title = { Text(stringResource(R.string.settings_cert_regen_dialog_title)) },
            text = { Text(stringResource(R.string.settings_cert_regen_dialog_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onRegenerateCertConfirmed()
                    showRegenCertDialog = false
                }) {
                    Text(stringResource(R.string.settings_cert_regenerate))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRegenCertDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

// ---- Sub-composables ----

@Composable
private fun SettingsItemRow(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        content()
    }
}

@Composable
private fun ClickableSettingsRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InfoRow(message: String, modifier: Modifier = Modifier) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun CertificateDetailDialog(
    cert: CertificateInfo,
    onDismiss: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_cert_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CertField(stringResource(R.string.cert_subject), cert.subject)
                CertField(stringResource(R.string.cert_issuer), cert.issuer)
                CertField(
                    stringResource(R.string.cert_expires),
                    dateFormat.format(Date(cert.expiresAt)),
                )
                CertField(stringResource(R.string.cert_fingerprint), cert.sha256Fingerprint)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

@Composable
private fun CertField(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Returns the last 16 hex chars of the fingerprint for the list-item subtitle. */
private fun CertificateInfo.shortFingerprint(): String =
    sha256Fingerprint.takeLast(19) // e.g. "…AB:CD:EF:12:34:56"
