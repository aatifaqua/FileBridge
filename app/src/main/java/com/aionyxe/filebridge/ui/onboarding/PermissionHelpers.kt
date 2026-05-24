package com.aionyxe.filebridge.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Returns whether the app currently holds the storage permission needed to serve files. */
fun isStoragePermissionGranted(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
    }

/**
 * Composable that tracks whether the storage permission is granted and provides a [request]
 * function.
 *
 * - **Android 11+**: launches `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`; grant status is
 *   re-checked on each `ON_RESUME` lifecycle event because the user returns from a Settings
 *   screen rather than a system dialog.
 * - **Android 10−**: uses the standard runtime permission dialog.
 *
 * @return Pair of (isGranted, request).
 */
@Composable
fun rememberStoragePermissionState(): Pair<Boolean, () -> Unit> {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    var isGranted by remember { mutableStateOf(isStoragePermissionGranted(context)) }

    // Re-check whenever the activity resumes (covers the "return from Settings" path on API 30+).
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isGranted = isStoragePermissionGranted(context)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        // API 30+: redirect to the all-files-access settings page.
        val settingsLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) {
            isGranted = Environment.isExternalStorageManager()
        }
        val request: () -> Unit = {
            settingsLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        }
        return isGranted to request
    } else {
        // API 29−: use the runtime permission dialog.
        val runtimeLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
        ) { perms ->
            isGranted = perms[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }
        val request: () -> Unit = {
            runtimeLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ),
            )
        }
        return isGranted to request
    }
}

/**
 * Returns whether the `POST_NOTIFICATIONS` permission is currently granted (Android 13+ only;
 * always `true` on earlier APIs where the permission doesn't exist) and a [request] function.
 *
 * @param onResult Called with the dialog result (`true` = granted, `false` = denied) **only** when
 *   the system permission dialog actually returns — i.e. after the user has explicitly responded.
 *   It is NOT called with the initial check state, so callers can distinguish "not yet asked" from
 *   "asked and denied".
 */
@Composable
fun rememberNotificationPermissionState(
    onResult: (Boolean) -> Unit = {},
): Pair<Boolean, () -> Unit> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return true to {}
    }
    val context = LocalContext.current
    var isGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        isGranted = granted
        onResult(granted)
    }

    return isGranted to { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}
