package ai.zasha.mlbbpicker.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Repository for hero meta statistics (win rate, pick rate, ban rate).
 * Fetches from mlbb.io API with offline fallback from bundled assets.
 */
class MetaStatsRepository(private val context: Context) {

    private val tag = "MetaStatsRepository"
    private val json = Json { ignoreUnknownKeys = true }

    private fun cacheFileName(rankId: Int) = "meta_stats_cache_$rankId.json"
    private fun cacheTimestampKey(rankId: Int) = "meta_stats_timestamp_$rankId"
    private val cacheMaxAgeMs = 6 * 60 * 60 * 1000L // 6 hours

    private val _offlineStatsFlow =
        kotlinx.coroutines.flow.MutableStateFlow<List<HeroMetaStats>>(emptyList())
    val offlineStatsFlow = _offlineStatsFlow.asStateFlow()
    val offlineStats: List<HeroMetaStats> get() = _offlineStatsFlow.value

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        MetaRankStore.load(context)
        // Reload whenever the selected rank changes (emits the current rank first)
        scope.launch {
            MetaRankStore.selectedRankId.collect {
                _offlineStatsFlow.value = loadOfflineStats()
            }
        }
    }

    fun reload() {
        scope.launch {
            _offlineStatsFlow.value = loadOfflineStats()
        }
    }

    private fun loadOfflineStats(): List<HeroMetaStats> {
        val file = MetaRankStore.selectedRank.file
        return try {
            val jsonText = DataPatchManager.getLocalFileText(context, file)
            json.decodeFromString<List<HeroMetaStats>>(jsonText)
        } catch (e: Exception) {
            Log.e(tag, "Failed to load $file, falling back to meta_stats.json", e)
            try {
                json.decodeFromString<List<HeroMetaStats>>(
                    DataPatchManager.getLocalFileText(context, DataVersion.DEFAULT_RANK.file)
                )
            } catch (e2: Exception) {
                Log.e(tag, "Failed to load meta_stats.json asset", e2)
                emptyList()
            }
        }
    }

    /** Load stats for the selected rank: try cache first, then online, then fallback to asset */
    suspend fun getMetaStats(
        rankId: Int = MetaRankStore.selectedRank.id,
        timeframeId: Int = 1
    ): List<HeroMetaStats> = withContext(Dispatchers.IO) {
        // 1. Check if cache is fresh
        val cached = loadFromCache(rankId)
        if (cached != null) {
            Log.d(tag, "Using cached meta stats (${cached.size} heroes)")
            return@withContext cached
        }

        // 2. Try online fetch
        try {
            val url = URL("https://mlbb.io/api/hero/filtered-statistics?rankId=$rankId&timeframeId=$timeframeId")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "MLBBPicker/1.0")

            if (conn.responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val parsed = json.decodeFromString<MetaStatsApiResponse>(responseText)
                if (parsed.success && parsed.data.heroes.isNotEmpty()) {
                    Log.d(tag, "Fetched ${parsed.data.heroes.size} heroes from API")
                    saveToCache(rankId, responseText)
                    return@withContext parsed.data.heroes
                }
            }
            Log.w(tag, "Online API returned ${conn.responseCode}")
        } catch (e: Exception) {
            Log.e(tag, "Online meta stats fetch failed", e)
        }

        // 3. Fallback to offline
        val offline = loadOfflineStats()
        Log.d(tag, "Using offline meta stats (${offline.size} heroes)")
        return@withContext offline
    }

    /** Get meta stats for a specific hero by ID */
    fun getStatsForHero(heroId: Int, stats: List<HeroMetaStats>): HeroMetaStats? {
        return stats.find { it.heroId == heroId }
    }

    private fun saveToCache(rankId: Int, jsonText: String) {
        try {
            context.openFileOutput(cacheFileName(rankId), Context.MODE_PRIVATE).use { fos ->
                OutputStreamWriter(fos).use { it.write(jsonText) }
            }
            // Save timestamp
            context.getSharedPreferences("mlbb_cache", Context.MODE_PRIVATE)
                .edit()
                .putLong(cacheTimestampKey(rankId), System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            Log.e(tag, "Failed to save cache", e)
        }
    }

    private fun loadFromCache(rankId: Int): List<HeroMetaStats>? {
        try {
            val prefs = context.getSharedPreferences("mlbb_cache", Context.MODE_PRIVATE)
            val timestamp = prefs.getLong(cacheTimestampKey(rankId), 0)
            if (System.currentTimeMillis() - timestamp > cacheMaxAgeMs) {
                return null // Cache expired
            }

            val file = context.getFileStreamPath(cacheFileName(rankId))
            if (!file.exists()) return null

            val jsonText = file.readText()
            val parsed = json.decodeFromString<MetaStatsApiResponse>(jsonText)
            return if (parsed.success) parsed.data.heroes else null
        } catch (e: Exception) {
            Log.e(tag, "Failed to read cache", e)
            return null
        }
    }
}

@kotlinx.serialization.Serializable
private data class MetaStatsApiResponse(
    val success: Boolean,
    val message: String = "",
    val data: MetaStatsData = MetaStatsData()
)

@kotlinx.serialization.Serializable
private data class MetaStatsData(
    val heroes: List<HeroMetaStats> = emptyList()
)
