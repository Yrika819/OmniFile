package com.omnifile.operations

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OperationsViewModel(
    private val repository: OperationRepository,
    private val manager: OperationManager,
) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _operations = MutableStateFlow<List<OperationSnapshot>>(emptyList())
    val operations: StateFlow<List<OperationSnapshot>> = _operations.asStateFlow()
    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch(Dispatchers.IO) {
            _operations.value = repository.findRecent()
        }
    }

    fun cancel(operationId: String) {
        scope.launch(Dispatchers.IO) {
            manager.requestCancellation(operationId)
            refresh()
        }
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }
}
