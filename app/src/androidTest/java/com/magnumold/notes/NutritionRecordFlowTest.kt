package com.magnumold.notes

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class NutritionRecordFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectingProductAndSavingCreatesVisibleNutritionRecord() {
        val initial = Note(
            id = 91001L,
            title = "",
            blocks = listOf(NoteBlock()),
            updated = 1L,
            type = RecordType.NUTRITION
        )
        val product = NutritionProduct(
            id = 91002L,
            name = "Овсянка",
            caloriesPer100g = 370.0,
            proteinPer100g = 13.0,
            fatPer100g = 7.0,
            carbsPer100g = 62.0
        )
        val changedNote = androidx.compose.runtime.mutableStateOf<Note?>(null)
        val saved = androidx.compose.runtime.mutableStateOf(false)

        composeRule.setContent {
            MaterialTheme {
                NutritionRecordEditor(
                    note = initial,
                    products = listOf(product),
                    topBarColor = Color(0xFF202020),
                    addButtonColor = Color(0xFF4A90E2),
                    onBack = {},
                    onSaved = { saved.value = true },
                    onChange = { changedNote.value = it },
                    onProductsChange = {}
                )
            }
        }

        composeRule.onNodeWithText("Продукт").performTextInput("Овсянка")
        composeRule.onNodeWithText("Овсянка").performClick()
        composeRule.onNodeWithText("Граммы").performTextReplacement("50")
        composeRule.onNodeWithText("Добавить в запись").performClick()

        composeRule.runOnIdle {
            val created = requireNotNull(changedNote.value)
            assertTrue(saved.value)
            assertTrue(created.hasContent())
            assertEquals(1, created.nutritionItems.size)
            assertEquals("Овсянка", recordCardPrimaryText(created))
            assertEquals(50.0, created.nutritionGrams, 0.000001)
            assertEquals(185.0, created.nutritionCalories, 0.000001)
            assertEquals(6.5, created.nutritionProtein, 0.000001)
            assertEquals(3.5, created.nutritionFat, 0.000001)
            assertEquals(31.0, created.nutritionCarbs, 0.000001)

            val emptySnapshot = PersistenceSnapshot(
                notes = emptyList(),
                collections = emptyList(),
                archived = emptyList(),
                trash = emptyList()
            )
            val persistedSnapshot = RecordListOperations.create(emptySnapshot, null, created)
            val visibleNutritionRecords = ModuleVisibilityOperations.visibleRecords(
                persistedSnapshot.notes,
                ModuleConfiguration().enabled
            ).filter { it.type == RecordType.NUTRITION }

            assertEquals(listOf(created), visibleNutritionRecords)
        }
    }
}
