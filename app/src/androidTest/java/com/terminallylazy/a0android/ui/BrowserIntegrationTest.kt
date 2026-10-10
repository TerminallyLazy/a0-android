package com.terminallylazy.a0android.ui

import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.terminallylazy.a0android.data.ServerProfile
import com.terminallylazy.a0android.data.ServerProfileStore
import com.terminallylazy.a0android.web.BrowserProfiles
import com.terminallylazy.a0android.web.ServerOrigin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern
import org.json.JSONObject

class BrowserIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private lateinit var server: MockWebServer
    private lateinit var fixtureTls: HandshakeCertificates
    private lateinit var profile: ServerProfile
    private val extraProfiles = mutableListOf<ServerProfile>()
    private val directName = "a0-direct-${System.nanoTime()}.txt"

    @Before fun setup() {
        if (android.os.Build.VERSION.SDK_INT >= 37) instrumentation.uiAutomation.grantRuntimePermission(context.packageName, "android.permission.ACCESS_LOCAL_NETWORK")
        val assets = instrumentation.context.assets
        val pem = assets.open("fixture-cert.pem").bufferedReader().use { it.readText() } + "\n" + assets.open("fixture-key.pem").bufferedReader().use { it.readText() }
        fixtureTls = HandshakeCertificates.Builder().heldCertificate(HeldCertificate.decode(pem)).build()
        server = fixtureServer()
        server.start()
        profile = runBlocking { ServerProfileStore(context).save("Android test fixture", requireNotNull(ServerOrigin.parse(server.url("/").toString()))) }
        compose.waitUntil(20000) { compose.activity.browser?.profile?.id == profile.id }
        eventually { js("document.getElementById('ready')?.textContent") == "\"Fixture ready\"" }
    }

    private fun fixtureServer() = MockWebServer().apply {
        // MockWebServer disables reuse for an ephemeral first bind. Keep it enabled
        // so the restarted fixture can bind that same port while old sockets drain.
        serverSocketFactory = object : javax.net.ServerSocketFactory() {
            override fun createServerSocket() = object : java.net.ServerSocket() {
                override fun setReuseAddress(on: Boolean) = super.setReuseAddress(true)
            }
            override fun createServerSocket(port: Int) = createServerSocket().apply { bind(java.net.InetSocketAddress(port)) }
            override fun createServerSocket(port: Int, backlog: Int) = createServerSocket().apply { bind(java.net.InetSocketAddress(port), backlog) }
            override fun createServerSocket(port: Int, backlog: Int, address: java.net.InetAddress) =
                createServerSocket().apply { bind(java.net.InetSocketAddress(address, port), backlog) }
        }
        useHttps(fixtureTls.sslSocketFactory(), false)
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path?.substringBefore('?')) {
                "/" -> MockResponse().setHeader("Content-Type", "text/html").setBody("""
                    <!doctype html><html><meta name="viewport" content="width=device-width,initial-scale=1"><body>
                    <h1 id="ready">Fixture ready</h1><input id="upload" type="file" multiple>
                    <button onclick="document.querySelector('#upload').click()">Choose upload</button>
                    <a id="direct" download="fixture.txt" href="/file">Download</a>
                    </body></html>
                """.trimIndent())
                // HTTP/2 is allowed to split the Cookie header into multiple fields.
                "/file" -> if (request.headers.values("Cookie").joinToString("; ").contains("session_fixture=allowed") && request.headers.values("Cookie").joinToString("; ").contains("csrf_fixture=allowed"))
                    MockResponse().setHeader("Content-Type", "text/plain").setHeader("Content-Disposition", "attachment; filename=$directName").setBody("direct fixture bytes")
                    else MockResponse().setResponseCode(403)
                else -> MockResponse().setResponseCode(404)
            }
        }
    }

    @After fun teardown() {
        if (compose.activityRule.scenario.state != androidx.lifecycle.Lifecycle.State.DESTROYED) {
            compose.runOnUiThread { compose.activity.browser?.dispose(); compose.activity.browser = null }
        }
        if (::profile.isInitialized) runBlocking { BrowserProfiles.forget(context, profile); ServerProfileStore(context).forget(profile.id) }
        extraProfiles.forEach { extra -> runBlocking { BrowserProfiles.forget(context, extra); ServerProfileStore(context).forget(extra.id) } }
        if (::server.isInitialized) server.shutdown()
    }

    @Test fun loadsTrustedHttpsAndRequiredFeatures() {
        assertTrue(BrowserProfiles.supportsDownloads())
        assertTrue(BrowserProfiles.supportsMultiple())
        assertEquals("true", js("window.isSecureContext"))
        assertEquals("\"object\"", js("typeof window.A0Downloads"))
        assertEquals("true", js("window.__a0DownloadAdapter === true"))
    }

    @Test fun viewportUnitsMatchVisibleWebViewAndSurviveResize() {
        fun assertViewport() {
            val heights = JSONObject(js("""JSON.stringify((() => {
                const values = {inner: innerHeight};
                for (const unit of ['vh', 'dvh', 'svh', 'lvh']) {
                    const probe = document.createElement('div');
                    probe.style.cssText = 'position:fixed;visibility:hidden;width:1px;height:100' + unit;
                    document.body.append(probe); values[unit] = probe.getBoundingClientRect().height; probe.remove();
                }
                return values;
            })())""").let { org.json.JSONTokener(it).nextValue() as String })
            assertTrue("Visible viewport must have height", heights.getDouble("inner") > 100)
            for (unit in listOf("vh", "dvh", "svh", "lvh")) {
                assertEquals("100$unit must match the WebView viewport", heights.getDouble("inner"), heights.getDouble(unit), 2.0)
            }
        }
        assertViewport()
        val device = UiDevice.getInstance(instrumentation)
        try {
            device.setOrientationLeft()
            compose.waitUntil(15000) { compose.activity.browser != null }
            eventually { js("document.getElementById('ready')?.textContent") == "\"Fixture ready\"" }
            assertViewport()
        } finally { device.setOrientationNatural(); device.unfreezeRotation() }
    }

    @Test fun connectionControlsStayInCompactMenuWithoutReplacingWorkspace() {
        compose.onNodeWithTag("workspace-toolbar").assertHeightIsEqualTo(48.dp)
        compose.onNodeWithText("Reload").assertDoesNotExist()
        compose.onNodeWithText("LAN access").assertDoesNotExist()
        val previous = compose.activity.browser
        compose.onNodeWithContentDescription("Connection menu").performClick()
        compose.onNodeWithText(profile.origin.value).assertIsDisplayed()
        compose.onNodeWithText("Servers").assertIsDisplayed()
        compose.onNodeWithText("Reload").assertIsDisplayed()
        UiDevice.getInstance(instrumentation).pressBack()
        compose.onNodeWithText("Reload").assertDoesNotExist()
        assertSame(previous, compose.activity.browser)
        assertEquals("\"Fixture ready\"", js("document.getElementById('ready').textContent"))
    }

    @Test fun viewportSizedDialogsAndStreamedReplyRemainVisible() {
        js("""(() => {
            document.body.innerHTML = '<style>.modal{position:fixed;inset:0;background:#0008;display:grid;place-items:center}.modal-inner{width:90vw;max-height:88vh;overflow:auto;background:white}.rail{position:fixed;right:0;top:20px;max-height:calc(100dvh - 32px);overflow:auto}.reply{max-height:70vh;overflow:auto}</style><nav class="rail"><button style="width:48px;height:48px">Files</button></nav><article class="reply" id="stream"></article><div class="modal"><section class="modal-inner"><h2>Settings fixture</h2><p>Dialog content must remain visible.</p><button id="close">Close</button></section></div>';
            document.querySelector('#close').onclick = () => document.querySelector('.modal').remove();
            return true;
        })()""")
        assertEquals("true", js("document.querySelector('.modal-inner').getBoundingClientRect().height > 100"))
        assertEquals("true", js("document.querySelector('.rail').getBoundingClientRect().height >= 48"))
        UiDevice.getInstance(instrumentation).wait(Until.findObject(By.text("Close")), 10000).click()
        assertEquals("true", js("document.querySelector('.modal') === null"))
        js("""window.fixtureStreamDone=false;let n=0;const timer=setInterval(()=>{
            document.querySelector('#stream').textContent+='Synthetic response '+(++n)+'. ';
            if(n===5){clearInterval(timer);window.fixtureStreamDone=true;}
        },100);""")
        eventually { js("window.fixtureStreamDone") == "true" }
        assertEquals("true", js("document.querySelector('#stream').getBoundingClientRect().height > 20 && document.querySelector('#stream').textContent.includes('response 5')"))
    }

    @Test fun stalledHealthySyncRecoversWithoutOverlappingReadsOrResending() {
        js("""(() => {
            document.body.insertAdjacentHTML('beforeend','<textarea id="chat-input"></textarea><button id="send-button">Send</button><p id="reply"></p>');
            window.fixtureSync={mode:'HEALTHY',lastSeq:0};
            window.fixtureContext='synthetic';window.reads=0;window.sends=0;
            window.Alpine={store:name=>name==='sync'?fixtureSync:name==='chats'?{selectedContext:{running:true}}:undefined};
            window.getContext=()=>fixtureContext;
            window.poll=()=>{window.reads++;return new Promise(resolve=>{window.releaseRead=()=>{document.querySelector('#reply').textContent='Recovered synthetic reply';resolve({ok:true});};});};
            document.querySelector('#send-button').onclick=()=>window.sends++;
        })()""")
        eventually { js("window.reads") == "1" }
        SystemClock.sleep(2500)
        assertEquals("One read stays in flight", "1", js("window.reads"))
        js("window.dispatchEvent(new Event('a0-app-background'));window.releaseRead();")
        eventually { js("document.querySelector('#reply').textContent") == "\"Recovered synthetic reply\"" }
        SystemClock.sleep(2500)
        assertEquals("No reads in background", "1", js("window.reads"))
        assertEquals("Recovery never resends", "0", js("window.sends"))
        js("window.dispatchEvent(new Event('a0-app-foreground'));")
        eventually { js("window.reads") == "2" }
        js("window.fixtureSync.mode='DEGRADED';window.releaseRead();")
        SystemClock.sleep(3000)
        assertEquals("WebUI owns degraded polling", "2", js("window.reads"))
        js("window.fixtureSync.mode='HEALTHY';window.fixtureContext=null;")
        SystemClock.sleep(2500)
        assertEquals("No chat needs no recovery", "2", js("window.reads"))
    }

    @Test fun androidBackDismissesToolsBeforeLeavingWorkspace() {
        js("document.body.insertAdjacentHTML('beforeend','<div id=\"chat-input\"></div><nav class=\"right-canvas-rail\"><button class=\"right-canvas-rail-button\">Fixture tool</button></nav>')")
        eventually { js("!!document.querySelector('#a0-tools-toggle')") == "true" }
        js("document.querySelector('#a0-tools-toggle').click()")
        assertEquals("true", js("document.body.classList.contains('a0-tools-open')"))
        // JavaScript-only checks do not advance Compose's test clock to register BackHandler.
        compose.waitForIdle()
        UiDevice.getInstance(instrumentation).pressBack()
        eventually { js("document.body.classList.contains('a0-tools-open')") == "false" }
        assertEquals("\"Fixture ready\"", js("document.querySelector('#ready').textContent"))
    }

    @Test fun immediateBlobRevocationSavesExactBytesThroughSaf() {
        val name = "a0-blob-${System.nanoTime()}.txt"
        js("""(() => { const b = new Blob(['exact blob fixture bytes'], {type:'text/plain'}); const u = URL.createObjectURL(b); const a = document.createElement('a'); a.href = u; a.download = '$name'; document.body.append(a); a.click(); a.remove(); URL.revokeObjectURL(u); return true; })()""")
        savePicker()
        compose.waitUntil(20000) { compose.onAllNodesWithText("Saved $name", substring = true).fetchSemanticsNodes().isNotEmpty() }
        assertEquals("exact blob fixture bytes", shell("cat /sdcard/Download/$name"))
        shell("rm /sdcard/Download/$name")
    }

    @Test fun bridgeSurvivesDocumentReplacementAndRejectsSubframes() {
        js("document.open(); document.write('<html><body><h1 id=ready>Replacement</h1></body></html>'); document.close();")
        assertEquals("\"Replacement\"", js("document.getElementById('ready').textContent"))
        val name = "a0-replaced-${System.nanoTime()}.txt"
        js("""(() => {const a = document.createElement('a'); const u = URL.createObjectURL(new Blob(['after replacement'])); a.href=u; a.download='$name'; a.click(); URL.revokeObjectURL(u); return true;})()""")
        savePicker()
        compose.waitUntil(20000) { compose.onAllNodesWithText("Saved $name", substring = true).fetchSemanticsNodes().isNotEmpty() }
        assertEquals("after replacement", shell("cat /sdcard/Download/$name"))
        shell("rm /sdcard/Download/$name")
        // A same-origin child can see the origin-bound object, but native code refuses its messages.
        js("""(() => {window.childGotReply=false;const f=document.createElement('iframe'); f.srcdoc="<script>A0Downloads.onmessage=()=>parent.childGotReply=true;A0Downloads.postMessage(JSON.stringify({v:1,kind:'hello',documentId:'child'}));setTimeout(()=>parent.childAttempted=true,1000);<\/script>"; document.body.append(f); return true; })()""")
        eventually { js("window.childAttempted") == "true" }
        assertEquals("false", js("window.childGotReply"))
    }

    @Test fun canceledPickerAllowsNextDownload() {
        fun blob(name: String) = js("""(() => { const a=document.createElement('a'); a.href=URL.createObjectURL(new Blob(['cancel test'])); a.download='$name'; a.click(); return true; })()""")
        blob("cancel-me.txt")
        val device = UiDevice.getInstance(instrumentation)
        assertTrue(device.wait(Until.hasObject(By.pkg("com.google.android.documentsui")), 15000))
        device.pressBack()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Download canceled.").fetchSemanticsNodes().isNotEmpty() }
        val name = "a0-next-${System.nanoTime()}.txt"
        blob(name); savePicker()
        compose.waitUntil(20000) { compose.onAllNodesWithText("Saved $name", substring = true).fetchSemanticsNodes().isNotEmpty() }
        shell("rm /sdcard/Download/$name")
    }

    @Test fun canceledTransferRemovesLatePickerDestination() {
        val name = "a0-stale-${System.nanoTime()}.txt"
        js("""(() => {const a=document.createElement('a');a.href=URL.createObjectURL(new Blob(['stale']));a.download='$name';a.click();URL.revokeObjectURL(a.href);})()""")
        val device = UiDevice.getInstance(instrumentation)
        assertTrue(device.wait(Until.hasObject(By.pkg("com.google.android.documentsui")), 15000))
        compose.runOnUiThread { compose.activity.browser?.downloads?.cancel() }
        savePicker()
        assertTrue(device.wait(Until.gone(By.pkg("com.google.android.documentsui")), 10000))
        eventually { shell("ls /sdcard/Download/$name 2>/dev/null").isEmpty() }
    }

    @Test fun recreationRestoresSameServerWithoutSwitchingProfiles() {
        val id = profile.id
        compose.activityRule.scenario.recreate()
        compose.waitUntil(20000) { compose.activity.browser?.profile?.id == id }
        eventually { js("document.getElementById('ready')?.textContent") == "\"Fixture ready\"" }
        assertTrue(js("location.origin").contains("localhost"))
    }

    @Test fun serverRestartOffersRetryAndPreservesSession() {
        js("document.cookie='restart_session=retained;path=/';localStorage.setItem('restart_marker','retained')")
        val port = server.port
        server.shutdown()
        openConnectionAction("Reload")
        compose.waitUntil(15000) {
            compose.onAllNodesWithText("Could not reach the server.", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        server = fixtureServer().also { it.start(port) }
        val previous = compose.activity.browser
        compose.onNodeWithText("Retry").performClick()
        compose.waitUntil(10000) { compose.activity.browser != null && compose.activity.browser !== previous }
        eventually { js("document.getElementById('ready')?.textContent") == "\"Fixture ready\"" }
        assertEquals(profile.id, compose.activity.browser?.profile?.id)
        assertTrue(js("document.cookie").contains("restart_session=retained"))
        assertEquals("\"retained\"", js("localStorage.getItem('restart_marker')"))
        val request = server.takeRequest(5, TimeUnit.SECONDS)
        assertEquals("GET", request?.method)
        assertEquals("/", request?.path)
        assertTrue(request?.headers?.values("Cookie")?.joinToString("; ")?.contains("restart_session=retained") == true)
    }

    @Test fun editingSavedServerSurvivesRecreation() {
        js("document.cookie='editor_session=retained;path=/';localStorage.setItem('editor_marker','retained')")
        openConnectionAction("Servers")
        compose.onNodeWithText("Edit").performClick()
        compose.onNodeWithText("Server name").performTextReplacement("Renamed fixture")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Edit server").assertExists()
        compose.onNodeWithText("Renamed fixture").assertExists()
        compose.onNodeWithText("Connect").performClick()
        compose.waitUntil(20000) { compose.activity.browser?.profile?.name == "Renamed fixture" }
        val saved = runBlocking { ServerProfileStore(context).state.first() }
        extraProfiles.addAll(saved.profiles.filter { it.id != profile.id })
        assertEquals(listOf(profile.id), saved.profiles.map { it.id })
        assertEquals(profile.id, saved.selectedId)
        eventually { js("document.cookie").contains("editor_session=retained") }
        assertEquals("\"retained\"", js("localStorage.getItem('editor_marker')"))
    }

    @Test fun changingOriginAfterEditorRecreationReplacesProfile() {
        js("document.cookie='editor_session=old;path=/'")
        val newOrigin = requireNotNull(ServerOrigin.parse(server.url("/").newBuilder().host("127.0.0.1").build().toString()))
        openConnectionAction("Servers")
        compose.onNodeWithText("Edit").performClick()
        compose.onNodeWithText("HTTPS address").performTextReplacement(newOrigin.value)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Edit server").assertExists()
        compose.onNodeWithText("Connect").performClick()
        compose.waitUntil(20000) { compose.activity.browser?.profile?.origin == newOrigin }
        val saved = runBlocking { ServerProfileStore(context).state.first() }
        extraProfiles.addAll(saved.profiles.filter { it.id != profile.id })
        assertEquals(1, saved.profiles.size)
        assertNotEquals(profile.id, saved.selectedId)
        assertEquals(newOrigin, saved.selected?.origin)
        eventually { js("document.getElementById('ready')?.textContent") == "\"Fixture ready\"" }
        assertFalse(js("document.cookie").contains("editor_session"))
    }

    @Test fun microphoneGrantAndBackgroundStopsCapture() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, "android.permission.RECORD_AUDIO")
        js("window.mediaResult='pending'; navigator.mediaDevices.getUserMedia({audio:true}).then(s=>{window.testStream=s;window.mediaResult='granted'}).catch(e=>{window.mediaResult=e.name});")
        compose.waitUntil(10000) { compose.onAllNodesWithText("Use your microphone?").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Continue").performClick()
        eventually { js("window.mediaResult") != "\"pending\"" }
        assertEquals("Microphone outcome", "\"granted\"", js("window.mediaResult"))
        assertEquals("\"live\"", js("window.testStream.getAudioTracks()[0].readyState"))
        compose.runOnUiThread { compose.activity.browser?.pause() }
        eventually { js("window.testStream.getAudioTracks()[0].readyState") == "\"ended\"" }
        compose.runOnUiThread { compose.activity.browser?.resume() }
    }

    @Test fun profilesIsolateCookiesEvenOnSameHostname() {
        js("document.cookie='test_cookie=server_a; path=/';localStorage.setItem('marker','server_a');")
        val first = profile
        val second = runBlocking { ServerProfileStore(context).save("Second isolated profile", first.origin) }
        extraProfiles.add(second)
        compose.waitUntil(20000) { compose.activity.browser?.profile?.id == second.id }
        eventually { js("document.getElementById('ready')?.textContent") == "\"Fixture ready\"" }
        assertFalse(js("document.cookie").contains("server_a"))
        assertEquals("null", js("localStorage.getItem('marker')"))
        js("document.cookie='test_cookie=server_b; path=/'")
        runBlocking { ServerProfileStore(context).select(first.id) }
        compose.waitUntil(20000) { compose.activity.browser?.profile?.id == first.id }
        eventually { js("document.cookie").contains("server_a") }
        assertFalse(js("document.cookie").contains("server_b"))
    }

    @Test fun liveServerLoadsStockWebUiOverHttps() {
        val address = InstrumentationRegistry.getArguments().getString("a0Server")
        org.junit.Assume.assumeTrue("Live server address supplied", address != null)
        val live = runBlocking { ServerProfileStore(context).save("Agent Zero live check", requireNotNull(ServerOrigin.parse(requireNotNull(address)))) }
        extraProfiles.add(live)
        compose.waitUntil(20000) { compose.activity.browser?.profile?.id == live.id }
        if (live.origin.url.host.endsWith(".devtunnels.ms")) {
            UiDevice.getInstance(instrumentation).wait(Until.findObject(By.text("Continue")), 15000)?.click()
        }
        try {
            eventually(60000) { js("!!document.querySelector('#chat-input') && !!document.querySelector('#send-button')") == "true" }
        } catch (failure: AssertionError) {
            fail("Live bootstrap state: " + js("JSON.stringify({path:location.pathname,ready:document.readyState,splash:!!document.querySelector('#startup-status'),componentCount:document.querySelectorAll('x-component').length,bodyLength:document.body?.textContent?.length})"))
        }
        assertEquals("true", js("window.isSecureContext"))
        assertEquals("true", js("window.__a0DownloadAdapter === true"))
        assertTrue(js("location.origin").contains(live.origin.url.host))
        eventually { js("window.Alpine?.store('sync')?.mode") == "\"HEALTHY\"" }
        // Open read-only WebUI surfaces without inspecting settings values or private chat bodies.
        listOf("settings/settings.html", "components/modals/scheduler/scheduler-modal.html", "projects/project-list.html", "components/plugins/list/plugin-list.html").forEach { path ->
            js("window.testModalReady=false; document.addEventListener('modal-content-loaded',()=>window.testModalReady=true,{once:true});openModal(${JSONObject.quote(path)});")
            eventually { js("window.testModalReady") == "true" }
            assertEquals("Live modal must be visibly expanded", "true", js("document.querySelector('.modal .modal-inner')?.getBoundingClientRect().height > 100"))
            js("closeModal();")
            eventually { js("document.querySelectorAll('.modal').length === 0") == "true" }
        }
        eventually { js("!!document.querySelector('#a0-tools-toggle')") == "true" }
        assertEquals("Rail stays out of the conversation", "true", js("getComputedStyle(document.querySelector('.right-canvas-rail')).display === 'none'"))
        js("document.querySelector('#a0-tools-toggle').click()")
        assertEquals("Live tools must have usable height", "true", js("document.querySelector('.right-canvas-rail')?.getBoundingClientRect().height > 100"))
        assertEquals("Tools have touch-sized targets", "true", js("[...document.querySelectorAll('.right-canvas-rail-button')].filter(e=>e.getClientRects().length).every(e=>e.getBoundingClientRect().height>=44 && e.getBoundingClientRect().width>=44)"))
        js("document.querySelector('#a0-tools-toggle').click()")
        // Real registered panels, including Swarm which has no modalPath on this server.
        listOf("Files", "Swarm").forEach { title ->
            js("document.querySelector('#a0-tools-toggle').click();document.querySelector('.right-canvas-rail-button[aria-label=\"$title\"]').click();")
            eventually { js("Alpine.store('rightCanvas').isOpen && document.querySelector('.right-canvas-surface-panel.is-active')?.getBoundingClientRect().height > 100") == "true" }
            eventually { js("Math.abs(document.querySelector('#right-canvas').getBoundingClientRect().width-innerWidth)<2") == "true" }
            assertEquals("Content is interactive", "true", js("getComputedStyle(document.querySelector('.right-canvas-surface-panel.is-active')).pointerEvents !== 'none'"))
            js("document.querySelector('.right-canvas-close-button').click()")
            eventually { js("Alpine.store('rightCanvas').isOpen") == "false" }
        }
        js("""window.fixtureSurface=Alpine.store('rightCanvas').getSurface('swarm');window.fixtureGate=fixtureSurface.canOpen;fixtureSurface.canOpen=()=>false;window.fixtureOpened=null;Alpine.store('rightCanvas').open('swarm').then(result=>window.fixtureOpened=result);""")
        eventually { js("window.fixtureOpened") == "false" }
        assertEquals("Server eligibility is retained", "false", js("Alpine.store('rightCanvas').isOpen"))
        assertEquals("Failed opening is explained", "false", js("document.querySelector('#a0-tools-status').hidden"))
        js("fixtureSurface.canOpen=fixtureGate;document.querySelector('#a0-tools-status').hidden=true;")
        // Render synthetic streamed text through the real server module. No prompt is sent.
        js("""window.testReply='pending';(async()=>{
            // A fresh profile has no selected chat: show the local synthetic render surface only.
            const fixtureStyle=document.createElement('style');
            fixtureStyle.textContent='#chat-area-wrapper{display:flex!important}.welcome-host{display:none!important}';
            document.head.append(fixtureStyle);
            try {
                const messages=await import(new URL('/js/messages.js',location.href).href);
                for(const content of ['Synthetic Android reply', 'Synthetic Android reply: **visible final answer**.']) {
                    messages.drawMessageResponse({id:'a0-android-render-fixture',type:'response',content,kvps:{finished:true}});
                    await new Promise(resolve=>setTimeout(resolve,150));
                }
                const row=document.querySelector('#message-a0-android-render-fixture');
                const body=row?.querySelector('.message-body');
                window.testReply=!!body && body.getBoundingClientRect().height>20 && body.textContent.includes('visible final answer') ? 'visible' : 'hidden';
                row?.remove();
            } catch(error) {window.testReply=String(error.stack).slice(0,1600);}
            finally {fixtureStyle.remove();}
        })();""")
        eventually { js("window.testReply") != "\"pending\"" }
        assertEquals("Real WebUI response renderer", "\"visible\"", js("window.testReply"))
    }

    @Test fun largeAndEmptyBlobsPreserveExactBytes() {
        listOf(0, 262161).forEach { size ->
            val name = "a0-size-$size-${System.nanoTime()}.bin"
            js("""(() => {const data=new Uint8Array($size);for(let i=0;i<data.length;i++)data[i]=i%251;const a=document.createElement('a');a.href=URL.createObjectURL(new Blob([data]));a.download='$name';a.click();URL.revokeObjectURL(a.href);})()""")
            savePicker()
            compose.waitUntil(20000) { compose.onAllNodesWithText("Saved $name", substring = true).fetchSemanticsNodes().isNotEmpty() }
            val expected = java.security.MessageDigest.getInstance("SHA-256").digest(ByteArray(size) { (it % 251).toByte() }).joinToString("") { "%02x".format(it) }
            assertEquals(expected, shell("sha256sum /sdcard/Download/$name").substringBefore(' '))
            shell("rm /sdcard/Download/$name")
        }
    }

    @Test fun oversizedBlobFailsBeforeSavePicker() {
        js("""(() => {const a=document.createElement('a');const unit=new Uint8Array(1024*1024);a.href=URL.createObjectURL(new Blob(Array(101).fill(unit)));a.download='too-large.bin';a.click();URL.revokeObjectURL(a.href);})()""")
        compose.waitUntil(15000) { compose.onAllNodesWithText("100 MiB limit", substring = true).fetchSemanticsNodes().isNotEmpty() }
        assertFalse(UiDevice.getInstance(instrumentation).hasObject(By.pkg("com.google.android.documentsui")))
    }

    @Test fun microphoneDenialLeavesTypingAvailable() {
        js("window.mediaResult='pending';navigator.mediaDevices.getUserMedia({audio:true}).then(s=>{s.getTracks().forEach(t=>t.stop());window.mediaResult='granted'}).catch(e=>window.mediaResult=e.name)")
        compose.waitUntil(10000) { compose.onAllNodesWithText("Use your microphone?").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Not now").performClick()
        eventually { js("window.mediaResult") == "\"NotAllowedError\"" }
        assertEquals("\"Fixture ready\"", js("document.querySelector('#ready').textContent"))
    }

    @Test fun forgetClearsOnlyTheSelectedSession() {
        js("document.cookie='forget_me=yes;path=/';localStorage.setItem('forget_me','yes')")
        openConnectionAction("Servers")
        compose.waitUntil(10000) { compose.activity.browser == null }
        assertTrue(runBlocking { BrowserProfiles.forget(context, profile) })
        compose.onNodeWithText("Workspace").performClick()
        compose.waitUntil(20000) { compose.activity.browser?.profile?.id == profile.id }
        eventually { js("document.getElementById('ready')?.textContent") == "\"Fixture ready\"" }
        assertFalse(js("document.cookie").contains("forget_me"))
        assertEquals("null", js("localStorage.getItem('forget_me')"))
    }

    @Test fun invalidCertificateIsBlocked() {
        val bad = MockWebServer()
        val untrusted = HandshakeCertificates.Builder().heldCertificate(HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()).build()
        bad.useHttps(untrusted.sslSocketFactory(), false)
        bad.enqueue(MockResponse().setBody("must not load"))
        bad.start()
        try {
            val other = runBlocking { ServerProfileStore(context).save("Untrusted certificate", requireNotNull(ServerOrigin.parse(bad.url("/").toString()))) }
            extraProfiles.add(other)
            compose.waitUntil(20000) { compose.onAllNodesWithText("certificate could not be verified", substring = true).fetchSemanticsNodes().isNotEmpty() }
            assertEquals(0, bad.requestCount)
        } finally { bad.shutdown() }
    }

    @Test fun authenticatedDirectDownloadUsesProfileCookiesAndSaf() {
        js("document.cookie='session_fixture=allowed;path=/';document.cookie='csrf_fixture=allowed;path=/';document.querySelector('#direct').removeAttribute('download');")
        UiDevice.getInstance(instrumentation).wait(Until.findObject(By.text("Download")), 10000).click()
        savePicker()
        compose.waitUntil(20000) { compose.onAllNodesWithText("Saved $directName", substring = true).fetchSemanticsNodes().isNotEmpty() }
        assertEquals("direct fixture bytes", shell("cat /sdcard/Download/$directName"))
        shell("rm /sdcard/Download/$directName")
    }

    @Test fun uploadPickerReturnsReadableContent() {
        val name = "a0-upload-${System.nanoTime()}.txt"
        js("""(() => {const a=document.createElement('a');a.href=URL.createObjectURL(new Blob(['upload fixture bytes'],{type:'text/plain'}));a.download='$name';a.click();URL.revokeObjectURL(a.href);})()""")
        savePicker()
        compose.waitUntil(20000) { compose.onAllNodesWithText("Saved $name", substring = true).fetchSemanticsNodes().isNotEmpty() }
        js("document.querySelector('#upload').setAttribute('accept','text/plain');")
        val device = UiDevice.getInstance(instrumentation)
        device.wait(Until.findObject(By.text("Choose upload")), 10000).click()
        val file = device.wait(Until.findObject(By.text(name)), 15000)
        assertNotNull("Fixture is visible in Android's document picker", file)
        file.click()
        eventually { js("document.querySelector('#upload').files.length") == "1" }
        js("document.querySelector('#upload').files[0].text().then(t=>window.uploadedFixture=t)")
        eventually { js("window.uploadedFixture") == "\"upload fixture bytes\"" }
        shell("rm /sdcard/Download/$name")
    }

    @Test fun sameHostnameDifferentPortsDoNotShareCookies() {
        val second = MockWebServer()
        val assets = instrumentation.context.assets
        val pem = assets.open("fixture-cert.pem").bufferedReader().use { it.readText() } + "\n" + assets.open("fixture-key.pem").bufferedReader().use { it.readText() }
        second.useHttps(HandshakeCertificates.Builder().heldCertificate(HeldCertificate.decode(pem)).build().sslSocketFactory(), false)
        second.dispatcher = server.dispatcher
        second.start()
        try {
            js("document.cookie='first_port=yes;path=/'")
            val other = runBlocking { ServerProfileStore(context).save("Other port", requireNotNull(ServerOrigin.parse(second.url("/").toString()))) }
            extraProfiles.add(other)
            compose.waitUntil(20000) { compose.activity.browser?.profile?.id == other.id }
            eventually { js("document.getElementById('ready')?.textContent") == "\"Fixture ready\"" }
            assertFalse(js("document.cookie").contains("first_port"))
            js("document.cookie='second_port=yes;path=/'")
            openConnectionAction("Servers")
            compose.waitUntil(10000) { compose.activity.browser == null }
            assertTrue(runBlocking { BrowserProfiles.forget(context, profile) })
            compose.onNodeWithText("Workspace").performClick()
            compose.waitUntil(20000) { compose.activity.browser?.profile?.id == other.id }
            eventually { js("document.cookie").contains("second_port=yes") }
        } finally { second.shutdown() }
    }

    @Test fun rendererTerminationOffersWorkingRetry() {
        org.junit.Assume.assumeTrue(androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.WEB_VIEW_RENDERER_TERMINATE))
        val previous = compose.activity.browser
        compose.runOnUiThread {
            val view = requireNotNull(compose.activity.browser).view
            assertTrue(requireNotNull(androidx.webkit.WebViewCompat.getWebViewRenderProcess(view)).terminate())
        }
        compose.waitUntil(15000) { compose.onAllNodesWithText("The web page stopped. Reload to reconnect.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Retry").performClick()
        compose.waitUntil(10000) { compose.activity.browser != null && compose.activity.browser !== previous }
        eventually { js("document.getElementById('ready')?.textContent") == "\"Fixture ready\"" }
    }

    private fun savePicker() {
        val device = UiDevice.getInstance(instrumentation)
        val save = device.wait(Until.findObject(By.text(Pattern.compile("save", Pattern.CASE_INSENSITIVE))), 15000)
        assertNotNull("Android save picker appeared", save)
        save.click()
    }
    private fun js(script: String): String {
        val latch = CountDownLatch(1); val result = AtomicReference("")
        instrumentation.runOnMainSync { compose.activity.browser?.view?.evaluateJavascript(script) { result.set(it); latch.countDown() } }
        assertTrue("JavaScript completed", latch.await(8, TimeUnit.SECONDS))
        return result.get()
    }
    private fun openConnectionAction(label: String) {
        compose.onNodeWithContentDescription("Connection menu").performClick()
        compose.onNodeWithText(label).performClick()
    }

    private fun eventually(timeout: Long = 20000, check: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < end) { if (check()) return; SystemClock.sleep(150) }
        assertTrue("WebView condition reached", check())
    }
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }
}
