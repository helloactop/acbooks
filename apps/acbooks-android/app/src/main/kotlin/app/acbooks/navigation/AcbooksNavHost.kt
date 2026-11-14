package app.acbooks.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.acbooks.feature.home.HomeScreen

private const val HOME_ROUTE = "home"

@Composable
fun AcbooksNavHost() {
    NavHost(navController = rememberNavController(), startDestination = HOME_ROUTE) {
        composable(HOME_ROUTE) { HomeScreen() }
    }
}
