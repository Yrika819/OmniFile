package com.omnifile.ui.home

import androidx.lifecycle.ViewModel
import com.omnifile.storage.SupportedRootRegistry
import com.omnifile.storage.SupportedRootSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Ready(val snapshot: SupportedRootSnapshot) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

class HomeViewModel(private val registry: SupportedRootRegistry) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        scope.launch(Dispatchers.IO) {
            runCatching { registry.snapshot() }
                .onSuccess { _state.value = HomeUiState.Ready(it) }
                .onFailure { _state.value = HomeUiState.Error("Storage sources could not be loaded.") }
        }
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }
}
