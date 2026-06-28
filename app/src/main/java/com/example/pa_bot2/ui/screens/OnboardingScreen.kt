package com.example.pa_bot2.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.pa_bot2.model.SettingsRepository
import com.example.pa_bot2.util.AuthHandler
import kotlinx.coroutines.launch

enum class OnboardingStep {
    WELCOME,
    SIGN_IN_PROGRESS
}

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepository = remember { SettingsRepository(context) }
    
    var currentStep by remember { mutableStateOf(OnboardingStep.WELCOME) }
    var capturedEmail by remember { mutableStateOf<String?>(null) }
    var isVerifying by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        // We'll handle this in the authHandler created below
    }

    val authHandler = remember(authLauncher) {
        AuthHandler(
            context = context,
            scope = scope,
            settingsRepository = settingsRepository,
            authLauncher = authLauncher,
            onCapturedEmail = { capturedEmail = it },
            onStarted = { 
                isVerifying = true
                currentStep = OnboardingStep.SIGN_IN_PROGRESS
                error = null
            },
            onFinished = {
                scope.launch {
                    settingsRepository.setOnboardingCompleted(true)
                    onFinished()
                }
            },
            onError = {
                error = it
                currentStep = OnboardingStep.WELCOME
                isVerifying = false
            }
        )
    }

    // Since we need to call handleAuthResult which is a suspend function, 
    // and we need the specific authHandler instance, we can't easily put it inside the launcher callback 
    // if the launcher callback is defined before the authHandler.
    // However, we can use a SideEffect or just re-capture the launcher.
    
    // Let's redefine the launcher to use the handler correctly
    val finalAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        scope.launch {
            authHandler.handleAuthResult(result, capturedEmail, isOnboarding = true)
        }
    }
    
    // Update the authHandler to use the final launcher
    val finalAuthHandler = remember(finalAuthLauncher) {
        AuthHandler(
            context = context,
            scope = scope,
            settingsRepository = settingsRepository,
            authLauncher = finalAuthLauncher,
            onCapturedEmail = { capturedEmail = it },
            onStarted = { 
                isVerifying = true
                currentStep = OnboardingStep.SIGN_IN_PROGRESS
                error = null
            },
            onFinished = {
                scope.launch {
                    settingsRepository.setOnboardingCompleted(true)
                    onFinished()
                }
            },
            onError = {
                error = it
                currentStep = OnboardingStep.WELCOME
                isVerifying = false
            }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(100.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Welcome to PA_bot2",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Use your own Google Gemini access to automatically extract details from your bills.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            if (error != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(48.dp))
            
            if (currentStep == OnboardingStep.SIGN_IN_PROGRESS) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Signing you in...")
            } else {
                Button(
                    onClick = { finalAuthHandler.handleGoogleSignIn() },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    enabled = !isVerifying
                ) {
                    Icon(Icons.Rounded.Login, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Login with Google")
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            settingsRepository.setOnboardingCompleted(true)
                            onFinished()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    enabled = !isVerifying
                ) {
                    Text("Skip for now")
                }
            }
        }
    }
}
