package ai.zasha.mlbbpicker.data

import kotlinx.serialization.Serializable

@Serializable
data class Hero(
    val id: Int,
    val hero_name: String,
    val role: String,
    val lane: String,
    val speciality: String,
    val img_src: String
)

@Serializable
data class CounteredHero(
    val id: Int,
    val name: String,
    val imgSrc: String,
    val score: Double
)

@Serializable
data class CounterSuggestion(
    val id: Int,
    val heroName: String,
    val imgSrc: String,
    val role: List<String> = emptyList(),
    val lane: List<String> = emptyList(),
    val speciality: List<String> = emptyList(),
    val score: Double,
    val tier: String = "",
    val counteredHeroes: List<CounteredHero> = emptyList()
)

@Serializable
data class SynergyHero(
    val id: Int,
    val name: String,
    val imgSrc: String? = null,
    val lane: List<String> = emptyList(),
    val score: Double? = null
) {
    val realScore: Double
        get() = score ?: lane.firstOrNull()?.toDoubleOrNull() ?: 0.0
}

@Serializable
data class SynergySuggestion(
    val id: Int,
    val heroName: String,
    val imgSrc: String,
    val role: List<String> = emptyList(),
    val lane: List<String> = emptyList(),
    val speciality: List<String> = emptyList(),
    val score: Double? = null,
    val synergyHeroes: List<SynergyHero> = emptyList()
)
