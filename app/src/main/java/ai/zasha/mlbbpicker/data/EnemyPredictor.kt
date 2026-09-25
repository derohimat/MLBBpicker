package ai.zasha.mlbbpicker.data

/** A hero the enemy is likely to pick, with a 0-100 likelihood score and short reasons. */
data class EnemyPrediction(
    val heroId: Int,
    val heroName: String,
    val imgSrc: String,
    val score: Double,
    val reasons: List<String>
)

/**
 * Predicts the enemy's next picks from the meta (pick/ban/win rate), heroes that counter
 * our picks, heroes that synergise with the enemy's picks and the lanes the enemy still needs.
 */
object EnemyPredictor {

    private const val W_META = 0.35
    private const val W_COUNTER = 0.30
    private const val W_SYNERGY = 0.15
    private const val W_LANE = 0.20

    private const val SECONDARY_LANE_FIT = 0.6
    private const val MAX_REASONS = 3

    fun predict(
        heroes: List<Hero>,
        metaStats: List<HeroMetaStats>,
        enemies: List<Hero?>,
        excludeIds: Set<Int>,
        counterThreats: List<CounterSuggestion> = emptyList(),
        enemySynergies: List<SynergySuggestion> = emptyList(),
        limit: Int = 8
    ): List<EnemyPrediction> {
        val candidates = heroes.filter { it.id !in excludeIds }
        if (candidates.isEmpty()) return emptyList()

        val statsById = metaStats.associateBy { it.heroId }
        val counterById = counterThreats.filter { it.id !in excludeIds }.associateBy { it.id }
        val synergyById = enemySynergies.filter { it.id !in excludeIds }.associateBy { it.id }
        val missingLanes = LaneAssigner.assign(enemies).missingLanes

        val maxPopularity = candidates.maxOf { popularity(statsById[it.id]) }
        val maxCounter = counterById.values.maxOfOrNull { it.score } ?: 0.0
        val maxSynergy = synergyById.values.maxOfOrNull { synergyScore(it) } ?: 0.0

        // Components without data don't count, so their weight goes to the others
        val hasMeta = maxPopularity > 0.0
        val hasCounter = maxCounter > 0.0
        val hasSynergy = maxSynergy > 0.0
        val totalWeight = (if (hasMeta) W_META else 0.0) +
            (if (hasCounter) W_COUNTER else 0.0) +
            (if (hasSynergy) W_SYNERGY else 0.0) +
            W_LANE

        return candidates.map { hero ->
            val stats = statsById[hero.id]
            val counter = counterById[hero.id]
            val synergy = synergyById[hero.id]
            val laneFit = laneFit(hero, missingLanes)

            val meta = if (hasMeta) metaComponent(stats, maxPopularity) else 0.0
            val counterPart = if (hasCounter && counter != null) counter.score / maxCounter else 0.0
            val synergyPart = if (hasSynergy && synergy != null) synergyScore(synergy) / maxSynergy else 0.0

            val weighted = (if (hasMeta) W_META * meta else 0.0) +
                (if (hasCounter) W_COUNTER * counterPart else 0.0) +
                (if (hasSynergy) W_SYNERGY * synergyPart else 0.0) +
                W_LANE * laneFit
            val score = (weighted / totalWeight * 100).coerceIn(0.0, 100.0)

            // Reasons, strongest contribution first
            val reasons = mutableListOf<Pair<Double, String>>()
            if (counter != null && counterPart > 0) {
                val names = counter.counteredHeroes.filter { it.score > 0 }.map { it.name }
                if (names.isNotEmpty()) reasons.add(W_COUNTER * counterPart to "Counters ${names.joinToString(", ")}")
            }
            if (synergy != null && synergyPart > 0) {
                val names = synergy.synergyHeroes.filter { it.realScore > 0 }.map { it.name }
                if (names.isNotEmpty()) reasons.add(W_SYNERGY * synergyPart to "Synergy w/ ${names.joinToString(", ")}")
            }
            if (stats != null && meta > 0) {
                val label = when {
                    stats.winRate >= 52.0 -> "Meta ${"%.0f".format(stats.winRate)}% WR"
                    stats.banRate >= stats.pickRate -> "Ban ${"%.0f".format(stats.banRate)}%"
                    else -> "Pick ${"%.1f".format(stats.pickRate)}%"
                }
                reasons.add(W_META * meta to label)
            }
            if (laneFit > 0 && missingLanes.size < Lane.entries.size) {
                val lane = hero.laneList.firstOrNull { it in missingLanes }
                if (lane != null) reasons.add(W_LANE * laneFit to "Fills ${lane.short}")
            }

            EnemyPrediction(
                heroId = hero.id,
                heroName = hero.hero_name,
                imgSrc = hero.img_src,
                score = score,
                reasons = reasons.sortedByDescending { it.first }.take(MAX_REASONS).map { it.second }
            )
        }
            .sortedWith(compareByDescending<EnemyPrediction> { it.score }.thenBy { it.heroName })
            .take(limit)
    }

    private fun popularity(stats: HeroMetaStats?): Double =
        if (stats == null) 0.0 else stats.pickRate + stats.banRate

    /** Popularity drives the meta part; a win rate above 50% adds up to 20%. */
    private fun metaComponent(stats: HeroMetaStats?, maxPopularity: Double): Double {
        if (stats == null) return 0.0
        val pop = popularity(stats) / maxPopularity
        val wrBonus = ((stats.winRate - 50.0) / 5.0).coerceIn(0.0, 1.0)
        return 0.8 * pop + 0.2 * wrBonus
    }

    private fun synergyScore(s: SynergySuggestion): Double =
        s.score ?: s.synergyHeroes.firstOrNull()?.realScore ?: 0.0

    private fun laneFit(hero: Hero, missingLanes: List<Lane>): Double {
        val lanes = hero.laneList
        return when {
            lanes.isEmpty() -> 0.0
            lanes.first() in missingLanes -> 1.0
            lanes.any { it in missingLanes } -> SECONDARY_LANE_FIT
            else -> 0.0
        }
    }
}
