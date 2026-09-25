package ai.zasha.mlbbpicker.data

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.Test

class DraftOrderTest {

    private fun hero(id: Int) = Hero(id, "Hero$id", "Fighter", "Exp Lane", "", "")

    private fun empty() = MutableList<Hero?>(5) { null }

    private fun board(
        format: DraftFormat = DraftFormat.RANKED_6,
        side: DraftSide = DraftSide.BLUE,
        allyBans: List<Hero?> = empty(),
        enemyBans: List<Hero?> = empty(),
        allies: List<Hero?> = empty(),
        enemies: List<Hero?> = empty(),
        bansSkipped: Boolean = false
    ) = DraftBoard(format, side, allyBans, enemyBans, allies, enemies, bansSkipped)

    private fun List<DraftStep>.codes() = map { "${it.team}${it.slotIndex}" }

    @Test
    fun `blue side picks 1-2-2-2-2-1 starting with us`() {
        assertEquals(
            listOf("ally0", "enemy0", "enemy1", "ally1", "ally2", "enemy2", "enemy3", "ally3", "ally4", "enemy4"),
            DraftOrder.pickSteps(DraftSide.BLUE).codes()
        )
    }

    @Test
    fun `red side mirrors the pick order`() {
        assertEquals(
            listOf("enemy0", "ally0", "ally1", "enemy1", "enemy2", "ally2", "ally3", "enemy3", "enemy4", "ally4"),
            DraftOrder.pickSteps(DraftSide.RED).codes()
        )
    }

    @Test
    fun `ban count follows the format`() {
        assertEquals(6, DraftOrder.banSteps(DraftFormat.RANKED_6).size)
        assertEquals(10, DraftOrder.banSteps(DraftFormat.RANKED_10).size)
    }

    @Test
    fun `draft starts in the ban phase with every ban slot on turn`() {
        val b = board(format = DraftFormat.RANKED_10)

        assertEquals(DraftPhase.BAN, DraftOrder.phase(b))
        assertEquals(10, DraftOrder.currentTurn(b).size)
        assertEquals("Ban phase (0/10)", DraftOrder.turnLabel(b))
    }

    @Test
    fun `skipping bans goes straight to the first pick`() {
        val b = board(bansSkipped = true)

        assertEquals(DraftPhase.PICK, DraftOrder.phase(b))
        assertEquals("Your pick #1", DraftOrder.turnLabel(b))
    }

    @Test
    fun `after bans and our first pick the enemy double-picks`() {
        val bans = MutableList<Hero?>(5) { null }.apply { this[0] = hero(90); this[1] = hero(91); this[2] = hero(92) }
        val enemyBans = MutableList<Hero?>(5) { null }.apply { this[0] = hero(93); this[1] = hero(94); this[2] = hero(95) }
        val allies = empty().apply { this[0] = hero(1) }
        val b = board(allyBans = bans, enemyBans = enemyBans, allies = allies)

        assertEquals(listOf("enemy0", "enemy1"), DraftOrder.currentTurn(b).codes())
        assertEquals("Enemy pick #1 & #2", DraftOrder.turnLabel(b))
    }

    @Test
    fun `second slot of a double pick stays on turn`() {
        val enemies = empty().apply { this[0] = hero(10) }
        val allies = empty().apply { this[0] = hero(1) }
        val b = board(side = DraftSide.BLUE, allies = allies, enemies = enemies, bansSkipped = true)

        assertEquals(listOf("enemy1"), DraftOrder.currentTurn(b).codes())
    }

    @Test
    fun `full board is done`() {
        val allies = MutableList<Hero?>(5) { hero(it) }
        val enemies = MutableList<Hero?>(5) { hero(10 + it) }
        val b = board(allies = allies, enemies = enemies, bansSkipped = true)

        assertEquals(DraftPhase.DONE, DraftOrder.phase(b))
        assertNull(DraftOrder.nextStep(b))
        assertEquals("Draft complete", DraftOrder.turnLabel(b))
    }
}
