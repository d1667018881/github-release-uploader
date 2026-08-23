package com.github.releaseuploader.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.github.releaseuploader.ui.screens.*

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object RepoList : Screen("repo_list")
    data object RepoDetail : Screen("repo_detail/{owner}/{repo}") {
        fun createRoute(owner: String, repo: String) = "repo_detail/$owner/$repo"
    }
    data object CodeBrowser : Screen("code_browser/{owner}/{repo}/{path}") {
        fun createRoute(owner: String, repo: String, path: String) = "code_browser/$owner/$repo/$path"
    }
}

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Login.route) {
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.RepoList.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.RepoList.route) {
            RepoListScreen(
                onRepoClick = { owner, repo ->
                    navController.navigate(Screen.RepoDetail.createRoute(owner, repo))
                },
                onLoggedOut = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.RepoList.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.RepoDetail.route,
            arguments = listOf(
                navArgument("owner") { type = NavType.StringType },
                navArgument("repo") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val owner = backStackEntry.arguments?.getString("owner") ?: ""
            val repo = backStackEntry.arguments?.getString("repo") ?: ""
            RepoDetailScreen(
                owner = owner,
                repo = repo,
                onFileClick = { path ->
                    navController.navigate(Screen.CodeBrowser.createRoute(owner, repo, path))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.CodeBrowser.route,
            arguments = listOf(
                navArgument("owner") { type = NavType.StringType },
                navArgument("repo") { type = NavType.StringType },
                navArgument("path") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val owner = backStackEntry.arguments?.getString("owner") ?: ""
            val repo = backStackEntry.arguments?.getString("repo") ?: ""
            val path = backStackEntry.arguments?.getString("path") ?: ""
            CodeBrowserScreen(
                owner = owner,
                repo = repo,
                path = path,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
