package com.aionyxe.filebridge.domain.validation

import com.aionyxe.filebridge.R
import javax.inject.Inject

/**
 * Pure input validation for settings. No Android dependencies beyond string resource ids.
 */
class SettingsValidator @Inject constructor() {

    fun validatePort(value: Int): ValidationResult =
        if (value in MIN_PORT..MAX_PORT) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(R.string.error_port_range)
        }

    fun validatePasvRange(min: Int, max: Int, ftpPort: Int): ValidationResult = when {
        min !in MIN_PORT..MAX_PORT || max !in MIN_PORT..MAX_PORT ->
            ValidationResult.Invalid(R.string.error_port_range)
        max <= min ->
            ValidationResult.Invalid(R.string.error_pasv_range)
        ftpPort in min..max ->
            ValidationResult.Invalid(R.string.error_pasv_overlap)
        else -> ValidationResult.Valid
    }

    fun validateUsername(value: String): ValidationResult =
        if (value.isNotBlank()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(R.string.error_username_blank)
        }

    fun validatePassword(value: String): ValidationResult =
        if (value.isNotEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(R.string.error_password_blank)
        }

    companion object {
        const val MIN_PORT = 1024
        const val MAX_PORT = 65535
    }
}
