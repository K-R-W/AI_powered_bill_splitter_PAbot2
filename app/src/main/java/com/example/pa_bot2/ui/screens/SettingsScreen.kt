package com.example.pa_bot2.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pa_bot2.BuildConfig
import com.example.pa_bot2.model.SettingsRepository
import com.example.pa_bot2.util.SupportedCurrencies
import com.example.pa_bot2.util.AuthHandler
import com.example.pa_bot2.util.SplitwiseAuthHandler
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepository = remember { SettingsRepository(context) }
    
    val isAiEnabled by settingsRepository.isAiEnabledFlow.collectAsState(initial = false)
    val isSplitwiseConnected by settingsRepository.isSplitwiseConnectedFlow.collectAsState(initial = false)
    val userEmail by settingsRepository.userEmailFlow.collectAsState(initial = null)
    val themeMode by settingsRepository.themeModeFlow.collectAsState(initial = "system")
    val defaultCurrency by settingsRepository.defaultCurrencyFlow.collectAsState(initial = "INR")
    var showCurrencyDialog by remember { mutableStateOf(false) }

    var isVerifying by remember { mutableStateOf(false) }
    var isVerifyingSplitwise by remember { mutableStateOf(false) }
    var capturedEmail by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var splitwiseError by remember { mutableStateOf<String?>(null) }

    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        // We'll handle this in the authHandler created below
    }

    val splitwiseAuthHandler = remember {
        SplitwiseAuthHandler(
            context = context,
            settingsRepository = settingsRepository,
            scope = scope,
            onStarted = { 
                isVerifyingSplitwise = true
                splitwiseError = null
            },
            onFinished = {
                isVerifyingSplitwise = false
            },
            onError = {
                splitwiseError = it
                isVerifyingSplitwise = false
            }
        )
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
                error = null
            },
            onFinished = {
                isVerifying = false
            },
            onError = {
                error = it
                isVerifying = false
            }
        )
    }

    val finalAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        scope.launch {
            authHandler.handleAuthResult(result, capturedEmail)
        }
    }

    val finalAuthHandler = remember(finalAuthLauncher) {
        AuthHandler(
            context = context,
            scope = scope,
            settingsRepository = settingsRepository,
            authLauncher = finalAuthLauncher,
            onCapturedEmail = { capturedEmail = it },
            onStarted = { 
                isVerifying = true
                error = null
            },
            onFinished = {
                isVerifying = false
            },
            onError = {
                error = it
                isVerifying = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsSectionHeader(Icons.Rounded.AccountCircle, "Account & AI")

            if (isAiEnabled) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Connected with Google", style = MaterialTheme.typography.labelLarge)
                            Text(userEmail ?: "Unknown account", style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick = {
                            scope.launch {
                                settingsRepository.logoutGoogle()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = "Logout")
                        }
                    }
                }
                
                Text(
                    "You are using your personal Google account's Gemini access for AI features.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isVerifying) {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Connecting...")
                        } else {
                            Icon(
                                Icons.Rounded.AccountCircle, 
                                null, 
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("No Google Account Connected", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Sign in to enable AI-powered bill extraction.",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            
                            if (error != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = error!!,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { finalAuthHandler.handleGoogleSignIn() }) {
                                Text("Connect Google Account")
                            }
                        }
                    }
                }
            }
            
            SettingsSectionHeader(Icons.Rounded.Groups, "Splitwise")

            if (isSplitwiseConnected) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Connected with Splitwise", style = MaterialTheme.typography.labelLarge)
                        }
                        IconButton(onClick = {
                            scope.launch {
                                settingsRepository.setSplitwiseAuth(null)
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = "Disconnect Splitwise")
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isVerifyingSplitwise) {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Connecting to Splitwise...")
                        } else {
                            Text("Not Connected to Splitwise", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Connect to sync groups and members.",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            
                            if (splitwiseError != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = splitwiseError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { splitwiseAuthHandler.startAuthFlow() }) {
                                Text("Connect Splitwise")
                            }
                        }
                    }
                }
            }

            SettingsSectionHeader(Icons.Rounded.Palette, "Appearance")

            Card(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Palette, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("Theme", style = MaterialTheme.typography.titleMedium)
                    }

                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val options = listOf("System", "Light", "Dark")
                        val values = listOf("system", "light", "dark")
                        
                        values.forEachIndexed { index, value ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = values.size),
                                onClick = { 
                                    scope.launch { settingsRepository.setThemeMode(value) }
                                },
                                selected = themeMode == value
                            ) {
                                Text(options[index])
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showCurrencyDialog = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Payments, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Default currency", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Used for new bills",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val current = SupportedCurrencies.find { it.code == defaultCurrency }
                    Text(
                        "${current?.symbol ?: defaultCurrency}  ${current?.code ?: ""}".trim(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.weight(1f))
            Text(
                "PA_bot2 • v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }

    if (showCurrencyDialog) {
        AlertDialog(
            onDismissRequest = { showCurrencyDialog = false },
            title = { Text("Default currency") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(SupportedCurrencies) { currency ->
                        ListItem(
                            headlineContent = { Text(currency.displayName) },
                            supportingContent = { Text(currency.code) },
                            leadingContent = {
                                Text(currency.symbol, style = MaterialTheme.typography.titleMedium)
                            },
                            trailingContent = {
                                if (currency.code == defaultCurrency) {
                                    Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            modifier = Modifier.clickable {
                                scope.launch { settingsRepository.setDefaultCurrency(currency.code) }
                                showCurrencyDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCurrencyDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun SettingsSectionHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
