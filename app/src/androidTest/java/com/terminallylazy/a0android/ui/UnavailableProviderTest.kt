package com.terminallylazy.a0android.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.terminallylazy.a0android.data.ServerProfileStore
import com.terminallylazy.a0android.web.BrowserProfiles
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test

/** Run on an emulator with no usable WebView provider; other environments skip this case. */
class UnavailableProviderTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val store get() = ServerProfileStore(InstrumentationRegistry.getInstrumentation().targetContext)

    @After fun cleanup() {
        runBlocking { store.state.first().profiles.forEach { store.forget(it.id) } }
    }

    @Test fun nativeSetupAndQrStayAvailableWhileWorkspaceIsBlocked() {
        assumeFalse("Requires an emulator without a usable WebView provider", BrowserProfiles.hasProvider())
        assertFalse(BrowserProfiles.supportsMultiple())
        assertFalse(BrowserProfiles.supportsDownloads())
        compose.onNodeWithText("Scan QR code").performClick()
        compose.onNodeWithText("Enter address manually").performScrollTo().performClick()
        compose.onNodeWithText("Server name").performTextInput("Unavailable provider fixture")
        compose.onNodeWithText("HTTPS address").performTextInput("https://example.invalid")
        compose.onNodeWithText("Connect").performClick()
        val explanation = "Install or enable Android System WebView, then reopen Agent Zero to use a workspace."
        compose.waitUntil(15000) { compose.onAllNodesWithText(explanation).fetchSemanticsNodes().isNotEmpty() }
        assertNull(compose.activity.browser)
        val original = runBlocking { store.state.first().selected }
        assertNotNull(original)
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText(explanation).assertExists()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15000) { compose.onAllNodesWithText(explanation).fetchSemanticsNodes().isNotEmpty() }
        assertNull(compose.activity.browser)
        assertEquals(original?.id, runBlocking { store.state.first().selectedId })
        compose.onNodeWithContentDescription("Connection menu").performClick()
        compose.onNodeWithText("Servers").performClick()
        compose.onNodeWithText("Add server").assertIsDisplayed()
    }
}
