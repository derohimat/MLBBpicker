package ai.zasha.mlbbpicker.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One rank bracket with its own meta stats file (id is the mlbb.io rankId). */
@Serializable
data class MetaRank(
    val id: Int,
    val name: String,
    val file: String
)

/** Describes the bundled/patched data: game patch, when it was crawled and which ranks exist. */
@Serializable
data class DataVersion(
    @SerialName("game_patch") val gamePatch: String? = null,
    @SerialName("generated_at") val generatedAt: String? = null,
    val timeframe: String? = null,
    val ranks: List<MetaRank> = emptyList()
) {
    /** Ranks to offer, never empty: falls back to the original Mythic file. */
    val availableRanks: List<MetaRank>
        get() = ranks.ifEmpty { listOf(DEFAULT_RANK) }

    fun rankFor(id: Int): MetaRank = availableRanks.firstOrNull { it.id == id } ?: availableRanks.first()

    /** Rank after [id] in the list, wrapping around (for a tap-to-cycle chip). */
    fun nextRank(id: Int): MetaRank {
        val list = availableRanks
        val index = list.indexOfFirst { it.id == id }
        return list[(index + 1) % list.size]
    }

    /** Short label such as "Patch 1.9.20" or "Patch ?" when unknown. */
    val patchLabel: String
        get() = "Patch ${gamePatch?.takeIf { it.isNotBlank() } ?: "?"}"

    /** Crawl date (yyyy-MM-dd) if known. */
    val generatedDate: String?
        get() = generatedAt?.take(10)

    companion object {
        const val FILE_NAME = "data_version.json"
        val DEFAULT_RANK = MetaRank(id = 4, name = "Mythic", file = "meta_stats.json")

        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String?): DataVersion = try {
            if (text.isNullOrBlank()) DataVersion() else json.decodeFromString<DataVersion>(text)
        } catch (e: Exception) {
            DataVersion()
        }
    }
}
