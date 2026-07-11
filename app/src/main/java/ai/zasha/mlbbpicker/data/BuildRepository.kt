package ai.zasha.mlbbpicker.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Repository for hero item builds.
 * Loads from bundled builds.json asset file.
 */
class BuildRepository(private val context: Context) {

    private val tag = "BuildRepository"
    private val json = Json { ignoreUnknownKeys = true }

    private val _buildsMapFlow =
        kotlinx.coroutines.flow.MutableStateFlow<Map<String, List<HeroBuild>>>(emptyMap())
    val buildsMapFlow = _buildsMapFlow.asStateFlow()
    private val buildsMap: Map<String, List<HeroBuild>> get() = _buildsMapFlow.value

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        reload()
    }

    fun reload() {
        scope.launch {
            _buildsMapFlow.value = loadBuilds()
        }
    }

    private fun loadBuilds(): Map<String, List<HeroBuild>> {
        return try {
            val jsonText = DataPatchManager.getLocalFileText(context, "builds.json")
            json.decodeFromString<Map<String, List<HeroBuild>>>(jsonText)
        } catch (e: Exception) {
            Log.e(tag, "Failed to load builds.json", e)
            emptyMap()
        }
    }

    /** Get builds for a specific hero by ID */
    fun getBuildsForHero(heroId: Int): List<HeroBuild> {
        return buildsMap[heroId.toString()] ?: emptyList()
    }

    /** Get the top (most liked) build for a hero */
    fun getTopBuild(heroId: Int): HeroBuild? {
        return getBuildsForHero(heroId).maxByOrNull { it.likes }
    }

    /** Check if builds are available */
    fun hasBuilds(): Boolean = buildsMap.isNotEmpty()
}
