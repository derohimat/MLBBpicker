package ai.zasha.mlbbpicker.data

/** Ranked draft formats: the ban phase size differs, the pick order is the same. */
enum class DraftFormat(val label: String, val bansPerTeam: Int) {
    RANKED_6("6 Bans", 3),
    RANKED_10("10 Bans", 5)
}

/** Blue side gets the first pick. */
enum class DraftSide { BLUE, RED }

enum class DraftAction { BAN, PICK }

enum class DraftPhase { BAN, PICK, DONE }

/**
 * One turn in the draft.
 * [team] is "ally" or "enemy", [slotIndex] the slot it fills, [group] the turn number
 * (steps in the same group happen together, e.g. a team's 2 consecutive picks).
 */
data class DraftStep(
    val team: String,
    val action: DraftAction,
    val slotIndex: Int,
    val group: Int
) {
    /** Slot type as used by the draft board: "ally", "enemy", "allyBan" or "enemyBan". */
    val slotType: String
        get() = if (action == DraftAction.BAN) "${team}Ban" else team
}

/** Current draft state as seen by [DraftOrder]; null entries are empty slots. */
data class DraftBoard(
    val format: DraftFormat,
    val side: DraftSide,
    val allyBans: List<Hero?>,
    val enemyBans: List<Hero?>,
    val allies: List<Hero?>,
    val enemies: List<Hero?>,
    val bansSkipped: Boolean = false
) {
    fun heroAt(step: DraftStep): Hero? {
        val list = when (step.slotType) {
            "allyBan" -> allyBans
            "enemyBan" -> enemyBans
            "ally" -> allies
            else -> enemies
        }
        return list.getOrNull(step.slotIndex)
    }
}

/** MLBB ranked draft order: simultaneous ban phase, then picks 1-2-2-2-2-1 starting with Blue. */
object DraftOrder {

    private val PICK_GROUPS = listOf(1, 2, 2, 2, 2, 1)

    /** Ranked bans happen at the same time for both teams, so they share one group. */
    fun banSteps(format: DraftFormat): List<DraftStep> =
        (0 until format.bansPerTeam).flatMap { i ->
            listOf(
                DraftStep("ally", DraftAction.BAN, i, group = 0),
                DraftStep("enemy", DraftAction.BAN, i, group = 0)
            )
        }

    fun pickSteps(side: DraftSide): List<DraftStep> {
        val firstTeam = if (side == DraftSide.BLUE) "ally" else "enemy"
        val secondTeam = if (side == DraftSide.BLUE) "enemy" else "ally"
        val nextSlot = mutableMapOf("ally" to 0, "enemy" to 0)
        return PICK_GROUPS.flatMapIndexed { g, size ->
            val team = if (g % 2 == 0) firstTeam else secondTeam
            List(size) {
                val slot = nextSlot.getValue(team)
                nextSlot[team] = slot + 1
                DraftStep(team, DraftAction.PICK, slot, group = g + 1)
            }
        }
    }

    fun phase(board: DraftBoard): DraftPhase {
        val step = nextStep(board) ?: return DraftPhase.DONE
        return if (step.action == DraftAction.BAN) DraftPhase.BAN else DraftPhase.PICK
    }

    /** The first step whose slot is still empty; bans come first unless skipped. */
    fun nextStep(board: DraftBoard): DraftStep? {
        val bans = if (board.bansSkipped) emptyList() else banSteps(board.format)
        return (bans + pickSteps(board.side)).firstOrNull { board.heroAt(it) == null }
    }

    /** All empty slots that are part of the current turn (e.g. both of the enemy's double pick). */
    fun currentTurn(board: DraftBoard): List<DraftStep> {
        val step = nextStep(board) ?: return emptyList()
        val steps = if (step.action == DraftAction.BAN) banSteps(board.format) else pickSteps(board.side)
        return steps.filter {
            it.group == step.group && (step.action == DraftAction.BAN || it.team == step.team) &&
                board.heroAt(it) == null
        }
    }

    fun turnLabel(board: DraftBoard): String {
        val turn = currentTurn(board)
        val first = turn.firstOrNull() ?: return "Draft complete"
        if (first.action == DraftAction.BAN) {
            val total = board.format.bansPerTeam * 2
            val done = banSteps(board.format).count { board.heroAt(it) != null }
            return "Ban phase ($done/$total)"
        }
        val who = if (first.team == "ally") "Your pick" else "Enemy pick"
        return "$who " + turn.joinToString(" & ") { "#${it.slotIndex + 1}" }
    }
}
