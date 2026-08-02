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
fun PrintReadyNavGraph(sharedUris: List<android.net.Uri> = emptyList()) {
    val navController = rememberNavController()
    val viewModel: PrintReadyViewModel = viewModel()

    // If we have shared URIs, we start at select_doc_type and store the uris in the ViewModel
    val startDestination = if (sharedUris.isNotEmpty()) {
        "select_doc_type/shared"
    } else {
        Routes.DASHBOARD
    }

    NavHost(navController = navController, startDestination = startDestination) {
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
                    navController.navigate("workspace/scan")
                },
                onRecentJobClick = { job ->
                    viewModel.loadPrintJob(job)
                    navController.navigate("workspace/scan")
                }
            )
        }
        composable(
            "select_doc_type/{sourceMode}",
            arguments = listOf(navArgument("sourceMode") { type = NavType.StringType })
        ) { backStack ->
            val sourceMode = backStack.arguments?.getString("sourceMode") ?: "scan"
            
            androidx.compose.runtime.LaunchedEffect(sharedUris) {
                if (sourceMode == "shared" && sharedUris.isNotEmpty()) {
                    viewModel.setPendingSharedUris(sharedUris)
                }
            }

            SelectDocumentTypeScreen(
                viewModel = viewModel,
                onBack = { 
                    if (navController.previousBackStackEntry == null) {
                        navController.navigate(Routes.DASHBOARD) {
                            popUpTo(0)
                        }
                    } else {
                        navController.popBackStack() 
                    }
                },
                onDocTypeSelected = { docType ->
                    viewModel.selectDocumentType(docType)
                    navController.navigate("workspace/$sourceMode")
                }
            )
        }
        composable(
            "workspace/{sourceMode}",
            arguments = listOf(
                navArgument("sourceMode") { type = NavType.StringType }
            )
        ) { backStack ->
            val sourceMode = backStack.arguments?.getString("sourceMode") ?: "scan"
            A4WorkspaceScreen(
                viewModel = viewModel,
                sourceMode = sourceMode,
                onBack = {
                    viewModel.resetLayout()
                    navController.popBackStack("dashboard", inclusive = false)
                }
            )
        }
    }
}
