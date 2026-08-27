package com.example.memory.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.memory.ui.home.HomeScreen
import com.example.memory.ui.listdetail.ListDetailScreen

private const val HOME_ROUTE = "home"
private const val LIST_DETAIL_ROUTE = "list/{listId}"

@Composable
fun MemoryNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = HOME_ROUTE) {
        composable(HOME_ROUTE) {
            HomeScreen(onOpenList = { listId -> navController.navigate("list/$listId") })
        }
        composable(
            route = LIST_DETAIL_ROUTE,
            arguments = listOf(navArgument("listId") { type = NavType.LongType })
        ) { backStackEntry ->
            val listId = backStackEntry.arguments?.getLong("listId") ?: return@composable
            ListDetailScreen(listId = listId, onBack = { navController.popBackStack() })
        }
    }
}
