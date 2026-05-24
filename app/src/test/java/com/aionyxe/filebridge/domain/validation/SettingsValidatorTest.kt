package com.aionyxe.filebridge.domain.validation

import com.aionyxe.filebridge.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsValidatorTest {

    private val validator = SettingsValidator()

    @Test
    fun `port below range is invalid`() {
        val result = validator.validatePort(80)
        assertEquals(ValidationResult.Invalid(R.string.error_port_range), result)
    }

    @Test
    fun `port above range is invalid`() {
        assertEquals(
            ValidationResult.Invalid(R.string.error_port_range),
            validator.validatePort(70000),
        )
    }

    @Test
    fun `port within range is valid`() {
        assertTrue(validator.validatePort(2121).isValid)
    }

    @Test
    fun `pasv max not greater than min is invalid`() {
        assertEquals(
            ValidationResult.Invalid(R.string.error_pasv_range),
            validator.validatePasvRange(min = 50000, max = 50000, ftpPort = 2121),
        )
    }

    @Test
    fun `ftp port inside pasv range is invalid`() {
        assertEquals(
            ValidationResult.Invalid(R.string.error_pasv_overlap),
            validator.validatePasvRange(min = 50000, max = 51000, ftpPort = 50500),
        )
    }

    @Test
    fun `pasv range out of bounds is invalid`() {
        assertEquals(
            ValidationResult.Invalid(R.string.error_port_range),
            validator.validatePasvRange(min = 100, max = 51000, ftpPort = 2121),
        )
    }

    @Test
    fun `valid pasv range passes`() {
        assertTrue(validator.validatePasvRange(50000, 51000, 2121).isValid)
    }

    @Test
    fun `whitespace-only username is invalid`() {
        assertEquals(
            ValidationResult.Invalid(R.string.error_username_blank),
            validator.validateUsername("   "),
        )
    }

    @Test
    fun `non-blank username is valid`() {
        assertTrue(validator.validateUsername("ftpuser").isValid)
    }

    @Test
    fun `empty password is invalid`() {
        assertEquals(
            ValidationResult.Invalid(R.string.error_password_blank),
            validator.validatePassword(""),
        )
    }

    @Test
    fun `non-empty password is valid`() {
        assertTrue(validator.validatePassword("hunter2").isValid)
    }
}
