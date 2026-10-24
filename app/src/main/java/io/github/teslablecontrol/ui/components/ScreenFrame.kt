@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.teslablecontrol.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.teslablecontrol.AppContainer
import io.github.teslablecontrol.R
import io.github.teslablecontrol.core.ControllerState
import io.github.teslablecontrol.core.LinkStatus
import io.github.teslablecontrol.keys.KeyManager
import io.github.teslablecontrol.ui.AddCarRoute
import io.github.teslablecontrol.ui.MainTopBar
import io.github.teslablecontrol.ui.MainViewModel
import io.github.teslablecontrol.ui.Navigator
import io.github.teslablecontrol.ui.format.bannerDetail
import io.github.teslablecontrol.ui.format.bannerTitle
import io.github.teslablecontrol.ui.format.bannerVariants
import io.github.teslablecontrol.ui.theme.LocalStatusColors

/** The frame of the five main screens: top bar, status banner, pull-to-refresh, scrolling content. */
@Composable
fun MainScreenFrame(
    viewModel: MainViewModel,
    navigator: Navigator,
    title: String,
    onRefresh: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state by viewModel.controllerState.collectAsState()
    val keyStatus by viewModel.keyStatus.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val keyProblem = when {
        profile?.vin == AppContainer.DEMO_PROFILE.vin -> null
        keyStatus == KeyManager.Status.UNUSABLE -> R.string.home_key_unusable_title
        state.keyPaired == false || profile?.paired == false -> R.string.status_not_paired
        else -> null
    }
    Scaffold(
        topBar = { MainTopBar(viewModel, navigator, title) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.activity?.commandId == "refresh",
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatusBanner(
                        state,
                        keyProblem = keyProblem?.let { stringResource(it) },
                        onConnect = { viewModel.refreshBody() },
                        onRefresh = onRefresh,
                        onWake = { viewModel.send("wake") },
                        onPair = { navigator.to(AddCarRoute) },
                    )
                    content()
                    Spacer(Modifier.size(24.dp))
                }
            }
        }
    }
}

/**
 * Connection and car status in words, with a coloured dot as a secondary cue. Changes are announced
 * politely by TalkBack. The button connects, wakes a sleeping car or refreshes; it keeps its place
 * and width whatever it says, and the status keeps two lines, so nothing below ever moves.
 */
@Composable
fun StatusBanner(
    state: ControllerState,
    keyProblem: String?,
    onConnect: () -> Unit,
    onRefresh: () -> Unit,
    onWake: () -> Unit,
    onPair: () -> Unit,
) {
    val context = LocalContext.current
    val status = LocalStatusColors.current
    val dot = when {
        keyProblem != null -> MaterialTheme.colorScheme.error
        state.link != LinkStatus.CONNECTED -> MaterialTheme.colorScheme.outline
        state.snapshot.isLocked == false -> status.warning
        else -> status.success
    }
    val connect = stringResource(R.string.action_connect)
    val refresh = stringResource(R.string.action_refresh)
    val wake = stringResource(R.string.action_wake)
    val pair = stringResource(R.string.action_pair_short)
    val asleep = state.link == LinkStatus.CONNECTED && state.snapshot.isAsleep == true
    val (titles, details) = remember(context) { bannerVariants(context) }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        // The headline gets the full width; the sentence and the button share the second row. Each
        // keeps room for its longest version, so the banner never resizes.
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp).semantics(mergeDescendants = false) {},
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                    if (state.activity != null) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.5.dp)
                    } else {
                        Box(Modifier.size(14.dp).background(dot, CircleShape))
                    }
                }
                Spacer(Modifier.width(12.dp))
                StableText(keyProblem ?: bannerTitle(context, state), titles, style = MaterialTheme.typography.titleMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                StableText(
                    if (keyProblem != null) stringResource(R.string.banner_detail_pair) else bannerDetail(context, state),
                    details,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(start = 30.dp),
                )
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = when {
                        keyProblem != null -> onPair
                        state.link != LinkStatus.CONNECTED -> onConnect
                        asleep -> onWake
                        else -> onRefresh
                    },
                    enabled = state.activity == null || keyProblem != null,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    StableText(
                        when {
                            keyProblem != null -> pair
                            state.link != LinkStatus.CONNECTED -> connect
                            asleep -> wake
                            else -> refresh
                        },
                        listOf(connect, refresh, wake, pair),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** A dot that never carries meaning on its own; used next to words. */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(12.dp).background(color, CircleShape))
}
