package com.terminallylazy.a0android.qr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LiveData
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/** Non-exported scanner. Only a validated origin is returned to the connection editor. */
class QrScannerActivity : ComponentActivity() {
    private var cameraEnabled by mutableStateOf(false)
    private var previewReady by mutableStateOf(false)
    private var issue by mutableStateOf<String?>(null)
    private var permissionDenied by mutableStateOf(false)
    private var reading by mutableStateOf(false)
    private var picking by mutableStateOf(false)
    private var permissionPending by mutableStateOf(false)
    private var previewView: PreviewView? = null
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var preview: Preview? = null
    private var observedCamera: LiveData<CameraState>? = null
    private var generation = 0
    private var returned = false
    private val analyzerExecutor = Executors.newSingleThreadExecutor()

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionPending = false
        permissionDenied = !granted
        if (granted) {
            cameraEnabled = true
            issue = null
            startCamera()
        } else issue = "Camera access is off. Choose a QR image, allow access in app settings, or enter the address manually."
    }
    private val imagePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        picking = false
        if (uri == null) return@registerForActivityResult
        reading = true
        lifecycleScope.launch {
            try {
                val payload = QrImageReader.read(contentResolver, uri)
                if (payload == null) issue = "No readable QR code found. Choose a clear image with the whole code visible, or enter the address manually."
                else receive(payload)
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { issue = "Could not read this QR image. Choose an image under 12 MiB, or enter the address manually." }
            finally { reading = false }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraEnabled = savedInstanceState?.getBoolean("camera") == true && hasCameraPermission()
        picking = savedInstanceState?.getBoolean("picking") == true
        permissionPending = savedInstanceState?.getBoolean("permissionPending") == true
        enableEdgeToEdge()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) { startCamera(); awaitCancellation() }
        }
        setContent {
            val colors = if (isSystemInDarkTheme()) darkColorScheme(primary = Color(0xFF91E3CB), background = Color(0xFF101817), surface = Color(0xFF101817))
                else lightColorScheme(primary = Color(0xFF176B56), background = Color(0xFFF5FAF7), surface = Color(0xFFF5FAF7))
            MaterialTheme(colorScheme = colors) { Scanner() }
        }
    }

    @Composable
    private fun Scanner() {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Connect with QR", style = MaterialTheme.typography.headlineLarge)
                Text("Scan the QR code in your server's tunnel settings, or a code containing its HTTPS address. You'll review the address before connecting.")
                if (cameraEnabled) {
                    AndroidView(factory = { context ->
                        PreviewView(context).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FIT_CENTER
                            previewStreamState.observe(this@QrScannerActivity) { previewReady = it == PreviewView.StreamState.STREAMING }
                            previewView = this
                            startCamera()
                        }
                    }, modifier = Modifier.fillMaxWidth().height(300.dp), onRelease = {
                        it.previewStreamState.removeObservers(this@QrScannerActivity)
                        if (previewView === it) { stopCamera(); previewView = null }
                    })
                    Text(if (previewReady) "Point the camera at the whole QR code." else "Starting camera…")
                    OutlinedButton(onClick = { cameraEnabled = false; stopCamera() }) { Text("Stop camera") }
                } else {
                    Button(enabled = !reading && !picking && !permissionPending, onClick = {
                        issue = null
                        if (hasCameraPermission()) { permissionDenied = false; cameraEnabled = true; startCamera() }
                        else { permissionPending = true; cameraPermission.launch(Manifest.permission.CAMERA) }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Use camera") }
                }
                OutlinedButton(enabled = !reading && !picking && !permissionPending, onClick = {
                    cameraEnabled = false
                    stopCamera()
                    issue = null
                    picking = true
                    imagePicker.launch(arrayOf("image/*"))
                }, modifier = Modifier.fillMaxWidth()) { Text("Choose QR image") }
                if (reading) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Reading QR image…") }
                issue?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (permissionDenied) TextButton(onClick = {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                }) { Text("Open app settings") }
                Text("Codes are read on this device. Camera images are not saved or uploaded.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { setResult(RESULT_FIRST_USER); finish() }) { Text("Enter address manually") }
                TextButton(onClick = { finish() }) { Text("Cancel") }
            }
        }
    }

    private fun hasCameraPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val view = previewView ?: return
        if (!cameraEnabled || !hasCameraPermission() || returned || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        stopCamera()
        val token = generation
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (token != generation || !cameraEnabled || isDestroyed || returned) return@addListener
            try {
                val cameraProvider = future.get()
                provider = cameraProvider
                val selector = when {
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                    else -> error("No camera")
                }
                val livePreview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                val frames = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                preview = livePreview
                analysis = frames
                frames.setAnalyzer(analyzerExecutor) { frame ->
                    val payload = try {
                        val plane = frame.planes[0]
                        QrDecoder.decode(QrDecoder.luminance(plane.buffer, frame.width, frame.height, plane.rowStride, plane.pixelStride))
                    } catch (_: Exception) { null }
                    finally { frame.close() }
                    if (payload != null) ContextCompat.getMainExecutor(this).execute {
                        if (token == generation && cameraEnabled && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) receive(payload)
                    }
                }
                val camera = cameraProvider.bindToLifecycle(this, selector, livePreview, frames)
                observedCamera = camera.cameraInfo.cameraState
                observedCamera?.observe(this) { state ->
                    if (token == generation && state.error != null) cameraUnavailable()
                }
            } catch (_: Exception) { cameraUnavailable() }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun receive(payload: String) {
        if (returned || isFinishing || isDestroyed) return
        cameraEnabled = false
        stopCamera()
        val origin = QrDestination.parse(payload)
        if (origin == null) { issue = QrDestination.invalidMessage; return }
        returned = true
        setResult(RESULT_OK, Intent().putExtra(ORIGIN_EXTRA, origin.value))
        finish()
    }

    private fun cameraUnavailable() {
        cameraEnabled = false
        stopCamera()
        issue = "Camera unavailable. Choose a QR image or enter the address manually."
    }

    private fun stopCamera() {
        generation++
        previewReady = false
        observedCamera?.removeObservers(this)
        observedCamera = null
        analysis?.clearAnalyzer()
        val owned = listOfNotNull(analysis, preview)
        if (owned.isNotEmpty()) provider?.unbind(*owned.toTypedArray())
        analysis = null
        preview = null
    }

    override fun onPause() { stopCamera(); super.onPause() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("camera", cameraEnabled)
        outState.putBoolean("picking", picking)
        outState.putBoolean("permissionPending", permissionPending)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() { stopCamera(); analyzerExecutor.shutdown(); super.onDestroy() }

    companion object { const val ORIGIN_EXTRA = "qr_server_origin" }
}
