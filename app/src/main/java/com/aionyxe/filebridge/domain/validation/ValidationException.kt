package com.aionyxe.filebridge.domain.validation

import androidx.annotation.StringRes

/** Carries a string-resource id so the UI can show a localized message for a failed [Result]. */
class ValidationException(@StringRes val reason: Int) : Exception()
