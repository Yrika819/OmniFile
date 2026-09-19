package com.omnifile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.omnifile.ui.theme.OmniFileTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OmniFileTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ProductionScaffold()
                }
            }
        }
    }
}

@Composable
private fun ProductionScaffold() {
    Text(text = stringResource(R.string.production_scaffold_operational))
}
