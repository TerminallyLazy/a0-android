package com.terminallylazy.a0android.ui

import android.Manifest
import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import androidx.camera.core.CameraState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.terminallylazy.a0android.data.ServerProfileStore
import com.terminallylazy.a0android.qr.QrScannerActivity
import com.terminallylazy.a0android.web.BrowserProfiles
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.*
import org.junit.Assert.*
import org.junit.runners.MethodSorters
import java.util.concurrent.TimeUnit

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class QrConnectionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val images = mutableListOf<Uri>()
    private val store get() = ServerProfileStore(context)

    @Before fun setup() {
        Intents.init()
        assertTrue(runBlocking { store.state.first().profiles.isEmpty() })
    }

    @After fun teardown() {
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<QrScannerActivity>().forEach { it.finish() }
        }
        instrumentation.waitForIdleSync()
        compose.runOnUiThread { compose.activity.browser?.dispose(); compose.activity.browser = null }
        runBlocking { store.state.first().profiles.forEach { BrowserProfiles.forget(context, it); store.forget(it.id) } }
        images.forEach { context.contentResolver.delete(it, null, null) }
        Intents.release()
    }

    @Test fun a_cameraDenialKeepsImageAndManualChoicesAvailable() {
        // This suite runs on a fresh task emulator before the later camera-grant test.
        openScanner()
        compose.onNodeWithText("Use camera").performClick()
        val device = UiDevice.getInstance(instrumentation)
        val deny = device.wait(Until.findObject(By.res("com.android.permissioncontroller", "permission_deny_button")), 10000)
        assertNotNull("Expected the Android camera permission prompt", deny)
        deny.click()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Camera access is off.", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Choose QR image").assertIsEnabled()
        compose.onNodeWithText("Enter address manually").performScrollTo().performClick()
        compose.onNodeWithText("Connect your server").assertIsDisplayed()
        assertTrue(runBlocking { store.state.first().profiles.isEmpty() })
    }

    @Test fun b_validImageIsOnlyADraftUntilConfirmedAndCancelDiscardsIt() {
        stubImage("https://agent.example:8443/")
        openScanner()
        compose.onNodeWithText("Choose QR image").performClick()
        awaitReview()
        compose.onNodeWithText("https://agent.example:8443").assertIsDisplayed()
        assertNull(compose.activity.browser)
        assertTrue(runBlocking { store.state.first().profiles.isEmpty() })
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Add server").performClick()
        assertEquals("", compose.onNodeWithText("HTTPS address").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
    }

    @Test fun c_reviewSurvivesRecreationAndOnlyConnectOpensHttpsFixture() {
        if (android.os.Build.VERSION.SDK_INT >= 37) instrumentation.uiAutomation.grantRuntimePermission(context.packageName, "android.permission.ACCESS_LOCAL_NETWORK")
        val assets = instrumentation.context.assets
        val pem = assets.open("fixture-cert.pem").bufferedReader().use { it.readText() } + "\n" + assets.open("fixture-key.pem").bufferedReader().use { it.readText() }
        val tls = HandshakeCertificates.Builder().heldCertificate(HeldCertificate.decode(pem)).build()
        MockWebServer().use { server ->
            server.useHttps(tls.sslSocketFactory(), false)
            server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("<html><body>QR connection fixture</body></html>"))
            server.start()
            stubImage(server.url("/").toString())
            openScanner()
            compose.onNodeWithText("Choose QR image").performClick()
            awaitReview()
            compose.onNodeWithText("Server name").performTextInput("QR fixture")
            compose.activityRule.scenario.recreate()
            awaitReview()
            compose.onNodeWithText("Server name").assertTextContains("QR fixture")
            assertEquals(0, server.requestCount)
            assertTrue(runBlocking { store.state.first().profiles.isEmpty() })
            compose.onNodeWithText("Connect").performClick()
            compose.waitUntil(15000) { compose.activity.browser?.profile?.name == "QR fixture" }
            assertNotNull(server.takeRequest(15, TimeUnit.SECONDS))
            assertEquals(server.url("/").toString().removeSuffix("/"), runBlocking { store.state.first().selected?.origin?.value })
        }
    }

    @Test fun d_credentialQrIsRejectedWithoutEchoingItsContents() {
        stubImage("https://agent.example?token=fixture-secret")
        openScanner()
        compose.onNodeWithText("Choose QR image").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Use a QR code containing only", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("fixture-secret", substring = true).assertCountEquals(0)
        assertTrue(runBlocking { store.state.first().profiles.isEmpty() })
        compose.onNodeWithText("Choose QR image").assertIsEnabled()
    }

    @Test fun e_cancelingPickerOrScannerDoesNotOpenAnEditorOrPersistAProfile() {
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(ActivityResult(Activity.RESULT_CANCELED, null))
        openScanner()
        compose.onNodeWithText("Choose QR image").performClick()
        compose.onNodeWithText("Use camera").assertIsEnabled()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Add server").assertIsDisplayed()
        compose.onAllNodesWithText("Review server").assertCountEquals(0)
        assertTrue(runBlocking { store.state.first().profiles.isEmpty() })
    }

    @Test fun z_cameraPreviewClosesInBackgroundAndResumesAfterRecreation() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
        openScanner()
        compose.onNodeWithText("Use camera").performClick()
        awaitPreview()
        val scanner = scannerActivity()
        instrumentation.runOnMainSync { scanner.recreate() }
        awaitPreview()
        val recreated = scannerActivity()
        UiDevice.getInstance(instrumentation).pressHome()
        val provider = ProcessCameraProvider.getInstance(context).get(10, TimeUnit.SECONDS)
        val deadline = SystemClock.elapsedRealtime() + 10000
        var closed = false
        while (!closed && SystemClock.elapsedRealtime() < deadline) {
            instrumentation.runOnMainSync { closed = provider.availableCameraInfos.all { it.cameraState.value?.type == CameraState.Type.CLOSED } }
            SystemClock.sleep(50)
        }
        assertTrue("Camera must close when backgrounded", closed)
        instrumentation.runOnMainSync { recreated.startActivity(Intent(recreated, QrScannerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) }
        awaitPreview()
        compose.onNodeWithText("Stop camera").performClick()
        compose.onNodeWithText("Use camera").assertIsDisplayed()
        assertTrue(runBlocking { store.state.first().profiles.isEmpty() })
    }

    private fun openScanner() { compose.onNodeWithText("Scan QR code").performClick(); compose.onNodeWithText("Connect with QR").assertIsDisplayed() }
    private fun awaitReview() { compose.waitUntil(15000) { compose.onAllNodesWithText("Review server").fetchSemanticsNodes().isNotEmpty() } }
    private fun awaitPreview() { compose.waitUntil(20000) { compose.onAllNodesWithText("Point the camera at the whole QR code.").fetchSemanticsNodes().isNotEmpty() } }
    private fun scannerActivity(): QrScannerActivity {
        var current: QrScannerActivity? = null
        instrumentation.runOnMainSync { current = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<QrScannerActivity>().single() }
        return requireNotNull(current)
    }
    private fun stubImage(payload: String) {
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 512, 512)
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(512 * 512) { if (matrix[it % 512, it / 512]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
        bitmap.setPixels(pixels, 0, 512, 0, 0, 512, 512)
        val uri = requireNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "a0-qr-fixture-${System.nanoTime()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        }))
        images += uri
        context.contentResolver.openOutputStream(uri).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, requireNotNull(it)) }
        bitmap.recycle()
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(ActivityResult(Activity.RESULT_OK, Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)))
    }
}
