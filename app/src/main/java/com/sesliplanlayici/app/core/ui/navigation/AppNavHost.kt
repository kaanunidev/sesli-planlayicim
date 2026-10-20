package com.sesliplanlayici.app.core.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sesliplanlayici.app.capture.ui.CaptureScreen
import com.sesliplanlayici.app.review.ui.ReviewScreen
import kotlinx.serialization.Serializable

@Serializable
data object CaptureRoute

/** Drafts are handed over through PendingDrafts, not as route arguments. */
@Serializable
data object ReviewRoute

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = CaptureRoute) {
        composable<CaptureRoute> {
            CaptureScreen(onOpenReview = { navController.navigate(ReviewRoute) { launchSingleTop = true } })
        }
        composable<ReviewRoute> {
            ReviewScreen(onBack = { navController.popBackStack() })
        }
    }
}
