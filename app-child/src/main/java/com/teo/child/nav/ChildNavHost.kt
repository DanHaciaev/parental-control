package com.teo.child.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.teo.child.home.HomeScreen
import com.teo.child.monitor.MonitorForegroundService
import com.teo.child.pairing.PairingScreen
import com.teo.child.pairing.PairingViewModel
import com.teo.child.permission.PermissionsScreen
import com.teo.child.work.WorkScheduler

private const val ROUTE_SPLASH = "splash"
private const val ROUTE_PAIRING = "pairing"
private const val ROUTE_PERMISSIONS = "permissions"
private const val ROUTE_HOME = "home"

private fun startProtection(context: android.content.Context) {
    MonitorForegroundService.start(context)
    WorkScheduler.scheduleAll(context)
    WorkScheduler.kickImmediate(context)
}

@Composable
fun ChildNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val pairingViewModel: PairingViewModel = hiltViewModel()
    val uiState by pairingViewModel.uiState.collectAsState()
    val onboardingComplete by pairingViewModel.onboardingComplete.collectAsState(initial = false)

    LaunchedEffect(uiState.loading, uiState.paired, onboardingComplete) {
        if (uiState.loading) return@LaunchedEffect

        val target = when {
            !uiState.paired -> ROUTE_PAIRING
            !onboardingComplete -> ROUTE_PERMISSIONS
            else -> ROUTE_HOME
        }
        if (navController.currentDestination?.route == target) return@LaunchedEffect

        if (target == ROUTE_HOME) startProtection(context)

        navController.navigate(target) {
            popUpTo(0)
            launchSingleTop = true
        }
    }

    NavHost(navController = navController, startDestination = ROUTE_SPLASH) {
        composable(ROUTE_SPLASH) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        composable(ROUTE_PAIRING) { PairingScreen(viewModel = pairingViewModel) }
        composable(ROUTE_PERMISSIONS) {
            PermissionsScreen(
                onAllGranted = {
                    pairingViewModel.markOnboardingComplete()
                    startProtection(context)
                    navController.navigate(ROUTE_HOME) {
                        popUpTo(0)
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(ROUTE_HOME) { HomeScreen() }
    }
}
