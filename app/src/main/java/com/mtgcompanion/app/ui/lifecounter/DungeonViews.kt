package com.mtgcompanion.app.ui.lifecounter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// A player's dungeon: the card as a small map with their room marked, the rooms they can move to,
// and the dungeons they can start. Used on a tile's options card and on a player's remote, in the
// ink of wherever it's shown. The logic is Dungeons.kt; the web app's src/lifecounter/DungeonMap.tsx.

/** The colours a dungeon is drawn in where it's shown. */
data class DungeonInk(val ink: Color, val muted: Color, val line: Color, val accent: Color)

/** The dungeon card as a map: rooms by row, lines down to the rooms each leads to, [state]'s room marked. */
@Composable
fun DungeonMap(state: DungeonState, ink: DungeonInk, modifier: Modifier = Modifier) {
    val dungeon = dungeonById(state.dungeon) ?: return
    val spots = dungeonLayout(dungeon)
    val here = roomOf(state)
    val next = if (inDungeon(state)) here?.next.orEmpty().toSet() else emptySet()
    val rows = (spots.maxOf { it.row } + 1).toFloat()
    val measurer = rememberTextMeasurer()
    Canvas(
        modifier
            .fillMaxWidth()
            .height((rows * 46).dp)
            .semantics { contentDescription = "${dungeon.name}: in ${here?.name ?: "no room"}" }
    ) {
        val rowH = size.height / rows
        fun at(id: String): Offset {
            val s = spots.first { it.id == id }
            return Offset(14.dp.toPx() + s.x.toFloat() * (size.width - 28.dp.toPx()), rowH / 2 + s.row.toFloat() * rowH - 8.dp.toPx())
        }
        for (r in dungeon.rooms) for (n in r.next) {
            val open = r.id == state.room
            drawLine(if (open) ink.accent else ink.muted.copy(alpha = 0.6f), at(r.id), at(n), strokeWidth = (if (open) 2.5f else 1.5f) * density)
        }
        for (r in dungeon.rooms) {
            val p = at(r.id)
            val isHere = r.id == state.room
            val radius = (if (isHere) 8 else 6).dp.toPx()
            drawCircle(if (isHere) ink.accent else ink.line, radius, p)
            drawCircle(if (isHere) ink.ink else if (r.id in next) ink.accent else ink.muted, radius, p, style = Stroke(width = (if (r.id in next) 2.5f else 1.5f) * density))
            val label = measurer.measure(
                r.name,
                TextStyle(color = if (isHere) ink.ink else ink.muted, fontSize = 10.sp, fontWeight = if (isHere) FontWeight.Bold else FontWeight.Medium)
            )
            drawText(label, topLeft = Offset(p.x - label.size.width / 2f, p.y + radius + 2.dp.toPx()))
        }
    }
}

/**
 * Venturing for one player: where they are and what it does, the rooms they can go to next, or the
 * dungeons they can start — Undercity when they have the initiative (taking it, and each upkeep
 * while they hold it, ventures into Undercity).
 */
@Composable
fun VentureSection(
    dungeon: DungeonState?,
    completed: Int,
    hasInitiative: Boolean,
    ink: DungeonInk,
    onVenture: (to: String, undercity: Boolean) -> Unit,
    onLeave: () -> Unit,
    onCompleted: ((Int) -> Unit)? = null
) {
    val here = roomOf(dungeon)
    val going = inDungeon(dungeon)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (dungeon != null && here != null) {
            Text(dungeonById(dungeon.dungeon)?.name.orEmpty() + if (dungeonDone(dungeon)) " · completed" else "", color = ink.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("${here.name}: ${here.text}", color = ink.muted, fontSize = 13.sp)
            DungeonMap(dungeon, ink)
        }
        Text(
            when {
                going -> "Venture into the dungeon: go to"
                dungeon != null -> "Venture into a new dungeon"
                else -> "Venture into the dungeon"
            },
            color = ink.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold
        )
        ventureOptions(dungeon, false).forEach { id ->
            val room = if (going) dungeonById(dungeon!!.dungeon)?.rooms?.firstOrNull { it.id == id } else null
            VentureOption(room?.name ?: dungeonById(id)?.name.orEmpty(), room?.text, ink, ink.line) { onVenture(id, false) }
        }
        if (!going && hasInitiative) {
            VentureOption("Undercity", "Venture into Undercity — for the initiative", ink, ink.accent) { onVenture("undercity", true) }
        }
        if (going && hasInitiative) Text("With the initiative, venturing into Undercity moves on in this dungeon.", color = ink.muted, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Dungeons completed: $completed", color = ink.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            onCompleted?.let { change ->
                SmallPill("−", ink) { change(-1) }
                SmallPill("+", ink) { change(1) }
            }
            if (going) SmallPill("Leave the dungeon", ink, onClick = onLeave)
        }
    }
}

@Composable
private fun VentureOption(title: String, text: String?, ink: DungeonInk, border: Color, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics { role = Role.Button }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(title, color = ink.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        text?.let { Text(it, color = ink.muted, fontSize = 13.sp) }
    }
}

@Composable
private fun SmallPill(label: String, ink: DungeonInk, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, ink.line, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .semantics { role = Role.Button }
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(label, color = ink.ink, fontSize = 14.sp)
    }
}
