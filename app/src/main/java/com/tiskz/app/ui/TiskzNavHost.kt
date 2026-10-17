package com.tiskz.app.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tiskz.app.data.AppContainer
import com.tiskz.app.ui.detail.CallDetailScreen
import com.tiskz.app.ui.dialer.DialerScreen
import com.tiskz.app.ui.home.HomeScreen
import com.tiskz.app.ui.onboarding.OnboardingScreen
import com.tiskz.app.ui.settings.SettingsScreen

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer no provisto") }

private object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val VOICE = "voice"
    const val DIALER = "dialer?number={number}"
    const val RECORD = "record/{id}"

    fun dialer(number: String = "") = "dialer?number=${Uri.encode(number)}"
    fun record(id: String) = "record/$id"
}

/**
 * @param pendingDial número recibido por un intent DIAL (por ejemplo, desde Contactos).
 */
@Composable
fun TiskzNavHost(pendingDial: String?, onDialConsumed: () -> Unit) {
    val navController = rememberNavController()
    val container = LocalAppContainer.current
    val start = remember { if (container.settings.current.onboardingDone) Routes.HOME else Routes.ONBOARDING }

    NavHost(navController = navController, startDestination = start) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onFinished = {
                navController.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
            })
        }
        composable(Routes.HOME) {
            HomeScreen(
                onOpenVoice = { navController.navigate(Routes.VOICE) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenDialer = { navController.navigate(Routes.dialer()) },
                onOpenRecord = { id -> navController.navigate(Routes.record(id)) },
            )
        }
        composable(
            Routes.DIALER,
            arguments = listOf(navArgument("number") { type = NavType.StringType; defaultValue = "" }),
        ) { entry ->
            DialerScreen(
                initialNumber = entry.arguments?.getString("number").orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.RECORD) { entry ->
            CallDetailScreen(
                recordId = entry.arguments?.getString("id").orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.VOICE) {
            com.tiskz.app.ui.voice.VoiceScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }

    LaunchedEffect(pendingDial) {
        if (pendingDial != null) {
            navController.navigate(Routes.dialer(pendingDial))
            onDialConsumed()
        }
    }
}
