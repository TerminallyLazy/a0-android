package com.terminallylazy.a0android.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.terminallylazy.a0android.BuildConfig
import com.terminallylazy.a0android.data.ServerProfile
import com.terminallylazy.a0android.files.DownloadCoordinator
import com.terminallylazy.a0android.files.TransferStatus
import com.terminallylazy.a0android.files.discardCreatedDocument
import com.terminallylazy.a0android.qr.QrDestination
import com.terminallylazy.a0android.qr.ConnectionLink
import com.terminallylazy.a0android.qr.QrScannerActivity
import com.terminallylazy.a0android.web.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress

class MainActivity : ComponentActivity() {
    internal var browser: BrowserController? = null
    private var restoredBrowser: Bundle? = null
    private var fileTarget: BrowserController? = null
    private var saveTarget: DownloadCoordinator? = null
    private var audioTarget: BrowserController? = null
    private var audioPrompt by mutableStateOf(false)
    private var localPromptResult: ((Boolean) -> Unit)? = null
    private var pendingConnection by mutableStateOf<String?>(null)

    private val openFiles = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uris = if (result.resultCode == RESULT_OK) {
            val data = result.data
            data?.clipData?.let { clip -> (0 until clip.itemCount).map { clip.getItemAt(it).uri } }
                ?: listOfNotNull(data?.data)
        } else emptyList()
        fileTarget?.filesSelected(uris); fileTarget = null
    }
    private val createFile = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data?.takeIf { result.resultCode == RESULT_OK }
        val target = saveTarget
        saveTarget = null
        if (target != null) target.destinationChosen(uri)
        else if (uri != null) discardCreatedDocument(this, lifecycleScope, uri) {
            android.widget.Toast.makeText(this, "Download canceled. An empty file may remain in the selected folder.", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    private val microphone = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        audioTarget?.audioPermissionResult(granted); audioTarget = null
    }
    private val localNetwork = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        localPromptResult?.invoke(granted); localPromptResult = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BrowserProfiles.pruneForgotten(this)
        restoredBrowser = savedInstanceState?.getBundle("browser")
        if (savedInstanceState == null) receiveConnection(intent)
        enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            val colors = if (dark) darkColorScheme(primary = Color(0xFF91E3CB), background = Color(0xFF101817), surface = Color(0xFF101817))
                else lightColorScheme(primary = Color(0xFF176B56), background = Color(0xFFF5FAF7), surface = Color(0xFFF5FAF7))
            MaterialTheme(colorScheme = colors) {
                Shell()
                if (audioPrompt) AlertDialog(
                    onDismissRequest = { declineAudio() },
                    title = { Text("Use your microphone?") },
                    text = { Text("${audioTarget?.profile?.name ?: "This server"} can receive audio while this app is open. Android will ask for microphone access.") },
                    confirmButton = { TextButton(onClick = {
                        audioPrompt = false
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            audioTarget?.audioPermissionResult(true); audioTarget = null
                        } else microphone.launch(Manifest.permission.RECORD_AUDIO)
                    }) { Text("Continue") } },
                    dismissButton = { TextButton(onClick = { declineAudio() }) { Text("Not now") } },
                )
            }
        }
    }

    private fun declineAudio() { audioPrompt = false; audioTarget?.audioPermissionResult(false); audioTarget = null }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receiveConnection(intent)
    }

    private fun receiveConnection(incoming: Intent) {
        if (incoming.action != Intent.ACTION_VIEW) return
        val address = incoming.dataString?.let(ConnectionLink::parse)
        if (address == null) {
            android.widget.Toast.makeText(this, "This connection link is invalid. Use an HTTPS server address.", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        pendingConnection = address.value
        // onCreate reads links only for a fresh Activity; saved UI state owns recreation.
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Shell(vm: ShellViewModel = viewModel()) {
        val profileState by vm.profiles.collectAsStateWithLifecycle()
        val error by vm.error.collectAsStateWithLifecycle()
        val busy by vm.busy.collectAsStateWithLifecycle()
        var settings by rememberSaveable { mutableStateOf(false) }
        var editingId by rememberSaveable { mutableStateOf<String?>(null) }
        var editor by rememberSaveable { mutableStateOf(false) }
        var qrAddress by rememberSaveable { mutableStateOf<String?>(null) }
        var scanPending by rememberSaveable { mutableStateOf(false) }
        val scanner = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (scanPending) {
                scanPending = false
                val origin = result.data?.getStringExtra(QrScannerActivity.ORIGIN_EXTRA)
                    ?.takeIf { result.resultCode == RESULT_OK }?.let(QrDestination::parse)
                if (origin != null || result.resultCode == RESULT_FIRST_USER) {
                    editingId = null
                    qrAddress = origin?.value
                    settings = true
                    editor = true
                    vm.clearError()
                }
            }
        }
        var forget by remember { mutableStateOf<ServerProfile?>(null) }
        // Resolve restored editor identity only after persistent profiles have loaded.
        // An empty initial list must never turn an existing-server edit into an add.
        val profiles = profileState
        if (profiles == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return
        }
        LaunchedEffect(pendingConnection) {
            pendingConnection?.let { address ->
                editingId = null
                qrAddress = address
                settings = true
                editor = true
                vm.clearError()
                pendingConnection = null
            }
        }
        val editing = profiles.profiles.find { it.id == editingId }
        val selected = profiles.selected
        val showSettings = settings || selected == null
        val darkSystem = isSystemInDarkTheme()
        val workspaceColors = darkColorScheme(primary = Color(0xFF91E3CB), background = Color(0xFF141922), surface = Color(0xFF141922))
        SideEffect {
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = showSettings && !darkSystem
                isAppearanceLightNavigationBars = showSettings && !darkSystem
            }
        }
        BackHandler(showSettings && selected != null && !editor) { if (!busy) settings = false }
        Scaffold(
            containerColor = if (showSettings) MaterialTheme.colorScheme.background else workspaceColors.background,
            topBar = {
                if (showSettings) TopAppBar(title = { Text("Agent Zero", style = MaterialTheme.typography.titleMedium) }, actions = {
                    if (selected != null) TextButton(enabled = !busy, onClick = { settings = false }) { Text("Workspace") }
                })
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
                if (showSettings) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Your agents.\nYour server.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Text("Connect to Agent Zero over HTTPS. Your server provides the workspace; this app handles files, voice, and Android controls.")
                        Button(enabled = !busy && !scanPending, onClick = { editingId = null; qrAddress = null; editor = true; vm.clearError() }, modifier = Modifier.fillMaxWidth()) { Text("Add server") }
                        OutlinedButton(enabled = !busy && !scanPending, onClick = {
                            scanPending = true
                            scanner.launch(Intent(this@MainActivity, QrScannerActivity::class.java))
                        }, modifier = Modifier.fillMaxWidth()) { Text("Scan QR code") }
                        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        profiles.profiles.forEach { profile ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(profile.name, style = MaterialTheme.typography.titleMedium)
                                    Text(profile.origin.value, style = MaterialTheme.typography.bodySmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(enabled = !busy, onClick = { vm.select(profile.id); settings = false }) { Text("Open") }
                                        TextButton(enabled = !busy, onClick = { editingId = profile.id; qrAddress = null; editor = true; vm.clearError() }) { Text("Edit") }
                                        TextButton(enabled = !busy, onClick = { forget = profile }) { Text("Forget") }
                                    }
                                }
                            }
                        }
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        HorizontalDivider()
                        Text("Privacy", style = MaterialTheme.typography.titleMedium)
                        Text("Login stays in your server's page. Saved sessions stay on this device and are excluded from backup. Cleartext HTTP and invalid certificates are blocked.", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = {
                            runCatching {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://terminallylazy.github.io/a0-android/privacy.html")))
                            }.onFailure {
                                android.widget.Toast.makeText(this@MainActivity, "Install or enable a browser to open the privacy policy.", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }) { Text("Privacy policy") }
                        Text("Agent Zero ${BuildConfig.VERSION_NAME} · ${if (BuildConfig.DEBUG) "Debug" else "Release"}\nAndroid ${Build.VERSION.RELEASE} · WebView ${WebView.getCurrentWebViewPackage()?.versionName ?: "unavailable"}", style = MaterialTheme.typography.labelSmall)
                        if (!BrowserProfiles.hasProvider()) Text("Install or enable Android System WebView, then reopen Agent Zero to use a workspace.", style = MaterialTheme.typography.bodySmall)
                        else if (!BrowserProfiles.supportsMultiple()) Text("This WebView supports one server session. Update Android System WebView for isolated saved servers and Forget.", style = MaterialTheme.typography.bodySmall)
                    }
                } else key(selected.id) {
                    MaterialTheme(colorScheme = workspaceColors) { Workspace(selected, onServers = { settings = true }) }
                }
            }
        }
        if (editor && editingId != null && editing == null) AlertDialog(
            onDismissRequest = { editor = false; editingId = null },
            title = { Text("Server no longer saved") },
            text = { Text("This server was removed. Add it again from Servers to reconnect.") },
            confirmButton = { TextButton(onClick = { editor = false; editingId = null }) { Text("Close") } },
        )
        else if (editor) ServerEditor(editing, error, qrAddress, onDismiss = { editor = false; qrAddress = null; vm.clearError() }) { name, address ->
            val old = editing
            vm.save(name, address, old) {
                editor = false; qrAddress = null; settings = false
            }
        }
        forget?.let { profile -> AlertDialog(
            onDismissRequest = { forget = null }, title = { Text("Forget ${profile.name}?") },
            text = { Text("Remove this saved server and its local browser session. Chats and files on the server will stay there.") },
            confirmButton = { TextButton(onClick = {
                // Workspace is disposed while Servers is visible, before browser data deletion.
                vm.forget(profile)
                forget = null
            }) { Text("Forget") } }, dismissButton = { TextButton(onClick = { forget = null }) { Text("Cancel") } },
        ) }
    }

    @Composable
    private fun Workspace(profile: ServerProfile, onServers: () -> Unit) {
        var state by remember { mutableStateOf(BrowserState()) }
        var transfer by remember { mutableStateOf(TransferStatus("")) }
        var generation by remember { mutableIntStateOf(0) }
        var access by remember { mutableStateOf<Boolean?>(null) }
        var needsLocal by remember { mutableStateOf(false) }
        var controller by remember { mutableStateOf<BrowserController?>(null) }
        var menu by remember { mutableStateOf(false) }
        LaunchedEffect(profile.id) {
            val local = withTimeoutOrNull(5000) { withContext(Dispatchers.IO) {
                try { InetAddress.getAllByName(profile.origin.url.host).any { it.isSiteLocalAddress || it.isLinkLocalAddress || it.isLoopbackAddress } }
                catch (_: Exception) { false }
            } } ?: false
            needsLocal = local && Build.VERSION.SDK_INT >= 37 && ContextCompat.checkSelfPermission(this@MainActivity, "android.permission.ACCESS_LOCAL_NETWORK") != PackageManager.PERMISSION_GRANTED
            access = !needsLocal
        }
        BackHandler(controller != null) {
            controller?.handleBack {
                if (state.canGoBack) controller?.goBack() else moveTaskToBack(true)
            }
        }
        Column(Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().height(48.dp).testTag("workspace-toolbar").padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(profile.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Box {
                        IconButton(onClick = { menu = true }, modifier = Modifier.semantics { contentDescription = "Connection menu" }) {
                            val ink = MaterialTheme.colorScheme.onSurface
                            Canvas(Modifier.size(24.dp)) {
                                for (offset in listOf(-6f, 0f, 6f)) drawCircle(ink, radius = 1.7.dp.toPx(), center = Offset(size.width / 2, size.height / 2 + offset.dp.toPx()))
                            }
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            SelectionContainer {
                                Text(profile.origin.value, Modifier.widthIn(max = 300.dp).padding(horizontal = 16.dp, vertical = 12.dp), style = MaterialTheme.typography.bodySmall)
                            }
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Servers") }, onClick = { menu = false; onServers() })
                            DropdownMenuItem(text = { Text("Reload") }, enabled = access == true, onClick = { menu = false; generation++ })
                            if (Build.VERSION.SDK_INT >= 37) DropdownMenuItem(text = { Text("LAN access") }, onClick = {
                                menu = false
                                localPromptResult = { granted -> if (granted) { access = true; generation++ } }
                                localNetwork.launch("android.permission.ACCESS_LOCAL_NETWORK")
                            })
                        }
                    }
                }
            }
            if (access == null) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Checking connection…", Modifier.padding(24.dp)) }
            if (access == false) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Allow local network access", style = MaterialTheme.typography.titleLarge)
                    Text("This HTTPS server is on a local network. Android needs permission to connect.")
                    Button(onClick = {
                        localPromptResult = { granted -> access = granted }
                        localNetwork.launch("android.permission.ACCESS_LOCAL_NETWORK")
                    }) { Text("Allow local network") }
                    TextButton(onClick = { startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }) { Text("Open app settings") }
                }
            }
            if (access == true) {
                if (state.progress in 0..99 && state.problem == null) LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth())
                if (transfer.message.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(transfer.message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    if (transfer.busy) TextButton(onClick = { controller?.downloads?.cancel() }) { Text("Cancel") }
                    else TextButton(onClick = { transfer = TransferStatus("") }) { Text("Dismiss") }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    key(generation) {
                        val context = LocalContext.current
                        var owned by remember { mutableStateOf<BrowserController?>(null) }
                        DisposableEffect(generation) { onDispose {
                            val old = owned
                            old?.dispose()
                            if (controller === old) controller = null
                            if (browser === old) browser = null
                            if (fileTarget === old) fileTarget = null
                            if (audioTarget === old) { audioTarget = null; audioPrompt = false }
                            if (saveTarget === old?.downloads) saveTarget = null
                        } }
                        AndroidView(factory = {
                            try {
                                check(BrowserProfiles.hasProvider())
                                BrowserController(context, profile, lifecycleScope, { state = it }, { transfer = it },
                                    saveDocument = { name, mime ->
                                        saveTarget = controller?.downloads
                                        createFile.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE, name))
                                    },
                                    selectFiles = { types, multiple ->
                                        fileTarget = controller
                                        openFiles.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(if (types.size == 1) types[0] else "*/*")
                                            .putExtra(Intent.EXTRA_MIME_TYPES, types).putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple))
                                    },
                                    requestAudio = { audioTarget = controller; audioPrompt = true },
                                    savedState = restoredBrowser.also { restoredBrowser = null },
                                ).also { owned = it; controller = it; browser = it }.view
                            } catch (_: IllegalStateException) {
                                val message = if (BrowserProfiles.hasProvider()) "Update Android System WebView to open isolated server sessions."
                                    else "Install or enable Android System WebView, then reopen Agent Zero to use a workspace."
                                state = BrowserState(problem = PageProblem.Provider(message))
                                android.view.View(context)
                            }
                        }, modifier = Modifier.fillMaxSize())
                    }
                    state.problem?.let { problem ->
                        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.Start) {
                                Text("Let's reconnect", style = MaterialTheme.typography.headlineMedium)
                                Text(problem.message())
                                Text(profile.origin.value, style = MaterialTheme.typography.bodySmall)
                                Button(onClick = { generation++ }) { Text("Retry") }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onPause() {
        browser?.pause(preservePending = fileTarget != null || saveTarget != null || audioTarget != null)
        super.onPause()
    }
    override fun onResume() { super.onResume(); browser?.resume() }
    override fun onSaveInstanceState(outState: Bundle) {
        browser?.let { outState.putBundle("browser", it.saveState()) }
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() { browser?.dispose(); browser = null; super.onDestroy() }
}

@Composable
private fun ServerEditor(profile: ServerProfile?, error: String?, qrAddress: String?, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by rememberSaveable(profile?.id) { mutableStateOf(profile?.name.orEmpty()) }
    var address by rememberSaveable(profile?.id, qrAddress) { mutableStateOf(qrAddress ?: profile?.origin?.value.orEmpty()) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (qrAddress != null) "Review server" else if (profile == null) "Connect your server" else "Edit server") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Server name") }, singleLine = true)
                if (qrAddress != null) {
                    Text("HTTPS address", style = MaterialTheme.typography.labelMedium)
                    // Show the entire scanned destination, including the end of long hostnames.
                    Text(address, style = MaterialTheme.typography.bodyLarge, softWrap = true)
                } else {
                    OutlinedTextField(address, { address = it }, label = { Text("HTTPS address") }, placeholder = { Text("https://agent.example.com") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), singleLine = true)
                }
                Text(if (qrAddress != null) "Check that this is your server's address. Connect only when you recognize it. You'll sign in on the server's page."
                    else "Use the root address. You'll sign in on your server's page.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, address) }, enabled = address.isNotBlank()) { Text("Connect") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
