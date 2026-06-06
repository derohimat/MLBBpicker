package ai.zasha.mlbbpicker.data

import android.content.Context
import android.content.res.AssetManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import java.io.ByteArrayInputStream

class HeroRepositoryTest {

    private lateinit var mockContext: Context
    private lateinit var mockAssetManager: AssetManager
    private lateinit var mockConnectivityManager: ConnectivityManager

    private val mockHeroesJson = """
        [
          {
            "id": 1,
            "hero_name": "Miya",
            "role": "Marksman",
            "lane": "Gold Lane",
            "speciality": "Finisher, Damage",
            "img_src": "http://img.com/miya"
          },
          {
            "id": 70,
            "hero_name": "Belerick",
            "role": "Tank",
            "lane": "Roam",
            "speciality": "Control",
            "img_src": "http://img.com/belerick"
          }
        ]
    """.trimIndent()

    private val mockCountersJson = """
        {
          "1": [
            {
              "id": 70,
              "heroName": "Belerick",
              "imgSrc": "http://img.com/belerick",
              "role": ["Tank"],
              "lane": ["Roam"],
              "speciality": ["Control"],
              "score": 6.5,
              "tier": "A"
            }
          ]
        }
    """.trimIndent()

    private val mockSynergiesJson = """
        {
          "1": [
            {
              "id": 70,
              "heroName": "Belerick",
              "imgSrc": "http://img.com/belerick",
              "role": ["Tank"],
              "lane": ["Roam"],
              "speciality": ["Control"],
              "score": 4.5,
              "synergyHeroes": [
                {
                  "id": 1,
                  "name": "Miya",
                  "imgSrc": "Gold Lane",
                  "lane": ["4.5"],
                  "score": null
                }
              ]
            }
          ]
        }
    """.trimIndent()

    @Before
    fun setUp() {
        mockContext = mock(Context::class.java)
        mockAssetManager = mock(AssetManager::class.java)
        mockConnectivityManager = mock(ConnectivityManager::class.java)

        `when`(mockContext.assets).thenReturn(mockAssetManager)
        `when`(mockContext.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(mockConnectivityManager)

        // Mock assets files
        `when`(mockAssetManager.open("heroes.json")).thenReturn(ByteArrayInputStream(mockHeroesJson.toByteArray()))
        `when`(mockAssetManager.open("counters.json")).thenReturn(ByteArrayInputStream(mockCountersJson.toByteArray()))
        `when`(mockAssetManager.open("synergies.json")).thenReturn(ByteArrayInputStream(mockSynergiesJson.toByteArray()))
    }

    @Test
    fun testLoadHeroes_success() {
        val repository = HeroRepository(mockContext)
        val list = repository.heroes
        assertEquals(2, list.size)
        assertEquals("Miya", list[0].hero_name)
        assertEquals("Belerick", list[1].hero_name)
    }

    @Test
    fun testOfflineCounterCalculation_success() = runTest {
        // Set online to false
        `when`(mockConnectivityManager.activeNetwork).thenReturn(null)

        val repository = HeroRepository(mockContext)
        // Check counters for Miya (ID: 1)
        val recommendations = repository.getCounterSuggestions(listOf(1))

        assertEquals(1, recommendations.size)
        val bestCounter = recommendations[0]
        assertEquals("Belerick", bestCounter.heroName)
        assertEquals(70, bestCounter.id)
        assertEquals(6.5, bestCounter.score)
        assertEquals("A", bestCounter.tier)
        assertEquals(1, bestCounter.counteredHeroes.size)
        assertEquals("Miya", bestCounter.counteredHeroes[0].name)
    }

    @Test
    fun testOfflineSynergyCalculation_success() = runTest {
        // Set online to false
        `when`(mockConnectivityManager.activeNetwork).thenReturn(null)

        val repository = HeroRepository(mockContext)
        // Check synergy with Miya (ID: 1)
        val recommendations = repository.getSynergySuggestions(listOf(1))

        assertEquals(1, recommendations.size)
        val bestSynergy = recommendations[0]
        assertEquals("Belerick", bestSynergy.heroName)
        assertEquals(70, bestSynergy.id)
        assertEquals(4.5, bestSynergy.score)
        assertEquals(1, bestSynergy.synergyHeroes.size)
        assertEquals("Miya", bestSynergy.synergyHeroes[0].name)
        assertEquals(4.5, bestSynergy.synergyHeroes[0].realScore)
    }
}
