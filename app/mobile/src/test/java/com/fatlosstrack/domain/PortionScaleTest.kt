package com.fatlosstrack.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PortionScaleTest {

    // ── Numbers ──────────────────────────────────────────────────────────

    @Test
    fun `scale rounds to nearest int`() {
        assertEquals(300, PortionScale.scale(200, 1.5))
        assertEquals(63, PortionScale.scale(125, 0.5)) // 62.5 -> 63
        assertEquals(0, PortionScale.scale(0, 2.0))
    }

    @Test
    fun `parse accepts decimal comma and rejects out of range`() {
        assertEquals(1.25, PortionScale.parse("1,25")!!, 0.0)
        assertEquals(1.33, PortionScale.parse(" 1.333 ")!!, 0.0)
        assertNull(PortionScale.parse("0"))
        assertNull(PortionScale.parse("10.5"))
        assertNull(PortionScale.parse("abc"))
    }

    @Test
    fun `format drops trailing zeros`() {
        assertEquals("2", PortionScale.format(2.0))
        assertEquals("1.5", PortionScale.format(1.5))
        assertEquals("0.25", PortionScale.format(0.25))
    }

    // ── Portion annotation ───────────────────────────────────────────────

    @Test
    fun `annotate appends multiplier`() {
        assertEquals("200g ×1.5", PortionScale.annotatePortion("200g", 1.5))
        assertEquals("×2", PortionScale.annotatePortion("", 2.0))
        assertEquals("200g", PortionScale.annotatePortion("200g", 1.0))
    }

    @Test
    fun `annotate folds into an existing multiplier`() {
        assertEquals("200g ×3", PortionScale.annotatePortion("200g ×1.5", 2.0))
        assertEquals("200g", PortionScale.annotatePortion("200g ×2", 0.5))
    }

    // ── itemsJson ────────────────────────────────────────────────────────

    @Test
    fun `scaleItemsJson scales nutrients and keeps other fields`() {
        val json = """[{"name":"Rice","portion":"200g","calories":260,"protein_g":5,"carbs_g":57,"fat_g":1}]"""
        val item = Json.parseToJsonElement(PortionScale.scaleItemsJson(json, 1.5)!!)
            .jsonArray[0].jsonObject
        assertEquals("Rice", item["name"]!!.jsonPrimitive.content)
        assertEquals("200g ×1.5", item["portion"]!!.jsonPrimitive.content)
        assertEquals(390, item["calories"]!!.jsonPrimitive.int)
        assertEquals(8, item["protein_g"]!!.jsonPrimitive.int) // 7.5 -> 8
        assertEquals(86, item["carbs_g"]!!.jsonPrimitive.int) // 85.5 -> 86
        assertEquals(2, item["fat_g"]!!.jsonPrimitive.int) // 1.5 -> 2
    }

    @Test
    fun `scaleItemsJson leaves 1x, null and invalid json untouched`() {
        val json = """[{"name":"Rice","calories":260}]"""
        assertSame(json, PortionScale.scaleItemsJson(json, 1.0))
        assertNull(PortionScale.scaleItemsJson(null, 2.0))
        assertEquals("not json", PortionScale.scaleItemsJson("not json", 2.0))
    }
}
