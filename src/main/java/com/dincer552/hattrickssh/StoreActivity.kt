package com.dincer552.hattrickssh

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import androidx.work.WorkManager

private val StoreBackground = Color(0xFF080D12)
private val StorePanel = Color(0xFF121B24)
private val StoreGreen = Color(0xFF38D98A)
private val StoreMuted = Color(0xFF91A1B2)

class StoreActivity : ComponentActivity() {
    private val installRequestState = mutableStateOf<Pair<String, Int>?>(null)
    private val resumeRevision = mutableIntStateOf(0)
    private var installRequestSequence = 0
    private fun captureInstallRequest(source: Intent?) {
        val appId = source?.getStringExtra("install_app_id")
        if (!appId.isNullOrBlank()) {
            installRequestSequence += 1
            installRequestState.value = appId to installRequestSequence
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(8, 13, 18)
        window.navigationBarColor = android.graphics.Color.BLACK
        StoreUpdateWorker.enqueuePeriodicCheck(this)
        StoreUpdateWorker.enqueueImmediateCheck(this)
        captureInstallRequest(intent)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = StoreGreen,
                background = StoreBackground,
                surface = StorePanel,
                onSurface = Color.White
            )) {
                StoreScreen(
                    installRequest = installRequestState.value,
                    statusRefreshKey = resumeRevision.intValue
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureInstallRequest(intent)
    }

    override fun onResume() {
        super.onResume()
        resumeRevision.intValue += 1
    }
}

@Composable
private fun StoreScreen(
    installRequest: Pair<String, Int>? = null,
    statusRefreshKey: Int = 0
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<StoreAppListing>>(emptyList()) }
    var catalogRevision by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var downloadId by remember { mutableStateOf<Long?>(null) }
    var downloadingApp by remember { mutableStateOf<StoreAppListing?>(null) }
    var downloadPercent by remember { mutableIntStateOf(0) }
    var updatePhase by remember { mutableStateOf<String?>(null) }
    var activeUpdateAppId by remember { mutableStateOf<String?>(null) }
    var installUri by remember { mutableStateOf<Uri?>(null) }
    var installPackageName by remember { mutableStateOf<String?>(null) }
    var readyInstallAppId by remember { mutableStateOf<String?>(null) }
    var installError by remember { mutableStateOf<String?>(null) }
    var showSignerSettings by remember { mutableStateOf(false) }
    var showTraceLog by remember { mutableStateOf(false) }
    var selectedApp by remember { mutableStateOf<StoreAppListing?>(null) }

    fun refreshCatalog() {
        if (loading) return
        loading = true
        loadError = null
        scope.launch {
            try {
                apps = StoreCatalogManager.loadCatalog(context)
                catalogRevision++
                if (apps.isEmpty()) {
                    loadError = "Katalog boş. app-catalog.json dosyasını kontrol edin."
                } else if (StoreCatalogManager.lastErrorHint != null) {
                    loadError = StoreCatalogManager.lastErrorHint
                } else {
                    loadError = null
                }
            } catch (error: Exception) {
                loadError = error.message ?: "Uygulama kataloğu alınamadı."
            } finally {
                loading = false
            }
        }
    }

    val installPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val uri = installUri
        val readyAppId = readyInstallAppId
        if (uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.packageManager.canRequestPackageInstalls()
        ) {
            val packageName = installPackageName
            updatePhase = "kuruluyor"
            val error = if (packageName != null) {
                launchStoreInstallOrUpdate(context, uri, packageName)
            } else {
                launchApkInstaller(context, uri)
            }
            if (error != null) {
                installError = error
                updatePhase = null
                activeUpdateAppId = null
            } else {
                readyAppId?.let { StoreUpdateNotifications.removeReady(context, it) }
                readyInstallAppId = null
            }
        } else if (uri != null) {
            installError = "Kurulum izni verilmedi. Ayarlar → bilinen kaynaklar: My App Store açın."
            readyAppId?.let { StoreUpdateNotifications.removeReady(context, it) }
            readyInstallAppId = null
            updatePhase = null
            activeUpdateAppId = null
        }
    }

    LaunchedEffect(statusRefreshKey) {
        if (statusRefreshKey > 0 && updatePhase == "kuruluyor") {
            updatePhase = null
            activeUpdateAppId = null
        }
    }

    LaunchedEffect(installRequest) {
        refreshCatalog()
        val installAppIdFromIntent = installRequest?.first
        if (installAppIdFromIntent != null) {
            val uri = StoreUpdateNotifications.installUri(context, installAppIdFromIntent)
            if (uri != null) {
                installUri = null
                installPackageName = null
                readyInstallAppId = null
                updatePhase = "kuruluyor"
                activeUpdateAppId = installAppIdFromIntent
                try {
                    val stagedUri = withContext(Dispatchers.IO) { createInstallableApkUri(context, uri) }
                    installUri = stagedUri
                    installPackageName = StoreUpdateNotifications.installPackageName(context, installAppIdFromIntent)
                    readyInstallAppId = installAppIdFromIntent
                } catch (error: Exception) {
                    installError = error.message ?: "APK kurulum için hazırlanamadı."
                    updatePhase = null
                    activeUpdateAppId = null
                }
                if (installUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    !context.packageManager.canRequestPackageInstalls()
                ) {
                    updatePhase = "izin"
                    installPermissionLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    )
                } else {
                    installUri?.let { stagedUri ->
                        val packageName = installPackageName
                        val error = if (packageName != null) {
                            launchStoreInstallOrUpdate(context, stagedUri, packageName)
                        } else {
                            launchApkInstaller(context, stagedUri)
                        }
                        if (error != null) {
                            installError = error
                            updatePhase = null
                            activeUpdateAppId = null
                        } else {
                            StoreUpdateNotifications.removeReady(context, installAppIdFromIntent)
                            readyInstallAppId = null
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(downloadId) {
        val id = downloadId ?: return@LaunchedEffect
        while (true) {
            val snapshot = withContext(Dispatchers.IO) {
                val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val cursor = manager.query(DownloadManager.Query().setFilterById(id)) ?: return@withContext null
                cursor.use {
                    if (!it.moveToFirst()) return@use null
                    val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val current = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    Triple(status, if (total > 0) (current * 100 / total).toInt().coerceIn(0, 100) else 0, manager.getUriForDownloadedFile(id))
                }
            }
            if (snapshot == null) {
                installError = "İndirme durumu okunamadı."
                downloadId = null
                downloadingApp = null
                updatePhase = null
                activeUpdateAppId = null
                break
            }
            downloadPercent = snapshot.second
            when (snapshot.first) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val uri = snapshot.third
                    val app = downloadingApp
                    val finishedId = id
                    downloadId = null
                    downloadingApp = null
                    scope.launch {
                        if (uri == null || app == null) {
                            installError = "İndirilen paket bulunamadı."
                            StoreUpdateTrace.event(context, "download", "error", "Downloaded file URI missing")
                            StoreUpdateTrace.endSession(context, "error", "missing uri")
                            updatePhase = null
                            activeUpdateAppId = null
                            return@launch
                        }
                        StoreUpdateTrace.event(
                            context, "download", "ok", "Download finished",
                            extra = mapOf("downloadId" to finishedId)
                        )
                        updatePhase = "dogrulaniyor"
                        installPackageName = app.packageName
                        val stageStarted = System.currentTimeMillis()
                        StoreUpdateTrace.event(context, "validate", "start", "Stage + identity + sha256")
                        val staged = try {
                            withContext(Dispatchers.IO) {
                                val stagedUri = createInstallableApkUri(context, uri)
                                val validation = validateDownloadedApk(context, stagedUri, app)
                                if (validation != null) null to validation else stagedUri to null
                            }
                        } catch (error: Exception) {
                            null to (error.message ?: "Paket hazırlanamadı.")
                        }
                        val validateMs = System.currentTimeMillis() - stageStarted
                        val stagedUri = staged.first
                        val validationError = staged.second
                        if (validationError != null || stagedUri == null) {
                            installError = validationError ?: "Paket doğrulanamadı."
                            StoreUpdateTrace.event(
                                context, "validate", "error", validationError ?: "unknown",
                                extra = mapOf("durationMs" to validateMs)
                            )
                            StoreUpdateTrace.endSession(context, "error", "validate failed")
                            updatePhase = null
                            activeUpdateAppId = null
                        } else {
                            StoreUpdateTrace.event(
                                context, "validate", "ok", "Package ready",
                                extra = mapOf("durationMs" to validateMs)
                            )
                            installUri = stagedUri
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                                !context.packageManager.canRequestPackageInstalls()
                            ) {
                                updatePhase = "izin"
                                StoreUpdateTrace.event(context, "permission", "required", "REQUEST_INSTALL_PACKAGES")
                                installPermissionLauncher.launch(
                                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                                )
                            } else {
                                updatePhase = "kuruluyor"
                                StoreUpdateTrace.event(context, "install", "start", "Android standart APK kurulum ekranı")
                                val err = launchStoreInstallOrUpdate(
                                    context,
                                    stagedUri,
                                    app.packageName,
                                    app.release?.versionCode?.toLong()
                                )
                                if (err != null) {
                                    installError = err
                                    StoreUpdateTrace.event(context, "install", "error", err)
                                    StoreUpdateTrace.endSession(context, "error", err)
                                    updatePhase = null
                                    activeUpdateAppId = null
                                } else {
                                    StoreUpdateTrace.event(context, "install", "session_started", "Waiting for user confirmation in Android installer")
                                }
                            }
                        }
                    }
                    break
                }
                DownloadManager.STATUS_FAILED -> {
                    installError = "Güncelleme indirilemedi. İnternet bağlantısını kontrol edin."
                    StoreUpdateTrace.event(context, "download", "error", "STATUS_FAILED", code = DownloadManager.STATUS_FAILED)
                    StoreUpdateTrace.endSession(context, "error", "download failed")
                    downloadId = null
                    downloadingApp = null
                    updatePhase = null
                    activeUpdateAppId = null
                    break
                }
                else -> delay(450)
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = StoreBackground) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("MY APP STORE", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text("Tek dokunuşla güncelle", color = StoreMuted, fontSize = 12.sp)
                }
                OutlinedButton(onClick = { showTraceLog = true }) {
                    Text("LOG", fontSize = 9.sp)
                }
                OutlinedButton(onClick = { showSignerSettings = true }) {
                    Text("İMZALAMA", fontSize = 9.sp)
                }
                OutlinedButton(onClick = { refreshCatalog() }, enabled = !loading) {
                    Text(if (loading) "Kontrol…" else "YENİLE")
                }
            }
            if (loading && apps.isEmpty()) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Uygulamalar kontrol ediliyor…", color = StoreMuted, fontSize = 12.sp)
            }
            loadError?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 12.sp) }
            installError?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 12.sp) }
            val phaseLabel = when (updatePhase) {
                "indiriliyor" -> "${downloadingApp?.name ?: "Uygulama"} güncelleniyor • $downloadPercent%"
                "dogrulaniyor" -> "Paket kontrol ediliyor (birkaç saniye sürebilir)…"
                "izin" -> "Kurulum izni gerekli — bir kez izin verin"
                "kuruluyor" -> "Android kurulum onayı açılıyor…"
                else -> null
            }
            if (phaseLabel != null) {
                Text(phaseLabel, color = StoreGreen, fontSize = 12.sp)
                if (updatePhase == "indiriliyor") {
                    LinearProgressIndicator(progress = { downloadPercent / 100f }, modifier = Modifier.fillMaxWidth())
                } else if (updatePhase == "dogrulaniyor" || updatePhase == "kuruluyor") {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(apps, key = { it.id }) { app ->
                    StoreAppCard(
                        app = app,
                        context = context,
                        busy = updatePhase != null,
                        isUpdating = activeUpdateAppId == app.id && updatePhase != null,
                        refreshKey = "$catalogRevision:$statusRefreshKey",
                        onOpenDetails = { selectedApp = app },
                        onInstall = {
                            installError = null
                            activeUpdateAppId = app.id
                            val versionCode = app.release?.versionCode?.toLong()
                            StoreUpdateTrace.startSession(
                                context,
                                appId = app.id,
                                packageName = app.packageName,
                                versionName = app.release?.versionName,
                                versionCode = versionCode,
                                reason = "user_tap"
                            )
                            val readyUri = if (versionCode != null &&
                                StoreUpdateNotifications.hasReadyVersion(context, app.id, versionCode)
                            ) StoreUpdateNotifications.installUri(context, app.id) else null
                            if (readyUri != null) {
                                StoreUpdateTrace.event(context, "ready_apk", "ok", "Using pre-downloaded APK")
                                updatePhase = "kuruluyor"
                                scope.launch {
                                    try {
                                        val staged = withContext(Dispatchers.IO) {
                                            createInstallableApkUri(context, readyUri)
                                        }
                                        installUri = staged
                                        installPackageName = app.packageName
                                        readyInstallAppId = app.id
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                                            !context.packageManager.canRequestPackageInstalls()
                                        ) {
                                            updatePhase = "izin"
                                            installPermissionLauncher.launch(
                                                Intent(
                                                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                                    Uri.parse("package:${context.packageName}")
                                                )
                                            )
                                        } else {
                                            StoreUpdateTrace.event(context, "install", "start", "ready apk")
                                            val err = launchStoreInstallOrUpdate(
                                                context, staged, app.packageName, versionCode
                                            )
                                            if (err != null) {
                                                installError = err
                                                StoreUpdateTrace.event(context, "install", "error", err)
                                                StoreUpdateTrace.endSession(context, "error", err)
                                                updatePhase = null
                                                activeUpdateAppId = null
                                            } else {
                                                StoreUpdateNotifications.removeReady(context, app.id)
                                                StoreUpdateTrace.event(context, "install", "session_started", "Waiting for user confirmation in Android installer")
                                            }
                                        }
                                    } catch (error: Exception) {
                                        installError = error.message ?: "Hazır güncelleme kurulamadı."
                                        StoreUpdateNotifications.removeReady(context, app.id)
                                        StoreUpdateTrace.event(context, "install", "error", installError ?: "")
                                        StoreUpdateTrace.endSession(context, "error", installError ?: "")
                                        updatePhase = null
                                        activeUpdateAppId = null
                                    }
                                }
                            } else {
                                updatePhase = "indiriliyor"
                                downloadPercent = 0
                                downloadingApp = app
                                StoreUpdateTrace.event(
                                    context, "download", "start",
                                    "DownloadManager enqueue",
                                    extra = mapOf("url" to app.downloadUrl)
                                )
                                val request = DownloadManager.Request(Uri.parse(app.downloadUrl))
                                    .setTitle("${app.name} güncelleniyor")
                                    .setDescription("${app.versionName} • Android onaylı kurulum")
                                    .setMimeType("application/vnd.android.package-archive")
                                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                                    .setAllowedOverMetered(true)
                                    .setAllowedOverRoaming(false)
                                    .setDestinationInExternalFilesDir(
                                        context,
                                        android.os.Environment.DIRECTORY_DOWNLOADS,
                                        "${app.id}-${app.versionCode}.apk"
                                    )
                                val enqueued = (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager)
                                    .enqueue(request)
                                downloadId = enqueued
                                StoreUpdateTrace.event(
                                    context, "download", "enqueued",
                                    "downloadId=$enqueued",
                                    code = enqueued.toInt()
                                )
                            }
                        }
                    )
                }
            }
            Text("Mağaza ${BuildConfig.VERSION_NAME} • Kurulum Android onayıyla tamamlanır", color = StoreMuted, fontSize = 10.sp)
        }
    }
    if (selectedApp != null) StoreAppDetailsDialog(app = selectedApp!!, onDismiss = { selectedApp = null })
    if (showSignerSettings) SignerSetupDialog(onDismiss = { showSignerSettings = false })
    if (showTraceLog) {
        UpdateTraceDialog(
            json = StoreUpdateTrace.exportJson(context),
            onDismiss = { showTraceLog = false },
            onShare = {
                context.startActivity(Intent.createChooser(StoreUpdateTrace.shareIntent(context), "Güncelleme logu"))
            },
            onCopy = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("store-update-log", StoreUpdateTrace.exportJson(context)))
                Toast.makeText(context, "Log panoya kopyalandı", Toast.LENGTH_SHORT).show()
            },
            onClear = {
                StoreUpdateTrace.clear(context)
                Toast.makeText(context, "Loglar temizlendi", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun StoreAppCard(
    app: StoreAppListing,
    context: Context,
    busy: Boolean,
    isUpdating: Boolean = false,
    refreshKey: String,
    onOpenDetails: () -> Unit,
    onInstall: () -> Unit
) {
    val installedCode = remember(app.packageName, refreshKey) { getInstalledVersionCode(context, app.packageName) }
    val release = app.release
    val packageState = resolveStorePackageState(installedCode, release?.versionCode?.toLong())
    val status = when (packageState) {
        StorePackageState.NOT_INSTALLED -> if (release == null) "Kurulu değil • Release bekleniyor" else "Yüklenmedi"
        StorePackageState.INSTALLED -> "Kurulu • Sürüm bilgisi yok"
        StorePackageState.UPDATE_AVAILABLE -> "Güncelleme mevcut"
        StorePackageState.UP_TO_DATE -> "Yüklü • Güncel"
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = StorePanel),
        shape = RoundedCornerShape(13.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenDetails)) {
                Text(app.icon, fontSize = 25.sp)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(app.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(app.description, color = StoreMuted, fontSize = 11.sp)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(release?.versionName ?: "Sürüm bekleniyor", color = StoreMuted, fontSize = 10.sp)
                Text(
                    if (isUpdating) "Güncelleniyor…" else status,
                    color = if (isUpdating || packageState == StorePackageState.UPDATE_AVAILABLE || packageState == StorePackageState.UP_TO_DATE) StoreGreen else StoreMuted,
                    fontSize = 10.sp
                )
            }
            if (release != null) {
                Button(
                    onClick = onInstall,
                    enabled = !busy && packageState != StorePackageState.UP_TO_DATE,
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        when {
                            isUpdating -> "GÜNCELLENİYOR…"
                            packageState == StorePackageState.NOT_INSTALLED -> "YÜKLE"
                            packageState == StorePackageState.INSTALLED -> "KURULU"
                            packageState == StorePackageState.UPDATE_AVAILABLE -> "GÜNCELLE • ${release.versionName}"
                            else -> "ZATEN GÜNCEL"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text("Paket: ${app.packageName}", color = Color(0xFF718293), fontSize = 9.sp)
        }
    }
}

@Composable
private fun StoreAppDetailsDialog(app: StoreAppListing, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var buildStatus by remember(app.repository) { mutableStateOf<GithubBuildStatus?>(null) }
    var buildLoading by remember(app.repository) { mutableStateOf(true) }
    var buildError by remember(app.repository) { mutableStateOf<String?>(null) }
    var refreshBuild by remember(app.repository) { mutableIntStateOf(0) }

    LaunchedEffect(app.repository, refreshBuild) {
        buildLoading = true
        buildError = null
        try {
            do {
                val latest = GithubBuildStatusClient.fetchLatest(app.repository)
                buildStatus = latest
                if (latest?.isActive == true) {
                    delay(12_000)
                }
            } while (buildStatus?.isActive == true)
        } catch (error: Exception) {
            buildError = error.message ?: "GitHub build bilgisi alınamadı."
        } finally {
            buildLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Row(verticalAlignment = Alignment.CenterVertically) {
            Text(app.icon, fontSize = 27.sp)
            Text(app.name, modifier = Modifier.padding(start = 10.dp), fontWeight = FontWeight.Bold)
        } },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(app.description, color = StoreMuted, fontSize = 13.sp)
                Text("Uygulama bilgileri", color = StoreGreen, fontWeight = FontWeight.Bold)
                Text("Paket adı: ${app.packageName}", fontSize = 11.sp)
                Text("GitHub deposu: ${app.repository}", fontSize = 11.sp)
                Text("Güncel sürüm: ${app.release?.versionName ?: "Bilinmiyor"}", fontSize = 11.sp)
                Spacer(Modifier.height(5.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("GitHub build durumu", color = StoreGreen, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { refreshBuild++ }, enabled = !buildLoading) {
                        Text("YENİLE", fontSize = 10.sp)
                    }
                }
                if (buildLoading && buildStatus == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("GitHub Actions kontrol ediliyor…", color = StoreMuted, fontSize = 11.sp)
                }
                buildError?.let {
                    Text(it, color = Color(0xFFFF8A80), fontSize = 11.sp)
                }
                buildStatus?.let { build ->
                    val statusColor = when {
                        build.status == "completed" && build.conclusion == "success" -> StoreGreen
                        build.status == "completed" && build.conclusion != "success" -> Color(0xFFFF8A80)
                        else -> Color(0xFFFFC857)
                    }
                    Text(build.statusLabel, color = statusColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(build.workflowName, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text("Build #${build.id} • ${build.branch} • ${build.event}", color = StoreMuted, fontSize = 10.sp)
                    Text(build.displayTitle, color = StoreMuted, fontSize = 11.sp)
                    Text("Başlangıç: ${build.createdAt.replace("T", " ").removeSuffix("Z")} UTC", color = StoreMuted, fontSize = 10.sp)
                    if (build.updatedAt != build.createdAt) {
                        Text("Son güncelleme: ${build.updatedAt.replace("T", " ").removeSuffix("Z")} UTC", color = StoreMuted, fontSize = 10.sp)
                    }
                    build.jobs.filter { it.failedSteps.isNotEmpty() }.forEach { job ->
                        Text("Hatalı görev: ${job.name}", color = Color(0xFFFF8A80), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        job.failedSteps.forEach { step ->
                            Text("• ${step}", color = Color(0xFFFF8A80), fontSize = 10.sp)
                        }
                    }
                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(build.htmlUrl)))
                    }) {
                        Text("GitHub'da build ayrıntılarını aç")
                    }
                    if (build.isActive) {
                        Text("Build çalışırken durum otomatik olarak yenilenir.", color = StoreMuted, fontSize = 10.sp)
                    }
                }
                if (!buildLoading && buildStatus == null && buildError == null) {
                    Text("Bu deponun main dalında henüz GitHub Actions build kaydı bulunamadı.", color = StoreMuted, fontSize = 11.sp)
                }
                Spacer(Modifier.height(5.dp))
                Text("Sürüm geçmişi", color = StoreGreen, fontWeight = FontWeight.Bold)
                if (app.updateHistory.isEmpty()) Text("Henüz sürüm geçmişi bulunamadı.", color = StoreMuted, fontSize = 11.sp)
                app.updateHistory.forEach { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Sürüm ${item.versionName} • ${item.publishedAt.ifBlank { "Tarih yok" }}", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text(item.notes.ifBlank { "Bu sürüm için açıklama eklenmemiş." }, color = StoreMuted, fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("KAPAT") } },
        containerColor = StorePanel,
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}

@Composable
private fun UpdateTraceDialog(
    json: String,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onClear: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Güncelleme logu (JSON)", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "Bu metni kopyalayıp gönder. Her aşama, süre ve hata kodu kayıtlı.",
                    color = StoreMuted,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(json, color = Color.White, fontSize = 10.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = onCopy) { Text("KOPYALA") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onShare) { Text("PAYLAŞ") }
                TextButton(onClick = onClear) { Text("TEMİZLE") }
                TextButton(onClick = onDismiss) { Text("KAPAT") }
            }
        },
        containerColor = StorePanel,
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}

@Suppress("DEPRECATION")
private fun getInstalledVersionCode(context: Context, packageName: String): Long? = try {
    val info = if (Build.VERSION.SDK_INT >= 33) {
        context.packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
    } else {
        context.packageManager.getPackageInfo(packageName, 0)
    }
    if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
} catch (_: android.content.pm.PackageManager.NameNotFoundException) {
    null
}

private fun validateDownloadedApk(context: Context, uri: Uri, app: StoreAppListing): String? {
    val expectedVersion = app.release?.versionCode?.toLong()
        ?: return "Katalogda beklenen APK sürümü bulunamadı."
    val identityError = validateApkIdentity(context, uri, app.packageName, expectedVersion)
    if (identityError != null) return identityError
    val expected = app.release?.sha256
    if (expected.isNullOrBlank()) return null
    return try {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        } ?: return "İndirilen APK okunamadı."
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (actual.equals(expected, ignoreCase = true)) null
        else "APK SHA-256 doğrulaması başarısız. Kurulum iptal edildi."
    } catch (error: Exception) {
        "APK doğrulanamadı: ${error.message ?: "bilinmeyen hata"}"
    }
}

internal fun launchApkInstaller(context: Context, uri: Uri): String? {
    val installIntent = createApkInstallIntent(uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        try {
            context.startActivity(installIntent)
        } catch (_: android.content.ActivityNotFoundException) {
            val fallback = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                clipData = android.content.ClipData.newUri(context.contentResolver, "APK", uri)
            }
            context.startActivity(fallback)
        }
        null
    } catch (error: Exception) {
        "Kurulum ekranı açılamadı (${error.javaClass.simpleName}): ${error.message ?: "neden belirtilmedi"}. My App Store için 'Bilinmeyen uygulamaları yükle' iznini kontrol et."
    }
}
