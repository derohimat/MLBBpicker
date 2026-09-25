package ai.zasha.mlbbpicker.data

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.Test

class DataVersionTest {

    private val json = """
        {
          "game_patch": "1.9.20",
          "generated_at": "2026-09-01T10:00:00Z",
          "timeframe": "Past 1 day",
          "ranks": [
            { "id": 2, "name": "Epic", "file": "meta_stats_epic.json" },
            { "id": 4, "name": "Mythic", "file": "meta_stats.json" },
            { "id": 5, "name": "Mythical Honor", "file": "meta_stats_mythical_honor.json" }
          ],
          "extra_field": true
        }
    """.trimIndent()

    @Test
    fun `parses version file and ignores unknown fields`() {
        val v = DataVersion.parse(json)

        assertEquals("Patch 1.9.20", v.patchLabel)
        assertEquals("2026-09-01", v.generatedDate)
        assertEquals(listOf("Epic", "Mythic", "Mythical Honor"), v.availableRanks.map { it.name })
    }

    @Test
    fun `missing or broken file falls back to bundled Mythic`() {
        listOf(null, "", "not json").forEach { text ->
            val v = DataVersion.parse(text)
            assertEquals(listOf(DataVersion.DEFAULT_RANK), v.availableRanks)
            assertEquals("Patch ?", v.patchLabel)
            assertNull(v.generatedDate)
        }
    }

    @Test
    fun `unknown rank id falls back to first rank`() {
        val v = DataVersion.parse(json)

        assertEquals("meta_stats.json", v.rankFor(4).file)
        assertEquals("Epic", v.rankFor(99).name)
    }

    @Test
    fun `next rank wraps around`() {
        val v = DataVersion.parse(json)

        assertEquals(5, v.nextRank(4).id)
        assertEquals(2, v.nextRank(5).id)
        assertEquals(DataVersion.DEFAULT_RANK, DataVersion().nextRank(4))
    }

    @Test
    fun `bundled data_version asset parses`() {
        val text = java.io.File("../app/src/main/assets/data_version.json").takeIf { it.exists() }?.readText()
            ?: java.io.File("src/main/assets/data_version.json").takeIf { it.exists() }?.readText()
            ?: return
        val v = DataVersion.parse(text)
        assertEquals("Mythic", v.rankFor(4).name)
    }
}
