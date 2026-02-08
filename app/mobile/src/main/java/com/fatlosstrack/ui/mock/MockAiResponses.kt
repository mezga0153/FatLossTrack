package com.fatlosstrack.ui.mock

/**
 * Mock AI analysis responses for meal photo and meal suggestion flows.
 */
object MockAiResponses {

    // ── Nutrition row ──
    data class NutritionRow(
        val name: String,
        val amount: String,
        val unit: String,
    )

    // ── Analysed meal item ──
    data class MealItem(
        val name: String,
        val portion: String,
        val nutrition: List<NutritionRow>,
    )

    // ── Full analysis result ──
    data class AnalysisResult(
        val description: String,
        val items: List<MealItem>,
        val totalCalories: Int,
        val aiNote: String,
    )

    // ── Log-meal responses (what you just ate) ──

    val logMealResults = listOf(
        AnalysisResult(
            description = "Grilled chicken breast with roasted vegetables and brown rice.",
            items = listOf(
                MealItem(
                    name = "Grilled chicken breast",
                    portion = "~180 g",
                    nutrition = listOf(
                        NutritionRow("Calories", "280", "kcal"),
                        NutritionRow("Protein", "42", "g"),
                        NutritionRow("Fat", "8", "g"),
                        NutritionRow("Carbs", "0", "g"),
                    ),
                ),
                MealItem(
                    name = "Roasted vegetables",
                    portion = "~150 g",
                    nutrition = listOf(
                        NutritionRow("Calories", "95", "kcal"),
                        NutritionRow("Protein", "3", "g"),
                        NutritionRow("Fat", "4", "g"),
                        NutritionRow("Carbs", "12", "g"),
                    ),
                ),
                MealItem(
                    name = "Brown rice",
                    portion = "~120 g cooked",
                    nutrition = listOf(
                        NutritionRow("Calories", "140", "kcal"),
                        NutritionRow("Protein", "3", "g"),
                        NutritionRow("Fat", "1", "g"),
                        NutritionRow("Carbs", "30", "g"),
                    ),
                ),
            ),
            totalCalories = 515,
            aiNote = "Solid meal. High protein, moderate carbs. This fits your deficit target well.",
        ),
        AnalysisResult(
            description = "Pepperoni pizza — 2 large slices with a side of garlic bread.",
            items = listOf(
                MealItem(
                    name = "Pepperoni pizza (2 slices)",
                    portion = "~260 g",
                    nutrition = listOf(
                        NutritionRow("Calories", "580", "kcal"),
                        NutritionRow("Protein", "22", "g"),
                        NutritionRow("Fat", "26", "g"),
                        NutritionRow("Carbs", "62", "g"),
                    ),
                ),
                MealItem(
                    name = "Garlic bread",
                    portion = "~80 g",
                    nutrition = listOf(
                        NutritionRow("Calories", "210", "kcal"),
                        NutritionRow("Protein", "4", "g"),
                        NutritionRow("Fat", "9", "g"),
                        NutritionRow("Carbs", "28", "g"),
                    ),
                ),
            ),
            totalCalories = 790,
            aiNote = "That's roughly 45% of your daily budget in one sitting. Not catastrophic — just plan a lighter dinner.",
        ),
    )

    // ── Suggest-meal responses (what you could make) ──

    val suggestMealResults = listOf(
        AnalysisResult(
            description = "Based on what I see: eggs, spinach, feta, and tomatoes — here's a quick option.",
            items = listOf(
                MealItem(
                    name = "Spinach & feta omelette",
                    portion = "3 eggs + 40g feta + handful spinach",
                    nutrition = listOf(
                        NutritionRow("Calories", "350", "kcal"),
                        NutritionRow("Protein", "26", "g"),
                        NutritionRow("Fat", "24", "g"),
                        NutritionRow("Carbs", "4", "g"),
                    ),
                ),
                MealItem(
                    name = "Tomato side salad",
                    portion = "~100 g with olive oil drizzle",
                    nutrition = listOf(
                        NutritionRow("Calories", "55", "kcal"),
                        NutritionRow("Protein", "1", "g"),
                        NutritionRow("Fat", "3", "g"),
                        NutritionRow("Carbs", "6", "g"),
                    ),
                ),
            ),
            totalCalories = 405,
            aiNote = "High protein, low carb. Keeps you in deficit and takes 10 minutes.",
        ),
        AnalysisResult(
            description = "I see chicken thighs, rice, broccoli, and soy sauce — try this.",
            items = listOf(
                MealItem(
                    name = "Soy-glazed chicken thigh stir-fry",
                    portion = "~200 g chicken + 100 g broccoli",
                    nutrition = listOf(
                        NutritionRow("Calories", "320", "kcal"),
                        NutritionRow("Protein", "34", "g"),
                        NutritionRow("Fat", "14", "g"),
                        NutritionRow("Carbs", "8", "g"),
                    ),
                ),
                MealItem(
                    name = "Steamed rice",
                    portion = "~130 g cooked",
                    nutrition = listOf(
                        NutritionRow("Calories", "170", "kcal"),
                        NutritionRow("Protein", "3", "g"),
                        NutritionRow("Fat", "0", "g"),
                        NutritionRow("Carbs", "37", "g"),
                    ),
                ),
            ),
            totalCalories = 490,
            aiNote = "Good balance. You'll stay ~60 kcal under your meal budget.",
        ),
    )
}
