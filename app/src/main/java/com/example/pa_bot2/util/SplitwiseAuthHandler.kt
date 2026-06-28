package com.example.pa_bot2.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.pa_bot2.BuildConfig
import com.example.pa_bot2.model.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class SplitwiseAuthHandler(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
    private val onStarted: () -> Unit,
    private val onFinished: () -> Unit,
    private val onError: (String) -> Unit
) {
    private val client = OkHttpClient()
    private val clientId = BuildConfig.SPLITWISE_CLIENT_ID
    private val clientSecret = BuildConfig.SPLITWISE_CLIENT_SECRET
    private val redirectUri = "http://localhost:8080/splitwise-callback"

    fun startAuthFlow() {
        onStarted()
        val authUrl = "https://secure.splitwise.com/oauth/authorize?" +
                "client_id=$clientId&" +
                "redirect_uri=$redirectUri&" +
                "response_type=code"
        
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun handleRedirect(uri: Uri) {
        val code = uri.getQueryParameter("code")
        if (code != null) {
            exchangeCodeForToken(code)
        } else {
            val error = uri.getQueryParameter("error") ?: "Unknown error"
            onError("Splitwise Auth Error: $error")
        }
    }

    private fun exchangeCodeForToken(code: String) {
        scope.launch {
            try {
                val requestBody = FormBody.Builder()
                    .add("client_id", clientId)
                    .add("client_secret", clientSecret)
                    .add("code", code)
                    .add("redirect_uri", redirectUri)
                    .add("grant_type", "authorization_code")
                    .build()

                val request = Request.Builder()
                    .url("https://secure.splitwise.com/oauth/token")
                    .post(requestBody)
                    .build()

                val response = withContext(Dispatchers.IO) {
                    client.newCall(request).execute()
                }

                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    val json = JSONObject(responseBody ?: "{}")
                    val accessToken = json.optString("access_token")
                    
                    if (accessToken.isNotEmpty()) {
                        settingsRepository.setSplitwiseAuth(accessToken)
                        onFinished()
                    } else {
                        onError("Failed to obtain Splitwise access token")
                    }
                } else {
                    onError("Splitwise token exchange failed: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e("SplitwiseAuthHandler", "Error exchanging code", e)
                onError("Splitwise Auth Exception: ${e.localizedMessage}")
            }
        }
    }
}
