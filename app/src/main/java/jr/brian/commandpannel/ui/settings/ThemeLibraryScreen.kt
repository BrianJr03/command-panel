package jr.brian.commandpannel.ui.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jr.brian.commandpannel.R
import jr.brian.commandpannel.ui.components.tccLookBackdrop
import jr.brian.commandpannel.ui.theme.CustomLook
import jr.brian.commandpannel.ui.theme.LocalTccColors
import jr.brian.commandpannel.ui.theme.LocalTccLookSpec
import jr.brian.commandpannel.ui.theme.lookLabel
import jr.brian.commandpannel.ui.theme.tccDisplayTextStyle
import jr.brian.commandpannel.ui.theme.tccLookColors
import kotlinx.coroutines.launch

/** The Themes library's pages. [key] is the route argument. */
internal enum class ThemeLibraryTab(val key: String, @StringRes val labelRes: Int) {
    LOCAL("local", R.string.theme_library_local),
    PACKS("packs", R.string.theme_library_packs);

    companion object {
        fun fromKey(key: String?): ThemeLibraryTab = entries.firstOrNull { it.key == key } ?: LOCAL
    }
}

/**
 * Every saved look (Local) and every pack in the TCP Themes folder (Packs), on two pages of a horizontal pager.
 * Gamepad: L1 / R1 switch pages from anywhere, D-pad moves along the tab row and down into the page, B goes back.
 * Touch: tap a tab or swipe.
 */
@Composable
internal fun ThemeLibraryScreen(
    initialTab: ThemeLibraryTab,
    state: SettingsState,
    vm: SettingsViewModel,
    onOpenStudio: (CustomLook) -> Unit,
    onClose: () -> Unit,
) {
    val tabs = ThemeLibraryTab.entries
    val pager = rememberPagerState(initialPage = initialTab.ordinal) { tabs.size }
    val scope = rememberCoroutineScope()
    val motion = LocalTccLookSpec.current.traits.animate
    val tabFocus = remember { tabs.associateWith { FocusRequester() } }
    BackHandler(onBack = onClose)
    LaunchedEffect(Unit) {
        vm.refreshThemeFolder()
        runCatching { tabFocus.getValue(initialTab).requestFocus() }
    }
    val select: (ThemeLibraryTab, Boolean) -> Unit = { tab, focusTab ->
        scope.launch {
            if (motion) pager.animateScrollToPage(tab.ordinal, animationSpec = tween(320, easing = FastOutSlowInEasing))
            else pager.scrollToPage(tab.ordinal)
        }
        if (focusTab) runCatching { tabFocus.getValue(tab).requestFocus() }
    }

    val backdrop = if (LocalCompanionThemed.current) {
        Modifier.tccLookBackdrop(LocalTccLookSpec.current, LocalTccColors.current, tccLookColors())
    } else {
        Modifier.background(p.bg)
    }
    PaddedBringIntoView {
        Column(
            Modifier.fillMaxSize().then(backdrop).systemBarsPadding()
                // L1 / R1 switch pages wherever focus is, and land focus on the new page's tab.
                .onPreviewKeyEvent { e ->
                    val step = when (e.key) {
                        Key.ButtonL1 -> -1
                        Key.ButtonR1 -> 1
                        else -> return@onPreviewKeyEvent false
                    }
                    if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) {
                        val next = (pager.currentPage + step).coerceIn(0, tabs.lastIndex)
                        if (next != pager.currentPage) select(tabs[next], true)
                    }
                    true
                }
                .padding(horizontal = 28.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Chip(stringResource(R.string.theme_back), onClick = onClose)
                Text(
                    lookLabel(stringResource(R.string.settings_cat_themes)), color = p.textPrimary, fontSize = 26.sp,
                    fontWeight = FontWeight.Bold, style = tccDisplayTextStyle(), modifier = Modifier.weight(1f),
                )
            }
            // Content padding after the row, so the focused chip's ring and scale aren't clipped at the edges.
            Row(
                Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                tabs.forEach { tab ->
                    Box(Modifier.focusRequester(tabFocus.getValue(tab))) {
                        Chip(stringResource(tab.labelRes), selected = pager.currentPage == tab.ordinal) { select(tab, false) }
                    }
                }
                Spacer(Modifier.width(6.dp))
                InfoLine(stringResource(R.string.theme_library_tabs_hint), p.textTertiary)
            }
            HorizontalPager(pager, Modifier.fillMaxWidth().weight(1f), beyondViewportPageCount = 0) { page ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    when (tabs[page]) {
                        ThemeLibraryTab.LOCAL -> {
                            MyLooksHeaderCard(state, vm)
                            SavedLooksList(state, vm, onOpenStudio)
                        }
                        ThemeLibraryTab.PACKS -> {
                            PacksHeaderCard(state, vm)
                            state.folderPacks.forEach { fp -> FolderPackCard(fp, state, vm, onOpenStudio) }
                        }
                    }
                    Spacer(Modifier.padding(bottom = 28.dp))
                }
            }
        }
    }
}
