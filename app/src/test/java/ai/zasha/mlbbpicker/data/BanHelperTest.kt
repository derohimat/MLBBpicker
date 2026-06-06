package ai.zasha.mlbbpicker.data

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Test

class BanHelperTest {

    @Test
    fun testBanHelperScoring() {
        val stats = listOf(
            HeroMetaStats(
                heroId = 1,
                heroName = "Miya",
                imgSrc = "http://img.com/miya",
                winRate = 55.0,
                banRate = 50.0,
                pickRate = 4.0
            ),
            HeroMetaStats(
                heroId = 2,
                heroName = "Belerick",
                imgSrc = "http://img.com/belerick",
                winRate = 48.0,
                banRate = 5.0,
                pickRate = 1.0
            )
        )

        val recommendations = BanHelper.getRecommendedBans(stats, count = 2)

        assertEquals(2, recommendations.size)
        val miyaRecommendation = recommendations.find { it.heroId == 1 }!!
        assertEquals(38.2, miyaRecommendation.banScore, 0.01)
        assertTrue(miyaRecommendation.reason.contains("Very High WR"))
        assertTrue(miyaRecommendation.reason.contains("Most Banned"))

        assertEquals(1, recommendations[0].heroId)
    }

    @Test
    fun testBanHelperExclusion() {
        val stats = listOf(
            HeroMetaStats(1, "Miya", "", winRate = 55.0, banRate = 50.0, pickRate = 4.0),
            HeroMetaStats(2, "Belerick", "", winRate = 48.0, banRate = 5.0, pickRate = 1.0)
        )

        val recommendations = BanHelper.getRecommendedBans(stats, count = 1, excludeHeroIds = setOf(1))

        assertEquals(1, recommendations.size)
        assertEquals(2, recommendations[0].heroId)
    }
}
