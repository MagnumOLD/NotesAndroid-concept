package com.magnumold.notes

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.util.Locale

internal object NutritionCatalogOperations {
    private fun safe(value: Double) = NutritionOperations.normalizeAmount(value)
    fun normalizeProduct(p: NutritionProduct) = p.copy(
        name = p.name.trim(), caloriesPer100g = safe(p.caloriesPer100g),
        proteinPer100g = safe(p.proteinPer100g), fatPer100g = safe(p.fatPer100g),
        carbsPer100g = safe(p.carbsPer100g)
    )
    fun createMealItem(product: NutritionProduct, grams: Double, id: String = java.util.UUID.randomUUID().toString()): NutritionMealItem {
        val p = normalizeProduct(product); val amount = safe(grams)
        fun calc(v: Double) = BigDecimal.valueOf(v).multiply(BigDecimal.valueOf(amount))
            .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP).toDouble()
        return NutritionMealItem(id, p.id, p.name, amount, p.caloriesPer100g, p.proteinPer100g,
            p.fatPer100g, p.carbsPer100g, calc(p.caloriesPer100g), calc(p.proteinPer100g),
            calc(p.fatPer100g), calc(p.carbsPer100g))
    }
    fun updateGrams(i: NutritionMealItem, grams: Double) = createMealItem(
        NutritionProduct(i.productId, i.productName, i.caloriesPer100g, i.proteinPer100g, i.fatPer100g, i.carbsPer100g), grams, i.id
    )
    fun withMealItems(note: Note, items: List<NutritionMealItem>): Note {
        fun total(f: (NutritionMealItem) -> Double) = NutritionOperations.sumAmounts(items.map(f)).toDouble()
        return note.copy(
            titleText = if (items.isEmpty()) "" else note.titleText.ifBlank { "Приём пищи" },
            nutritionItems = items, nutritionGrams = total { it.grams },
            nutritionCalories = total { it.calories }, nutritionProtein = total { it.protein },
            nutritionFat = total { it.fat }, nutritionCarbs = total { it.carbs })
    }
    fun productsToJson(products: List<NutritionProduct>) = JSONArray().also { a ->
        products.forEach { raw -> val p = normalizeProduct(raw)
            a.put(JSONObject().put("id", p.id).put("name", p.name).put("caloriesPer100g", p.caloriesPer100g)
                .put("proteinPer100g", p.proteinPer100g).put("fatPer100g", p.fatPer100g).put("carbsPer100g", p.carbsPer100g))
        }
    }
    fun parseProducts(a: JSONArray?): List<NutritionProduct> {
        if (a == null) return emptyList()
        val ids = mutableSetOf<Long>()
        return (0 until a.length()).mapNotNull { n ->
            val o = a.optJSONObject(n) ?: return@mapNotNull null
            val p = normalizeProduct(NutritionProduct(o.optLong("id", newRecordId()), o.optString("name"),
                o.optDouble("caloriesPer100g"), o.optDouble("proteinPer100g"), o.optDouble("fatPer100g"), o.optDouble("carbsPer100g")))
            if (p.id == 0L || p.name.isBlank() || !ids.add(p.id)) null else p
        }
    }
    fun mealItemsToJson(items: List<NutritionMealItem>) = JSONArray().also { a ->
        items.forEach { i -> a.put(JSONObject().put("id", i.id).put("productId", i.productId).put("productName", i.productName)
            .put("grams", safe(i.grams)).put("caloriesPer100g", safe(i.caloriesPer100g))
            .put("proteinPer100g", safe(i.proteinPer100g)).put("fatPer100g", safe(i.fatPer100g))
            .put("carbsPer100g", safe(i.carbsPer100g)).put("calories", safe(i.calories))
            .put("protein", safe(i.protein)).put("fat", safe(i.fat)).put("carbs", safe(i.carbs))) }
    }
    fun parseMealItems(a: JSONArray?): List<NutritionMealItem> {
        if (a == null) return emptyList()
        return (0 until a.length()).mapNotNull { n ->
            val o = a.optJSONObject(n) ?: return@mapNotNull null
            val name = o.optString("productName").trim()
            if (name.isBlank()) return@mapNotNull null
            createMealItem(
                NutritionProduct(o.optLong("productId"), name, safe(o.optDouble("caloriesPer100g")),
                    safe(o.optDouble("proteinPer100g")), safe(o.optDouble("fatPer100g")), safe(o.optDouble("carbsPer100g"))),
                safe(o.optDouble("grams")),
                o.optString("id").ifBlank { java.util.UUID.randomUUID().toString() }
            )
        }
    }
    fun mergeProducts(old: List<NutritionProduct>, incoming: List<NutritionProduct>): List<NutritionProduct> {
        val result = old.toMutableList(); val ids = result.mapTo(mutableSetOf()) { it.id }
        incoming.map(::normalizeProduct).forEach { p ->
            val same = result.any { it.name.equals(p.name, true) && it.caloriesPer100g == p.caloriesPer100g &&
                it.proteinPer100g == p.proteinPer100g && it.fatPer100g == p.fatPer100g && it.carbsPer100g == p.carbsPer100g }
            if (!same) { var id = p.id; while (id == 0L || id in ids) id = newRecordId(); ids += id; result += p.copy(id = id) }
        }
        return result
    }
    fun search(products: List<NutritionProduct>, query: String): List<NutritionProduct> {
        val q = Normalizer.normalize(query.trim(), Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        return (if (q.isBlank()) products else products.filter {
            Normalizer.normalize(it.name, Normalizer.Form.NFKC).lowercase(Locale.ROOT).contains(q)
        }).sortedBy { it.name.lowercase(Locale.ROOT) }
    }
}
