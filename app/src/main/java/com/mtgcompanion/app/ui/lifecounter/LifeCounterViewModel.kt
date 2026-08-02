package com.mtgcompanion.app.ui.lifecounter

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayerLife(val id: Int, val life: Int)

/**
 * Session-only multiplayer life tracker — no persistence, since a life total only matters for the
 * game currently being played. Held in a ViewModel purely so a config-change (rotation) during a
 * long Commander game doesn't reset everyone back to their starting life.
 */
class LifeCounterViewModel : ViewModel() {
    private val _startingLife = MutableStateFlow(40)
    val startingLife: StateFlow<Int> = _startingLife.asStateFlow()

    private val _players = MutableStateFlow(defaultPlayers(4, 40))
    val players: StateFlow<List<PlayerLife>> = _players.asStateFlow()

    fun setPlayerCount(count: Int) {
        _players.value = defaultPlayers(count, _startingLife.value)
    }

    fun setStartingLife(value: Int) {
        _startingLife.value = value
        _players.value = _players.value.map { it.copy(life = value) }
    }

    fun adjust(playerId: Int, delta: Int) {
        _players.value = _players.value.map { if (it.id == playerId) it.copy(life = it.life + delta) else it }
    }

    fun resetAll() {
        _players.value = defaultPlayers(_players.value.size, _startingLife.value)
    }

    private companion object {
        fun defaultPlayers(count: Int, life: Int) = (1..count).map { PlayerLife(it, life) }
    }
}
