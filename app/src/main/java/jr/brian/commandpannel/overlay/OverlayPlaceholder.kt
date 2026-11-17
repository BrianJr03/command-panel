package jr.brian.commandpannel.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Phase 2 overlay content: proves the window mechanics (correct display, full size, touchable, animated) before the
 * real stock UI replaces it in phase 3. A dim scrim over the whole bottom display, a centered card, and a Close
 * button that exercises the dismiss path.
 */
@Composable
fun OverlayPlaceholder(dismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xBF000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF161616))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Command Panel", color = Color.White, fontSize = 24.sp)
            Text("Bottom-screen overlay · phase 2", color = Color.Gray, fontSize = 14.sp)
            Button(onClick = dismiss) { Text("Close") }
        }
    }
}
