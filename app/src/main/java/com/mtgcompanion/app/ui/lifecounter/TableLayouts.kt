package com.mtgcompanion.app.ui.lifecounter

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints

/**
 * Which edge of the phone a seat's player sits at, with the phone lying flat in the middle of the
 * table. A tile is turned so the bottom of its content points at that edge — i.e. toward the player
 * reading it.
 */
enum class SeatFacing { BOTTOM, LEFT, TOP, RIGHT }

/** One cell of a table layout. A null [seat] is an empty place at the table, drawn as a blank. */
data class SeatCell(val seat: Int?, val facing: SeatFacing)

data class TableRow(val cells: List<SeatCell>)

data class TableLayout(val id: String, val rows: List<TableRow>) {
    val playerCount: Int get() = rows.sumOf { row -> row.cells.count { it.seat != null } }
}

/**
 * Seat arrangements for 1–10 players. Seats are numbered in turn order, clockwise around the table
 * (bottom seat if any, up the left side, across the top, down the right side), so passing the turn
 * walks around the table the way players actually sit.
 */
object TableLayouts {
    const val DEFAULT_ID = "4-grid"

    private fun full(seat: Int?, facing: SeatFacing) = TableRow(listOf(SeatCell(seat, facing)))
    private fun pair(left: Int?, right: Int?) =
        TableRow(listOf(SeatCell(left, SeatFacing.LEFT), SeatCell(right, SeatFacing.RIGHT)))

    /** [pairs] side-by-side rows, left column seats numbered bottom-up then right column top-down. */
    private fun columns(id: String, pairs: Int, top: Boolean = false, bottom: Boolean = false): TableLayout {
        var next = 1
        val bottomSeat = if (bottom) next++ else null
        val leftSeats = List(pairs) { next++ }.reversed()     // bottom-up on the left side
        val topSeat = if (top) next++ else null
        val rightSeats = List(pairs) { next++ }                 // top-down on the right side
        val rows = buildList {
            if (topSeat != null) add(full(topSeat, SeatFacing.TOP))
            repeat(pairs) { i -> add(pair(leftSeats[i], rightSeats[i])) }
            if (bottomSeat != null) add(full(bottomSeat, SeatFacing.BOTTOM))
        }
        return TableLayout(id, rows)
    }

    val all: List<TableLayout> = listOf(
        TableLayout("1-solo", listOf(full(1, SeatFacing.BOTTOM))),

        TableLayout("2-facing", listOf(full(2, SeatFacing.TOP), full(1, SeatFacing.BOTTOM))),
        TableLayout("2-sides", listOf(pair(1, 2))),

        columns("3-top", pairs = 1, top = true),
        columns("3-bottom", pairs = 1, bottom = true),
        TableLayout("3-gap", listOf(pair(2, 3), pair(1, null))),

        columns(DEFAULT_ID, pairs = 2),
        columns("4-ends", pairs = 1, top = true, bottom = true),

        columns("5-top", pairs = 2, top = true),
        columns("5-bottom", pairs = 2, bottom = true),

        columns("6-grid", pairs = 3),
        columns("6-ends", pairs = 2, top = true, bottom = true),

        columns("7-top", pairs = 3, top = true),
        columns("7-bottom", pairs = 3, bottom = true),

        columns("8-grid", pairs = 4),
        columns("8-ends", pairs = 3, top = true, bottom = true),

        columns("9-top", pairs = 4, top = true),
        columns("9-bottom", pairs = 4, bottom = true),

        columns("10-grid", pairs = 5),
        columns("10-ends", pairs = 4, top = true, bottom = true)
    )

    fun byId(id: String): TableLayout = all.firstOrNull { it.id == id } ?: all.first { it.id == DEFAULT_ID }
}

/**
 * Turns a tile to face its seat. A plain graphicsLayer rotation would keep the tile's original
 * (unrotated) bounds, so a sideways tile would lay its content out tall-and-narrow inside a
 * wide-and-short cell. For LEFT/RIGHT this measures the content with width and height swapped,
 * then rotates it into place, so the content genuinely fills the cell. Pointer input is transformed
 * through the rotation too — a "swipe up" is up from the seated player's point of view.
 */
fun Modifier.faceSeat(facing: SeatFacing): Modifier = when (facing) {
    SeatFacing.BOTTOM -> this
    SeatFacing.TOP -> this.graphicsLayer { rotationZ = 180f }
    SeatFacing.LEFT, SeatFacing.RIGHT -> this.layout { measurable, constraints ->
        val placeable = measurable.measure(
            Constraints(
                minWidth = constraints.minHeight,
                maxWidth = constraints.maxHeight,
                minHeight = constraints.minWidth,
                maxHeight = constraints.maxWidth
            )
        )
        layout(placeable.height, placeable.width) {
            // Rotation is about the content's centre, so first centre the (swapped) content over
            // the cell, then rotate it.
            placeable.placeWithLayer(
                x = (placeable.height - placeable.width) / 2,
                y = (placeable.width - placeable.height) / 2
            ) {
                rotationZ = if (facing == SeatFacing.LEFT) 90f else -90f
            }
        }
    }
}
