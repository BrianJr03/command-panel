package jr.brian.commandpannel.ui.settings

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import jr.brian.commandpannel.ui.theme.LocalTccLookSpec

/** The companion's screens: the two-pane settings, the Themes library, Theme Studio, Replay Studio and the gradient library. */
internal object CompanionRoutes {
    const val SETTINGS = "settings"
    const val THEMES = "themes?tab={tab}"
    const val STUDIO = "studio"
    /** Replay Studio (saved clips). [STUDIO] is Theme Studio. */
    const val REPLAY_STUDIO = "replay_studio"
    /** The saved gradient backgrounds (#67), from Appearance. */
    const val GRADIENTS = "gradients"

    fun themes(tab: ThemeLibraryTab) = "themes?tab=${tab.key}"

    /**
     * The route for a [jr.brian.commandpannel.MainActivity.EXTRA_SCREEN] value, or null for one that can't be opened
     * from outside. Only Replay Studio for now: Theme Studio needs a draft and the Themes library a tab.
     */
    fun forScreen(screen: String?): String? = when (screen) {
        jr.brian.commandpannel.MainActivity.SCREEN_REPLAY_STUDIO -> REPLAY_STUDIO
        else -> null
    }
}

/**
 * Hosts the companion's screens. Theme Studio edits [SettingsState.studioDraft] (set through
 * [SettingsViewModel.openStudio] before navigating), since a new look isn't saved yet and can't be a route argument.
 */
@Composable
internal fun CompanionNavHost(state: SettingsState, vm: SettingsViewModel) {
    val nav = rememberNavController()
    val motion = LocalTccLookSpec.current.traits.animate
    val openStudio: (jr.brian.commandpannel.ui.theme.CustomLook) -> Unit = { look ->
        vm.openStudio(look)
        nav.navigate(CompanionRoutes.STUDIO) { launchSingleTop = true }
    }
    val openThemes: (ThemeLibraryTab) -> Unit = { tab -> nav.navigate(CompanionRoutes.themes(tab)) { launchSingleTop = true } }
    val openReplayStudio: () -> Unit = { nav.navigate(CompanionRoutes.REPLAY_STUDIO) { launchSingleTop = true } }
    val openGradients: () -> Unit = { nav.navigate(CompanionRoutes.GRADIENTS) { launchSingleTop = true } }
    val closeStudio: () -> Unit = {
        vm.closeStudio()
        nav.popBackStack()
    }
    // A screen asked for from outside (the panel's Replay Studio tile, #49), taken once. Back returns to settings.
    val requestedScreen by jr.brian.commandpannel.CompanionPresence.requestedScreen.collectAsState()
    LaunchedEffect(requestedScreen) {
        val route = CompanionRoutes.forScreen(requestedScreen ?: return@LaunchedEffect)
        jr.brian.commandpannel.CompanionPresence.requestedScreen.value = null
        if (route != null) nav.navigate(route) { launchSingleTop = true }
    }
    NavHost(
        navController = nav,
        startDestination = CompanionRoutes.SETTINGS,
        enterTransition = { if (motion) fadeIn(tween(220)) else EnterTransition.None },
        exitTransition = { if (motion) fadeOut(tween(160)) else ExitTransition.None },
        popEnterTransition = { if (motion) fadeIn(tween(220)) else EnterTransition.None },
        popExitTransition = { if (motion) fadeOut(tween(160)) else ExitTransition.None },
    ) {
        composable(CompanionRoutes.SETTINGS) {
            SettingsContent(state, vm, openStudio, openThemes, openReplayStudio, openGradients)
        }
        composable(
            CompanionRoutes.THEMES,
            arguments = listOf(navArgument("tab") { type = NavType.StringType; defaultValue = ThemeLibraryTab.LOCAL.key }),
        ) { entry ->
            ThemeLibraryScreen(
                initialTab = ThemeLibraryTab.fromKey(entry.arguments?.getString("tab")),
                state = state,
                vm = vm,
                onOpenStudio = openStudio,
                onClose = { nav.popBackStack() },
            )
        }
        composable(CompanionRoutes.STUDIO) {
            val draft = state.studioDraft
            if (draft == null) {
                // Nothing to edit (the process was recreated while Studio was open): go back.
                LaunchedEffect(Unit) { nav.popBackStack() }
            } else {
                ThemeStudioScreen(draft, state, vm, onClose = closeStudio)
            }
        }
        composable(CompanionRoutes.GRADIENTS) {
            // #68 adds the editor: onNew / onEdit open it.
            GradientLibraryScreen(state, vm, onClose = { nav.popBackStack() })
        }
        composable(CompanionRoutes.REPLAY_STUDIO) {
            jr.brian.commandpannel.replay.studio.ReplayStudioScreen(onClose = { nav.popBackStack() })
        }
    }
}
