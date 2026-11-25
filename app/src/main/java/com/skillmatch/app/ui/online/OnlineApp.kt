package com.skillmatch.app.ui.online

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.skillmatch.app.ui.components.LoadingState
import com.skillmatch.app.ui.components.MessageBanner
import com.skillmatch.app.ui.components.MessageKind
import com.skillmatch.app.ui.components.PrimaryButton
import com.skillmatch.app.ui.components.ScreenTitle
import com.skillmatch.app.ui.components.SecondaryButton
import com.skillmatch.app.ui.theme.Sm

object OnlineRoutes {
    const val JOBS = "jobs"
    const val DETAIL = "detail/{id}"
    const val SKILLS = "skills"
    const val ACCOUNT = "account"
    fun detail(id: Int) = "detail/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(OnlineRoutes.JOBS, "Listings", Icons.Filled.Search),
    Tab(OnlineRoutes.SKILLS, "My skills", Icons.Filled.Tune),
    Tab(OnlineRoutes.ACCOUNT, "Account", Icons.Filled.Person),
)

/** Switches to a bottom tab without stacking copies of it. */
private fun NavHostController.goToTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun SkillMatchOnlineApp(vm: OnlineViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(Sm.BlueBg)) {
        // A plum strip behind the status bar keeps its icons readable on every screen.
        Spacer(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(Sm.PlumDark))
        if (!state.ready) {
            LoadingState("Opening SkillMatch…")
        } else if (state.profile == null && state.startupError != null) {
            StartupError(state, vm)
        } else if (state.profile == null) {
            Box(Modifier.weight(1f).fillMaxWidth().navigationBarsPadding().imePadding()) { AuthPane(state, vm) }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth()) { SignedInShell(state, vm) }
        }
    }
}

@Composable
private fun StartupError(state: OnlineState, vm: OnlineViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTitle("Can't reach the server", Sm.PlumDark)
        MessageBanner(state.startupError ?: "Something went wrong.", MessageKind.Error)
        PrimaryButton("Try again", onClick = { vm.restore() })
        ConnectionCard(state.serverUrl, state.connectionError, vm::setServerUrl)
        SecondaryButton("Log out", onClick = { vm.logout() }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SignedInShell(state: OnlineState, vm: OnlineViewModel) {
    val phase = state.research.phase
    val inFlow = phase == ResearchPhase.Setup || phase == ResearchPhase.Finish ||
        phase == ResearchPhase.Survey || phase == ResearchPhase.Done
    if (inFlow) {
        Box(Modifier.fillMaxSize().navigationBarsPadding()) { ResearchFlow(state, vm) }
    } else {
        MainTabs(state, vm)
    }
}

@Composable
private fun MainTabs(state: OnlineState, vm: OnlineViewModel) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = !state.research.active && route != OnlineRoutes.DETAIL
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            NavHost(nav, startDestination = OnlineRoutes.JOBS) {
                composable(OnlineRoutes.JOBS) {
                    JobsScreen(
                        state = state, vm = vm,
                        onOpen = { nav.navigate(OnlineRoutes.detail(it)) },
                        onEditSkills = { nav.goToTab(OnlineRoutes.SKILLS) },
                    )
                }
                composable(OnlineRoutes.DETAIL, arguments = listOf(navArgument("id") { type = NavType.IntType })) { e ->
                    val id = e.arguments?.getInt("id") ?: -1
                    OnlineDetailScreen(
                        state = state, vm = vm, id = id,
                        onBack = { nav.popBackStack() },
                        onEditSkills = { nav.goToTab(OnlineRoutes.SKILLS) },
                    )
                }
                composable(OnlineRoutes.SKILLS) {
                    SkillsScreen(state, vm, onSaved = { nav.goToTab(OnlineRoutes.JOBS) })
                }
                composable(OnlineRoutes.ACCOUNT) { AccountScreen(state, vm) }
            }
        }
        if (showBar) TabBar(nav, route) else Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun TabBar(nav: NavHostController, route: String?) {
    NavigationBar(containerColor = Color.White) {
        tabs.forEach { t ->
            NavigationBarItem(
                selected = route == t.route,
                onClick = { if (route != t.route) nav.goToTab(t.route) },
                icon = { Icon(t.icon, contentDescription = null) },
                label = { Text(t.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Sm.Plum,
                    selectedTextColor = Sm.Plum,
                    indicatorColor = Sm.PlumTint,
                    unselectedIconColor = Sm.Faint,
                    unselectedTextColor = Sm.Faint,
                ),
            )
        }
    }
}
