package io.github.aldrinadira.jalanz.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.aldrinadira.jalanz.core.designsystem.JalanzTheme
import io.github.aldrinadira.jalanz.core.designsystem.R as DesignR

/** Required attribution for every data source the app uses (NFR-10, spec 05 §5). Add a line with each new source. */
private val attributionLines = listOf(
    R.string.attribution_osm,
    R.string.attribution_openfreemap,
    R.string.attribution_photon,
    R.string.attribution_valhalla,
    R.string.attribution_tolls,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttributionScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.attribution_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(DesignR.drawable.ic_arrow_back), stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(attributionLines) { line ->
                Text(stringResource(line), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, locale = "ms")
@Composable
private fun AttributionScreenPreview() {
    JalanzTheme {
        AttributionScreen(onBack = {})
    }
}
