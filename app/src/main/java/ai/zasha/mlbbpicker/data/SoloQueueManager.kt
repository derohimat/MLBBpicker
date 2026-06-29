package ai.zasha.mlbbpicker.data

import kotlin.math.sqrt

/**
 * Computes solo queue hero rankings using a weighted scoring formula:
 *   soloScore = winRate * sqrt(pickRate) * roleMultiplier
 *
 * Role multipliers reflect solo-carry potential:
 *   Assassin=1.3, Fighter=1.2, Marksman=1.1, Mage=1.0, Tank=0.8, Support=0.7
 */
object SoloQueueManager {

    private val ROLE_MULTIPLIERS = mapOf(
        "assassin" to 1.3,
        "fighter" to 1.2,
        "marksman" to 1.1,
        "mage" to 1.0,
        "tank" to 0.8,
        "support" to 0.7
    )

    /**
     * Compute solo queue rankings for all heroes with meta stats.
     * Returns a sorted list (highest soloScore first) with tier labels.
     */
    fun computeSoloRankings(
        heroes: List<Hero>,
        metaStats: List<HeroMetaStats>,
        buildRepository: BuildRepository
    ): List<SoloHeroRank> {
        val statsMap = metaStats.associateBy { it.heroId }

        val scored = heroes.mapNotNull { hero ->
            val stats = statsMap[hero.id] ?: return@mapNotNull null
            if (stats.winRate <= 0 || stats.pickRate <= 0) return@mapNotNull null

            val roleMultiplier = getRoleMultiplier(hero.roleList)
            val soloScore = stats.winRate * sqrt(stats.pickRate) * roleMultiplier

            // Get the top build (most liked) for this hero
            val builds = buildRepository.getBuildsForHero(hero.id)
            val topBuild = builds.maxByOrNull { it.likes }

            SoloHeroRank(
                heroId = hero.id,
                heroName = hero.hero_name,
                imgSrc = hero.img_src,
                role = hero.roleList,
                soloScore = soloScore,
                winRate = stats.winRate,
                pickRate = stats.pickRate,
                banRate = stats.banRate,
                tier = "", // Assigned below
                topBuild = topBuild
            )
        }.sortedByDescending { it.soloScore }

        // Assign tiers based on percentile ranking
        return assignTiers(scored)
    }

    /**
     * Apply the solo carry weight multiplier to counter/synergy suggestions.
     * Used to re-rank suggestions when Solo Mode is active in the Draft.
     */
    fun reRankWithSoloWeight(
        suggestions: List<CounterSuggestion>,
        heroes: List<Hero>
    ): List<CounterSuggestion> {
        val heroMap = heroes.associateBy { it.id }
        return suggestions
            .map { suggestion ->
                val hero = heroMap[suggestion.id]
                val multiplier = if (hero != null) getRoleMultiplier(hero.roleList) else 1.0
                suggestion.copy(score = suggestion.score * multiplier)
            }
            .sortedByDescending { it.score }
    }

    fun reRankSynergiesWithSoloWeight(
        suggestions: List<SynergySuggestion>,
        heroes: List<Hero>
    ): List<SynergySuggestion> {
        val heroMap = heroes.associateBy { it.id }
        return suggestions
            .map { suggestion ->
                val hero = heroMap[suggestion.id]
                val multiplier = if (hero != null) getRoleMultiplier(hero.roleList) else 1.0
                val currentScore = suggestion.score ?: 0.0
                suggestion.copy(score = currentScore * multiplier)
            }
            .sortedByDescending { it.score }
    }

    private fun getRoleMultiplier(roles: List<String>): Double {
        // Use the highest multiplier among the hero's roles
        return roles.maxOfOrNull { role ->
            ROLE_MULTIPLIERS[role.lowercase()] ?: 1.0
        } ?: 1.0
    }

    private fun assignTiers(ranked: List<SoloHeroRank>): List<SoloHeroRank> {
        if (ranked.isEmpty()) return emptyList()
        val total = ranked.size
        return ranked.mapIndexed { index, rank ->
            val percentile = index.toDouble() / total
            val tier = when {
                percentile < 0.10 -> "S"
                percentile < 0.25 -> "A"
                percentile < 0.50 -> "B"
                percentile < 0.75 -> "C"
                else -> "D"
            }
            rank.copy(tier = tier)
        }
    }
}
