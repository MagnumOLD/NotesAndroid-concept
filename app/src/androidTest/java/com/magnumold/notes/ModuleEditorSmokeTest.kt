package com.magnumold.notes

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class ModuleEditorSmokeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun record(type: RecordType) = Note(
        id = 91001L,
        title = "",
        blocks = listOf(NoteBlock()),
        updated = 1L,
        type = type
    )

    @Test
    fun nutritionEditorShowsAllMacroFieldsAndCalculatedSummary() {
        composeRule.setContent {
            MaterialTheme {
                NutritionRecordEditor(
                    note = record(RecordType.NUTRITION).copy(
                        titleText = "Овсянка",
                        nutritionCalories = 150.0,
                        nutritionProtein = 5.0,
                        nutritionFat = 3.0,
                        nutritionCarbs = 27.0
                    ),
                    topBarColor = Color(0xFF202020),
                    onBack = {},
                    onChange = {}
                )
            }
        }

        composeRule.onNodeWithText("Продукт или приём пищи").assertExists()
        composeRule.onNodeWithText("Масса, г").assertExists()
        composeRule.onNodeWithText("Калории, ккал").assertExists()
        composeRule.onNodeWithText("Белки, г").assertExists()
        composeRule.onNodeWithText("Жиры, г").assertExists()
        composeRule.onNodeWithText("Углеводы, г").assertExists()
        composeRule.onNodeWithText("150 ккал  •  Б 5 г  •  Ж 3 г  •  У 27 г").assertExists()
    }

    @Test
    fun nutritionEditorAcceptsCommaDecimalsAndRejectsMalformedNumbers() {
        val state = androidx.compose.runtime.mutableStateOf(record(RecordType.NUTRITION))
        composeRule.setContent {
            MaterialTheme {
                NutritionRecordEditor(
                    note = state.value,
                    topBarColor = Color(0xFF202020),
                    onBack = {},
                    onChange = { state.value = it }
                )
            }
        }

        composeRule.onNodeWithText("Белки, г").performTextReplacement("12,5")
        composeRule.runOnIdle { assertEquals(12.5, state.value.nutritionProtein, 0.0) }

        composeRule.onNodeWithText("Белки, г").performTextReplacement("1,2,3")
        composeRule.runOnIdle { assertEquals(12.5, state.value.nutritionProtein, 0.0) }
    }

    @Test
    fun purchaseEditorShowsQuantityPriceTotalAndBoughtControl() {
        composeRule.setContent {
            MaterialTheme {
                PurchaseRecordEditor(
                    note = record(RecordType.PURCHASE).copy(
                        titleText = "Хлеб",
                        purchaseQuantity = 2.0,
                        purchaseUnitPrice = 1.5
                    ),
                    topBarColor = Color(0xFF202020),
                    onBack = {},
                    onChange = {}
                )
            }
        }

        composeRule.onNodeWithText("Что купить").assertExists()
        composeRule.onNodeWithText("Количество").assertExists()
        composeRule.onNodeWithText("Цена за единицу").assertExists()
        composeRule.onNodeWithText("Итого").assertExists()
        composeRule.onNodeWithText("3.00").assertExists()
        composeRule.onNodeWithText("Куплено").assertExists()
    }

    @Test
    fun purchaseEditorAcceptsCommaDecimalsAndRejectsMultipleSeparators() {
        val state = androidx.compose.runtime.mutableStateOf(record(RecordType.PURCHASE))
        composeRule.setContent {
            MaterialTheme {
                PurchaseRecordEditor(
                    note = state.value,
                    topBarColor = Color(0xFF202020),
                    onBack = {},
                    onChange = { state.value = it }
                )
            }
        }

        composeRule.onNodeWithText("Количество").performTextReplacement("2,5")
        composeRule.runOnIdle { assertEquals(2.5, state.value.purchaseQuantity, 0.0) }

        composeRule.onNodeWithText("Количество").performTextReplacement("2,5,7")
        composeRule.runOnIdle { assertEquals(2.5, state.value.purchaseQuantity, 0.0) }
    }

    @Test
    fun workoutInputsDisplayAndSaveClampedValues() {
        val state = androidx.compose.runtime.mutableStateOf(record(RecordType.WORKOUT))
        composeRule.setContent {
            MaterialTheme {
                WorkoutRecordEditor(
                    note = state.value,
                    topBarColor = Color(0xFF202020),
                    onBack = {},
                    onChange = { state.value = it }
                )
            }
        }

        composeRule.onNodeWithText("Подходы").performTextReplacement("5000")
        composeRule.runOnIdle {
            assertEquals(100, state.value.workoutSets)
        }
        composeRule.onNodeWithText("Вес, кг (если нужен)").performTextReplacement("1500")
        composeRule.runOnIdle {
            assertEquals(1_000.0, state.value.workoutWeightKg, 0.0)
        }
    }

    @Test
    fun workoutEditorShowsExerciseParametersAndVolume() {
        composeRule.setContent {
            MaterialTheme {
                WorkoutRecordEditor(
                    note = record(RecordType.WORKOUT).copy(
                        titleText = "Подтягивания",
                        workoutSets = 4,
                        workoutReps = 8,
                        workoutWeightKg = 10.0
                    ),
                    topBarColor = Color(0xFF202020),
                    onBack = {},
                    onChange = {}
                )
            }
        }

        composeRule.onNodeWithText("Упражнение").assertExists()
        composeRule.onNodeWithText("Подходы").assertExists()
        composeRule.onNodeWithText("Повторения").assertExists()
        composeRule.onNodeWithText("Вес, кг (если нужен)").assertExists()
        composeRule.onNodeWithText("Общий объём").assertExists()
        composeRule.onNodeWithText("320.0 кг").assertExists()
    }
}
