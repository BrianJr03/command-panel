package jr.brian.commandpannel.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import jr.brian.commandpannel.ui.components.noRippleClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jr.brian.commandpannel.R
import jr.brian.commandpannel.ui.theme.LocalTccColors

/**
 * Stock "Only top screen" tip, shown before blanking the bottom screen (unless suppressed). "Do not show again"
 * persists the suppression; confirming applies the mode.
 */
@Composable
fun BottomTipDialog(onConfirm: (dontShowAgain: Boolean) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalTccColors.current
    var dontShow by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xBB000000)).noRippleClickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 300.dp, max = 400.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(colors.dialogBg)
                .noRippleClickable(enabled = false) {}
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.tip_title_text), color = colors.primary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(12.dp))
            Text(stringResource(R.string.tip_content_text), color = colors.primary, fontSize = 15.sp)
            Spacer(Modifier.size(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.noRippleClickable { dontShow = !dontShow },
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .then(
                            if (dontShow) Modifier.background(colors.accent)
                            else Modifier.border(2.dp, colors.secondary, RoundedCornerShape(4.dp))
                        ),
                    contentAlignment = Alignment.Center,
                ) { if (dontShow) Text("✓", color = Color.White, fontSize = 12.sp) }
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.tip_dialog_cb_text_no_more_tip), color = colors.secondary, fontSize = 13.sp)
            }
            Spacer(Modifier.size(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Text(
                    stringResource(android.R.string.cancel),
                    color = colors.secondary, fontSize = 16.sp,
                    modifier = Modifier.noRippleClickable(onClick = onDismiss).padding(8.dp),
                )
                Text(
                    stringResource(android.R.string.ok),
                    color = colors.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.noRippleClickable { onConfirm(dontShow) }.padding(8.dp),
                )
            }
        }
    }
}
