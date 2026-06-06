package ai.zasha.mlbbpicker.data

/**
 * Recommends heroes to ban based on meta statistics.
 * Uses a weighted scoring formula: banScore = (winRate * 0.4) + (banRate * 0.3) + (pickRate * 0.3)
 */
object BanHelper {

    fun getRecommendedBans(
        metaStats: List<HeroMetaStats>,
        count: Int = 8,
        excludeHeroIds: Set<Int> = emptySet()
    ): List<BanRecommendation> {
        if (metaStats.isEmpty()) return emptyList()

        return metaStats
            .filter { it.heroId !in excludeHeroIds }
            .map { stat ->
                val banScore = (stat.winRate * 0.4) + (stat.banRate * 0.3) + (stat.pickRate * 0.3)
                val reason = buildReason(stat)

                BanRecommendation(
                    heroId = stat.heroId,
                    heroName = stat.heroName,
                    imgSrc = stat.imgSrc,
                    winRate = stat.winRate,
                    banRate = stat.banRate,
                    pickRate = stat.pickRate,
                    banScore = banScore,
                    reason = reason
                )
            }
            .sortedByDescending { it.banScore }
            .take(count)
    }

    private fun buildReason(stat: HeroMetaStats): String {
        val reasons = mutableListOf<String>()

        if (stat.winRate >= 54.0) reasons.add("Very High WR")
        else if (stat.winRate >= 52.0) reasons.add("High WR")

        if (stat.banRate >= 40.0) reasons.add("Most Banned")
        else if (stat.banRate >= 20.0) reasons.add("Highly Banned")
        else if (stat.banRate >= 10.0) reasons.add("Often Banned")

        if (stat.pickRate >= 3.0) reasons.add("Very Popular")
        else if (stat.pickRate >= 2.0) reasons.add("Popular")

        if (reasons.isEmpty()) {
            reasons.add("Strong in Meta")
        }

        return reasons.joinToString(" · ")
    }
}
