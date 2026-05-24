package com.aionyxe.filebridge.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aionyxe.filebridge.R
import com.aionyxe.filebridge.domain.model.AuthMode
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    // Keep pager in sync with ViewModel step (ViewModel is source of truth).
    LaunchedEffect(uiState.currentStep) {
        if (pagerState.currentPage != uiState.currentStep) {
            pagerState.animateScrollToPage(uiState.currentStep)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier.weight(1f),
        ) { page ->
            when (page) {
                0 -> WelcomePage()
                1 -> PermissionPage(
                    uiState = uiState,
                    onStoragePermissionResult = viewModel::onStoragePermissionResult,
                    onStoragePermissionSkipped = viewModel::onStoragePermissionSkipped,
                    onNotificationPermissionResult = viewModel::onNotificationPermissionResult,
                    onNotificationPermissionSkipped = viewModel::onNotificationPermissionSkipped,
                )
                2 -> QuickSetupPage(
                    uiState = uiState,
                    onAuthModeChanged = viewModel::onAuthModeChanged,
                    onUsernameChanged = viewModel::onUsernameChanged,
                    onPasswordChanged = viewModel::onPasswordChanged,
                    onRootDirSelected = viewModel::onRootDirSelected,
                )
            }
        }

        // ---- Page indicator dots ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) { index ->
                val isSelected = index == uiState.currentStep
                val color by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    label = "dot_color_$index",
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (isSelected) 10.dp else 7.dp)
                        .background(color, CircleShape),
                )
            }
        }

        // ---- Navigation buttons ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (uiState.currentStep > 0) {
                OutlinedButton(onClick = {
                    viewModel.onBack()
                }) {
                    Text(stringResource(R.string.onboarding_back))
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            AnimatedContent(
                targetState = uiState.currentStep,
                label = "next_finish_button",
            ) { step ->
                when {
                    step < 2 -> {
                        val nextEnabled = step != 1 ||
                            (uiState.storagePermissionResolved && uiState.notificationPermissionResolved)
                        Button(
                            onClick = {
                                viewModel.onNext()
                                scope.launch { pagerState.animateScrollToPage(step + 1) }
                            },
                            enabled = nextEnabled,
                        ) {
                            Text(stringResource(R.string.onboarding_next))
                        }
                    }
                    else -> {
                        Button(
                            onClick = viewModel::onFinish,
                            enabled = uiState.canFinish && !uiState.isLoading,
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Text(stringResource(R.string.onboarding_finish))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

// ════════════════════════════════════════════════
// Step 0 — Welcome
// ════════════════════════════════════════════════

@Composable
private fun WelcomePage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Router,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(80.dp),
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_tagline),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(32.dp))

        WelcomeBullet(
            icon = Icons.Outlined.Folder,
            text = stringResource(R.string.onboarding_welcome_bullet_browse),
        )
        WelcomeBullet(
            icon = Icons.Outlined.Router,
            text = stringResource(R.string.onboarding_welcome_bullet_transfer),
        )
        WelcomeBullet(
            icon = Icons.Outlined.Security,
            text = stringResource(R.string.onboarding_welcome_bullet_secure),
        )
    }
}

@Composable
private fun WelcomeBullet(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Text(text = text, style = MaterialTheme.typography.titleMedium)
    }
}

// ════════════════════════════════════════════════
// Step 1 — Permissions
// ════════════════════════════════════════════════

@Composable
private fun PermissionPage(
    uiState: OnboardingUiState,
    onStoragePermissionResult: (Boolean) -> Unit,
    onStoragePermissionSkipped: () -> Unit,
    onNotificationPermissionResult: (Boolean) -> Unit,
    onNotificationPermissionSkipped: () -> Unit,
) {
    val (storageGranted, requestStorage) = rememberStoragePermissionState()
    // onResult is called only when the system dialog returns — never on first composition.
    val (notifGranted, requestNotification) = rememberNotificationPermissionState(
        onResult = onNotificationPermissionResult,
    )
    var whyExpanded by remember { mutableStateOf(false) }

    // Propagate live storage grant status to the ViewModel.
    LaunchedEffect(storageGranted) { onStoragePermissionResult(storageGranted) }

    // Auto-resolve notification if the permission is already granted at page load
    // (e.g. the user granted it outside onboarding).
    LaunchedEffect(notifGranted) {
        if (notifGranted) onNotificationPermissionResult(true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp),
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.onboarding_permission_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_permission_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))

        // ---- Storage permission ----
        if (storageGranted) {
            GrantedBadge(label = stringResource(R.string.onboarding_permission_storage_granted))
        } else {
            Button(
                onClick = requestStorage,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.onboarding_permission_grant_storage))
            }
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = { whyExpanded = !whyExpanded }) {
                Text(stringResource(R.string.onboarding_permission_why))
            }
            if (whyExpanded) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 2.dp,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_permission_why_body),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (!uiState.storagePermissionResolved) {
                TextButton(onClick = onStoragePermissionSkipped) {
                    Text(stringResource(R.string.onboarding_permission_skip))
                }
            }
            // When storage is denied/skipped, inform the user that Downloads will be used.
            if (uiState.storagePermissionResolved && !uiState.storagePermissionGranted) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.onboarding_permission_storage_skipped_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // ---- Notification permission (Android 13+) ----
        Spacer(modifier = Modifier.height(24.dp))
        when {
            notifGranted -> {
                GrantedBadge(
                    label = stringResource(R.string.onboarding_permission_notifications_granted),
                )
            }
            uiState.notificationPermissionResolved -> {
                // User denied via the system dialog or clicked Skip — show a neutral note.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(
                            R.string.onboarding_permission_notifications_declined_hint,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                // Not yet asked — show the request button and a skip option.
                Button(
                    onClick = requestNotification,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.onboarding_permission_notifications))
                }
                Text(
                    text = stringResource(R.string.onboarding_permission_notifications_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
                TextButton(onClick = onNotificationPermissionSkipped) {
                    Text(stringResource(R.string.onboarding_permission_notifications_skip))
                }
            }
        }
    }
}

@Composable
private fun GrantedBadge(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

// ════════════════════════════════════════════════
// Step 2 — Quick Setup
// ════════════════════════════════════════════════

@Composable
private fun QuickSetupPage(
    uiState: OnboardingUiState,
    onAuthModeChanged: (AuthMode) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onRootDirSelected: (String) -> Unit,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val dirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            onRootDirSelected(it.toString())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.onboarding_setup_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        // Auth mode
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            AuthMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = uiState.authMode == mode,
                    onClick = { onAuthModeChanged(mode) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = AuthMode.entries.size,
                    ),
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

        // Credentials (only when SINGLE_USER)
        if (uiState.authMode == AuthMode.SINGLE_USER) {
            OutlinedTextField(
                value = uiState.username,
                onValueChange = onUsernameChanged,
                label = { Text(stringResource(R.string.settings_username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.password,
                onValueChange = onPasswordChanged,
                label = { Text(stringResource(R.string.settings_password)) },
                singleLine = true,
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Outlined.VisibilityOff
                            } else {
                                Icons.Outlined.Visibility
                            },
                            contentDescription = null,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Root directory
        val downloadsPath = remember {
            Environment
                .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .absolutePath
        }
        val rootLabel = when {
            uiState.rootDirUri.isEmpty() ->
                stringResource(R.string.onboarding_setup_root_not_set)
            uiState.rootDirUri == downloadsPath ->
                stringResource(R.string.onboarding_setup_root_downloads_default)
            else ->
                Uri.parse(uiState.rootDirUri).lastPathSegment ?: uiState.rootDirUri
        }

        OutlinedButton(
            onClick = { dirPickerLauncher.launch(null) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = rootLabel, maxLines = 1)
        }

        Text(
            text = stringResource(R.string.onboarding_setup_root_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
