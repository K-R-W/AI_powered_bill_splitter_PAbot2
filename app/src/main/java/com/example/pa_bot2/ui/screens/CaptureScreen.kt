package com.example.pa_bot2.ui.screens

import android.Manifest
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.pa_bot2.model.Bill
import com.example.pa_bot2.ocr.BillExtractor
import com.example.pa_bot2.ocr.ExtractionResult
import com.example.pa_bot2.ocr.GeminiBillExtractor
import com.example.pa_bot2.model.SettingsRepository
import com.example.pa_bot2.util.createTempFile
import com.example.pa_bot2.util.getCameraProvider
import com.example.pa_bot2.util.refreshGoogleAccessToken
import com.example.pa_bot2.util.takePicture
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun CaptureScreen(
    sharedImageUri: String? = null,
    onBack: () -> Unit,
    onCaptured: (Bill) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    
    val settingsRepository = remember { SettingsRepository(context) }
    val isAiEnabled by settingsRepository.isAiEnabledFlow.collectAsState(initial = false)
    val googleAccessToken by settingsRepository.googleAccessTokenFlow.collectAsState(initial = null)
    val defaultCurrency by settingsRepository.defaultCurrencyFlow.collectAsState(initial = "INR")
    
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    
    val billExtractor = remember(googleAccessToken) {
        Log.d("CaptureScreen", "Initializing extractor. HasToken: ${googleAccessToken != null}")
        googleAccessToken?.let { GeminiBillExtractor(context, it) }
    }
    
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var processingJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Use a stable reference to the current extractor
    val currentExtractor by rememberUpdatedState(billExtractor)

    // Binds the camera preview + capture use cases to the lifecycle. Called on
    // initial load and again whenever extraction fails so the user can retry.
    fun bindCamera() {
        if (!cameraPermissionState.status.isGranted) return
        scope.launch {
            try {
                val cameraProvider = context.getCameraProvider()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Log.e("CaptureScreen", "Camera binding failed", e)
            }
        }
    }

    // Single extraction path shared by camera capture, gallery picks and shared
    // images. [extractUri] is the source to read; [imageUriString] is what gets
    // stored on the resulting Bill.
    fun processImage(extractUri: Uri, imageUriString: String) {
        isProcessing = true
        errorMessage = null
        processingJob = scope.launch {
            try {
                // Wait for the extractor if it's not ready yet (Google token may still be loading)
                var extractor = currentExtractor
                if (extractor == null) {
                    var retryCount = 0
                    while (extractor == null && retryCount < 15) {
                        kotlinx.coroutines.delay(300)
                        extractor = currentExtractor
                        retryCount++
                    }
                }

                if (extractor == null) {
                    errorMessage = "AI engine not ready. Ensure you are signed in with Google."
                    isProcessing = false
                    return@launch
                }

                // Stop the camera while we process
                context.getCameraProvider().unbindAll()

                val result = try {
                    extractor.extract(extractUri)
                } catch (e: GeminiBillExtractor.AuthenticationException) {
                    // Google access tokens expire ~hourly. Silently refresh and retry once
                    // before surfacing an error, so the user isn't asked to sign in again.
                    Log.w("CaptureScreen", "Auth failed during extraction; attempting silent token refresh", e)
                    val refreshedToken = refreshGoogleAccessToken(context, settingsRepository)
                    if (refreshedToken != null) {
                        try {
                            GeminiBillExtractor(context, refreshedToken).extract(extractUri)
                        } catch (e2: Exception) {
                            Log.e("CaptureScreen", "Extraction failed after token refresh", e2)
                            errorMessage = "Authentication failed. Please reconnect Google in Settings."
                            ExtractionResult(emptyList())
                        }
                    } else {
                        errorMessage = "Session expired. Please reconnect Google in Settings."
                        ExtractionResult(emptyList())
                    }
                }

                when {
                    errorMessage != null -> {
                        isProcessing = false
                        bindCamera()
                    }
                    result.items.isEmpty() -> {
                        errorMessage = "No entries found. Try a clearer, well-lit photo of the receipt."
                        isProcessing = false
                        bindCamera()
                    }
                    else -> {
                        val newBill = Bill(
                            title = result.suggestedTitle ?: "Scanned Bill",
                            items = result.items,
                            imageUri = imageUriString,
                            currencyCode = defaultCurrency
                        )
                        onCaptured(newBill)
                    }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.e("CaptureScreen", "Extraction failed", e)
                    errorMessage = e.message ?: "An unknown error occurred during extraction."
                }
                isProcessing = false
                bindCamera()
            } finally {
                processingJob = null
            }
        }
    }

    val handleBack = {
        if (isProcessing) {
            showDiscardDialog = true
        } else {
            onBack()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { processImage(it, it.toString()) }
    }

    // Handle shared image if provided
    LaunchedEffect(sharedImageUri) {
        sharedImageUri?.let {
            processImage(Uri.parse(it), it)
        }
    }

    // Disable back gestures while processing
    BackHandler(enabled = true, onBack = handleBack)

    if (!isAiEnabled) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Icon(Icons.Rounded.ErrorOutline, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(16.dp))
                Text("Scanning needs Google", style = MaterialTheme.typography.headlineSmall)
                Text("Connect your Google account in Settings to scan bills. You can still add bills manually.", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Button(onClick = onBack) { Text("Go Back") }
            }
        }
        return
    }

    LaunchedEffect(cameraPermissionState.status.isGranted) {
        if (cameraPermissionState.status.isGranted) {
            bindCamera()
        } else {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Receipt") },
                navigationIcon = {
                    IconButton(
                        onClick = handleBack
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isProcessing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FloatingActionButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = "Upload from Gallery")
                    }

                    if (cameraPermissionState.status.isGranted) {
                        LargeFloatingActionButton(
                            onClick = {
                                val file = context.createTempFile()
                                isProcessing = true
                                imageCapture.takePicture(
                                    ContextCompat.getMainExecutor(context),
                                    file,
                                    onImageCaptured = { capturedFile ->
                                        processImage(Uri.fromFile(capturedFile), capturedFile.absolutePath)
                                    },
                                    onError = {
                                        Log.e("CaptureScreen", "Capture failed", it)
                                        isProcessing = false
                                    }
                                )
                            }
                        ) {
                            Icon(Icons.Rounded.CameraAlt, contentDescription = "Capture")
                        }
                    }
                    
                    // Spacer to balance the Row if camera isn't granted but we show gallery
                    if (!cameraPermissionState.status.isGranted) {
                        Spacer(modifier = Modifier.size(56.dp))
                    }
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            if (cameraPermissionState.status.isGranted) {
                // Keep AndroidView in composition but camera is unbound
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )
                if (isProcessing) {
                    Card(
                        modifier = Modifier.padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("AI is analyzing the receipt...")
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = handleBack) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Camera permission is required")
                    Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                        Text("Request Permission")
                    }
                }
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard Scan?") },
            text = { Text("The bill is still being processed. Do you want to stop and discard it?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        processingJob?.cancel()
                        isProcessing = false
                        showDiscardDialog = false
                        onBack()
                    }
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Continue Scan")
                }
            }
        )
    }

    errorMessage?.let { error ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Extraction Error") },
            text = { Text(error) },
            confirmButton = {
                Button(onClick = { errorMessage = null }) {
                    Text("OK")
                }
            }
        )
    }
}
