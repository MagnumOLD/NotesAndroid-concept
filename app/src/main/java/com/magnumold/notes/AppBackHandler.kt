package com.magnumold.notes

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

/**
 * The app-wide entry point for handling Android system Back.
 *
 * Keep handlers local to the UI state they dismiss. Nested composables are
 * composed after their parents, so their handlers naturally take precedence
 * while a nested surface is visible.
 */
@Composable
internal fun AppBackHandler(
    enabled: Boolean = true,
    onBack: () -> Unit
) {
    BackHandler(enabled = enabled, onBack = onBack)
}
