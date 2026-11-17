package jr.brian.commandpannel.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    modifier: Modifier = Modifier,
) {
    val layout = state.layout
    // Arrange mode: top row and hardware row can swap vertically (rows keep their weights), the two columns can
    // swap horizontally (columns keep their weights), and the temp/fan quarters can swap.
    val frameRate: @Composable (Modifier) -> Unit = { m -> FrameRatePanel(state, actions, m) }
    val quickTiles: @Composable (Modifier) -> Unit = { m -> QuickTilesPanel(state, onTile, m) }
    val topRow: @Composable (Modifier) -> Unit = { m ->
        Row(m, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (layout.settingsColsSwapped) {
                quickTiles(Modifier.weight(0.60f).fillMaxHeight())
                frameRate(Modifier.weight(0.40f).fillMaxHeight())
            } else {
                frameRate(Modifier.weight(0.40f).fillMaxHeight())
                quickTiles(Modifier.weight(0.60f).fillMaxHeight())
            }
        }
    }
    val hardwareRow: @Composable (Modifier) -> Unit = { m -> HardwareRow(state, layout, m) }
    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (layout.settingsRowsSwapped) {
            hardwareRow(Modifier.fillMaxWidth().weight(0.30f))
            topRow(Modifier.fillMaxWidth().weight(0.70f))
        } else {
            topRow(Modifier.fillMaxWidth().weight(0.70f))
            hardwareRow(Modifier.fillMaxWidth().weight(0.30f))
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
                Modifier.clickable { actions.toggleRefreshRate() },
            ) { FrameRatePill(state.is120Hz) }
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${state.fps}", color = colors.primary, fontSize = 46.sp, fontWeight = FontWeight.Bold)
                Text(" FPS", color = colors.primary, fontSize = 15.sp, modifier = Modifier.padding(bottom = 10.dp))
            }
            Text("CURRENT FPS", color = colors.panelText, fontSize = 11.sp)
        }
    }
}

/** The "60/120 FPS MODE" pill (stock `bg_60/bg_120_frame_rate_mode` gradients), rendered in Compose. */
@Composable
private fun FrameRatePill(is120: Boolean) {
    // Solid two-colour gradient (no fade to transparent) so the pill reads as a filled badge.
    val brush = if (is120) {
        androidx.compose.ui.graphics.Brush.horizontalGradient(
            listOf(Color(0xFFE066FF), Color(0xFFD11AFF)),
        )
    } else {
        androidx.compose.ui.graphics.Brush.horizontalGradient(
            listOf(Color(0xFF1BBDDE), Color(0xFF0070E7)),
        )
    }
    Box(
        modifier = Modifier
            .size(width = 150.dp, height = 34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(brush),
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
private fun HardwareRow(state: OverlayUiState, layout: jr.brian.commandpannel.system.OverlayLayout, modifier: Modifier) {
    val tempCard: @Composable RowScope.() -> Unit = {
        InfoCard(
            Modifier.weight(1f).fillMaxHeight(),
            icon = "ic_temp",
            iconTint = TempColors.forCelsius(state.tempC),
            title = "TEMP",
            value = "${state.tempC}℃",
        )
    }
    val fanCard: @Composable RowScope.() -> Unit = {
        InfoCard(
            Modifier.weight(1f).fillMaxHeight(),
            icon = "ic_fan_normal",
            iconTint = null,
            title = "FAN",
            value = "${state.fanPercent}%",
        )
    }
    // The temp+fan quarters share the narrow (0.4) column; rings fill the wide (0.6) column.
    val quarters: @Composable (Modifier) -> Unit = { m ->
        Row(m, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (card in layout.settingsQuarterOrder) {
                when (card) {
                    jr.brian.commandpannel.system.OverlayCardId.TEMP -> tempCard()
                    jr.brian.commandpannel.system.OverlayCardId.FAN -> fanCard()
                    else -> Unit
                }
            }
        }
    }
    val rings: @Composable (Modifier) -> Unit = { m ->
        TccCard(m) {
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
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        if (layout.settingsColsSwapped) {
            rings(Modifier.weight(0.6f).fillMaxHeight())
            quarters(Modifier.weight(0.4f).fillMaxHeight())
        } else {
            quarters(Modifier.weight(0.4f).fillMaxHeight())
            rings(Modifier.weight(0.6f).fillMaxHeight())
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
            Text(value, color = colors.primary, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        }
    }
}
