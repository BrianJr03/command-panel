package jr.brian.commandpannel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import dagger.hilt.android.AndroidEntryPoint

/**
 * Companion settings screen on the top display. Phase 0 placeholder: shows the build and renders a sample of the
 * imported stock icons and strings so the resource import can be checked on the device.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Placeholder() }
    }
}

@Composable
private fun Placeholder() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.app_name), color = Color.White, fontSize = 28.sp)
        Text(
            "${BuildConfig.APPLICATION_ID} ${BuildConfig.VERSION_NAME} · phase 0",
            color = Color.Gray,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            StockItem(R.drawable.ic_dual_screen_display_black, R.string.dualscreen_asynchronous_display)
            StockItem(R.drawable.ic_just_screen_above_black, R.string.only_theupper_screen_displayed)
            StockItem(R.drawable.ic_just_screen_below_black, R.string.only_following_screen_displayed)
        }
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            StockItem(R.drawable.ic_screen_control_black, R.string.navigation_screen_control_text)
            StockItem(R.drawable.ic_task_manage_black, R.string.navigation_task_manage_text)
            StockItem(R.drawable.ic_system_set_black, R.string.navigation_system_set_text)
        }
    }
}

@Composable
private fun StockItem(@DrawableRes icon: Int, @StringRes label: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(48.dp))
        Text(stringResource(label), color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}
