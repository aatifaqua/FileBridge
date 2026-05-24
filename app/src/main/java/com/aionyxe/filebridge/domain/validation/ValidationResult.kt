package com.aionyxe.filebridge.domain.validation

import androidx.annotation.StringRes

sealed interface ValidationResult {
    data object Valid : ValidationResult

    data class Invalid(@StringRes val reason: Int) : ValidationResult

    val isValid: Boolean get() = this is Valid
}
