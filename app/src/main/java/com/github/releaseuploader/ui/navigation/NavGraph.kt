package com.github.releaseuploader.ui.navigation

import android.net.Uri
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
    data object RepoDetail : Screen("repo_detail/{owner}/{repo}/{branch}") {
        // branch 可能含 "/"（如 release/v1.0），必须 Uri.encode，取参时自动解码
        fun createRoute(owner: String, repo: String, branch: String) = "repo_detail/$owner/$repo/${Uri.encode(branch)}"
    }
    data object RepoFiles : Screen("repo_files/{owner}/{repo}/{branch}") {
        fun createRoute(owner: String, repo: String, branch: String) = "repo_files/$owner/$repo/${Uri.encode(branch)}"
    }
    data object CodeBrowser : Screen("code_browser/{owner}/{repo}/{branch}/{path}") {
        // path/branch 含 "/" 时必须 Uri.encode，Navigation 在编码后的 URI 上匹配，取参时自动解码
        fun createRoute(owner: String, repo: String, branch: String, path: String) =
            "code_browser/$owner/$repo/${Uri.encode(branch)}/${Uri.encode(path)}"
    }
}

private fun NavHostController.navigateToLogin() {
    navigate(Screen.Login.route) {
        popUpTo(0) { inclusive = true }
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
                onRepoClick = { owner, repo, branch ->
                    navController.navigate(Screen.RepoDetail.createRoute(owner, repo, branch))
                },
                onLoggedOut = {
                    navController.navigateToLogin()
                }
            )
        }

        // 仓库概览页（官方 App 风格：功能入口 + README）
        composable(
            route = Screen.RepoDetail.route,
            arguments = listOf(
                navArgument("owner") { type = NavType.StringType },
                navArgument("repo") { type = NavType.StringType },
                navArgument("branch") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val owner = backStackEntry.arguments?.getString("owner") ?: ""
            val repo = backStackEntry.arguments?.getString("repo") ?: ""
            val branch = backStackEntry.arguments?.getString("branch") ?: "main"
            RepoDetailScreen(
                owner = owner,
                repo = repo,
                branch = branch,
                onCodeClick = {
                    navController.navigate(Screen.RepoFiles.createRoute(owner, repo, branch))
                },
                onLoggedOut = {
                    navController.navigateToLogin()
                },
                onBack = { navController.popBackStack() }
            )
        }

        // 仓库文件浏览页（目录导航 + 上传）
        composable(
            route = Screen.RepoFiles.route,
            arguments = listOf(
                navArgument("owner") { type = NavType.StringType },
                navArgument("repo") { type = NavType.StringType },
                navArgument("branch") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val owner = backStackEntry.arguments?.getString("owner") ?: ""
            val repo = backStackEntry.arguments?.getString("repo") ?: ""
            val branch = backStackEntry.arguments?.getString("branch") ?: "main"
            RepoFilesScreen(
                owner = owner,
                repo = repo,
                branch = branch,
                onFileClick = { path ->
                    navController.navigate(Screen.CodeBrowser.createRoute(owner, repo, branch, path))
                },
                onLoggedOut = {
                    navController.navigateToLogin()
                },
                onBack = { navController.popBackStack() }
            )
        }

        // 代码查看页
        composable(
            route = Screen.CodeBrowser.route,
            arguments = listOf(
                navArgument("owner") { type = NavType.StringType },
                navArgument("repo") { type = NavType.StringType },
                navArgument("branch") { type = NavType.StringType },
                navArgument("path") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val owner = backStackEntry.arguments?.getString("owner") ?: ""
            val repo = backStackEntry.arguments?.getString("repo") ?: ""
            val branch = backStackEntry.arguments?.getString("branch") ?: "main"
            val path = backStackEntry.arguments?.getString("path") ?: ""
            CodeBrowserScreen(
                owner = owner,
                repo = repo,
                branch = branch,
                path = path,
                onLoggedOut = {
                    navController.navigateToLogin()
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
