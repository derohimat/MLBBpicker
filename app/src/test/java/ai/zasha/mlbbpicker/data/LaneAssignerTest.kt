package ai.zasha.mlbbpicker.data

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Test

class LaneAssignerTest {

    private fun hero(id: Int, name: String, lane: String) = Hero(
        id = id,
        hero_name = name,
        role = "Fighter",
        lane = lane,
        speciality = "",
        img_src = ""
    )

    private val chou = hero(1, "Chou", "Exp Lane, Roam")
    private val ling = hero(2, "Ling", "Jungle")
    private val kagura = hero(3, "Kagura", "Mid Lane")
    private val tigreal = hero(4, "Tigreal", "Roam")
    private val miya = hero(5, "Miya", "Gold Lane")
    private val fredrinn = hero(6, "Fredrinn", "Jungle, Exp Lane")

    @Test
    fun `parses hero lanes in order`() {
        assertEquals(listOf(Lane.JUNGLE, Lane.EXP), fredrinn.laneList)
        assertEquals(Lane.GOLD, Lane.fromLabel(" Gold Lane "))
    }

    @Test
    fun `standard team gets every hero on its main lane`() {
        val result = LaneAssigner.assign(listOf(chou, ling, kagura, tigreal, miya))

        assertEquals(
            listOf(Lane.EXP, Lane.JUNGLE, Lane.MID, Lane.ROAM, Lane.GOLD),
            result.slots.map { it?.lane }
        )
        assertTrue(result.missingLanes.isEmpty())
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `flex hero moves to secondary lane when main lane is taken`() {
        val result = LaneAssigner.assign(listOf(fredrinn, ling, null, null, null))

        assertEquals(Lane.EXP, result.slots[0]?.lane)
        assertFalse(result.slots[0]!!.isOffLane)
        assertEquals(Lane.JUNGLE, result.slots[1]?.lane)
        assertNull(result.slots[2])
    }

    @Test
    fun `manual lane is kept and flagged off-lane`() {
        val manual = listOf<Lane?>(null, null, null, null, Lane.ROAM)
        val result = LaneAssigner.assign(listOf(null, null, null, null, miya), manual)

        val slot = result.slots[4]!!
        assertEquals(Lane.ROAM, slot.lane)
        assertTrue(slot.isManual)
        assertTrue(slot.isOffLane)
        assertTrue(result.warnings.any { it.message.contains("off-lane") })
    }

    @Test
    fun `manual lane on empty slot is ignored`() {
        val manual = listOf<Lane?>(Lane.GOLD, null, null, null, null)
        val result = LaneAssigner.assign(listOf(null, null, null, null, null), manual)

        assertTrue(result.slots.all { it == null })
        assertEquals(Lane.entries.toList(), result.missingLanes)
    }

    @Test
    fun `two junglers - one is sent off-lane instead of doubling jungle`() {
        val otherJungler = hero(7, "Hayabusa", "Jungle")
        val result = LaneAssigner.assign(listOf(ling, otherJungler, kagura, tigreal, miya))

        assertTrue(result.missingLanes.isEmpty())
        assertEquals(1, result.slots.count { it?.lane == Lane.JUNGLE })
        assertEquals(1, result.slots.count { it?.isOffLane == true })
        assertTrue(result.warnings.any { it.message.contains("off-lane in EXP") })
    }

    @Test
    fun `partial team lists lanes still needed`() {
        val result = LaneAssigner.assign(listOf(ling, kagura, tigreal, null, null))

        assertEquals(listOf(Lane.EXP, Lane.GOLD), result.missingLanes)
        assertTrue(result.warnings.any { it.message == "Need: EXP, GOLD" && it.severity == WarningSeverity.INFO })
    }

    @Test
    fun `auto hero avoids a lane taken manually`() {
        val manual = listOf<Lane?>(Lane.JUNGLE, null, null, null, null)
        val result = LaneAssigner.assign(listOf(chou, ling, null, null, null), manual)

        assertEquals(Lane.JUNGLE, result.slots[0]?.lane)
        assertTrue(result.slots[1]!!.lane != Lane.JUNGLE)
    }
}
