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
    // 仓库功能列表页
    data object Releases : Screen("repo_releases/{owner}/{repo}") {
        fun createRoute(owner: String, repo: String) = "repo_releases/$owner/$repo"
    }
    data object Contributors : Screen("repo_contributors/{owner}/{repo}") {
        fun createRoute(owner: String, repo: String) = "repo_contributors/$owner/$repo"
    }
    data object Watchers : Screen("repo_watchers/{owner}/{repo}") {
        fun createRoute(owner: String, repo: String) = "repo_watchers/$owner/$repo"
    }
    data object Issues : Screen("repo_issues/{owner}/{repo}") {
        fun createRoute(owner: String, repo: String) = "repo_issues/$owner/$repo"
    }
    data object Pulls : Screen("repo_pulls/{owner}/{repo}") {
        fun createRoute(owner: String, repo: String) = "repo_pulls/$owner/$repo"
    }
    data object Actions : Screen("repo_actions/{owner}/{repo}") {
        fun createRoute(owner: String, repo: String) = "repo_actions/$owner/$repo"
    }
    // 详情页
    data object ReleaseDetail : Screen("repo_release_detail/{owner}/{repo}/{releaseId}") {
        fun createRoute(owner: String, repo: String, releaseId: Long) = "repo_release_detail/$owner/$repo/$releaseId"
    }
    data object WorkflowRuns : Screen("repo_workflow_runs/{owner}/{repo}/{workflowId}/{workflowName}") {
        fun createRoute(owner: String, repo: String, workflowId: Long, workflowName: String) =
            "repo_workflow_runs/$owner/$repo/$workflowId/${Uri.encode(workflowName)}"
    }
    data object RunJobs : Screen("repo_run_jobs/{owner}/{repo}/{runId}") {
        fun createRoute(owner: String, repo: String, runId: Long) = "repo_run_jobs/$owner/$repo/$runId"
    }
    data object JobDetail : Screen("repo_job_detail/{owner}/{repo}/{jobId}/{jobName}") {
        fun createRoute(owner: String, repo: String, jobId: Long, jobName: String) =
            "repo_job_detail/$owner/$repo/$jobId/${Uri.encode(jobName)}"
    }
    data object StepLogs : Screen("repo_step_logs/{owner}/{repo}/{jobId}/{stepNumber}/{stepName}") {
        fun createRoute(owner: String, repo: String, jobId: Long, stepNumber: Int, stepName: String) =
            "repo_step_logs/$owner/$repo/$jobId/$stepNumber/${Uri.encode(stepName)}"
    }
}

private fun NavHostController.navigateToLogin() {
    navigate(Screen.Login.route) {
        popUpTo(0) { inclusive = true }
    }
}

/** 仓库功能列表页的统一 composable 注册 */
private fun androidx.navigation.NavGraphBuilder.repoListScreen(
    navController: NavHostController,
    route: String,
    content: @Composable (String, String, () -> Unit, () -> Unit) -> Unit
) {
    composable(
        route = route,
        arguments = listOf(
            navArgument("owner") { type = NavType.StringType },
            navArgument("repo") { type = NavType.StringType }
        )
    ) { backStackEntry ->
        val owner = backStackEntry.arguments?.getString("owner") ?: ""
        val repo = backStackEntry.arguments?.getString("repo") ?: ""
        content(
            owner,
            repo,
            { navController.navigateToLogin() },
            { navController.popBackStack() }
        )
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
                onIssuesClick = {
                    navController.navigate(Screen.Issues.createRoute(owner, repo))
                },
                onPullsClick = {
                    navController.navigate(Screen.Pulls.createRoute(owner, repo))
                },
                onActionsClick = {
                    navController.navigate(Screen.Actions.createRoute(owner, repo))
                },
                onReleasesClick = {
                    navController.navigate(Screen.Releases.createRoute(owner, repo))
                },
                onContributorsClick = {
                    navController.navigate(Screen.Contributors.createRoute(owner, repo))
                },
                onWatchersClick = {
                    navController.navigate(Screen.Watchers.createRoute(owner, repo))
                },
                onReadmeLinkClick = { path ->
                    // README 里指向本仓库的文档链接，App 内打开代码页
                    if (path.isNotBlank()) {
                        navController.navigate(Screen.CodeBrowser.createRoute(owner, repo, branch, path))
                    }
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

        // 仓库功能列表页
        repoListScreen(navController, Screen.Contributors.route) { o, r, onLogout, onBack ->
            ContributorsScreen(o, r, onLogout, onBack)
        }
        repoListScreen(navController, Screen.Watchers.route) { o, r, onLogout, onBack ->
            WatchersScreen(o, r, onLogout, onBack)
        }
        repoListScreen(navController, Screen.Issues.route) { o, r, onLogout, onBack ->
            IssuesScreen(o, r, onLogout, onBack)
        }
        repoListScreen(navController, Screen.Pulls.route) { o, r, onLogout, onBack ->
            PullsScreen(o, r, onLogout, onBack)
        }

        // 发行版列表（点击进详情）
        composable(
            route = Screen.Releases.route,
            arguments = listOf(
                navArgument("owner") { type = NavType.StringType },
                navArgument("repo") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val owner = backStackEntry.arguments?.getString("owner") ?: ""
            val repo = backStackEntry.arguments?.getString("repo") ?: ""
            ReleasesScreen(
                owner = owner,
                repo = repo,
                onReleaseClick = { releaseId ->
                    navController.navigate(Screen.ReleaseDetail.createRoute(owner, repo, releaseId))
                },
                onLoggedOut = { navController.navigateToLogin() },
                onBack = { navController.popBackStack() }
            )
        }

        // 操作（工作流）列表（点击看运行记录）
        composable(
            route = Screen.Actions.route,
            arguments = listOf(
                navArgument("owner") { type = NavType.StringType },
                navArgument("repo") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val owner = backStackEntry.arguments?.getString("owner") ?: ""
            val repo = backStackEntry.arguments?.getString("repo") ?: ""
            ActionsScreen(
                owner = owner,
                repo = repo,
                onWorkflowClick = { workflowId, workflowName ->
                    navController.navigate(Screen.WorkflowRuns.createRoute(owner, repo, workflowId, workflowName))
                },
                onLoggedOut = { navController.navigateToLogin() },
                onBack = { navController.popBackStack() }
            )
        }

        // 发行版详情页（完整说明 + 附件下载）
        composable(
            route = Screen.ReleaseDetail.route,
            arguments = listOf(
                navArgument("owner") { type = NavType.StringType },
                navArgument("repo") { type = NavType.StringType },
                navArgument("releaseId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val owner = backStackEntry.arguments?.getString("owner") ?: ""
            val repo = backStackEntry.arguments?.getString("repo") ?: ""
            val releaseId = backStackEntry.arguments?.getLong("releaseId") ?: 0L
            ReleaseDetailScreen(
                owner = owner,
                repo = repo,
                releaseId = releaseId,
                onLoggedOut = { navController.navigateToLogin() },
                onBack = { navController.popBackStack() }
            )
        }

        // 工作流运行记录页
        composable(
            route = Screen.WorkflowRuns.route,
            arguments = listOf(
                navArgument("owner") { type = NavType.StringType },
                navArgument("repo") { type = NavType.StringType },
                navArgument("workflowId") { type = NavType.LongType },
                navArgument("workflowName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val owner = backStackEntry.arguments?.getString("owner") ?: ""
            val repo = backStackEntry.arguments?.getString("repo") ?: ""
            val workflowId = backStackEntry.arguments?.getLong("workflowId") ?: 0L
            val workflowName = backStackEntry.arguments?.getString("workflowName") ?: ""
            WorkflowRunsScreen(
                owner = owner,
                repo = repo,
                workflowId = workflowId,
                workflowName = workflowName,
                onRunClick = { runId ->
                    navController.navigate(Screen.RunJobs.createRoute(owner, repo, runId))
                },
                onLoggedOut = { navController.navigateToLogin() },
                onBack = { navController.popBackStack() }
            )
        }

        // 运行详情页（概要 + 产物 + 任务列表）
        composable(
            route = Screen.RunJobs.route,
            arguments = listOf(
                navArgument("owner") { type = NavType.StringType },
                navArgument("repo") { type = NavType.StringType },
                navArgument("runId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val owner = backStackEntry.arguments?.getString("owner") ?: ""
            val repo = backStackEntry.arguments?.getString("repo") ?: ""
            val runId = backStackEntry.arguments?.getLong("runId") ?: 0L
            RunJobsScreen(
                owner = owner,
                repo = repo,
                runId = runId,
                onJobClick = { jobId, jobName ->
                    navController.navigate(Screen.JobDetail.createRoute(owner, repo, jobId, jobName))
                },
                onLoggedOut = { navController.navigateToLogin() },
                onBack = { navController.popBackStack() }
            )
        }

        // 任务详情页（状态 + 耗时 + 步骤列表）
        composable(
            route = Screen.JobDetail.route,
            arguments = listOf(
                navArgument("owner") { type = NavType.StringType },
                navArgument("repo") { type = NavType.StringType },
                navArgument("jobId") { type = NavType.LongType },
                navArgument("jobName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val owner = backStackEntry.arguments?.getString("owner") ?: ""
            val repo = backStackEntry.arguments?.getString("repo") ?: ""
            val jobId = backStackEntry.arguments?.getLong("jobId") ?: 0L
            val jobName = backStackEntry.arguments?.getString("jobName") ?: ""
            JobDetailScreen(
                owner = owner,
                repo = repo,
                jobId = jobId,
                jobName = jobName,
                onStepClick = { stepNumber, stepName ->
                    navController.navigate(Screen.StepLogs.createRoute(owner, repo, jobId, stepNumber, stepName))
                },
                onLoggedOut = { navController.navigateToLogin() },
                onBack = { navController.popBackStack() }
            )
        }

        // 步骤日志页
        composable(
            route = Screen.StepLogs.route,
            arguments = listOf(
                navArgument("owner") { type = NavType.StringType },
                navArgument("repo") { type = NavType.StringType },
                navArgument("jobId") { type = NavType.LongType },
                navArgument("stepNumber") { type = NavType.IntType },
                navArgument("stepName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val owner = backStackEntry.arguments?.getString("owner") ?: ""
            val repo = backStackEntry.arguments?.getString("repo") ?: ""
            val jobId = backStackEntry.arguments?.getLong("jobId") ?: 0L
            val stepNumber = backStackEntry.arguments?.getInt("stepNumber") ?: 1
            val stepName = backStackEntry.arguments?.getString("stepName") ?: ""
            StepLogsScreen(
                owner = owner,
                repo = repo,
                jobId = jobId,
                stepNumber = stepNumber,
                stepName = stepName,
                onLoggedOut = { navController.navigateToLogin() },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
