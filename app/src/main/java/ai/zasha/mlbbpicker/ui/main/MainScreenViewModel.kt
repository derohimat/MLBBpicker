package ai.zasha.mlbbpicker.ui.main

import androidx.lifecycle.ViewModel
import ai.zasha.mlbbpicker.data.Hero
import ai.zasha.mlbbpicker.data.HeroRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class MainScreenState(
    val heroes: List<Hero> = emptyList(),
    val isServiceRunning: Boolean = false,
    val isDrawOverlayGranted: Boolean = false,
    val isUsageStatsGranted: Boolean = false,
    val autoDetectEnabled: Boolean = true,
    val autoHideEnabled: Boolean = true
)

class MainScreenViewModel(private val repository: HeroRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(MainScreenState())
    val uiState: StateFlow<MainScreenState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(heroes = repository.heroes) }
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
}
