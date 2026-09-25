package ai.zasha.mlbbpicker.data

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test

class EnemyPredictorTest {

    private fun hero(id: Int, name: String, lane: String) = Hero(id, name, "Fighter", lane, "", "img$id")

    private fun stats(id: Int, pick: Double, ban: Double, wr: Double = 50.0) =
        HeroMetaStats(heroId = id, heroName = "h$id", imgSrc = "", pickRate = pick, banRate = ban, winRate = wr)

    private val ling = hero(1, "Ling", "Jungle")
    private val miya = hero(2, "Miya", "Gold Lane")
    private val layla = hero(3, "Layla", "Gold Lane")
    private val fanny = hero(4, "Fanny", "Jungle")
    private val kagura = hero(5, "Kagura", "Mid Lane")

    private val heroes = listOf(ling, miya, layla, fanny, kagura)
    private val noEnemies = List<Hero?>(5) { null }

    @Test
    fun `picked and banned heroes are never predicted`() {
        val result = EnemyPredictor.predict(
            heroes, emptyList(), noEnemies, excludeIds = setOf(ling.id, miya.id)
        )

        assertFalse(result.any { it.heroId == ling.id || it.heroId == miya.id })
        assertEquals(3, result.size)
    }

    @Test
    fun `empty draft follows the meta`() {
        val meta = listOf(stats(1, pick = 2.0, ban = 1.0), stats(4, pick = 10.0, ban = 30.0), stats(5, pick = 5.0, ban = 5.0))
        val result = EnemyPredictor.predict(heroes, meta, noEnemies, emptySet())

        assertEquals(listOf("Fanny", "Kagura", "Ling"), result.take(3).map { it.heroName })
        assertTrue(result.first().reasons.any { it.startsWith("Ban") })
    }

    @Test
    fun `hero that counters our picks ranks higher`() {
        val meta = listOf(stats(3, 5.0, 5.0), stats(4, 5.0, 5.0))
        val threats = listOf(
            CounterSuggestion(
                id = 4, heroName = "Fanny", imgSrc = "", score = 7.0,
                counteredHeroes = listOf(
                    CounteredHero(2, "Miya", "", 7.0),
                    CounteredHero(5, "Kagura", "", 0.0)
                )
            )
        )
        val result = EnemyPredictor.predict(
            listOf(layla, fanny), meta, noEnemies, emptySet(), counterThreats = threats
        )

        assertEquals("Fanny", result.first().heroName)
        assertTrue(result.first().reasons.contains("Counters Miya"))
    }

    @Test
    fun `hero filling the enemy's missing lane ranks higher`() {
        val enemies = listOf<Hero?>(hero(10, "Chou", "Exp Lane"), ling, kagura, hero(11, "Tigreal", "Roam"), null)
        val meta = listOf(stats(3, 5.0, 5.0), stats(4, 5.0, 5.0))
        val result = EnemyPredictor.predict(listOf(layla, fanny), meta, enemies, emptySet())

        assertEquals("Layla", result.first().heroName)
        assertTrue(result.first().reasons.contains("Fills GOLD"))
    }

    @Test
    fun `scores stay within 0 to 100 and are limited`() {
        val result = EnemyPredictor.predict(heroes, emptyList(), noEnemies, emptySet(), limit = 2)

        assertEquals(2, result.size)
        assertTrue(result.all { it.score in 0.0..100.0 })
    }
}
