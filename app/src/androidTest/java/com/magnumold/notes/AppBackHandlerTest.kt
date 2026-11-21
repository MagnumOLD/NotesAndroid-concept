package com.magnumold.notes

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class AppBackHandlerTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun pressBack() {
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
    }

    @Test
    fun nestedHandlerConsumesBackBeforeParentAndParentHandlesNextBack() {
        val parentBacks = AtomicInteger()
        val nestedBacks = AtomicInteger()
        val nestedVisible = mutableStateOf(true)

        composeRule.setContent {
            AppBackHandler { parentBacks.incrementAndGet() }
            if (nestedVisible.value) {
                AppBackHandler {
                    nestedBacks.incrementAndGet()
                    nestedVisible.value = false
                }
            }
        }

        pressBack()
        assertEquals(1, nestedBacks.get())
        assertEquals(0, parentBacks.get())

        pressBack()
        assertEquals(1, parentBacks.get())
    }

    @Test
    fun backOnNutritionRecordLeavesEditorThroughItsCallback() {
        val backCount = AtomicInteger()
        composeRule.setContent {
            MaterialTheme {
                NutritionRecordEditor(
                    note = nutritionNote(),
                    products = emptyList(),
                    topBarColor = Color(0xFF202020),
                    addButtonColor = Color(0xFF4A90E2),
                    onBack = { backCount.incrementAndGet() },
                    onSaved = {},
                    onChange = {},
                    onProductsChange = {}
                )
            }
        }

        pressBack()
        assertEquals(1, backCount.get())
    }

    @Test
    fun backOnNutritionCatalogClosesCatalogWithoutLeavingEditor() {
        val backCount = AtomicInteger()
        composeRule.setContent {
            MaterialTheme {
                NutritionRecordEditor(
                    note = nutritionNote(),
                    products = emptyList(),
                    topBarColor = Color(0xFF202020),
                    addButtonColor = Color(0xFF4A90E2),
                    onBack = { backCount.incrementAndGet() },
                    onSaved = {},
                    onChange = {},
                    onProductsChange = {}
                )
            }
        }

        composeRule.onNodeWithText("База продуктов").performClick()
        pressBack()

        assertEquals(0, backCount.get())
        composeRule.onNodeWithText("Продукты в приёме пищи").assertExists()
    }

    @Test
    fun backOnNutritionProductDialogDismissesDialogBeforeCatalog() {
        val backCount = AtomicInteger()
        composeRule.setContent {
            MaterialTheme {
                NutritionRecordEditor(
                    note = nutritionNote(),
                    products = emptyList(),
                    topBarColor = Color(0xFF202020),
                    addButtonColor = Color(0xFF4A90E2),
                    onBack = { backCount.incrementAndGet() },
                    onSaved = {},
                    onChange = {},
                    onProductsChange = {}
                )
            }
        }

        composeRule.onNodeWithText("База продуктов").performClick()
        composeRule.onNodeWithText("+").performClick()
        composeRule.onNodeWithText("Новый продукт").assertExists()

        pressBack()

        assertEquals(0, backCount.get())
        composeRule.onNodeWithText("Новый продукт").assertDoesNotExist()
        composeRule.onNodeWithText("База пока пуста. Нажми +, чтобы добавить продукт.").assertExists()
    }

    private fun nutritionNote(): Note {
        val product = NutritionProduct(99101L, "Тестовый продукт", 100.0, 10.0, 2.0, 5.0)
        return NutritionCatalogOperations.withMealItems(
            Note(
                id = 99102L,
                title = "",
                blocks = listOf(NoteBlock()),
                updated = 1L,
                type = RecordType.NUTRITION
            ),
            listOf(NutritionCatalogOperations.createMealItem(product, 100.0))
        )
    }
}
