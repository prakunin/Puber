@file:Suppress("MatchingDeclarationName") // the file is named for the function, PlotFit is its result

package com.kino.puber.ui.feature.details.component

internal data class PlotFit(val widthPx: Int, val heightPx: Int)

/**
 * The widths a plot may take: [widest] down in steps of [step] while the next one is not below
 * [floor], then [floor] itself if the last step stopped above it. Same rule as the design stand's
 * `plotSteps` (docs/design/content/index.src.html).
 */
internal fun plotWidthSteps(widest: Int, step: Int, floor: Int): List<Int> {
    require(step > 0) { "step must be positive: $step" }
    val steps = mutableListOf(widest)
    while (steps.last() - step >= floor) steps += steps.last() - step
    if (steps.last() > floor) steps += floor
    return steps
}

/**
 * Picks the narrowest step that draws the plot in no more height than the widest step does.
 *
 * The block height is always the widest step's text height (clamped to [minHeightPx] and
 * [budgetPx]), never the narrower text's: a narrower plot must not move the fact lines and the
 * action row below it. A plot that already overflows [budgetPx] at the widest step scrolls there
 * and keeps that width.
 *
 * @param stepsPx candidate widths, widest first.
 * @param heightAt block height (text plus border padding) at a width.
 */
internal fun choosePlotWidth(
    stepsPx: List<Int>,
    floorPx: Int,
    budgetPx: Int,
    minHeightPx: Int,
    heightAt: (Int) -> Int,
): PlotFit {
    val widest = stepsPx.first()
    val natural = heightAt(widest)
    if (natural > budgetPx) return PlotFit(widest, budgetPx)

    val height = natural.coerceIn(minOf(minHeightPx, budgetPx), budgetPx)
    val narrower = stepsPx.drop(1).filter { it >= floorPx }
    val chosen = narrower.asReversed().firstOrNull { heightAt(it) <= height }
    return PlotFit(chosen ?: widest, height)
}
