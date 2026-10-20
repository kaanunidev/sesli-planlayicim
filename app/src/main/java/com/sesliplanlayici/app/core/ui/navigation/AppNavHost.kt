package com.sesliplanlayici.app.core.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

/** Result the review screen leaves on the capture screen's back stack entry. */
private const val SAVED_COUNT = "saved_count"

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = CaptureRoute) {
        composable<CaptureRoute> { entry ->
            val savedCount by entry.savedStateHandle.getStateFlow<Int?>(SAVED_COUNT, null)
                .collectAsStateWithLifecycle()
            CaptureScreen(
                onOpenReview = { navController.navigate(ReviewRoute) { launchSingleTop = true } },
                savedCount = savedCount,
                onSavedMessageShown = { entry.savedStateHandle[SAVED_COUNT] = null },
            )
        }
        composable<ReviewRoute> {
            ReviewScreen(
                onBack = { navController.popBackStack() },
                onSaved = { count ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(SAVED_COUNT, count)
                    navController.popBackStack()
                },
            )
        }
    }
}
