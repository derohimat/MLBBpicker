package ai.zasha.mlbbpicker.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.zasha.mlbbpicker.data.Hero
import ai.zasha.mlbbpicker.data.HeroRepository
import ai.zasha.mlbbpicker.data.HeroMetaStats
import ai.zasha.mlbbpicker.data.MetaStatsRepository
import ai.zasha.mlbbpicker.data.DataPatchManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainScreenState(
    val heroes: List<Hero> = emptyList(),
    val metaStats: List<HeroMetaStats> = emptyList(),
    val isServiceRunning: Boolean = false,
    val isDrawOverlayGranted: Boolean = false,
    val isUsageStatsGranted: Boolean = false,
    val autoDetectEnabled: Boolean = true,
    val autoHideEnabled: Boolean = true,
    val isUpdatingPatch: Boolean = false,
    val patchUpdateProgress: Float = 0f,
    val patchUpdateStatus: String = ""
)

class MainScreenViewModel(
    private val repository: HeroRepository,
    private val metaStatsRepository: MetaStatsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainScreenState())
    val uiState: StateFlow<MainScreenState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(heroes = repository.heroes) }
        loadMetaStats()
    }

    private fun loadMetaStats() {
        viewModelScope.launch {
            val stats = metaStatsRepository.getMetaStats()
            _uiState.update { it.copy(metaStats = stats) }
        }
    }

    fun updateStatus(
        isServiceRunning: Boolean,
        isDrawOverlayGranted: Boolean,
        isUsageStatsGranted: Boolean,
        autoDetectEnabled: Boolean,
        autoHideEnabled: Boolean
    ) {
        _uiState.update {
            it.copy(
                isServiceRunning = isServiceRunning,
                isDrawOverlayGranted = isDrawOverlayGranted,
                isUsageStatsGranted = isUsageStatsGranted,
                autoDetectEnabled = autoDetectEnabled,
                autoHideEnabled = autoHideEnabled
            )
        }
    }

    fun triggerPatchUpdate(context: Context) {
        if (_uiState.value.isUpdatingPatch) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isUpdatingPatch = true,
                    patchUpdateProgress = 0f,
                    patchUpdateStatus = "Starting update..."
                )
            }

            val result = DataPatchManager.updatePatches(context.applicationContext) { progress, status ->
                _uiState.update {
                    it.copy(
                        patchUpdateProgress = progress,
                        patchUpdateStatus = status
                    )
                }
            }

            if (result.isSuccess) {
                repository.reload()
                metaStatsRepository.reload()
                _uiState.update {
                    it.copy(
                        isUpdatingPatch = false,
                        patchUpdateStatus = "Update completed successfully!",
                        heroes = repository.heroes,
                        metaStats = metaStatsRepository.offlineStats
                    )
                }
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                _uiState.update {
                    it.copy(
                        isUpdatingPatch = false,
                        patchUpdateStatus = "Update failed: $errorMsg"
                    )
                }
            }
        }
    }

    fun clearPatchUpdate(context: Context) {
        viewModelScope.launch {
            val success = DataPatchManager.clearPatches(context.applicationContext)
            if (success) {
                repository.reload()
                metaStatsRepository.reload()
                _uiState.update {
                    it.copy(
                        patchUpdateStatus = "Local patches cleared. Reloaded default assets.",
                        heroes = repository.heroes,
                        metaStats = metaStatsRepository.offlineStats
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        patchUpdateStatus = "Failed to clear patch files."
                    )
                }
            }
        }
    }
}
