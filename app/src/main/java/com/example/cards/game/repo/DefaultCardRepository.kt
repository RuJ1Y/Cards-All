package com.example.cards.game.repo

import com.example.cards.game.model.CardDefinition
import com.example.cards.game.model.EffectType
import com.example.cards.game.model.SkillDefinition
import com.example.cards.game.model.SkillEffect
import com.example.cards.game.model.SkillType
import com.example.cards.game.model.TargetCamp
import com.example.cards.game.model.TargetSlot
import kotlin.random.Random

interface CardRepository {
    fun all(): List<CardDefinition>
    fun getById(id: String): CardDefinition
    fun createDeck(seed: Int = Random.nextInt()): List<String>
}

class DefaultCardRepository : CardRepository {

    private val cards: List<CardDefinition> = listOf(
        card(
            id = "c01_hamburger",
            name = "汉堡包",
            description = "指定我方一名角色回复5HP",
            targetCamp = TargetCamp.ALLY,
            targetSlot = TargetSlot.SINGLE,
            effects = listOf(SkillEffect(EffectType.HEAL, 5))
        ),
        card(
            id = "c02_grenade",
            name = "手雷",
            description = "使敌方全体扣除3HP",
            targetCamp = TargetCamp.ENEMY,
            targetSlot = TargetSlot.ALL,
            effects = listOf(SkillEffect(EffectType.DAMAGE, 3))
        ),
        card(
            id = "c03_coffee",
            name = "咖啡",
            description = "指定我方一名角色提供2回合2点增伤buff",
            targetCamp = TargetCamp.ALLY,
            targetSlot = TargetSlot.SINGLE,
            effects = emptyList()
        ),
        card(
            id = "c04_tower_never_fall",
            name = "塔不灭！塔不灭！",
            description = "指定敌方一名角色提供1回合沉默buff",
            targetCamp = TargetCamp.ENEMY,
            targetSlot = TargetSlot.SINGLE,
            effects = emptyList()
        ),
        card(
            id = "c05_again_once",
            name = "再来一次~",
            description = "指定我方一名角色立即行动一次，不消耗回合次数",
            targetCamp = TargetCamp.ALLY,
            targetSlot = TargetSlot.SINGLE,
            effects = emptyList()
        ),
        card(
            id = "c06_mini_burger",
            name = "M汉堡（怎么这么小）",
            description = "指定我方一名角色回复3HP",
            targetCamp = TargetCamp.ALLY,
            targetSlot = TargetSlot.SINGLE,
            effects = listOf(SkillEffect(EffectType.HEAL, 3))
        ),
        card(
            id = "c07_lottery",
            name = "福利彩票",
            description = "10%概率回满cost",
            targetCamp = TargetCamp.SELF,
            targetSlot = TargetSlot.SINGLE,
            effects = emptyList()
        ),
        card(
            id = "c08_one_for_two",
            name = "一换二",
            description = "立即从牌堆中抽取两张卡牌",
            targetCamp = TargetCamp.SELF,
            targetSlot = TargetSlot.SINGLE,
            effects = emptyList()
        ),
        card(
            id = "c09_little_ivan",
            name = "小伊万",
            description = "全场所有角色扣除10hp",
            targetCamp = TargetCamp.SELF,
            targetSlot = TargetSlot.SINGLE,
            effects = emptyList()
        ),
        card(
            id = "c10_world_peace",
            name = "世界和平",
            description = "全场所有角色恢复5hp",
            targetCamp = TargetCamp.SELF,
            targetSlot = TargetSlot.SINGLE,
            effects = emptyList()
        ),
        card(
            id = "c11_steel_plate",
            name = "加装钢板",
            description = "指定我方一名角色获取3护盾",
            targetCamp = TargetCamp.ALLY,
            targetSlot = TargetSlot.SINGLE,
            effects = listOf(SkillEffect(EffectType.SHIELD, 3))
        ),
        card(
            id = "c12_stun_gun",
            name = "电击枪",
            description = "指定敌方一名角色获取1回合硬控buff",
            targetCamp = TargetCamp.ENEMY,
            targetSlot = TargetSlot.SINGLE,
            effects = emptyList()
        ),
        card(
            id = "c13_fufu",
            name = "fufu",
            description = "你获得了一个fufu玩哦，然后什么也没有发生",
            targetCamp = TargetCamp.SELF,
            targetSlot = TargetSlot.SINGLE,
            effects = emptyList()
        ),
        card(
            id = "c14_revive",
            name = "秽土转生",
            description = "使hp为0的角色复活并且hp恢复到10",
            targetCamp = TargetCamp.ALLY,
            targetSlot = TargetSlot.SINGLE,
            effects = emptyList()
        ),
        card(
            id = "c15_turbo_boost",
            name = "turbo boost",
            description = "指定一名我方角色获取2回合2点增伤buff",
            targetCamp = TargetCamp.ALLY,
            targetSlot = TargetSlot.SINGLE,
            effects = emptyList()
        ),
        card(
            id = "c16_million_pound",
            name = "百万英镑",
            description = "指定一名我方角色下一次释放特殊技能不消耗cost",
            targetCamp = TargetCamp.ALLY,
            targetSlot = TargetSlot.SINGLE,
            effects = emptyList()
        ),
        card(
            id = "c17_roast_chicken",
            name = "烧鸡",
            description = "指定我方一名角色回复6HP",
            targetCamp = TargetCamp.ALLY,
            targetSlot = TargetSlot.SINGLE,
            effects = listOf(SkillEffect(EffectType.HEAL, 6))
        ),
        card(
            id = "c18_c0_boom",
            name = "C0自爆",
            description = "在场全体角色扣除10HP",
            targetCamp = TargetCamp.SELF,
            targetSlot = TargetSlot.SINGLE,
            effects = emptyList()
        ),
        card(
            id = "c19_rho_aias",
            name = "炽天覆七重圆环",
            description = "指定一名我方角色使其受到的下一次伤害变为1",
            targetCamp = TargetCamp.ALLY,
            targetSlot = TargetSlot.SINGLE,
            effects = emptyList()
        ),
        card(
            id = "c20_mingdao",
            name = "名刀司命",
            description = "指定一名我方角色获取特殊buff，可免疫一次致命攻击",
            targetCamp = TargetCamp.ALLY,
            targetSlot = TargetSlot.SINGLE,
            effects = emptyList()
        ),
        card(
            id = "c21_water",
            name = "Water",
            description = "指定我方一名角色回复1HP",
            targetCamp = TargetCamp.ALLY,
            targetSlot = TargetSlot.SINGLE,
            effects = listOf(SkillEffect(EffectType.HEAL, 1))
        ),
        card(
            id = "c22_dual_doctor",
            name = "善恶怪医",
            description = "有50%概率使我方全体恢复5HP，50%概率使我方全体扣除5HP",
            targetCamp = TargetCamp.ALLY,
            targetSlot = TargetSlot.ALL,
            effects = emptyList()
        ),
        card(
            id = "c23_paraquat",
            name = "百草枯",
            description = "指定敌方一名角色受到持续2回合3点伤害的中毒buff",
            targetCamp = TargetCamp.ENEMY,
            targetSlot = TargetSlot.SINGLE,
            effects = emptyList()
        ),
        card(
            id = "c24_man",
            name = "Man！",
            description = "指定一名对方角色扣除3HP",
            targetCamp = TargetCamp.ENEMY,
            targetSlot = TargetSlot.SINGLE,
            effects = listOf(SkillEffect(EffectType.DAMAGE, 3))
        )
    )

    private val byId = cards.associateBy { it.id }

    // 强度分档：60% 弱卡，30% 强卡，10% 毁灭卡。
    private val weakCardIds = listOf(
        "c01_hamburger",
        "c06_mini_burger",
        "c08_one_for_two",
        "c11_steel_plate",
        "c13_fufu",
        "c21_water",
        "c24_man"
    )

    private val strongCardIds = listOf(
        "c02_grenade",
        "c03_coffee",
        "c04_tower_never_fall",
        "c05_again_once",
        "c07_lottery",
        "c10_world_peace",
        "c12_stun_gun",
        "c15_turbo_boost",
        "c16_million_pound",
        "c17_roast_chicken",
        "c19_rho_aias",
        "c20_mingdao",
        "c22_dual_doctor",
        "c23_paraquat"
    )

    private val devastatingCardIds = listOf(
        "c09_little_ivan",
        "c14_revive",
        "c18_c0_boom"
    )

    override fun all(): List<CardDefinition> = cards

    override fun getById(id: String): CardDefinition {
        return requireNotNull(byId[id]) { "卡牌不存在: $id" }
    }

    override fun createDeck(seed: Int): List<String> {
        val random = Random(seed)
        return List(30) {
            val tierRoll = random.nextInt(100)
            when {
                tierRoll < 60 -> weakCardIds.random(random)
                tierRoll < 90 -> strongCardIds.random(random)
                else -> devastatingCardIds.random(random)
            }
        }
    }

    private fun card(
        id: String,
        name: String,
        description: String,
        targetCamp: TargetCamp,
        targetSlot: TargetSlot,
        effects: List<SkillEffect>
    ): CardDefinition {
        return CardDefinition(
            id = id,
            name = name,
            description = description,
            skill = SkillDefinition(
                id = "skill_$id",
                name = name,
                description = description,
                type = SkillType.CARD,
                targetCamp = targetCamp,
                targetSlot = targetSlot,
                effects = effects
            )
        )
    }
}
