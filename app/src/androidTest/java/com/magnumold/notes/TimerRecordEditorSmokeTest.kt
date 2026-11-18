package com.magnumold.notes

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class TimerRecordEditorSmokeTest {
    @get:Rule
    val composeRule = createComposeRule()


    @Test
    fun timerCompletionKeepsTheLatestEditedTitle() {
        val state = androidx.compose.runtime.mutableStateOf(
            Note(
                id = 91003L,
                title = "",
                titleText = "Initial title",
                blocks = listOf(NoteBlock()),
                updated = 1L,
                type = RecordType.TIMER,
                status = RecordStatus.IN_PROGRESS,
                timerDurationSeconds = 1L,
                timerEndsAtMillis = System.currentTimeMillis() + 900L
            )
        )

        composeRule.setContent {
            MaterialTheme {
                TimerRecordEditor(
                    note = state.value,
                    topBarColor = Color(0xFF202020),
                    onBack = {},
                    onChange = { state.value = it }
                )
            }
        }

        composeRule.runOnIdle {
            state.value = state.value.copy(titleText = "Updated title")
        }
        composeRule.waitUntil(5_000L) {
            state.value.status == RecordStatus.COMPLETED
        }
        composeRule.runOnIdle {
            assertEquals("Updated title", state.value.titleText)
            assertEquals(null, state.value.timerEndsAtMillis)
        }
    }

    @Test
    fun idleTimerShowsDurationAndStartControls() {
        val note = Note(
            id = 91002L,
            title = "",
            titleText = "Перерыв",
            blocks = listOf(NoteBlock()),
            updated = 1L,
            type = RecordType.TIMER,
            timerDurationSeconds = 300L,
            timerRemainingSeconds = null,
            timerEndsAtMillis = null
        )

        composeRule.setContent {
            MaterialTheme {
                TimerRecordEditor(
                    note = note,
                    topBarColor = Color(0xFF202020),
                    onBack = {},
                    onChange = {}
                )
            }
        }

        composeRule.onNodeWithText("Таймер").assertExists()
        composeRule.onNodeWithText("Длительность, секунд").assertExists()
        composeRule.onNodeWithText("Старт").assertExists()
        composeRule.onNodeWithText("05:00").assertExists()
    }
}
