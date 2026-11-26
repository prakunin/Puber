package com.kino.puber.ui.feature.details.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PlotWidthTest {

    private val steps = listOf(590, 520, 460)

    private class Fake(private val lines: Map<Int, Int>) {
        val asked = mutableListOf<Int>()
        fun heightAt(width: Int): Int {
            asked += width
            return lines.getValue(width) * LINE + 2
        }
    }

    private fun choose(
        lines: Map<Int, Int>,
        steps: List<Int> = this.steps,
        budget: Int = 200,
        minHeight: Int = 110,
        fake: Fake = Fake(lines),
    ): PlotFit = choosePlotWidth(
        stepsPx = steps,
        floorPx = 460,
        budgetPx = budget,
        minHeightPx = minHeight,
        heightAt = fake::heightAt,
    )

    @Test
    fun choosePlotWidth_returnsNarrowestStepThatKeepsTheHeight_whenPlotIsShort() {
        val fit = choose(mapOf(590 to 3, 520 to 4, 460 to 4))

        assertEquals(PlotFit(460, 110), fit)
    }

    @Test
    fun choosePlotWidth_acceptsStepAtExactlyTheHeight_andRejectsOneLineMore() {
        val fit = choose(mapOf(590 to 5, 520 to 6, 460 to 7))

        assertEquals(PlotFit(520, 110), fit)
    }

    @Test
    fun choosePlotWidth_neverGoesBelowFloor_whenTextFitsEvenNarrower() {
        val fit = choose(
            lines = mapOf(590 to 3, 520 to 3, 460 to 3, 400 to 3),
            steps = listOf(590, 520, 460, 400),
        )

        assertEquals(460, fit.widthPx)
    }

    @Test
    fun choosePlotWidth_heightEqualsHeightAtWidestStep_forEveryCase() {
        data class Case(val lines: Map<Int, Int>, val budget: Int, val expectedHeight: Int)

        // 18 px a line plus 2 px border at the widest step: 3 lines = 56, 8 = 146, 9 = 164.
        val cases = listOf(
            Case(mapOf(590 to 3, 520 to 4, 460 to 4), 200, 110),
            Case(mapOf(590 to 5, 520 to 6, 460 to 7), 200, 110),
            Case(mapOf(590 to 8, 520 to 9, 460 to 10), 200, 146),
            Case(mapOf(590 to 20, 520 to 22, 460 to 25), 200, 200),
            Case(mapOf(590 to 2, 520 to 2, 460 to 2), 108, 108),
            Case(mapOf(590 to 9, 520 to 9, 460 to 9), 108, 108),
        )
        cases.forEach { case ->
            assertEquals(case.expectedHeight, choose(case.lines, budget = case.budget).heightPx, "$case")
        }
    }

    @Test
    fun choosePlotWidth_keepsHeightWithinBudget_whenBudgetIsBelowMinHeight() {
        val fit = choose(mapOf(590 to 2, 520 to 2, 460 to 2), budget = 108)

        assertEquals(108, fit.heightPx)
    }

    @Test
    fun choosePlotWidth_returnsWidestStep_whenPlotOverflowsAtWidest() {
        val fit = choose(mapOf(590 to 20, 520 to 22, 460 to 25))

        assertEquals(PlotFit(590, 200), fit)
    }

    @Test
    fun choosePlotWidth_queriesOnlyTheWidestStep_whenPlotOverflowsAtWidest() {
        val lines = mapOf(590 to 20, 520 to 22, 460 to 25)
        val fake = Fake(lines)

        choose(lines, fake = fake)

        assertEquals(listOf(590), fake.asked)
    }

    @Test
    fun plotWidthSteps_endsAtTheFloor_whenTheLastStepFallsBelowIt() {
        assertEquals(listOf(590, 520, 460), plotWidthSteps(590, 70, 460))
    }

    @Test
    fun plotWidthSteps_doesNotRepeatTheFloor_whenAStepLandsOnIt() {
        assertEquals(listOf(590, 530, 470, 410), plotWidthSteps(590, 60, 410))
    }

    @Test
    fun plotWidthSteps_isTheWidestOnly_whenTheFloorIsNotBelowIt() {
        assertEquals(listOf(400), plotWidthSteps(400, 70, 460))
    }

    private companion object {
        const val LINE = 18
    }
}
