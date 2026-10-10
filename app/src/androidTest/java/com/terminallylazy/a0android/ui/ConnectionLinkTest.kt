package com.terminallylazy.a0android.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.terminallylazy.a0android.data.ServerProfileStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ConnectionLinkTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun incomingLinkRequiresReviewSurvivesRecreationAndCancelDoesNotSave() {
        launchLink("a0android://connect?server=https%3A%2F%2Fagent.example%3A8443")
        compose.waitUntil(10000) { compose.onAllNodesWithText("Review server").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("https://agent.example:8443").assertIsDisplayed()
        assertNull(compose.activity.browser)
        compose.onNodeWithText("Server name").performTextInput("Incoming server")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Server name").assertTextContains("Incoming server")
        compose.onNodeWithText("https://agent.example:8443").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.activityRule.scenario.recreate()
        compose.onAllNodesWithText("Review server").assertCountEquals(0)
        assertTrue(runBlocking { ServerProfileStore(context).state.first().profiles.isEmpty() })
        assertNull(compose.activity.browser)
    }

    @Test fun invalidIncomingLinkCannotOpenOrSaveAServer() {
        launchLink("a0android://connect?server=https%3A%2F%2Fagent.example%3Ftoken%3Dnot-a-credential")
        compose.waitForIdle()
        compose.onAllNodesWithText("Review server").assertCountEquals(0)
        compose.onAllNodesWithText("not-a-credential", substring = true).assertCountEquals(0)
        assertTrue(runBlocking { ServerProfileStore(context).state.first().profiles.isEmpty() })
        assertNull(compose.activity.browser)
    }

    private fun launchLink(link: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).apply {
            setPackage(context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
