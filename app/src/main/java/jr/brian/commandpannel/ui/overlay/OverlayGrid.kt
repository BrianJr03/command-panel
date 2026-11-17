package jr.brian.commandpannel.ui.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import jr.brian.commandpannel.system.CardSpan
import jr.brian.commandpannel.system.GridCell
import jr.brian.commandpannel.system.OverlayCardGroup
import jr.brian.commandpannel.system.OverlayCardId
import jr.brian.commandpannel.ui.overlay.arrange.ArrangeController
import jr.brian.commandpannel.ui.overlay.arrange.ArrangeableCard
import jr.brian.commandpannel.ui.overlay.arrange.EmptyCell
import jr.brian.commandpannel.ui.overlay.arrange.arrangeLayer

/**
 * Renders one tab as a 2-column snap grid from [cells] (`card -> GridCell`). FULL cards span the row; HALF cards take
 * a column; empty columns render as droppable [EmptyCell]s. Each row's height comes from its cards' intrinsic
 * [OverlayCardId.rowWeight], which Compose normalizes to fill the tab. [cardContent] draws a given card's body.
 */
@Composable
fun OverlayGrid(
    group: OverlayCardGroup,
    cells: Map<OverlayCardId, GridCell>,
    arrange: ArrangeController,
    modifier: Modifier = Modifier,
    cardContent: @Composable (OverlayCardId, Modifier) -> Unit,
) {
    val maxRow = cells.values.maxOfOrNull { it.row } ?: 0
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in 0..maxRow) {
            val inRow = cells.filterValues { it.row == row }
            if (inRow.isEmpty()) continue // skip fully-empty rows so they don't take height
            val fullCard = inRow.keys.firstOrNull { it.span == CardSpan.FULL }
            // Tallest card in the row sets its height, so a row with mismatched halves is order-independent.
            val rowWeight = inRow.keys.maxOf { it.rowWeight }
            Row(
                Modifier.fillMaxWidth().weight(rowWeight).arrangeLayer(arrange, inRow.keys),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (fullCard != null) {
                    ArrangeableCard(fullCard, arrange, Modifier.fillMaxSize()) {
                        cardContent(fullCard, Modifier.fillMaxSize())
                    }
                } else {
                    for (col in 0..1) {
                        val card = inRow.entries.firstOrNull { it.value.col == col }?.key
                        if (card != null) {
                            ArrangeableCard(card, arrange, Modifier.weight(1f).fillMaxHeight()) {
                                cardContent(card, Modifier.fillMaxSize())
                            }
                        } else {
                            EmptyCell(group, GridCell(row, col), arrange, Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
            }
        }
    }
}
