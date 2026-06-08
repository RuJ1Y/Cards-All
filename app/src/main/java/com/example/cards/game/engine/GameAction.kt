package com.example.cards.game.engine

import com.example.cards.game.model.SkillType

sealed interface GameAction {

    data class UseCharacterSkill(
        val actorPlayerId: String,
        val actorCharacterIndex: Int,
        val skillType: SkillType,
        val targetPlayerId: String,
        val targetCharacterIndex: Int?
    ) : GameAction

    data class UseCard(
        val actorPlayerId: String,
        val handIndex: Int,
        val targetPlayerId: String,
        val targetCharacterIndex: Int?
    ) : GameAction

    data class DiscardCard(
        val actorPlayerId: String,
        val handIndex: Int
    ) : GameAction

    data class EndTurn(
        val actorPlayerId: String
    ) : GameAction
}

