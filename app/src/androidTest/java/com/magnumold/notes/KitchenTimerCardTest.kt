package com.magnumold.notes

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class KitchenTimerCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun timerModuleUsesSharedCardContentAndOverflowActions() {
        val timer = KitchenTimer(
            id = 93001L,
            title = "Гречка",
            durationSeconds = 600L,
            remainingSeconds = 255L,
            status = KitchenTimerStatus.PAUSED
        )

        composeRule.setContent {
            MaterialTheme {
                KitchenTimersScreen(
                    timers = listOf(timer),
                    settings = KitchenTimerSettings(),
                    topBarColor = Color(0xFF202020),
                    addButtonColor = Color(0xFF607D8B),
                    noteColors = listOf(
                        Color(0xFFFFD6D6),
                        Color(0xFFFFE6C7),
                        Color(0xFFFFF2B2),
                        Color(0xFFD8F0D2),
                        Color(0xFFD5E8FF),
                        Color(0xFFE8D9FF)
                    ),
                    dark = false,
                    onTimersChange = {},
                    onSettingsChange = {},
                    onRefresh = {}
                )
            }
        }

        composeRule.onNodeWithText("Гречка").assertExists()
        composeRule.onNodeWithText("На паузе").assertExists()
        composeRule.onNodeWithText("04:15").assertExists()

        composeRule.onNodeWithContentDescription("Дополнительные действия").performClick()
        composeRule.onNodeWithText("Редактировать").assertExists()
        composeRule.onNodeWithText("Удалить").assertExists()
    }
}
