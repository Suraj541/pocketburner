package com.example.ui.launcher

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.DownloadListener
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WebAsset
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.ConnectionStatusPill

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LauncherScreen(
    viewModel: LauncherViewModel,
    onNavigateTab: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentUrl by viewModel.currentUrl.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val usbFlashState by viewModel.usbFlashState.collectAsStateWithLifecycle()
    val wifiOtaState by viewModel.wifiOtaState.collectAsStateWithLifecycle()
    val networkEndpoints by viewModel.networkEndpoints.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val importedWebFirmware by viewModel.importedWebFirmware.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var addressInput by remember(currentUrl) { mutableStateOf(currentUrl) }
    var pageProgress by remember { mutableIntStateOf(100) }
    var canGoBack by remember { mutableStateOf(false) }
    var pendingPickRequest by remember { mutableStateOf<PendingPickAndFlashRequest?>(null) }
    var pendingWebFileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val binaryPickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            val req = pendingPickRequest
            pendingPickRequest = null
            if (uri != null) {
                viewModel.importAndFlashPickedUri(
                    uri = uri,
                    baudRate = req?.baudRate ?: settings.defaultBaudRate,
                    offsetHex = req?.offsetHex ?: "0x10000",
                )
            }
        },
    )

    val webFileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            pendingWebFileCallback?.onReceiveValue(uris.toTypedArray())
            pendingWebFileCallback = null
        },
    )

    val webView = remember(context) {
        WebView(context).apply {
            this.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = false
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = false
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                useWideViewPort = true
                loadWithOverviewMode = true
            }
            addJavascriptInterface(viewModel.createJsBridge(), "AndroidLauncher")
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.pickAndFlashEvents.collect { req ->
            pendingPickRequest = req
            binaryPickLauncher.launch(arrayOf("*/*"))
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.navigateTabEvents.collect { route ->
            onNavigateTab(route)
        }
    }

    LaunchedEffect(currentUrl) {
        if (webView.url != currentUrl) {
            webView.loadUrl(currentUrl)
        }
    }

    DisposableEffect(webView) {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                viewModel.updateActiveWebUrl(url)
                if (!url.isNullOrBlank()) {
                    addressInput = url
                }
                canGoBack = view?.canGoBack() == true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                viewModel.updateActiveWebUrl(url)
                canGoBack = view?.canGoBack() == true
                pageProgress = 100
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val target = request?.url?.toString().orEmpty()
                if (LauncherViewModel.isBinaryFirmwareUrl(target, null, null) &&
                    (target.startsWith("http://") || target.startsWith("https://"))
                ) {
                    viewModel.importAndDownloadBinaryFromWeb(target, null)
                    return true
                }
                return false
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                pageProgress = newProgress
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?,
            ): Boolean {
                pendingWebFileCallback?.onReceiveValue(null)
                pendingWebFileCallback = filePathCallback
                val mimeTypes = fileChooserParams?.acceptTypes?.filter { it.isNotBlank() }?.toTypedArray()
                    ?.takeIf { it.isNotEmpty() } ?: arrayOf("*/*")
                webFileChooserLauncher.launch(mimeTypes)
                return true
            }
        }
        webView.setDownloadListener(
            DownloadListener { url, _, contentDisposition, mimeType, _ ->
                val suggested = URLUtil.guessFileName(url, contentDisposition, mimeType)
                viewModel.importAndDownloadBinaryFromWeb(url, suggested)
            }
        )
        onDispose {
            pendingWebFileCallback?.onReceiveValue(null)
            pendingWebFileCallback = null
            webView.destroy()
        }
    }

    BackHandler(enabled = canGoBack) {
        if (webView.canGoBack()) {
            webView.goBack()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Header + Connection Pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.WebAsset, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("ESP Web & Launcher Portal", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "ESPHome Web, bmorcelli/Launcher & ESP AP WebUI",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            ConnectionStatusPill(
                connectionState = connectionState,
                onClick = { onNavigateTab("connect") },
            )
        }

        // Direct Portal Chips (No Manual Searching Required)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.testTag("launcher_portal_presets_row"),
        ) {
            items(viewModel.portalPresets, key = { it.id }) { preset ->
                val selected = currentUrl.startsWith(preset.url, ignoreCase = true)
                FilterChip(
                    selected = selected,
                    onClick = {
                        addressInput = preset.url
                        viewModel.selectPortalUrl(preset.url)
                        webView.loadUrl(preset.url)
                    },
                    label = { Text(preset.label, maxLines = 1) },
                    modifier = Modifier.testTag("launcher_preset_${preset.id}"),
                )
            }
            items(networkEndpoints, key = { "${it.host}:${it.port}" }) { ep ->
                val epUrl = "http://${ep.host}:${ep.port}"
                FilterChip(
                    selected = currentUrl.startsWith(epUrl),
                    onClick = {
                        addressInput = epUrl
                        viewModel.selectPortalUrl(epUrl)
                        webView.loadUrl(epUrl)
                    },
                    label = { Text("${ep.serviceName} (${ep.host})") },
                )
            }
        }

        val activePresetSubtitle = viewModel.portalPresets
            .firstOrNull { currentUrl.startsWith(it.url, ignoreCase = true) }
            ?.subtitle
        if (!activePresetSubtitle.isNullOrBlank()) {
            Text(
                text = activePresetSubtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        } else {
            Spacer(Modifier.height(6.dp))
        }

        // Compact URL & Web Navigation Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        ) {
            IconButton(
                onClick = { if (webView.canGoBack()) webView.goBack() },
                enabled = canGoBack,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            OutlinedTextField(
                value = addressInput,
                onValueChange = { addressInput = it },
                singleLine = true,
                placeholder = { Text("http://192.168.4.1 or portal URL") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(
                    onGo = {
                        viewModel.selectPortalUrl(addressInput)
                        webView.loadUrl(viewModel.currentUrl.value)
                    }
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("launcher_url_input"),
            )
            IconButton(onClick = { webView.reload() }, modifier = Modifier.testTag("launcher_reload_button")) {
                Icon(Icons.Filled.Refresh, contentDescription = "Reload page")
            }
            IconButton(onClick = viewModel::scanWifiEndpoints, modifier = Modifier.testTag("launcher_mdns_scan_button")) {
                Icon(Icons.Filled.WifiFind, contentDescription = "Discover LAN WebUIs")
            }
        }

        if (pageProgress in 1..99) {
            LinearProgressIndicator(
                progress = { pageProgress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
        }

        if (usbFlashState.isFlashing) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(
                        "USB OTG Flashing: ${usbFlashState.statusText}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { usbFlashState.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        if (wifiOtaState.isFlashing) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Wi-Fi OTA: ${wifiOtaState.statusText}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { wifiOtaState.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    TextButton(
                        onClick = viewModel::cancelWifiOta,
                        modifier = Modifier.testTag("launcher_cancel_ota_button"),
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }

        importedWebFirmware?.let { fw ->
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Ready to Flash: ${fw.title}", style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${fw.sizeBytes / 1024} KB • Offset ${fw.flashOffset}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Button(
                        onClick = {
                            viewModel.flashFirmwareViaUsbOtg(fw, settings.defaultBaudRate, fw.flashOffset)
                            viewModel.dismissImportedFirmwareBanner()
                        },
                        modifier = Modifier.testTag("launcher_flash_downloaded_button"),
                    ) {
                        Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("USB")
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.pushFirmwareViaWifiOta(fw, currentUrl)
                            viewModel.dismissImportedFirmwareBanner()
                        },
                        modifier = Modifier.testTag("launcher_ota_downloaded_button"),
                    ) {
                        Icon(Icons.Filled.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("OTA")
                    }
                    IconButton(onClick = viewModel::dismissImportedFirmwareBanner) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss")
                    }
                }
            }
        }

        statusMessage?.let { msg ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = viewModel::clearStatusMessage) { Text("OK") }
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { webView },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("launcher_webview"),
            )
        }
    }
}
