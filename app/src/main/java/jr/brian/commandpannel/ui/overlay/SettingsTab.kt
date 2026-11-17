package jr.brian.commandpannel.ui.overlay

import jr.brian.commandpannel.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import jr.brian.commandpannel.ui.components.noRippleClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import jr.brian.commandpannel.system.OverlayCardGroup
import jr.brian.commandpannel.system.OverlayCardId
import jr.brian.commandpannel.ui.components.RingGauge
import jr.brian.commandpannel.ui.components.TccCard
import jr.brian.commandpannel.ui.components.TccIconButton
import jr.brian.commandpannel.ui.components.rememberStockPainter
import jr.brian.commandpannel.ui.components.stockIconColorFilter
import jr.brian.commandpannel.ui.theme.LocalTccColors
import jr.brian.commandpannel.ui.theme.RingColors
import jr.brian.commandpannel.ui.theme.TempColors

/**
 * Stock Settings tab: frame-rate panel (left) and quick-settings grid (right) across the top 70%, with the hardware
 * telemetry row across the bottom 30%.
 */
@Composable
fun SettingsTab(
    state: OverlayUiState,
    actions: OverlayActions,
    onTile: (String) -> Unit,
    arrange: jr.brian.commandpannel.ui.overlay.arrange.ArrangeController,
    modifier: Modifier = Modifier,
) {
    OverlayGrid(OverlayCardGroup.SETTINGS_TAB, state.layout.settingsCells, arrange, modifier) { card, m ->
        when (card) {
            OverlayCardId.FRAME_RATE -> FrameRatePanel(state, actions, m)
            OverlayCardId.QUICK_TILES -> QuickTilesPanel(state, onTile, m)
            OverlayCardId.RINGS -> RingsCard(state, m)
            OverlayCardId.TEMP -> TempFanCard(state, m)
            else -> Unit
        }
    }
}

@Composable
internal fun FrameRatePanel(state: OverlayUiState, actions: OverlayActions, modifier: Modifier) {
    val colors = LocalTccColors.current
    val displayStyle = jr.brian.commandpannel.ui.theme.tccDisplayTextStyle()
    TccCard(modifier) {
        // Tapping the whole card toggles the refresh rate; the rate shows as a small Hz label in the top-left corner
        // (same text style as the FPS counter) to save space. In a big/tall card the counter grows to fill it.
        BoxWithConstraints(Modifier.fillMaxSize().noRippleClickable { actions.toggleRefreshRate() }.padding(14.dp)) {
            val big = maxHeight > maxWidth * 0.52f
            val numberSp = if (big) 76.sp else 46.sp
            val fpsSp = if (big) 24.sp else 15.sp
            val hzSp = if (big) 22.sp else 16.sp
            Text(
                stringResource(R.string.overlay_refresh_hz, if (state.is120Hz) 120 else 60),
                color = colors.primary,
                fontSize = hzSp,
                fontWeight = FontWeight.Bold,
                style = displayStyle,
                modifier = Modifier.align(Alignment.TopStart),
            )
            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.Bottom) {
                Text("${state.fps}", color = colors.primary, fontSize = numberSp, fontWeight = FontWeight.Bold, maxLines = 1, style = displayStyle)
                Text(stringResource(R.string.overlay_fps_suffix), color = colors.primary, fontSize = fpsSp, modifier = Modifier.padding(bottom = if (big) 14.dp else 10.dp))
            }
        }
    }
}

@Composable
internal fun QuickTilesPanel(state: OverlayUiState, onTile: (String) -> Unit, modifier: Modifier) {
    val colors = LocalTccColors.current
    val pages = state.tiles.chunked(8)
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { pages.size.coerceAtLeast(1) })
    TccCard(modifier) {
        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp)) {
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { pageIndex ->
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    items(pages.getOrElse(pageIndex) { emptyList() }, key = { it.id }) { tile ->
                        TccIconButton(
                            icon = tile.icon,
                            label = tile.title,
                            // Without names the tiles grow into the freed space.
                            circleSize = if (state.hideTileLabels) 50.dp else 48.dp,
                            active = tile.active,
                            labelSize = 10.sp,
                            showLabel = !state.hideTileLabels,
                            onClick = { onTile(tile.id) },
                        )
                    }
                }
            }
            if (pages.size > 1) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    repeat(pages.size) { i ->
                        Box(
                            Modifier.padding(horizontal = 4.dp).size(8.dp).clip(CircleShape)
                                .background(if (i == pagerState.currentPage) colors.accent else colors.secondary)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun RingsCard(state: OverlayUiState, modifier: Modifier) {
    val cpu: @Composable (Modifier) -> Unit = { m -> RingGauge(state.cpuFraction, RingColors.Cpu, stringResource(R.string.gauge_cpu), "%.2f".format(state.cpuGhz), stringResource(R.string.unit_ghz), m) }
    val gpu: @Composable (Modifier) -> Unit = { m -> RingGauge(state.gpuFraction, RingColors.Gpu, stringResource(R.string.gauge_gpu), "${state.gpuMhz}", stringResource(R.string.unit_mhz), m) }
    val pwr: @Composable (Modifier) -> Unit = { m -> RingGauge(state.powerFraction, RingColors.Pwr, stringResource(R.string.gauge_pwr), "%.2f".format(state.powerW), stringResource(R.string.unit_watts), m) }
    val ram: @Composable (Modifier) -> Unit = { m -> RingGauge(state.ramFraction, RingColors.Ram, stringResource(R.string.gauge_ram), "%.2f".format(state.ramGb), stringResource(R.string.unit_gb), m) }
    TccCard(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
            // In a tall/square card (e.g. moved to its own big slot) fill the space with a 2x2 grid; a wide strip stays 1x4.
            if (maxHeight > maxWidth * 0.52f) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        cpu(Modifier.weight(1f).fillMaxHeight()); gpu(Modifier.weight(1f).fillMaxHeight())
                    }
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pwr(Modifier.weight(1f).fillMaxHeight()); ram(Modifier.weight(1f).fillMaxHeight())
                    }
                }
            } else {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    cpu(Modifier.weight(1f).fillMaxHeight()); gpu(Modifier.weight(1f).fillMaxHeight())
                    pwr(Modifier.weight(1f).fillMaxHeight()); ram(Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

/** Combined TEMP + FAN readings in one card, so the hardware strip is a single row (frees height for quick tiles).
 *  Adapts like the rings: stacked rows in a compact strip, two side-by-side stat columns in a tall/big card. */
@Composable
internal fun TempFanCard(state: OverlayUiState, modifier: Modifier) {
    TccCard(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
            if (maxHeight > maxWidth * 0.52f) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBlock(icon = "ic_temp", iconTint = TempColors.forCelsius(state.tempC), title = stringResource(R.string.overlay_temp), value = stringResource(R.string.unit_celsius, state.tempC))
                    StatBlock(icon = "ic_fan_normal", iconTint = null, title = stringResource(R.string.overlay_fan), value = stringResource(R.string.unit_percent, state.fanPercent))
                }
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                    InfoRow(icon = "ic_temp", iconTint = TempColors.forCelsius(state.tempC), title = stringResource(R.string.overlay_temp), value = stringResource(R.string.unit_celsius, state.tempC))
                    InfoRow(icon = "ic_fan_normal", iconTint = null, title = stringResource(R.string.overlay_fan), value = stringResource(R.string.unit_percent, state.fanPercent))
                }
            }
        }
    }
}

/** One stat as a vertical block (icon + label on top, big value below) — the tall-card form of a temp/fan reading. */
@Composable
private fun RowScope.StatBlock(icon: String, iconTint: Color?, title: String, value: String) {
    val colors = LocalTccColors.current
    Column(
        Modifier.weight(1f).fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = rememberStockPainter(icon),
                contentDescription = title,
                colorFilter = stockIconColorFilter(iconTint ?: colors.tertiary),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(6.dp))
            Text(title, color = colors.tertiary, fontSize = 13.sp)
        }
        Text(
            value,
            color = colors.primary,
            fontSize = (28 * jr.brian.commandpannel.ui.theme.LocalTccLookSpec.current.settingsTab.infoValueScale).sp,
            fontWeight = FontWeight.Medium,
            style = jr.brian.commandpannel.ui.theme.tccDisplayTextStyle(),
        )
    }
}

@Composable
private fun InfoRow(icon: String, iconTint: Color?, title: String, value: String) {
    val colors = LocalTccColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = rememberStockPainter(icon),
            contentDescription = title,
            colorFilter = stockIconColorFilter(iconTint ?: colors.tertiary),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(6.dp))
        Text(title, color = colors.tertiary, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            color = colors.primary,
            fontSize = (20 * jr.brian.commandpannel.ui.theme.LocalTccLookSpec.current.settingsTab.infoValueScale).sp,
            fontWeight = FontWeight.Medium,
            style = jr.brian.commandpannel.ui.theme.tccDisplayTextStyle(),
        )
    }
}
