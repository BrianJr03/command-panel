package jr.brian.commandpannel.ui.settings

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jr.brian.commandpannel.R
import jr.brian.commandpannel.ui.theme.LocalTccLookSpec
import jr.brian.commandpannel.ui.theme.tccTextStyle

/**
 * What a panel background costs (#80), for the Performance details under Experimental's Animated backgrounds switch.
 * Update the numbers here when the Thor baseline is measured again (`scripts/verify-gradient-background.sh --baseline`).
 */
internal object BackgroundCost {
    /** One bar: [labelRes] names the background, [cpuPercent] is the app's processor use while the panel is open. */
    data class Entry(@StringRes val labelRes: Int, val cpuPercent: Int)

    /** App CPU from the #66 baseline on the AYN Thor (mean of 3 runs), in the chart's fixed order. */
    val ENTRIES: List<Entry> = listOf(
        Entry(R.string.settings_performance_still, 3),
        Entry(R.string.settings_performance_gradient, 12),
        Entry(R.string.settings_performance_gif, 13),
    )

    /**
     * Each value's bar length as a fraction (0..1) of the longest bar, from a shared zero baseline: the largest value
     * fills the bar area. Negative values count as 0; all zero (or empty) gives all 0.
     */
    fun fractions(values: List<Int>): List<Float> {
        val max = values.maxOrNull()?.takeIf { it > 0 } ?: return values.map { 0f }
        return values.map { (it.coerceAtLeast(0).toFloat() / max).coerceIn(0f, 1f) }
    }

    /**
     * The chart's content description: [title] then every item, as [item] formats it ("Still image, about 3%."),
     * joined into [whole] ("Processor use while the panel is open. <items>").
     */
    fun describe(
        title: String,
        entries: List<Pair<String, Int>>,
        item: (String, Int) -> String,
        whole: (String, String) -> String,
    ): String = whole(title, entries.joinToString(" ") { (label, value) -> item(label, value) })
}

/** Height of each bar's row, shared by the labels and the bars so they line up. */
private val BAR_ROW = 26.dp
private val BAR_THICKNESS = 10.dp
/** Room kept at the end of the bar area for the value printed after the longest bar. */
private val VALUE_ROOM = 52.dp

/**
 * "Performance" (#80): a row under the Animated backgrounds switch that opens to show what moving backgrounds cost, as
 * a small chart and plain points. A or a tap opens and closes it; focus stays on the row. The open state survives
 * leaving the section (and the companion's other screens).
 */
@Composable
internal fun BackgroundPerformanceCard(modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    val motion = LocalTccLookSpec.current.traits.animate
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "performanceChevron")
    Card(modifier) {
        val title = stringResource(R.string.settings_performance)
        FocusRow(selected = false, onClick = { open = !open }) {
            Text(title, color = p.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, style = tccTextStyle(), modifier = Modifier.weight(1f))
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = stringResource(if (open) R.string.settings_performance_shown else R.string.settings_performance_hidden),
                tint = p.textSecondary,
                modifier = Modifier.size(24.dp).rotate(if (motion) turn else if (open) 180f else 0f),
            )
        }
        if (open) {
            Spacer(Modifier.height(4.dp))
            PerformanceChart()
            Spacer(Modifier.height(6.dp))
            listOf(
                R.string.settings_performance_fact_panel,
                R.string.settings_performance_fact_cap,
                R.string.settings_performance_fact_still,
                R.string.settings_performance_fact_memory,
            ).forEach { Fact(stringResource(it)) }
        }
    }
}

/**
 * "Processor use while the panel is open": one measure, so one hue (the look's accent) and no legend. Horizontal bars
 * from a shared zero baseline (the one axis line), thin with rounded ends at the data end, each value printed after
 * its bar in the text color. Screen readers get the three values in one description.
 */
@Composable
private fun PerformanceChart() {
    val entries = BackgroundCost.ENTRIES
    val labels = entries.map { stringResource(it.labelRes) }
    val values = entries.map { it.cpuPercent }
    val fractions = BackgroundCost.fractions(values)
    val title = stringResource(R.string.settings_performance_chart_title)
    val itemPattern = stringResource(R.string.settings_performance_chart_item)
    val wholePattern = stringResource(R.string.settings_performance_chart_desc)
    val description = BackgroundCost.describe(
        title, labels.zip(values),
        item = { label, v -> String.format(itemPattern, label, v) },
        whole = { t, items -> String.format(wholePattern, t, items) },
    )
    val accent = p.accent
    val axis = p.border
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = p.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, style = tccTextStyle())
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(end = 10.dp)) {
                labels.forEach { label ->
                    Box(Modifier.height(BAR_ROW), contentAlignment = Alignment.CenterStart) {
                        Text(label, color = p.textSecondary, fontSize = 13.sp, style = tccTextStyle(), maxLines = 1)
                    }
                }
            }
            BoxWithConstraints(
                Modifier.weight(1f).drawBehind {
                    // The shared zero baseline: the chart's one axis.
                    drawLine(axis, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 1.dp.toPx())
                },
            ) {
                val room = (maxWidth - VALUE_ROOM).coerceAtLeast(0.dp)
                Column {
                    fractions.forEachIndexed { i, f ->
                        Row(Modifier.height(BAR_ROW), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.width(room * f).height(BAR_THICKNESS)
                                    .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                                    .background(accent),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.settings_performance_value, values[i]), color = p.textPrimary,
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, style = tccTextStyle(), maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        InfoLine(stringResource(R.string.settings_performance_note), p.textTertiary)
    }
}

/** One plain-language point, with a small dot. */
@Composable
private fun Fact(text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.padding(top = 7.dp).size(4.dp).clip(CircleShape).background(p.textTertiary))
        CardDesc(text)
    }
}
