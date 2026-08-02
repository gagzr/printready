package com.printready.app.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.printready.app.ui.screens.*
import com.printready.app.viewmodel.PrintReadyViewModel

object Routes {
    const val DASHBOARD = "dashboard"
    const val SELECT_DOC_TYPE = "select_doc_type/{sourceMode}"
    const val WORKSPACE = "workspace/{multiCard}/{sourceMode}"
}

@Composable
fun PrintReadyNavGraph() {
    val navController = rememberNavController()
    val viewModel: PrintReadyViewModel = viewModel()

    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {
        composable(Routes.DASHBOARD) {
            androidx.compose.runtime.LaunchedEffect(Unit) {
                viewModel.clearWorkspace()
            }
            DashboardScreen(
                viewModel = viewModel,
                onNewScan = { navController.navigate("select_doc_type/scan") },
                onNewGallery = { navController.navigate("select_doc_type/gallery") },
                onPresetSelected = { docType ->
                    viewModel.selectDocumentType(docType)
                    navController.navigate("workspace/false/scan")
                },
                onRecentJobClick = { job ->
                    viewModel.loadPrintJob(job)
                    navController.navigate("workspace/false/scan")
                }
            )
        }
        composable(
            "select_doc_type/{sourceMode}",
            arguments = listOf(navArgument("sourceMode") { type = NavType.StringType })
        ) { backStack ->
            val sourceMode = backStack.arguments?.getString("sourceMode") ?: "scan"
            SelectDocumentTypeScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onDocTypeSelected = { docType ->
                    viewModel.selectDocumentType(docType)
                    navController.navigate("workspace/false/$sourceMode")
                }
            )
        }
        composable(
            "workspace/{multiCard}/{sourceMode}",
            arguments = listOf(
                navArgument("multiCard") { type = NavType.BoolType },
                navArgument("sourceMode") { type = NavType.StringType }
            )
        ) { backStack ->
            val multiCard = backStack.arguments?.getBoolean("multiCard") ?: false
            val sourceMode = backStack.arguments?.getString("sourceMode") ?: "scan"
            A4WorkspaceScreen(
                viewModel = viewModel,
                multiCard = multiCard,
                sourceMode = sourceMode,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
