package com.omnifile.ui.operations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.omnifile.operations.OperationSnapshot
import com.omnifile.operations.OperationState

@Composable
fun OperationsPanel(
    operations: List<OperationSnapshot>,
    onCancel: (String) -> Unit,
    applyNavigationBarsPadding: Boolean = true,
) {
    if (operations.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (applyNavigationBarsPadding) Modifier.navigationBarsPadding() else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Operations", style = MaterialTheme.typography.titleMedium)
        operations.forEach { operation ->
            HorizontalDivider()
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${operation.type}: ${operation.intendedFinalName}")
                    val progress = operation.expectedBytes?.let {
                        "${operation.bytesCompleted} B / $it B"
                    } ?: "${operation.bytesCompleted} B · size unknown"
                    Text(
                        "${operation.state} · $progress",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    operation.errorMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                if (operation.state in setOf(
                        OperationState.PLANNED,
                        OperationState.TRANSFERRING,
                        OperationState.VERIFYING,
                        OperationState.FINALIZING,
                        OperationState.INTERRUPTED,
                        OperationState.RETRYABLE_FAILURE,
                    )
                ) {
                    Button(onClick = { onCancel(operation.operationId) }) { Text("Cancel") }
                }
            }
        }
    }
}
