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
import java.net.ServerSocket
import java.net.SocketTimeoutException

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
    private val redirectUri = "http://localhost:$CALLBACK_PORT/splitwise-callback"

    fun startAuthFlow() {
        onStarted()
        scope.launch {
            try {
                val code = withContext(Dispatchers.IO) { listenForCallback() }
                if (code != null) exchangeCodeForToken(code)
                else onError("Splitwise auth timed out or was cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Auth flow error", e)
                onError("Splitwise Auth Exception: ${e.localizedMessage}")
            }
        }

        val authUrl = "https://secure.splitwise.com/oauth/authorize?" +
                "client_id=$clientId&" +
                "redirect_uri=$redirectUri&" +
                "response_type=code"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    // Opens a ServerSocket, waits for the browser redirect, replies with a
    // dismissal page so the user sees a clean message instead of a network error,
    // and returns the authorization code (or null on timeout/error).
    private fun listenForCallback(): String? {
        ServerSocket(CALLBACK_PORT).use { server ->
            server.soTimeout = TIMEOUT_MS
            return try {
                server.accept().use { socket ->
                    val request = socket.getInputStream().bufferedReader().readLine() ?: return null
                    // Request line: "GET /splitwise-callback?code=xxx HTTP/1.1"
                    val code = Regex("[?&]code=([^& ]+)").find(request)?.groupValues?.get(1)
                    val error = Regex("[?&]error=([^& ]+)").find(request)?.groupValues?.get(1)

                    val (status, body) = if (code != null) {
                        "200 OK" to SUCCESS_HTML
                    } else {
                        "400 Bad Request" to errorHtml(error ?: "unknown_error")
                    }
                    socket.getOutputStream().write(
                        "HTTP/1.1 $status\r\nContent-Type: text/html; charset=utf-8\r\nConnection: close\r\n\r\n$body".toByteArray()
                    )
                    code
                }
            } catch (e: SocketTimeoutException) {
                Log.w(TAG, "Callback listener timed out")
                null
            }
        }
    }

    private suspend fun exchangeCodeForToken(code: String) {
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

            val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")
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
            Log.e(TAG, "Error exchanging code", e)
            onError("Splitwise Auth Exception: ${e.localizedMessage}")
        }
    }

    companion object {
        private const val TAG = "SplitwiseAuthHandler"
        private const val CALLBACK_PORT = 8080
        private const val TIMEOUT_MS = 5 * 60 * 1000 // 5 minutes

        private val SUCCESS_HTML = """
            <!DOCTYPE html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Authenticated</title>
            <style>body{font-family:sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;margin:0;background:#f5f5f5}
            .card{background:#fff;border-radius:12px;padding:32px 40px;text-align:center;box-shadow:0 2px 8px rgba(0,0,0,.12)}
            h2{margin:0 0 8px;color:#1a1a1a}p{margin:0;color:#666}</style></head>
            <body><div class="card"><h2>&#10003; Connected to Splitwise</h2><p>You can close this tab and return to the app.</p></div></body></html>
        """.trimIndent()

        private fun errorHtml(error: String) = """
            <!DOCTYPE html><html><head><meta charset="utf-8"><title>Error</title>
            <style>body{font-family:sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;margin:0;background:#f5f5f5}
            .card{background:#fff;border-radius:12px;padding:32px 40px;text-align:center;box-shadow:0 2px 8px rgba(0,0,0,.12)}
            h2{margin:0 0 8px;color:#c00}p{margin:0;color:#666}</style></head>
            <body><div class="card"><h2>Authentication failed</h2><p>$error — please return to the app and try again.</p></div></body></html>
        """.trimIndent()
    }
}
