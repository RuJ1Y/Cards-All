package com.example.cards.game.repo

import com.example.cards.game.model.CharacterTemplate
import com.example.cards.game.model.EffectType
import com.example.cards.game.model.SkillDefinition
import com.example.cards.game.model.SkillEffect
import com.example.cards.game.model.SkillType
import com.example.cards.game.model.TargetCamp
import com.example.cards.game.model.TargetSlot

interface CharacterRepository {
    fun all(): List<CharacterTemplate>
    fun getById(id: String): CharacterTemplate
}

class DefaultCharacterRepository : CharacterRepository {

    // 初始三角色 + 当前抽奖角色（Saber）。
    private val templates = listOf(
        CharacterTemplate(
            id = "swordsman",
            name = "剑士",
            role = "输出",
            maxHp = 25,
            ultimateRequired = 5,
            normalSkill = SkillDefinition(
                id = "swordsman_n",
                name = "平斩",
                description = "指定敌方一名角色造成1点伤害",
                type = SkillType.NORMAL,
                targetCamp = TargetCamp.ENEMY,
                targetSlot = TargetSlot.SINGLE,
                effects = listOf(SkillEffect(EffectType.DAMAGE, 1))
            ),
            powerSkill = SkillDefinition(
                id = "swordsman_p",
                name = "怒斩",
                description = "对敌方两位角色各造成2点伤害",
                type = SkillType.POWER,
                targetCamp = TargetCamp.ENEMY,
                targetSlot = TargetSlot.ALL,
                effects = listOf(SkillEffect(EffectType.DAMAGE, 2))
            ),
            ultimateSkill = SkillDefinition(
                id = "swordsman_u",
                name = "剑士的荣耀",
                description = "指定敌方一名角色造成4点伤害，并为自己提供3点护盾",
                type = SkillType.ULTIMATE,
                targetCamp = TargetCamp.ENEMY,
                targetSlot = TargetSlot.SINGLE,
                effects = listOf(SkillEffect(EffectType.DAMAGE, 4))
            )
        ),
        CharacterTemplate(
            id = "mage",
            name = "法师",
            role = "输出",
            maxHp = 20,
            ultimateRequired = 6,
            normalSkill = SkillDefinition(
                id = "mage_n",
                name = "水炮",
                description = "指定敌方一名角色造成1点伤害",
                type = SkillType.NORMAL,
                targetCamp = TargetCamp.ENEMY,
                targetSlot = TargetSlot.SINGLE,
                effects = listOf(SkillEffect(EffectType.DAMAGE, 1))
            ),
            powerSkill = SkillDefinition(
                id = "mage_p",
                name = "火球",
                description = "对敌方两位角色各造成3点伤害，并附加2回合1点伤害的灼烧buff",
                type = SkillType.POWER,
                targetCamp = TargetCamp.ENEMY,
                targetSlot = TargetSlot.ALL,
                effects = listOf(SkillEffect(EffectType.DAMAGE, 3))
            ),
            ultimateSkill = SkillDefinition(
                id = "mage_u",
                name = "魔力超频",
                description = "为自己提供2回合2点伤害的增伤buff（灼烧不受影响）",
                type = SkillType.ULTIMATE,
                targetCamp = TargetCamp.SELF,
                targetSlot = TargetSlot.SINGLE,
                effects = emptyList()
            )
        ),
        CharacterTemplate(
            id = "priest",
            name = "牧师",
            role = "治疗",
            maxHp = 15,
            ultimateRequired = 4,
            normalSkill = SkillDefinition(
                id = "priest_n",
                name = "诅咒",
                description = "指定敌方一名角色造成1点伤害",
                type = SkillType.NORMAL,
                targetCamp = TargetCamp.ENEMY,
                targetSlot = TargetSlot.SINGLE,
                effects = listOf(SkillEffect(EffectType.DAMAGE, 1))
            ),
            powerSkill = SkillDefinition(
                id = "priest_p",
                name = "祝福",
                description = "指定我方一名角色提供治疗4点HP",
                type = SkillType.POWER,
                targetCamp = TargetCamp.ALLY,
                targetSlot = TargetSlot.SINGLE,
                effects = listOf(SkillEffect(EffectType.HEAL, 4))
            ),
            ultimateSkill = SkillDefinition(
                id = "priest_u",
                name = "我不是放牛的",
                description = "立即为自己与队友提供3点治疗，并附加2回合2点治疗的buff",
                type = SkillType.ULTIMATE,
                targetCamp = TargetCamp.ALLY,
                targetSlot = TargetSlot.ALL,
                effects = listOf(SkillEffect(EffectType.HEAL, 3))
            )
        ),
        CharacterTemplate(
            id = "saber",
            name = "Fate / saber",
            role = "输出",
            maxHp = 25,
            ultimateRequired = 6,
            normalSkill = SkillDefinition(
                id = "saber_n",
                name = "骑士不徒手而亡",
                description = "指定敌方一名角色造成1点伤害",
                type = SkillType.NORMAL,
                targetCamp = TargetCamp.ENEMY,
                targetSlot = TargetSlot.SINGLE,
                effects = listOf(SkillEffect(EffectType.DAMAGE, 1))
            ),
            powerSkill = SkillDefinition(
                id = "saber_p",
                name = "魔力放出",
                description = "对敌方一位角色造成2点伤害，对另一名角色造成1点伤害",
                type = SkillType.POWER,
                targetCamp = TargetCamp.ENEMY,
                targetSlot = TargetSlot.SINGLE,
                effects = listOf(SkillEffect(EffectType.DAMAGE, 2))
            ),
            ultimateSkill = SkillDefinition(
                id = "saber_u",
                name = "EXcalibur",
                description = "指定敌方一名角色造成5点伤害，另一名角色造成2点伤害",
                type = SkillType.ULTIMATE,
                targetCamp = TargetCamp.ENEMY,
                targetSlot = TargetSlot.SINGLE,
                effects = listOf(SkillEffect(EffectType.DAMAGE, 5))
            )
        )
    )

    private val byId = templates.associateBy { it.id }

    override fun all(): List<CharacterTemplate> = templates

    override fun getById(id: String): CharacterTemplate {
        return requireNotNull(byId[id]) { "角色不存在: $id" }
    }
}
