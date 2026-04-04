package com.example.rokidglasses.viewmodel

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GlassesResponseFontScaleTest {

    @Test
    fun `default response font scale percent favors smaller live response text`() {
        assertThat(DEFAULT_RESPONSE_FONT_SCALE_PERCENT).isEqualTo(85)
    }

    @Test
    fun `clampResponseFontScalePercent keeps supported range`() {
        assertThat(clampResponseFontScalePercent(20)).isEqualTo(50)
        assertThat(clampResponseFontScalePercent(100)).isEqualTo(100)
        assertThat(clampResponseFontScalePercent(200)).isEqualTo(140)
    }

    @Test
    fun `responseFontScaleMultiplier reflects clamped percentage`() {
        assertThat(responseFontScaleMultiplier(50)).isEqualTo(0.5f)
        assertThat(responseFontScaleMultiplier(140)).isEqualTo(1.4f)
    }

    @Test
    fun `snapResponseFontScalePercent rounds to nearest 5 percent step`() {
        assertThat(snapResponseFontScalePercent(87)).isEqualTo(85)
        assertThat(snapResponseFontScalePercent(88)).isEqualTo(90)
    }

    @Test
    fun `larger response font scale reduces character budget per page`() {
        assertThat(effectiveMaxCharsPerPage(120, 140)).isLessThan(effectiveMaxCharsPerPage(120, 100))
    }

    @Test
    fun `smaller response font scale increases character budget per page`() {
        assertThat(effectiveMaxCharsPerPage(120, 50)).isGreaterThan(effectiveMaxCharsPerPage(120, 100))
    }

    @Test
    fun `larger response font scale increases page count for same text`() {
        val text = List(80) { "word$it" }.joinToString(" ")

        val defaultPages = paginateResponseText(text, 100)
        val largerPages = paginateResponseText(text, 140)

        assertThat(largerPages.size).isGreaterThan(defaultPages.size)
    }

    @Test
    fun `smaller response font scale reduces page count for same text`() {
        val text = List(80) { "word$it" }.joinToString(" ")

        val defaultPages = paginateResponseText(text, 100)
        val smallerPages = paginateResponseText(text, 50)

        assertThat(smallerPages.size).isAtMost(defaultPages.size)
    }

    @Test
    fun `display render state changes when only font scale changes`() {
        val smaller = resolveDisplayTextRenderState(
            text = "same text",
            responseFontScalePercent = 60,
            useResponseFontScale = true,
            isPaginated = false,
        )
        val larger = resolveDisplayTextRenderState(
            text = "same text",
            responseFontScalePercent = 90,
            useResponseFontScale = true,
            isPaginated = false,
        )

        assertThat(smaller == larger).isFalse()
    }
}
