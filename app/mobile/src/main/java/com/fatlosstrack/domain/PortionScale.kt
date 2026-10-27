package com.fatlosstrack.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import java.math.BigDecimal
import kotlin.math.roundToInt

/**
 * Local portion scaling for meals (½×, 1.5×, 2× or a custom multiplier) — no AI call.
 * Callers always scale from the unscaled values, so repeated taps don't compound
 * and going back to 1× restores the original numbers exactly.
 */
object PortionScale {

    const val MIN = 0.1
    const val MAX = 10.0

    /** Quick-pick multipliers shown as chips. */
    val PRESETS = listOf(0.5, 1.0, 1.5, 2.0)

    private val NUTRIENT_KEYS = setOf("calories", "protein_g", "carbs_g", "fat_g")

    /** Trailing "×1.5" annotation left on a portion by an earlier scale. */
    private val ANNOTATION = Regex("""\s*×(\d+(?:\.\d+)?)$""")

    fun scale(value: Int, factor: Double): Int = (value * factor).roundToInt()

    /** Round to two decimals so float noise (1.4999…) never reaches labels or comparisons. */
    fun normalize(factor: Double): Double = (factor * 100).roundToInt() / 100.0

    /** Parse user input ("1.25" or "1,25"); null unless it's a number in [MIN]..[MAX]. */
    fun parse(input: String): Double? =
        input.trim().replace(',', '.').toDoubleOrNull()
            ?.let(::normalize)
            ?.takeIf { it in MIN..MAX }

    /** Shortest decimal form: 1.5 → "1.5", 2.0 → "2", 0.25 → "0.25". */
    fun format(factor: Double): String =
        BigDecimal.valueOf(normalize(factor)).stripTrailingZeros().toPlainString()

    /**
     * Annotate a portion with the multiplier ("200g" → "200g ×1.5"). An existing annotation is
     * folded in ("200g ×1.5" scaled 2× → "200g ×3") and dropped when the result is 1×.
     */
    fun annotatePortion(portion: String, factor: Double): String {
        val match = ANNOTATION.find(portion)
        val base = if (match != null) portion.substring(0, match.range.first) else portion
        val combined = normalize((match?.groupValues?.get(1)?.toDoubleOrNull() ?: 1.0) * factor)
        return when {
            combined == 1.0 -> base
            base.isBlank() -> "×${format(combined)}"
            else -> "$base ×${format(combined)}"
        }
    }

    /**
     * Scale calories/macros and annotate portions in a stored `itemsJson` array.
     * Other fields are kept as-is; unparseable JSON is returned unchanged.
     */
    fun scaleItemsJson(itemsJson: String?, factor: Double): String? {
        if (itemsJson.isNullOrBlank() || factor == 1.0) return itemsJson
        return try {
            JsonArray(
                Json.parseToJsonElement(itemsJson).jsonArray.map { el ->
                    val obj = el as? JsonObject ?: return@map el
                    JsonObject(
                        obj.mapValues { (key, value) ->
                            val primitive = value as? JsonPrimitive
                            when (key) {
                                in NUTRIENT_KEYS -> primitive?.doubleOrNull
                                    ?.let { JsonPrimitive((it * factor).roundToInt()) } ?: value
                                "portion" -> primitive?.contentOrNull
                                    ?.let { JsonPrimitive(annotatePortion(it, factor)) } ?: value
                                else -> value
                            }
                        },
                    )
                },
            ).toString()
        } catch (_: Exception) {
            itemsJson
        }
    }
}
