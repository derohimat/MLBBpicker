package ai.zasha.mlbbpicker.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class CounterResponse(
    val success: Boolean,
    val message: String = "",
    val data: List<CounterSuggestion> = emptyList()
)

@Serializable
data class SynergyResponse(
    val success: Boolean,
    val message: String = "",
    val data: List<SynergySuggestion> = emptyList()
)

class HeroRepository(private val context: Context) {

    private val tag = "HeroRepository"
    private val json = Json { ignoreUnknownKeys = true }

    private var _heroes: List<Hero>? = null
    val heroes: List<Hero>
        get() {
            if (_heroes == null) {
                _heroes = loadHeroes()
            }
            return _heroes!!
        }

    private var _offlineCounters: Map<String, List<CounterSuggestion>>? = null
    private val offlineCounters: Map<String, List<CounterSuggestion>>
        get() {
            if (_offlineCounters == null) {
                _offlineCounters = loadOfflineCounters()
            }
            return _offlineCounters!!
        }

    private var _offlineSynergies: Map<String, List<SynergySuggestion>>? = null
    private val offlineSynergies: Map<String, List<SynergySuggestion>>
        get() {
            if (_offlineSynergies == null) {
                _offlineSynergies = loadOfflineSynergies()
            }
            return _offlineSynergies!!
        }

    fun reload() {
        _heroes = null
        _offlineCounters = null
        _offlineSynergies = null
    }

    private fun loadHeroes(): List<Hero> {
        return try {
            val jsonText = DataPatchManager.getLocalFileText(context, "heroes.json")
            json.decodeFromString<List<Hero>>(jsonText)
        } catch (e: Exception) {
            Log.e(tag, "Failed to load heroes.json", e)
            emptyList()
        }
    }

    private fun loadOfflineCounters(): Map<String, List<CounterSuggestion>> {
        return try {
            val jsonText = DataPatchManager.getLocalFileText(context, "counters.json")
            json.decodeFromString<Map<String, List<CounterSuggestion>>>(jsonText)
        } catch (e: Exception) {
            Log.e(tag, "Failed to load counters.json", e)
            emptyMap()
        }
    }

    private fun loadOfflineSynergies(): Map<String, List<SynergySuggestion>> {
        return try {
            val jsonText = DataPatchManager.getLocalFileText(context, "synergies.json")
            json.decodeFromString<Map<String, List<SynergySuggestion>>>(jsonText)
        } catch (e: Exception) {
            Log.e(tag, "Failed to load synergies.json", e)
            emptyMap()
        }
    }

    private fun isOnline(): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
            activeNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            Log.e(tag, "Failed to check connectivity state, defaulting to offline mode", e)
            false
        }
    }

    suspend fun getCounterSuggestions(enemyHeroIds: List<Int>): List<CounterSuggestion> = withContext(Dispatchers.IO) {
        if (enemyHeroIds.isEmpty()) return@withContext emptyList()

        if (isOnline()) {
            try {
                Log.d(tag, "Fetching counter suggestions online for: $enemyHeroIds")
                val url = URL("https://mlbb.io/api/hero/counter-pick-suggestions")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "application/json")

                val body = "{\"enemyHeroes\":${enemyHeroIds}}"
                OutputStreamWriter(conn.outputStream).use { it.write(body) }

                if (conn.responseCode == 200) {
                    val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                    val response = json.decodeFromString<CounterResponse>(responseText)
                    if (response.success) {
                        return@withContext response.data
                    }
                }
                Log.w(tag, "Online API returned code ${conn.responseCode}, falling back to offline.")
            } catch (e: Exception) {
                Log.e(tag, "Online counter request failed, falling back to offline", e)
            }
        }

        // Offline Fallback Calculation
        return@withContext computeOfflineCounters(enemyHeroIds)
    }

    suspend fun getSynergySuggestions(allyHeroIds: List<Int>): List<SynergySuggestion> = withContext(Dispatchers.IO) {
        if (allyHeroIds.isEmpty()) return@withContext emptyList()

        if (isOnline()) {
            try {
                Log.d(tag, "Fetching synergy suggestions online for: $allyHeroIds")
                val url = URL("https://mlbb.io/api/hero/hero-synergy-suggestions")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "application/json")

                val body = "{\"allyHeroes\":${allyHeroIds}}"
                OutputStreamWriter(conn.outputStream).use { it.write(body) }

                if (conn.responseCode == 200) {
                    val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                    val response = json.decodeFromString<SynergyResponse>(responseText)
                    if (response.success) {
                        return@withContext response.data
                    }
                }
                Log.w(tag, "Online API returned code ${conn.responseCode}, falling back to offline.")
            } catch (e: Exception) {
                Log.e(tag, "Online synergy request failed, falling back to offline", e)
            }
        }

        // Offline Fallback Calculation
        return@withContext computeOfflineSynergies(allyHeroIds)
    }

    private fun computeOfflineCounters(enemyHeroIds: List<Int>): List<CounterSuggestion> {
        val scoreSum = mutableMapOf<Int, Double>()
        val countMap = mutableMapOf<Int, Int>()
        val detailsMap = mutableMapOf<Int, CounterSuggestion>()

        for (enemyId in enemyHeroIds) {
            val suggestions = offlineCounters[enemyId.toString()] ?: continue
            for (suggestion in suggestions) {
                val candidateId = suggestion.id
                scoreSum[candidateId] = (scoreSum[candidateId] ?: 0.0) + suggestion.score
                countMap[candidateId] = (countMap[candidateId] ?: 0) + 1
                if (!detailsMap.containsKey(candidateId)) {
                    detailsMap[candidateId] = suggestion
                }
            }
        }

        if (scoreSum.isEmpty()) return emptyList()

        // Create the combined list of suggestions
        return scoreSum.map { (candidateId, totalScore) ->
            val numCountered = countMap[candidateId] ?: 0
            val averageScore = totalScore / enemyHeroIds.size
            val baseSuggestion = detailsMap[candidateId]!!

            // Construct counteredHeroes list for details
            val counteredList = enemyHeroIds.mapNotNull { enemyId ->
                val enemyHero = heroes.find { it.id == enemyId } ?: return@mapNotNull null
                val enemyCounters = offlineCounters[enemyId.toString()] ?: return@mapNotNull null
                val match = enemyCounters.find { it.id == candidateId }
                CounteredHero(
                    id = enemyId,
                    name = enemyHero.hero_name,
                    imgSrc = enemyHero.img_src,
                    score = match?.score ?: 0.0
                )
            }

            // Determine temporary tier based on average score
            val tier = when {
                averageScore >= 8.5 -> "SS"
                averageScore >= 7.5 -> "S"
                averageScore >= 6.5 -> "A"
                averageScore >= 5.5 -> "B"
                averageScore >= 4.5 -> "C"
                else -> "D"
            }

            baseSuggestion.copy(
                score = averageScore,
                tier = tier,
                counteredHeroes = counteredList
            )
        }.sortedWith(
            compareByDescending<CounterSuggestion> { countMap[it.id] ?: 0 }
                .thenByDescending { it.score }
        )
    }

    private fun computeOfflineSynergies(allyHeroIds: List<Int>): List<SynergySuggestion> {
        val scoreSum = mutableMapOf<Int, Double>()
        val countMap = mutableMapOf<Int, Int>()
        val detailsMap = mutableMapOf<Int, SynergySuggestion>()

        for (allyId in allyHeroIds) {
            val suggestions = offlineSynergies[allyId.toString()] ?: continue
            for (suggestion in suggestions) {
                val candidateId = suggestion.id
                scoreSum[candidateId] = (scoreSum[candidateId] ?: 0.0) + (suggestion.score ?: 0.0)
                countMap[candidateId] = (countMap[candidateId] ?: 0) + 1
                if (!detailsMap.containsKey(candidateId)) {
                    detailsMap[candidateId] = suggestion
                }
            }
        }

        if (scoreSum.isEmpty()) return emptyList()

        return scoreSum.map { (candidateId, totalScore) ->
            val numSynergized = countMap[candidateId] ?: 0
            val averageScore = totalScore / allyHeroIds.size
            val baseSuggestion = detailsMap[candidateId]!!

            val synergyList = allyHeroIds.mapNotNull { allyId ->
                val allyHero = heroes.find { it.id == allyId } ?: return@mapNotNull null
                val allySynergies = offlineSynergies[allyId.toString()] ?: return@mapNotNull null
                val match = allySynergies.find { it.id == candidateId }
                val mScore = match?.score ?: match?.synergyHeroes?.find { it.id == candidateId }?.realScore ?: 0.0
                SynergyHero(
                    id = allyId,
                    name = allyHero.hero_name,
                    imgSrc = allyHero.img_src,
                    score = mScore
                )
            }

            baseSuggestion.copy(
                score = averageScore,
                synergyHeroes = synergyList
            )
        }.sortedWith(
            compareByDescending<SynergySuggestion> { countMap[it.id] ?: 0 }
                .thenByDescending { it.score ?: 0.0 }
        )
    }
}
