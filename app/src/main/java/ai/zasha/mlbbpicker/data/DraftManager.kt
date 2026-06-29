package ai.zasha.mlbbpicker.data

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object DraftManager {
    val selectedEnemies = mutableStateListOf<Hero?>(null, null, null, null, null)
    val selectedAllies = mutableStateListOf<Hero?>(null, null, null, null, null)

    val counterSuggestions = mutableStateListOf<CounterSuggestion>()
    val synergySuggestions = mutableStateListOf<SynergySuggestion>()
    val banRecommendations = mutableStateListOf<BanRecommendation>()

    var isSoloMode by mutableStateOf(false)

    private var recommendationJob: Job? = null

    fun clear() {
        recommendationJob?.cancel()
        for (i in 0 until 5) {
            selectedEnemies[i] = null
            selectedAllies[i] = null
        }
        counterSuggestions.clear()
        synergySuggestions.clear()
        banRecommendations.clear()
    }

    fun swapSlots(fromType: String, fromIdx: Int, toType: String, toIdx: Int) {
        val fromList = if (fromType == "enemy") selectedEnemies else selectedAllies
        val toList = if (toType == "enemy") selectedEnemies else selectedAllies

        val temp = fromList[fromIdx]
        fromList[fromIdx] = toList[toIdx]
        toList[toIdx] = temp
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
        val allPickedIds = (enemies + allies).toSet()
        val bans = BanHelper.getRecommendedBans(metaStats, excludeHeroIds = allPickedIds)
        banRecommendations.clear()
        banRecommendations.addAll(bans)

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

            withContext(Dispatchers.Main) {
                counterSuggestions.clear()
                counterSuggestions.addAll(localCounters.take(15))
                synergySuggestions.clear()
                synergySuggestions.addAll(localSynergies.take(15))
            }
        }
    }
}
