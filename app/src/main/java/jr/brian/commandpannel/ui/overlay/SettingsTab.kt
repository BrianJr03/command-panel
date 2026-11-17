package jr.brian.commandpannel.ui.overlay

import androidx.compose.foundation.background
import jr.brian.commandpannel.ui.components.noRippleClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
            OverlayCardId.TEMP -> InfoCard(m, icon = "ic_temp", iconTint = TempColors.forCelsius(state.tempC), title = "TEMP", value = "${state.tempC}℃")
            OverlayCardId.FAN -> InfoCard(m, icon = "ic_fan_normal", iconTint = null, title = "FAN", value = "${state.fanPercent}%")
            else -> Unit
        }
    }
}

@Composable
private fun FrameRatePanel(state: OverlayUiState, actions: OverlayActions, modifier: Modifier) {
    val colors = LocalTccColors.current
    TccCard(modifier) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier.noRippleClickable { actions.toggleRefreshRate() },
            ) { FrameRatePill(state.is120Hz) }
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${state.fps}", color = colors.primary, fontSize = 46.sp, fontWeight = FontWeight.Bold, style = jr.brian.commandpannel.ui.theme.tccDisplayTextStyle())
                Text(" FPS", color = colors.primary, fontSize = 15.sp, modifier = Modifier.padding(bottom = 10.dp))
            }
            Text("CURRENT FPS", color = colors.panelText, fontSize = 11.sp)
        }
    }
}

/** The "60/120 FPS MODE" pill (stock `bg_60/bg_120_frame_rate_mode` gradients), rendered in Compose. */
@Composable
private fun FrameRatePill(is120: Boolean) {
    // Single flat fill (no gradient, no fade) so the pill reads as a solid filled badge.
    val fill = if (is120) Color(0xFFD11AFF) else Color(0xFF1BBDDE)
    Box(
        modifier = Modifier
            .size(width = 150.dp, height = 34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (is120) "120 FPS MODE" else "60 FPS MODE",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun QuickTilesPanel(state: OverlayUiState, onTile: (String) -> Unit, modifier: Modifier) {
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
                            circleSize = 48.dp,
                        active = tile.active,
                        labelSize = 10.sp,
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
private fun RingsCard(state: OverlayUiState, modifier: Modifier) {
    TccCard(modifier) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RingGauge(state.cpuFraction, RingColors.Cpu, "CPU", "%.2f".format(state.cpuGhz), "GHz", Modifier.weight(1f).fillMaxHeight())
            RingGauge(state.gpuFraction, RingColors.Gpu, "GPU", "${state.gpuMhz}", "MHz", Modifier.weight(1f).fillMaxHeight())
            RingGauge(state.powerFraction, RingColors.Pwr, "PWR", "%.2f".format(state.powerW), "W", Modifier.weight(1f).fillMaxHeight())
            RingGauge(state.ramFraction, RingColors.Ram, "RAM", "%.2f".format(state.ramGb), "GB", Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun InfoCard(modifier: Modifier, icon: String, iconTint: Color?, title: String, value: String) {
    val colors = LocalTccColors.current
    TccCard(modifier) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceEvenly) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = rememberStockPainter(icon),
                    contentDescription = title,
                    colorFilter = stockIconColorFilter(iconTint ?: colors.tertiary),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(title, color = colors.tertiary, fontSize = 12.sp)
            }
            Text(value, color = colors.primary, fontSize = (22 * jr.brian.commandpannel.ui.theme.LocalTccLookSpec.current.settingsTab.infoValueScale).sp, fontWeight = FontWeight.Medium, style = jr.brian.commandpannel.ui.theme.tccDisplayTextStyle())
        }
    }
}
