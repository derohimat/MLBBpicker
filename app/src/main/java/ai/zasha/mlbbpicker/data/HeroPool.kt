package ai.zasha.mlbbpicker.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The heroes the player is comfortable with, saved across sessions. */
object HeroPool {

    private const val PREFS_NAME = "mlbb_picker_prefs"
    private const val KEY_POOL = "pref_hero_pool"

    var ids by mutableStateOf(emptySet<Int>())
        private set

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val saved = prefs(context).getString(KEY_POOL, null)
        ids = saved.orEmpty().split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
    }

    fun contains(heroId: Int): Boolean = heroId in ids

    fun toggle(context: Context, heroId: Int) {
        load(context)
        ids = if (heroId in ids) ids - heroId else ids + heroId
        prefs(context).edit().putString(KEY_POOL, ids.joinToString(",")).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
