package ai.zasha.mlbbpicker.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Holds the data version info and the rank the user wants meta stats for. */
object MetaRankStore {

    private const val TAG = "MetaRankStore"
    private const val PREFS_NAME = "mlbb_picker_prefs"
    private const val KEY_RANK = "pref_meta_rank_id"

    private val _dataVersion = MutableStateFlow(DataVersion())
    val dataVersion: StateFlow<DataVersion> = _dataVersion.asStateFlow()

    private val _selectedRankId = MutableStateFlow(DataVersion.DEFAULT_RANK.id)
    val selectedRankId: StateFlow<Int> = _selectedRankId.asStateFlow()

    private var loaded = false

    val selectedRank: MetaRank
        get() = _dataVersion.value.rankFor(_selectedRankId.value)

    /** Load data_version.json and the saved rank; [force] re-reads after a data patch update. */
    fun load(context: Context, force: Boolean = false) {
        if (loaded && !force) return
        loaded = true
        val text = try {
            DataPatchManager.getLocalFileText(context, DataVersion.FILE_NAME)
        } catch (e: Exception) {
            Log.w(TAG, "No ${DataVersion.FILE_NAME}, using defaults")
            null
        }
        val version = DataVersion.parse(text)
        _dataVersion.value = version
        val saved = prefs(context).getInt(KEY_RANK, DataVersion.DEFAULT_RANK.id)
        _selectedRankId.value = version.rankFor(saved).id
    }

    fun selectRank(context: Context, rankId: Int) {
        load(context)
        val id = _dataVersion.value.rankFor(rankId).id
        prefs(context).edit().putInt(KEY_RANK, id).apply()
        _selectedRankId.value = id
    }

    fun cycleRank(context: Context) {
        load(context)
        selectRank(context, _dataVersion.value.nextRank(_selectedRankId.value).id)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
