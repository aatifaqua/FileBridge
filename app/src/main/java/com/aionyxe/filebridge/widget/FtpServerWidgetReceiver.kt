package com.aionyxe.filebridge.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Entry point that the Android system uses to route widget broadcasts to [FtpServerWidget].
 * Declared in the manifest alongside the `<appwidget-provider>` metadata.
 */
class FtpServerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FtpServerWidget()
}
