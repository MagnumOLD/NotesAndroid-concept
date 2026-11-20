package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NutritionCatalogOperationsTest {
    private val buckwheat = NutritionProduct(10L, "Гречка", 343.0, 13.3, 3.4, 71.5)
    @Test fun mealItemCalculatesMacrosFromProductPerHundredGrams() {
        val i = NutritionCatalogOperations.createMealItem(buckwheat, 150.0)
        assertEquals(514.5, i.calories, 0.000001); assertEquals(19.95, i.protein, 0.000001)
        assertEquals(5.1, i.fat, 0.000001); assertEquals(107.25, i.carbs, 0.000001)
    }
    @Test fun changingGramsRecalculatesFromFrozenProductValues() {
        val a = NutritionCatalogOperations.createMealItem(buckwheat, 100.0)
        val b = NutritionCatalogOperations.updateGrams(a, 250.0)
        assertEquals(857.5, b.calories, 0.000001); assertEquals(a.productName, b.productName); assertEquals(a.id, b.id)
    }
    @Test fun mealTotalsAndNoteFieldsStayInSync() {
        val items = listOf(NutritionCatalogOperations.createMealItem(buckwheat, 150.0), NutritionCatalogOperations.createMealItem(buckwheat, 50.0))
        val note = NutritionCatalogOperations.withMealItems(Note(1L, "", emptyList(), 1L, type = RecordType.NUTRITION), items)
        assertEquals(200.0, note.nutritionGrams, 0.000001); assertEquals(686.0, note.nutritionCalories, 0.000001)
        assertEquals(26.6, note.nutritionProtein, 0.000001); assertEquals(2, note.nutritionItems.size); assertTrue(note.hasContent())
    }
    @Test fun productImportDeduplicatesExactProductsAndKeepsDifferentValues() {
        val changed = buckwheat.copy(id = 11L, caloriesPer100g = 350.0)
        val merged = NutritionCatalogOperations.mergeProducts(listOf(buckwheat), listOf(buckwheat.copy(id = 12L), changed))
        assertEquals(2, merged.size); assertEquals(350.0, merged.last().caloriesPer100g, 0.0); assertEquals(2, merged.map { it.id }.toSet().size)
    }
    @Test fun backupRoundTripsMealContentsAndTheReusableProductCatalog() {
        val item = NutritionCatalogOperations.createMealItem(buckwheat, 175.0)
        val meal = NutritionCatalogOperations.withMealItems(
            Note(50L, "", emptyList(), 1L, type = RecordType.NUTRITION), listOf(item)
        )
        val raw = createBackupJson(
            items = listOf(meal), collections = emptyList(), archived = emptyList(), trash = emptyList(),
            nutritionProducts = listOf(buckwheat)
        )
        val parsed = parseBackupJson(raw)
        assertEquals(listOf(buckwheat), parsed.nutritionProducts)
        assertEquals(listOf(item), parsed.notes.single().nutritionItems)
    }

    @Test fun productAndMealItemsRoundTripThroughJson() {
        val item = NutritionCatalogOperations.createMealItem(buckwheat, 80.0)
        assertEquals(listOf(buckwheat), NutritionCatalogOperations.parseProducts(NutritionCatalogOperations.productsToJson(listOf(buckwheat))))
        assertEquals(item, NutritionCatalogOperations.parseMealItems(NutritionCatalogOperations.mealItemsToJson(listOf(item))).single())
    }
}
