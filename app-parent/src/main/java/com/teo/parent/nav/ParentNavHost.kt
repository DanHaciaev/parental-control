package com.teo.parent.nav

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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.teo.parent.accessibility.AccessibilityPermissionHelper
import com.teo.parent.admin.DeviceAdminHelper
import com.teo.parent.auth.AuthViewModel
import com.teo.parent.auth.LoginScreen
import com.teo.parent.dashboard.DashboardScreen
import com.teo.parent.pairing.PairingScreen
import com.teo.parent.pin.SetPinScreen
import com.teo.parent.protect.ProtectSetupScreen

private const val ROUTE_SPLASH = "splash"
private const val ROUTE_LOGIN = "login"
private const val ROUTE_PAIRING = "pairing/{familyId}"
private const val ROUTE_PIN = "set_pin/{familyId}"
private const val ROUTE_PROTECT = "protect_setup"
private const val ROUTE_DASHBOARD = "dashboard"

@Composable
fun ParentNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val authViewModel: AuthViewModel = hiltViewModel()
    val uiState by authViewModel.uiState.collectAsState()

    LaunchedEffect(uiState.loading, uiState.isSignedIn, uiState.childLinked, uiState.pinSet) {
        if (uiState.loading) return@LaunchedEffect

        val ownAppProtected = DeviceAdminHelper.isActive(context) && AccessibilityPermissionHelper.isEnabled(context)
        val targetRoute = when {
            !uiState.isSignedIn -> ROUTE_LOGIN
            !uiState.childLinked -> ROUTE_PAIRING
            !uiState.pinSet -> ROUTE_PIN
            !ownAppProtected -> ROUTE_PROTECT
            else -> ROUTE_DASHBOARD
        }
        if (navController.currentDestination?.route == targetRoute) return@LaunchedEffect

        val path = when (targetRoute) {
            ROUTE_PAIRING -> "pairing/${uiState.familyId}"
            ROUTE_PIN -> "set_pin/${uiState.familyId}"
            else -> targetRoute
        }
        navController.navigate(path) {
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
        composable(ROUTE_LOGIN) {
            LoginScreen(viewModel = authViewModel)
        }
        composable(
            ROUTE_PAIRING,
            arguments = listOf(navArgument("familyId") { type = NavType.StringType })
        ) {
            PairingScreen(onPaired = { authViewModel.refreshFamilyState() })
        }
        composable(
            ROUTE_PIN,
            arguments = listOf(navArgument("familyId") { type = NavType.StringType })
        ) {
            SetPinScreen(onSaved = { authViewModel.refreshFamilyState() })
        }
        composable(ROUTE_PROTECT) {
            ProtectSetupScreen(
                onDone = {
                    navController.navigate(ROUTE_DASHBOARD) {
                        popUpTo(0)
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(ROUTE_DASHBOARD) {
            DashboardScreen(onSignOut = { authViewModel.signOut() })
        }
    }
}
