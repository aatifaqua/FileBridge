package com.aionyxe.filebridge.data.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * Typed DataStore Preferences keys. Enums are persisted by their [Enum.name].
 */
object SettingsKeys {
    val PROTOCOL = stringPreferencesKey("protocol")
    val FTP_PORT = intPreferencesKey("ftp_port")
    val PASV_MIN_PORT = intPreferencesKey("pasv_min_port")
    val PASV_MAX_PORT = intPreferencesKey("pasv_max_port")
    val AUTH_MODE = stringPreferencesKey("auth_mode")
    val USERNAME = stringPreferencesKey("username")
    val ROOT_DIR_URI = stringPreferencesKey("root_dir_uri")
    val ACCESS_MODE = stringPreferencesKey("access_mode")
    val START_ON_APP_LAUNCH = booleanPreferencesKey("start_on_app_launch")
    val START_ON_BOOT = booleanPreferencesKey("start_on_boot")
    val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
}
