package ai.zasha.mlbbpicker.data

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import junit.framework.TestCase.assertFalse
import org.junit.Test

class TeamAnalyzerTest {

    private val miyaMM = Hero(
        id = 1,
        hero_name = "Miya",
        role = "Marksman",
        lane = "Gold Lane",
        speciality = "Reap, Damage",
        img_src = "http://img.com/miya"
    )

    private val belerickTank = Hero(
        id = 2,
        hero_name = "Belerick",
        role = "Tank",
        lane = "Roam",
        speciality = "Crowd Control, Initiator",
        img_src = "http://img.com/belerick"
    )

    private val eudoraMage = Hero(
        id = 3,
        hero_name = "Eudora",
        role = "Mage",
        lane = "Mid Lane",
        speciality = "Burst, Magic Damage",
        img_src = "http://img.com/eudora"
    )

    private val chouFighter = Hero(
        id = 4,
        hero_name = "Chou",
        role = "Fighter",
        lane = "EXP Lane",
        speciality = "Control, Chase",
        img_src = "http://img.com/chou"
    )

    private val rafaelaSupport = Hero(
        id = 5,
        hero_name = "Rafaela",
        role = "Support",
        lane = "Roam",
        speciality = "Guard, Sustain",
        img_src = "http://img.com/rafaela"
    )

    @Test
    fun testEmptyTeam() {
        val analysis = TeamAnalyzer.analyze(emptyList())
        assertEquals(0, analysis.overallScore)
        assertFalse(analysis.hasTank)
    }

    @Test
    fun testBalancedComposition() {
        val allies = listOf(miyaMM, belerickTank, eudoraMage, chouFighter, rafaelaSupport)
        val analysis = TeamAnalyzer.analyze(allies)

        assertTrue(analysis.hasTank)
        assertTrue(analysis.hasSupport)
        assertTrue(analysis.hasMage)
        assertTrue(analysis.hasMarksman)
        assertTrue(analysis.hasFighter)
        assertTrue(analysis.ccCount >= 2)
        assertTrue(analysis.overallScore >= 80)
        assertTrue(analysis.warnings.any { it.message.contains("Balanced composition") })
    }

    @Test
    fun testImbalancedNoTank() {
        val allies = listOf(miyaMM, eudoraMage, chouFighter)
        val analysis = TeamAnalyzer.analyze(allies)

        assertFalse(analysis.hasTank)
        assertTrue(analysis.warnings.any { it.message.contains("No Tank") })
        assertTrue(analysis.overallScore < 70)
    }

    @Test
    fun testAllPhysicalDamage() {
        val allies = listOf(miyaMM, chouFighter, Hero(6, "Alucard", "Fighter, Assassin", "EXP Lane", "Chase, Damage", ""))
        val analysis = TeamAnalyzer.analyze(allies)

        assertEquals(0, analysis.magicCount)
        assertTrue(analysis.warnings.any { it.message.contains("All Physical") })
    }
}
