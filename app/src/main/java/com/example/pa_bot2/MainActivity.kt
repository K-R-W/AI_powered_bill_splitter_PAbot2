package com.example.pa_bot2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import com.example.pa_bot2.navigation.Destination
import com.example.pa_bot2.ui.screens.CaptureScreen
import com.example.pa_bot2.ui.screens.AdaptiveMainScreen
import com.example.pa_bot2.ui.screens.DetailScreen
import com.example.pa_bot2.ui.screens.SettingsScreen
import com.example.pa_bot2.ui.screens.OnboardingScreen
import com.example.pa_bot2.ui.theme.PA_bot2Theme
import com.example.pa_bot2.model.Bill
import com.example.pa_bot2.model.AppDatabase
import com.example.pa_bot2.model.BillRepository
import com.example.pa_bot2.model.SettingsRepository
import com.example.pa_bot2.util.SplitwiseAuthHandler
import kotlinx.coroutines.launch

import android.content.Intent

class MainActivity : ComponentActivity() {
    private var sharedImageUriState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = AppDatabase.getDatabase(this)
        BillRepository.initialize(this, database.billDao())
        lifecycleScope.launch {
            BillRepository.loadBills()
        }

        enableEdgeToEdge()
        
        handleIntent(intent)

        setContent {
            val context = LocalContext.current
            val settingsRepository = remember { SettingsRepository(context) }
            val themeMode by settingsRepository.themeModeFlow.collectAsState(initial = "system")
            val sharedImageUri by sharedImageUriState

            PA_bot2Theme(themeMode = themeMode) {
                AppNavigation(settingsRepository, sharedImageUri) {
                    sharedImageUriState.value = null
                }
            }
        }
        
        // Handle initial intent if app was started via deep link
        intent?.data?.let { uri ->
            if (uri.host == "localhost" && uri.port == 8080 && uri.path == "/splitwise-callback") {
                handleSplitwiseCallback(uri)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
        intent.data?.let { uri ->
            if (uri.host == "localhost" && uri.port == 8080 && uri.path == "/splitwise-callback") {
                handleSplitwiseCallback(uri)
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            val uri = intent.getParcelableExtra<android.os.Parcelable>(Intent.EXTRA_STREAM) as? android.net.Uri
            uri?.let {
                lifecycleScope.launch {
                    val localUri = copyUriToInternalStorage(it)
                    sharedImageUriState.value = localUri?.toString()
                }
            }
        }
    }

    private suspend fun copyUriToInternalStorage(uri: android.net.Uri): android.net.Uri? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return@withContext null
            val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(System.currentTimeMillis())
            val storageDir = externalCacheDir ?: cacheDir
            val localFile = java.io.File(storageDir, "SHARED_IMAGE_$timeStamp.jpg")
            
            localFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            android.net.Uri.fromFile(localFile)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to copy shared image", e)
            null
        }
    }

    private fun handleSplitwiseCallback(uri: android.net.Uri) {
        val settingsRepository = SettingsRepository(this)
        val authHandler = SplitwiseAuthHandler(
            context = this,
            settingsRepository = settingsRepository,
            scope = lifecycleScope,
            onStarted = {},
            onFinished = {},
            onError = { /* Log error or show toast */ }
        )
        authHandler.handleRedirect(uri)
    }
}

@Composable
fun AppNavigation(
    settingsRepository: SettingsRepository, 
    sharedImageUri: String? = null,
    onSharedImageHandled: () -> Unit
) {
    val isOnboardingCompleted by settingsRepository.isOnboardingCompletedFlow.collectAsState(initial = null)
    val defaultCurrency by settingsRepository.defaultCurrencyFlow.collectAsState(initial = "INR")

    if (isOnboardingCompleted == null) {
        return
    }

    val initialDestination = remember(isOnboardingCompleted) {
        if (isOnboardingCompleted == true) Destination.Home else Destination.Onboarding
    }
    val backStack = rememberNavBackStack(initialDestination)

    LaunchedEffect(sharedImageUri, isOnboardingCompleted) {
        if (sharedImageUri != null && isOnboardingCompleted == true) {
            if (backStack.none { it is Destination.Capture && it.sharedImageUri == sharedImageUri }) {
                backStack.add(Destination.Capture(sharedImageUri = sharedImageUri))
            }
            onSharedImageHandled()
        }
    }

    val activity = LocalActivity.current

    NavDisplay(
        backStack = backStack,
        onBack = {
            if (backStack.size > 1) {
                backStack.removeAt(backStack.size - 1)
            } else {
                activity?.finish()
            }
        },
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            (fadeIn(animationSpec = tween(280)) +
                slideInHorizontally(animationSpec = tween(280)) { it / 8 }) togetherWith
                (fadeOut(animationSpec = tween(180)) +
                    slideOutHorizontally(animationSpec = tween(180)) { -it / 16 })
        },
        popTransitionSpec = {
            (fadeIn(animationSpec = tween(280)) +
                slideInHorizontally(animationSpec = tween(280)) { -it / 8 }) togetherWith
                (fadeOut(animationSpec = tween(180)) +
                    slideOutHorizontally(animationSpec = tween(180)) { it / 16 })
        },
        predictivePopTransitionSpec = {
            (fadeIn(animationSpec = tween(280)) +
                slideInHorizontally(animationSpec = tween(280)) { -it / 8 }) togetherWith
                (fadeOut(animationSpec = tween(180)) +
                    slideOutHorizontally(animationSpec = tween(180)) { it / 16 })
        },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider<NavKey> {
            entry<Destination.Onboarding> {
                OnboardingScreen(
                    onFinished = {
                        backStack.clear()
                        backStack.add(Destination.Home)
                    }
                )
            }
            entry<Destination.Home> {
                AdaptiveMainScreen(
                    onScanBill = { backStack.add(Destination.Capture()) },
                    onAddManual = { backStack.add(Destination.Details(initialBill = Bill(title = "New Bill", currencyCode = defaultCurrency))) },
                    onOpenSettings = { backStack.add(Destination.Settings) }
                )
            }
            entry<Destination.Capture> { key ->
                CaptureScreen(
                    sharedImageUri = key.sharedImageUri,
                    onBack = { backStack.removeAt(backStack.size - 1) },
                    onCaptured = { bill ->
                        backStack.removeAt(backStack.size - 1)
                        backStack.add(Destination.Details(initialBill = bill))
                    }
                )
            }
            entry<Destination.Details> { key ->
                DetailScreen(
                    billId = key.billId,
                    initialBill = key.initialBill,
                    onBack = { backStack.removeAt(backStack.size - 1) }
                )
            }
            entry<Destination.Settings> {
                SettingsScreen(onBack = { backStack.removeAt(backStack.size - 1) })
            }
        }
    )
}
