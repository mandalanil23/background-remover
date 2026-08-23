package com.bgremover.pngmaker.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import android.net.Uri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bgremover.pngmaker.ui.EditorViewModel
import com.bgremover.pngmaker.ui.screens.AboutScreen
import com.bgremover.pngmaker.ui.screens.CropScreen
import com.bgremover.pngmaker.ui.screens.HomeScreen
import com.bgremover.pngmaker.ui.screens.ImageSelectionScreen
import com.bgremover.pngmaker.ui.screens.PreviewScreen
import com.bgremover.pngmaker.ui.screens.PrivacyPolicyScreen
import com.bgremover.pngmaker.ui.screens.ProcessingScreen
import com.bgremover.pngmaker.ui.screens.RecentImagesScreen
import com.bgremover.pngmaker.ui.screens.SaveShareScreen
import com.bgremover.pngmaker.ui.screens.SettingsScreen
import com.bgremover.pngmaker.ui.screens.SplashScreen

private const val TRANSITION_MS = 260

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    initialImageUri: Uri? = null,
    onInitialImageConsumed: () -> Unit = {}
) {
    // Activity-scoped so the current image survives navigation and rotation.
    val editorViewModel: EditorViewModel = viewModel()
    val editorState by editorViewModel.state.collectAsStateWithLifecycle()

    // An image shared in from another app skips the splash and lands on the picker screen
    // with the photo already loaded.
    val startRoute = if (initialImageUri != null) Routes.HOME else Routes.SPLASH

    LaunchedEffect(initialImageUri) {
        val uri = initialImageUri ?: return@LaunchedEffect
        editorViewModel.onImageSelected(uri)
        navController.navigate(Routes.SELECT)
        onInitialImageConsumed()
    }

    NavHost(
        navController = navController,
        startDestination = startRoute,
        modifier = modifier,
        enterTransition = {
            slideInHorizontally(tween(TRANSITION_MS)) { it / 6 } + fadeIn(tween(TRANSITION_MS))
        },
        exitTransition = {
            fadeOut(tween(TRANSITION_MS / 2))
        },
        popEnterTransition = {
            fadeIn(tween(TRANSITION_MS))
        },
        popExitTransition = {
            slideOutHorizontally(tween(TRANSITION_MS)) { it / 6 } + fadeOut(tween(TRANSITION_MS))
        }
    ) {
        composable(
            route = Routes.SPLASH,
            exitTransition = { fadeOut(tween(TRANSITION_MS)) }
        ) {
            SplashScreen(
                onFinished = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onUploadImage = { navController.navigate(Routes.SELECT) },
                onOpenRecent = { navController.navigate(Routes.RECENT) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenAbout = { navController.navigate(Routes.ABOUT) },
                onOpenPrivacy = { navController.navigate(Routes.PRIVACY) }
            )
        }

        composable(Routes.SELECT) {
            ImageSelectionScreen(
                state = editorState,
                onImagePicked = editorViewModel::onImageSelected,
                onCropImage = { navController.navigate(Routes.CROP) },
                onStartProcessing = {
                    editorViewModel.process()
                    navController.navigate(Routes.PROCESSING)
                },
                onDismissError = editorViewModel::dismissError,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.CROP) {
            CropScreen(
                sourceUri = editorState.source?.uri,
                onCropped = { uri ->
                    // The cropped file becomes the source, so the rest of the pipeline —
                    // processing, preview, export — needs no knowledge of cropping at all.
                    editorViewModel.onImageSelected(uri)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.PROCESSING,
            enterTransition = { fadeIn(tween(TRANSITION_MS)) },
            exitTransition = { fadeOut(tween(TRANSITION_MS)) }
        ) {
            ProcessingScreen(
                state = editorState,
                onCancel = {
                    editorViewModel.cancelProcessing()
                    navController.popBackStack()
                },
                onRetry = editorViewModel::process,
                onDismissError = {
                    editorViewModel.dismissError()
                    navController.popBackStack()
                },
                onFinished = {
                    navController.navigate(Routes.PREVIEW) {
                        popUpTo(Routes.PROCESSING) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.PREVIEW) {
            PreviewScreen(
                state = editorState,
                onContinue = { navController.navigate(Routes.SAVE) },
                onProcessAnother = {
                    // Navigate first so the preview never flashes an empty state while the
                    // view model is being cleared.
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                    editorViewModel.reset()
                },
                onConsumeNotice = editorViewModel::consumeNotice,
                onDismissError = editorViewModel::dismissError,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SAVE) {
            SaveShareScreen(
                state = editorState,
                onSave = editorViewModel::savePng,
                onShare = editorViewModel::share,
                onOpenSaved = editorViewModel::openSavedImage,
                requiresLegacyPermission = editorViewModel.requiresLegacyStoragePermission(),
                onProcessAnother = {
                    // Navigate first so the preview never flashes an empty state while the
                    // view model is being cleared.
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                    editorViewModel.reset()
                },
                onConsumeNotice = editorViewModel::consumeNotice,
                onDismissError = editorViewModel::dismissError,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.RECENT) {
            RecentImagesScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
                onOpenAbout = { navController.navigate(Routes.ABOUT) }
            )
        }

        composable(Routes.PRIVACY) {
            PrivacyPolicyScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.ABOUT) {
            AboutScreen(
                onBack = { navController.popBackStack() },
                onOpenPrivacy = { navController.navigate(Routes.PRIVACY) }
            )
        }
    }
}
