package com.aionyxe.filebridge.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.aionyxe.filebridge.R
import com.aionyxe.filebridge.service.ServiceLauncher

/**
 * 2×1 home-screen widget showing server status (running / stopped) and a tap-to-toggle action.
 *
 * State is stored in Glance's managed `Preferences` DataStore and updated by [FtpForegroundService]
 * on every [com.aionyxe.filebridge.domain.model.ServerState] transition.
 */
class FtpServerWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val isRunning = prefs[KEY_RUNNING] ?: false
            val address = prefs[KEY_ADDRESS] ?: ""

            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.widgetBackground)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // App label
                    Text(
                        text = context.getString(R.string.widget_label),
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            color = GlanceTheme.colors.primary,
                        ),
                    )
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val statusText = when {
                            isRunning && address.isNotEmpty() ->
                                context.getString(R.string.widget_status_running, address)
                            isRunning ->
                                context.getString(R.string.widget_status_running_idle)
                            else ->
                                context.getString(R.string.widget_status_stopped)
                        }
                        Text(
                            text = statusText,
                            style = TextStyle(
                                color = if (isRunning) {
                                    GlanceTheme.colors.primary
                                } else {
                                    GlanceTheme.colors.onSurfaceVariant
                                },
                            ),
                            modifier = GlanceModifier.defaultWeight(),
                        )
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        androidx.glance.Button(
                            text = if (isRunning) {
                                context.getString(R.string.widget_action_stop)
                            } else {
                                context.getString(R.string.widget_action_start)
                            },
                            onClick = if (isRunning) {
                                actionRunCallback<StopServerWidgetAction>()
                            } else {
                                actionRunCallback<StartServerWidgetAction>()
                            },
                        )
                    }
                }
            }
        }
    }

    companion object {
        val KEY_RUNNING = booleanPreferencesKey("widget_server_running")
        val KEY_ADDRESS = stringPreferencesKey("widget_server_address")

        /** Called from [FtpForegroundService] on every server-state transition. */
        suspend fun updateState(
            context: Context,
            glanceId: GlanceId,
            isRunning: Boolean,
            address: String,
        ) {
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    set(KEY_RUNNING, isRunning)
                    set(KEY_ADDRESS, address)
                }
            }
            FtpServerWidget().update(context, glanceId)
        }
    }
}

// ---- Action callbacks ----

class StartServerWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        ServiceLauncher.start(context)
    }
}

class StopServerWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        ServiceLauncher.stop(context)
    }
}
