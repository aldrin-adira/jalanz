package io.github.aldrinadira.jalanz

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aldrinadira.jalanz.core.map.JalanzMap
import io.github.aldrinadira.jalanz.feature.settings.AttributionScreen
import io.github.aldrinadira.jalanz.core.designsystem.R as DesignR

/** Top-level phone UI. Two screens for now, so plain state instead of a navigation library. */
@Composable
fun JalanzApp() {
    var showAttribution by rememberSaveable { mutableStateOf(false) }
    if (showAttribution) {
        BackHandler { showAttribution = false }
        AttributionScreen(onBack = { showAttribution = false })
    } else {
        MapHome(onShowAttribution = { showAttribution = true })
    }
}

@Composable
private fun MapHome(onShowAttribution: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        JalanzMap(Modifier.fillMaxSize())
        SmallFloatingActionButton(
            onClick = onShowAttribution,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .safeDrawingPadding()
                .padding(12.dp),
        ) {
            Icon(painterResource(DesignR.drawable.ic_info), stringResource(R.string.action_data_sources))
        }
    }
}
