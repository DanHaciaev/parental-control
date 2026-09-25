package com.teo.parent.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import com.teo.parent.pin.AppLockScreen
import com.teo.parent.pin.PinChallengeViewModel
import com.teo.parent.pin.SetPinScreen
import com.teo.parent.protect.ProtectSetupScreen
import com.teo.parent.work.ParentAlertService

private const val ROUTE_SPLASH = "splash"
private const val ROUTE_LOGIN = "login"
private const val ROUTE_PAIRING = "pairing/{familyId}"
private const val ROUTE_PIN = "set_pin/{familyId}"
private const val ROUTE_PROTECT = "protect_setup"
private const val ROUTE_DASHBOARD = "dashboard"

@Composable
fun ParentNavHost(openInstallApprovals: MutableState<Boolean> = mutableStateOf(false)) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val authViewModel: AuthViewModel = hiltViewModel()
    val uiState by authViewModel.uiState.collectAsState()
    val lockViewModel: PinChallengeViewModel = hiltViewModel()
    val lockUiState by lockViewModel.uiState.collectAsState()
    var appUnlocked by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(lockUiState.unlocked) {
        if (lockUiState.unlocked) appUnlocked = true
    }

    // Also re-arm on sign-out, so signing into a different (or the same) account within
    // the same running process doesn't inherit a stale unlocked flag.
    LaunchedEffect(uiState.isSignedIn) {
        if (!uiState.isSignedIn) {
            appUnlocked = false
            lockViewModel.lock()
        }
    }

    // Re-lock whenever the app leaves the foreground, so a child can't just switch away and back in.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                appUnlocked = false
                lockViewModel.lock()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
        // Starts the instant-delivery listener as soon as setup is actually done — no separate
        // permission needed (it's a plain foreground service, not an overlay), so there's nothing
        // to wait on beyond reaching the dashboard for the first time.
        if (targetRoute == ROUTE_DASHBOARD) ParentAlertService.start(context)
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

    Box(modifier = Modifier.fillMaxSize()) {
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
                DashboardScreen(
                    onSignOut = { authViewModel.signOut() },
                    openInstallApprovals = openInstallApprovals
                )
            }
        }

        if (uiState.pinSet && !appUnlocked) {
            AppLockScreen(viewModel = lockViewModel, modifier = Modifier.fillMaxSize())
        }
    }
}
