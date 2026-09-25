package ai.zasha.mlbbpicker.data

/** The five MLBB lane positions, in the order they are usually called out in draft. */
enum class Lane(val label: String, val short: String) {
    EXP("Exp Lane", "EXP"),
    JUNGLE("Jungle", "JG"),
    MID("Mid Lane", "MID"),
    ROAM("Roam", "ROAM"),
    GOLD("Gold Lane", "GOLD");

    companion object {
        fun fromLabel(value: String): Lane? {
            val v = value.trim()
            return entries.firstOrNull {
                it.label.equals(v, ignoreCase = true) ||
                    it.short.equals(v, ignoreCase = true) ||
                    it.name.equals(v, ignoreCase = true)
            }
        }
    }
}

/** Lanes the hero can play, most preferred first, e.g. "Jungle, Exp Lane" -> [JUNGLE, EXP]. */
val Hero.laneList: List<Lane>
    get() = lane.split(",").mapNotNull { Lane.fromLabel(it) }

/** Lane assigned to one ally slot. */
data class SlotLane(
    val lane: Lane,
    /** True when the user picked this lane by hand instead of the auto assignment. */
    val isManual: Boolean,
    /** True when the hero doesn't list this lane as one it plays. */
    val isOffLane: Boolean
)

data class LaneAssignment(
    /** One entry per ally slot; null for empty slots. */
    val slots: List<SlotLane?>,
    /** Lanes nobody on the team covers yet. */
    val missingLanes: List<Lane>,
    val warnings: List<TeamWarning>
)

/**
 * Assigns each picked ally hero to a lane so that the team covers all five lanes,
 * preferring each hero's main lane. Manual picks per slot are kept as-is.
 */
object LaneAssigner {

    private const val MAIN_LANE_SCORE = 3
    private const val SECONDARY_LANE_SCORE = 2
    private const val DUPLICATE_LANE_PENALTY = -4

    fun assign(allies: List<Hero?>, manualLanes: List<Lane?> = emptyList()): LaneAssignment {
        val lanes = arrayOfNulls<Lane>(allies.size)
        val manual = BooleanArray(allies.size)

        // Manual lanes are fixed; only filled slots count
        allies.forEachIndexed { i, hero ->
            val m = manualLanes.getOrNull(i)
            if (hero != null && m != null) {
                lanes[i] = m
                manual[i] = true
            }
        }

        // Search the best assignment for the remaining heroes (at most 5! = 120 combos)
        val autoSlots = allies.indices.filter { allies[it] != null && !manual[it] }
        var best: List<Lane>? = null
        var bestScore = Int.MIN_VALUE

        fun search(pos: Int, chosen: MutableList<Lane>, score: Int) {
            if (pos == autoSlots.size) {
                if (score > bestScore) {
                    bestScore = score
                    best = chosen.toList()
                }
                return
            }
            val hero = allies[autoSlots[pos]]!!
            for (lane in Lane.entries) {
                val taken = lanes.count { it == lane } + chosen.count { it == lane }
                chosen.add(lane)
                search(pos + 1, chosen, score + fitScore(hero, lane) + if (taken > 0) DUPLICATE_LANE_PENALTY else 0)
                chosen.removeAt(chosen.lastIndex)
            }
        }
        search(0, mutableListOf(), 0)

        best?.forEachIndexed { k, lane -> lanes[autoSlots[k]] = lane }

        val slots = allies.mapIndexed { i, hero ->
            val lane = lanes[i]
            if (hero == null || lane == null) null
            else SlotLane(lane, manual[i], isOffLane = lane !in hero.laneList)
        }

        val covered = slots.mapNotNull { it?.lane }.toSet()
        val missing = Lane.entries.filter { it !in covered }

        val warnings = mutableListOf<TeamWarning>()
        Lane.entries.forEach { lane ->
            val count = slots.count { it?.lane == lane }
            if (count >= 2) {
                warnings.add(TeamWarning("$count heroes in ${lane.short}", WarningSeverity.WARNING))
            }
        }
        slots.forEachIndexed { i, slot ->
            if (slot != null && slot.isOffLane) {
                warnings.add(
                    TeamWarning("${allies[i]!!.hero_name} off-lane in ${slot.lane.short}", WarningSeverity.WARNING)
                )
            }
        }
        val filled = allies.count { it != null }
        if (filled >= 3 && missing.isNotEmpty()) {
            val severity = if (filled == allies.size) WarningSeverity.CRITICAL else WarningSeverity.INFO
            warnings.add(TeamWarning("Need: ${missing.joinToString(", ") { it.short }}", severity))
        }

        return LaneAssignment(slots, missing, warnings)
    }

    private fun fitScore(hero: Hero, lane: Lane): Int {
        return when (hero.laneList.indexOf(lane)) {
            0 -> MAIN_LANE_SCORE
            -1 -> 0
            else -> SECONDARY_LANE_SCORE
        }
    }
}
