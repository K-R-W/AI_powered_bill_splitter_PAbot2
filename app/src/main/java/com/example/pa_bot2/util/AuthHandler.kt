package com.example.pa_bot2.util

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.pa_bot2.BuildConfig
import com.example.pa_bot2.model.SettingsRepository
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Silently obtains a fresh Google access token for the previously authorized
 * account, without showing any UI. Returns the new token, or null if the user
 * needs to re-authorize interactively (e.g. consent revoked) or no account is stored.
 *
 * Google access tokens from the authorization API are short-lived (~1 hour), so
 * this lets the app transparently recover from expiry instead of forcing a sign-in.
 */
suspend fun refreshGoogleAccessToken(
    context: Context,
    settingsRepository: SettingsRepository
): String? {
    val email = settingsRepository.userEmailFlow.first() ?: return null
    return try {
        val authorizationClient = Identity.getAuthorizationClient(context)
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/generative-language.retriever")))
            .build()
        val result = authorizationClient.authorize(request).await()
        if (result.hasResolution()) {
            // Interaction required — caller must trigger an interactive sign-in.
            null
        } else {
            result.accessToken?.also { token ->
                settingsRepository.setGoogleAuth(email, token)
            }
        }
    } catch (e: Exception) {
        Log.e("AuthHandler", "Silent Google token refresh failed", e)
        null
    }
}

class AuthHandler(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val authLauncher: ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult>,
    private val onCapturedEmail: (String) -> Unit,
    private val onStarted: () -> Unit,
    private val onFinished: () -> Unit,
    private val onError: (String) -> Unit
) {
    private val credentialManager = CredentialManager.create(context)
    private val authorizationClient = Identity.getAuthorizationClient(context)
    private val serverClientId = BuildConfig.GOOGLE_SERVER_CLIENT_ID

    fun handleGoogleSignIn() {
        scope.launch {
            try {
                onStarted()
                Log.d("AuthHandler", "Starting Google Sign-In flow")
                
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(serverClientId)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(context, request)
                val credential = result.credential

                Log.d("AuthHandler", "Received credential type: ${credential.type}")

                if (credential is GoogleIdTokenCredential) {
                    val email = credential.id
                    Log.d("AuthHandler", "Successfully authenticated: $email")
                    onCapturedEmail(email)
                    requestGeminiAuthorization(email)
                } else if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    // Fallback: manually parse if the 'is' check fails
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val email = googleIdTokenCredential.id
                    Log.d("AuthHandler", "Successfully authenticated via fallback: $email")
                    onCapturedEmail(email)
                    requestGeminiAuthorization(email)
                } else {
                    onError("Unexpected credential type: ${credential.type}")
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w("AuthHandler", "User cancelled the sign-in flow")
                onError("Sign-in cancelled.")
            } catch (e: Exception) {
                Log.e("AuthHandler", "Error during sign-in flow", e)
                onError("Google Sign-In failed: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    private suspend fun requestGeminiAuthorization(email: String) {
        try {
            val requestedScopes = listOf(Scope(
//                "https://www.googleapis.com/auth/generative-language"
                "https://www.googleapis.com/auth/generative-language.retriever"
            ))
            val authorizationRequest = AuthorizationRequest.builder()
                .setRequestedScopes(requestedScopes)
                .build()

            Log.d("AuthHandler", "Requesting authorization for Gemini scope")
            val authResult = authorizationClient.authorize(authorizationRequest).await()
            
            if (authResult.hasResolution()) {
                Log.d("AuthHandler", "Authorization requires user resolution")
                val pendingIntent = authResult.pendingIntent
                if (pendingIntent != null) {
                    authLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                } else {
                    onError("Authorization required but no resolution provided.")
                }
            } else {
                val accessToken = authResult.accessToken
                Log.d("AuthHandler", "Authorization granted without resolution")
                if (accessToken != null) {
                    settingsRepository.setGoogleAuth(email, accessToken)
                    onFinished()
                } else {
                    onError("Failed to obtain access token.")
                }
            }
        } catch (e: Exception) {
            Log.e("AuthHandler", "Error during Gemini authorization", e)
            onError("Gemini authorization failed: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    suspend fun handleAuthResult(result: ActivityResult, capturedEmail: String?, isOnboarding: Boolean = false) {
        try {
            Log.d("AuthHandler", "Handling authorization result: ${result.resultCode}")
            if (result.resultCode != Activity.RESULT_OK) {
                onError("Authorization was not completed.")
                return
            }

            val authorizationResult = authorizationClient.getAuthorizationResultFromIntent(result.data)
            val accessToken = authorizationResult.accessToken
            if (capturedEmail != null && accessToken != null) {
                Log.d("AuthHandler", "Successfully obtained access token for $capturedEmail")
                settingsRepository.setGoogleAuth(capturedEmail, accessToken)
                if (isOnboarding) {
                    settingsRepository.setOnboardingCompleted(true)
                }
                onFinished()
            } else {
                onError("Failed to obtain access token or email.")
            }
        } catch (e: Exception) {
            Log.e("AuthHandler", "Error handling authorization result", e)
            onError("Authorization failed: ${e.localizedMessage ?: "Unknown error"}")
        }
    }
}
