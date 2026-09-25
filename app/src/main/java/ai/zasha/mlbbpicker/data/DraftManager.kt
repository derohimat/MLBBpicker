package ai.zasha.mlbbpicker.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object DraftManager {
    val selectedEnemies = mutableStateListOf<Hero?>(null, null, null, null, null)
    val selectedAllies = mutableStateListOf<Hero?>(null, null, null, null, null)

    val allyBans = mutableStateListOf<Hero?>(null, null, null, null, null)
    val enemyBans = mutableStateListOf<Hero?>(null, null, null, null, null)

    var draftFormat by mutableStateOf(DraftFormat.RANKED_6)
    var draftSide by mutableStateOf(DraftSide.BLUE)

    /** Set when the user skips entering bans (e.g. enemy bans unknown). */
    var bansSkipped by mutableStateOf(false)

    /** Lane the user set by hand for each ally slot; null means auto-assign. */
    val allyLanes = mutableStateListOf<Lane?>(null, null, null, null, null)

    val counterSuggestions = mutableStateListOf<CounterSuggestion>()
    val synergySuggestions = mutableStateListOf<SynergySuggestion>()
    val banRecommendations = mutableStateListOf<BanRecommendation>()
    val enemyPredictions = mutableStateListOf<EnemyPrediction>()

    var isSoloMode by mutableStateOf(false)

    private var recommendationJob: Job? = null

    fun clear() {
        recommendationJob?.cancel()
        for (i in 0 until 5) {
            selectedEnemies[i] = null
            selectedAllies[i] = null
            allyLanes[i] = null
            allyBans[i] = null
            enemyBans[i] = null
        }
        bansSkipped = false
        counterSuggestions.clear()
        synergySuggestions.clear()
        banRecommendations.clear()
        enemyPredictions.clear()
    }

    fun swapSlots(fromType: String, fromIdx: Int, toType: String, toIdx: Int) {
        val fromList = if (fromType == "enemy") selectedEnemies else selectedAllies
        val toList = if (toType == "enemy") selectedEnemies else selectedAllies

        val temp = fromList[fromIdx]
        fromList[fromIdx] = toList[toIdx]
        toList[toIdx] = temp

        // Manual lanes follow the hero within the ally team; reset them when crossing teams
        if (fromType == "ally" && toType == "ally") {
            val tempLane = allyLanes[fromIdx]
            allyLanes[fromIdx] = allyLanes[toIdx]
            allyLanes[toIdx] = tempLane
        } else {
            if (fromType == "ally") allyLanes[fromIdx] = null
            if (toType == "ally") allyLanes[toIdx] = null
        }
    }

    /** Put [hero] in a slot ("ally", "enemy", "allyBan", "enemyBan"); a new ally hero starts on its auto-assigned lane. */
    fun setHero(type: String, index: Int, hero: Hero?) {
        when (type) {
            "enemy" -> selectedEnemies[index] = hero
            "allyBan" -> allyBans[index] = hero
            "enemyBan" -> enemyBans[index] = hero
            else -> {
                if (selectedAllies[index]?.id != hero?.id) allyLanes[index] = null
                selectedAllies[index] = hero
            }
        }
    }

    fun heroAt(type: String, index: Int): Hero? = when (type) {
        "enemy" -> selectedEnemies.getOrNull(index)
        "allyBan" -> allyBans.getOrNull(index)
        "enemyBan" -> enemyBans.getOrNull(index)
        else -> selectedAllies.getOrNull(index)
    }

    val bannedIds: Set<Int>
        get() = (allyBans + enemyBans).filterNotNull().map { it.id }.toSet()

    fun board() = DraftBoard(
        format = draftFormat,
        side = draftSide,
        allyBans = allyBans.toList(),
        enemyBans = enemyBans.toList(),
        allies = selectedAllies.toList(),
        enemies = selectedEnemies.toList(),
        bansSkipped = bansSkipped
    )

    /** Cycle a slot's lane: auto -> EXP -> JG -> MID -> ROAM -> GOLD -> auto. */
    fun cycleAllyLane(index: Int) {
        val current = allyLanes[index]
        val lanes = Lane.entries
        allyLanes[index] = if (current == null) lanes.first() else lanes.getOrNull(current.ordinal + 1)
    }

    fun updateRecommendations(
        coroutineScope: CoroutineScope,
        heroRepository: HeroRepository,
        metaStats: List<HeroMetaStats>
    ) {
        recommendationJob?.cancel()
        
        val enemies = selectedEnemies.filterNotNull().map { it.id }
        val allies = selectedAllies.filterNotNull().map { it.id }

        // Update ban recommendations excluding already picked heroes
        val banned = bannedIds
        val allPickedIds = (enemies + allies).toSet() + banned
        val bans = BanHelper.getRecommendedBans(metaStats, excludeHeroIds = allPickedIds)
        banRecommendations.clear()
        banRecommendations.addAll(bans)

        val enemySlots = selectedEnemies.toList()

        recommendationJob = coroutineScope.launch(Dispatchers.Default) {
            var localCounters = if (enemies.isNotEmpty()) {
                heroRepository.getCounterSuggestions(enemies)
            } else {
                emptyList()
            }

            var localSynergies = if (allies.isNotEmpty()) {
                heroRepository.getSynergySuggestions(allies)
            } else {
                emptyList()
            }

            // Apply Solo carry role weights if Solo Mode is active
            if (isSoloMode) {
                val heroes = heroRepository.heroes
                localCounters = SoloQueueManager.reRankWithSoloWeight(localCounters, heroes)
                localSynergies = SoloQueueManager.reRankSynergiesWithSoloWeight(localSynergies, heroes)
            }

            // Banned heroes can't be picked by anyone
            localCounters = localCounters.filter { it.id !in banned }
            localSynergies = localSynergies.filter { it.id !in banned }

            // Enemy prediction: heroes that counter us and heroes that fit the enemy's picks
            val counterThreats = if (allies.isNotEmpty()) heroRepository.getCounterSuggestions(allies) else emptyList()
            val enemySynergies = if (enemies.isNotEmpty()) heroRepository.getSynergySuggestions(enemies) else emptyList()
            val predictions = EnemyPredictor.predict(
                heroes = heroRepository.heroes,
                metaStats = metaStats,
                enemies = enemySlots,
                excludeIds = allPickedIds,
                counterThreats = counterThreats,
                enemySynergies = enemySynergies
            )

            withContext(Dispatchers.Main) {
                enemyPredictions.clear()
                enemyPredictions.addAll(predictions)
                counterSuggestions.clear()
                counterSuggestions.addAll(localCounters.take(15))
                synergySuggestions.clear()
                synergySuggestions.addAll(localSynergies.take(15))
            }
        }
    }
}
