package ai.zasha.mlbbpicker.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json

/**
 * Repository for hero item builds.
 * Loads from bundled builds.json asset file.
 */
class BuildRepository(private val context: Context) {

    private val tag = "BuildRepository"
    private val json = Json { ignoreUnknownKeys = true }

    private var _buildsMap: Map<String, List<HeroBuild>>? = null
    private val buildsMap: Map<String, List<HeroBuild>>
        get() {
            if (_buildsMap == null) {
                _buildsMap = loadBuilds()
            }
            return _buildsMap!!
        }

    fun reload() {
        _buildsMap = null
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
