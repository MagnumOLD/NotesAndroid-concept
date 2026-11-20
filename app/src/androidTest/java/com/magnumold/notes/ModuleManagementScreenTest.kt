package com.magnumold.notes

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class ModuleManagementScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun screenShowsSixModulesAndTheirSwitches() {
        composeRule.setContent {
            MaterialTheme {
                ModuleManagementScreen(
                    configuration = ModuleConfiguration(),
                    onConfigurationChange = {},
                    onBack = {},
                    themeColors = CustomThemeColors(topBar = Color.DarkGray)
                )
            }
        }

        composeRule.onNodeWithText("Управление модулями").assertIsDisplayed()
        composeRule.onNodeWithText("Заметки").assertIsDisplayed()
        composeRule.onNodeWithText("Задачи").assertIsDisplayed()
        composeRule.onNodeWithText("Таймеры").assertIsDisplayed()
        composeRule.onNodeWithText("КБЖУ").assertIsDisplayed()
        composeRule.onNodeWithText("Тренировки").assertIsDisplayed()
        composeRule.onNodeWithText("Покупки").assertIsDisplayed()
        composeRule.onAllNodes(isToggleable()).assertCountEquals(6)
    }

    @Test
    fun togglingModuleChangesOnlyItsEnabledState() {
        val state = androidx.compose.runtime.mutableStateOf(ModuleConfiguration())

        composeRule.setContent {
            MaterialTheme {
                ModuleManagementScreen(
                    configuration = state.value,
                    onConfigurationChange = { state.value = it },
                    onBack = {},
                    themeColors = CustomThemeColors(topBar = Color.DarkGray)
                )
            }
        }

        composeRule.onAllNodes(isToggleable())[0].performClick()
        composeRule.runOnIdle {
            assertFalse(AppModule.NOTES in state.value.enabled)
            assertTrue(AppModule.TASKS in state.value.enabled)
            assertTrue(AppModule.NOTES in state.value.order)
        }
    }
}
