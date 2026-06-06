package ai.zasha.mlbbpicker.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Hero(
    val id: Int,
    val hero_name: String,
    val role: String,
    val lane: String,
    val speciality: String,
    val img_src: String
) {
    /** Returns primary roles as a list, e.g. "Fighter, Assassin" -> ["Fighter", "Assassin"] */
    val roleList: List<String>
        get() = role.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    /** Returns primary role only */
    val primaryRole: String
        get() = roleList.firstOrNull() ?: ""

    /** Returns specialities as a list */
    val specialityList: List<String>
        get() = speciality.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    /** Check if hero has a specific role */
    fun hasRole(role: String): Boolean =
        roleList.any { it.equals(role, ignoreCase = true) }

    /** Check if hero deals magic damage based on role/speciality */
    val isMagicDamage: Boolean
        get() = hasRole("Mage") || specialityList.any {
            it.contains("Magic", ignoreCase = true)
        }
}

@Serializable
data class CounteredHero(
    val id: Int,
    val name: String,
    val imgSrc: String,
    val score: Double
)

@Serializable
data class CounterSuggestion(
    val id: Int,
    val heroName: String,
    val imgSrc: String,
    val role: List<String> = emptyList(),
    val lane: List<String> = emptyList(),
    val speciality: List<String> = emptyList(),
    val score: Double,
    val tier: String = "",
    val counteredHeroes: List<CounteredHero> = emptyList()
)

@Serializable
data class SynergyHero(
    val id: Int,
    val name: String,
    val imgSrc: String? = null,
    val lane: List<String> = emptyList(),
    val score: Double? = null
) {
    val realScore: Double
        get() = score ?: lane.firstOrNull()?.toDoubleOrNull() ?: 0.0
}

@Serializable
data class SynergySuggestion(
    val id: Int,
    val heroName: String,
    val imgSrc: String,
    val role: List<String> = emptyList(),
    val lane: List<String> = emptyList(),
    val speciality: List<String> = emptyList(),
    val score: Double? = null,
    val synergyHeroes: List<SynergyHero> = emptyList()
)

// ─── Meta Stats ──────────────────────────────────────────────────────────────

@Serializable
data class HeroMetaStats(
    @SerialName("hero_id") val heroId: Int,
    @SerialName("hero_name") val heroName: String,
    @SerialName("img_src") val imgSrc: String,
    val role: List<String> = emptyList(),
    val lane: List<String> = emptyList(),
    val speciality: List<String> = emptyList(),
    @SerialName("pick_rate") val pickRate: Double = 0.0,
    @SerialName("win_rate") val winRate: Double = 0.0,
    @SerialName("ban_rate") val banRate: Double = 0.0,
    @SerialName("rank_name") val rankName: String = "",
    @SerialName("timeframe_name") val timeframeName: String = ""
)

// ─── Build Recommendation ────────────────────────────────────────────────────

@Serializable
data class HeroBuild(
    val title: String = "",
    val author: String = "",
    val spell: String = "",
    val emblem: String = "",
    @SerialName("emblem_talents") val emblemTalents: List<String> = emptyList(),
    val items: List<String> = emptyList(),
    val likes: Int = 0
)

// ─── Team Composition Analysis ───────────────────────────────────────────────

data class TeamAnalysis(
    val hasTank: Boolean = false,
    val hasSupport: Boolean = false,
    val hasMage: Boolean = false,
    val hasMarksman: Boolean = false,
    val hasAssassin: Boolean = false,
    val hasFighter: Boolean = false,
    val physicalCount: Int = 0,
    val magicCount: Int = 0,
    val ccCount: Int = 0,
    val filledRolesCount: Int = 0,
    val warnings: List<TeamWarning> = emptyList(),
    val overallScore: Int = 0 // 0-100
)

data class TeamWarning(
    val message: String,
    val severity: WarningSeverity
)

enum class WarningSeverity { INFO, WARNING, CRITICAL }

// ─── Ban Phase Helper ────────────────────────────────────────────────────────

data class BanRecommendation(
    val heroId: Int,
    val heroName: String,
    val imgSrc: String,
    val winRate: Double,
    val banRate: Double,
    val pickRate: Double,
    val banScore: Double,
    val reason: String
)

