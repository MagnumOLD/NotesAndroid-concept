package com.magnumold.notes

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class ModulesScreenCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun everyModuleUsesTheSharedRecordCardSurface() {
        composeRule.setContent {
            MaterialTheme {
                ModulesScreen(
                    configuration = ModuleConfiguration(),
                    onConfigurationChange = {},
                    onOpenModule = {},
                    onOpenManagement = {},
                    onBack = {},
                    themeColors = CustomThemeColors(topBar = Color.DarkGray)
                )
            }
        }

        AppModule.entries.filter { it != AppModule.ALL }.forEach { module ->
            composeRule.onNodeWithTag("module-card-${module.name}").assertIsDisplayed()
        }
        composeRule.onNodeWithText("Текст, списки и быстрые записи").assertIsDisplayed()
    }

    @Test
    fun tappingAnUnselectedEnabledModuleOpensThatModule() {
        var opened: AppModule? = null
        composeRule.setContent {
            MaterialTheme {
                ModulesScreen(
                    configuration = ModuleConfiguration(),
                    onConfigurationChange = {},
                    onOpenModule = { opened = it },
                    onOpenManagement = {},
                    onBack = {},
                    themeColors = CustomThemeColors(topBar = Color.DarkGray)
                )
            }
        }

        composeRule.onNodeWithText("Заметки").performClick()
        composeRule.runOnIdle {
            assertEquals(AppModule.NOTES, opened)
        }
    }
}
