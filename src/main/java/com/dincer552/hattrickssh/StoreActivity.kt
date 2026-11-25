package com.dincer552.hattrickssh

import android.app.DownloadManager
import android.graphics.Bitmap
import android.graphics.Canvas
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import androidx.work.WorkManager

private val StoreBackground = Color(0xFF080D12)
private val StorePanel = Color(0xFF121B24)
private val StoreGreen = Color(0xFF38D98A)
private val StoreMuted = Color(0xFF91A1B2)

@Composable
private fun StoreRealAppIcon(
    packageName: String,
    fallbackEmoji: String,
    size: androidx.compose.ui.unit.Dp,
    fontSize: androidx.compose.ui.unit.TextUnit
) {
    val context = LocalContext.current
    val iconBitmap = remember(packageName) {
        try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val width = drawable.intrinsicWidth.takeIf { it > 0 }?.coerceAtMost(192) ?: 96
            val height = drawable.intrinsicHeight.takeIf { it > 0 }?.coerceAtMost(192) ?: 96
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap,
            contentDescription = "$packageName uygulama simgesi",
            modifier = Modifier.size(size)
        )
    } else {
        Text(fallbackEmoji, fontSize = fontSize)
    }
}

private const val MANUAL_QUEUE_PREFS = "store_manual_update_queue"
private const val MANUAL_QUEUE_IDS = "queue_ids"
private const val MANUAL_ACTIVE_DOWNLOAD_ID = "active_download_id"
private const val MANUAL_ACTIVE_APP_ID = "active_app_id"
private const val MANUAL_INSTALLING_APP_ID = "installing_app_id"
private const val MANUAL_INSTALL_QUEUE_ACTIVE = "install_queue_active"

private fun readManualQueueIds(context: Context): List<String> {
    val prefs = context.getSharedPreferences(MANUAL_QUEUE_PREFS, Context.MODE_PRIVATE)
    return try {
        val array = JSONArray(prefs.getString(MANUAL_QUEUE_IDS, "[]") ?: "[]")
        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun writeManualQueueIds(context: Context, ids: List<String>) {
    val array = JSONArray()
    ids.distinct().forEach(array::put)
    context.getSharedPreferences(MANUAL_QUEUE_PREFS, Context.MODE_PRIVATE)
        .edit().putString(MANUAL_QUEUE_IDS, array.toString()).apply()
}

class StoreActivity : ComponentActivity() {
    private val installRequestState = mutableStateOf<Pair<String, Int>?>(null)
    private val resumeRevision = mutableIntStateOf(0)
    private val activityResumedState = mutableStateOf(false)
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
        // Manual update downloads are now queued by the store UI. Stop the legacy
        // parallel prefetch worker so it cannot bypass the per-app FIFO queue.
        StoreUpdateWorker.cancelAll(this)
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
                    statusRefreshKey = resumeRevision.intValue,
                    activityResumed = activityResumedState.value
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
        activityResumedState.value = true
        resumeRevision.intValue += 1
    }

    override fun onPause() {
        activityResumedState.value = false
        super.onPause()
    }
}

@Composable
private fun StoreScreen(
    installRequest: Pair<String, Int>? = null,
    statusRefreshKey: Int = 0,
    activityResumed: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<StoreAppListing>>(emptyList()) }
    var catalogRevision by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val manualQueuePrefs = remember {
        context.getSharedPreferences(MANUAL_QUEUE_PREFS, Context.MODE_PRIVATE)
    }
    var updateQueueIds by remember { mutableStateOf(readManualQueueIds(context)) }
    var installQueueActive by remember {
        mutableStateOf(manualQueuePrefs.getBoolean(MANUAL_INSTALL_QUEUE_ACTIVE, false))
    }
    var downloadId by remember {
        mutableStateOf(manualQueuePrefs.getLong(MANUAL_ACTIVE_DOWNLOAD_ID, -1L).takeIf { it > 0L })
    }
    var activeDownloadAppId by remember {
        mutableStateOf(manualQueuePrefs.getString(MANUAL_ACTIVE_APP_ID, null))
    }
    var downloadingApp by remember { mutableStateOf<StoreAppListing?>(null) }
    var downloadPercent by remember { mutableIntStateOf(0) }
    var updatePhase by remember { mutableStateOf<String?>(null) }
    var activeUpdateAppId by remember { mutableStateOf<String?>(null) }
    var validatingAppId by remember { mutableStateOf<String?>(null) }
    var readyOrder by remember { mutableStateOf(StoreUpdateNotifications.readyAppIds(context)) }
    var installingAppId by remember {
        mutableStateOf(manualQueuePrefs.getString(MANUAL_INSTALLING_APP_ID, null))
    }
    var installUri by remember { mutableStateOf<Uri?>(null) }
    var installPackageName by remember { mutableStateOf<String?>(null) }
    var readyInstallAppId by remember { mutableStateOf<String?>(null) }
    var installError by remember { mutableStateOf<String?>(null) }
    var showSignerSettings by remember { mutableStateOf(false) }
    var showTraceLog by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }
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

    fun setInstallQueueActive(active: Boolean) {
        installQueueActive = active
        manualQueuePrefs.edit().putBoolean(MANUAL_INSTALL_QUEUE_ACTIVE, active).apply()
    }

    val installPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val uri = installUri
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
                installingAppId = null
                manualQueuePrefs.edit().remove(MANUAL_INSTALLING_APP_ID).apply()
                updatePhase = null
                activeUpdateAppId = null
                readyInstallAppId = null
                setInstallQueueActive(false)
            } else {
                // Keep the validated APK until Android reports that installation succeeded.
            }
        } else if (uri != null) {
            installError = "Kurulum izni verilmedi. Ayarlar → bilinen kaynaklar: My App Store açın."
            installingAppId = null
            manualQueuePrefs.edit().remove(MANUAL_INSTALLING_APP_ID).apply()
            readyInstallAppId = null
            updatePhase = null
            activeUpdateAppId = null
            setInstallQueueActive(false)
        }
    }

    fun launchReadyInstall(app: StoreAppListing, uri: Uri) {
        if (installingAppId != null) return
        installError = null
        installUri = null
        installPackageName = app.packageName
        readyInstallAppId = app.id
        installingAppId = app.id
        manualQueuePrefs.edit().putString(MANUAL_INSTALLING_APP_ID, app.id).apply()
        updatePhase = "hazirlaniyor"
        // Older ready-queue entries may still contain DownloadManager's
        // content://downloads URI. Copy every queued APK into our FileProvider
        // before handing it to Android's package installer.
        scope.launch {
            try {
                val stagedUri = withContext(Dispatchers.IO) {
                    try {
                        createInstallableApkUri(context, uri)
                    } catch (staleDownloadUri: Exception) {
                        val downloadedFile = storeDownloadFile(context, app.id, app.versionCode.toLong())
                        if (!downloadedFile.isFile || downloadedFile.length() <= 0L) throw staleDownloadUri
                        createInstallableApkUri(context, downloadedFile)
                    }
                }
                installUri = stagedUri
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    !context.packageManager.canRequestPackageInstalls()
                ) {
                    updatePhase = "izin"
                    installPermissionLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    )
                } else {
                    updatePhase = "kuruluyor"
                    val error = launchStoreInstallOrUpdate(
                        context, stagedUri, app.packageName, app.versionCode.toLong()
                    )
                    if (error != null) {
                        installError = error
                        installingAppId = null
                        manualQueuePrefs.edit().remove(MANUAL_INSTALLING_APP_ID).apply()
                        updatePhase = null
                        readyInstallAppId = null
                        setInstallQueueActive(false)
                    }
                }
            } catch (error: Exception) {
                installError = "APK kurulum için hazırlanamadı: ${error.message ?: error.javaClass.simpleName}"
                installingAppId = null
                manualQueuePrefs.edit().remove(MANUAL_INSTALLING_APP_ID).apply()
                updatePhase = null
                readyInstallAppId = null
                setInstallQueueActive(false)
            }
        }
    }

    fun launchNextQueuedInstallIfForeground() {
        if (!activityResumed || !installQueueActive || installingAppId != null || apps.isEmpty()) return
        val nextReadyId = readyOrder.firstOrNull() ?: return
        val nextApp = apps.firstOrNull { it.id == nextReadyId } ?: return
        val nextUri = StoreUpdateNotifications.installUri(context, nextReadyId) ?: return
        launchReadyInstall(nextApp, nextUri)
    }

    fun finishInstallQueueIfIdle() {
        if (installQueueActive && readyOrder.isEmpty() && updateQueueIds.isEmpty() &&
            downloadId == null && downloadingApp == null && validatingAppId == null && installingAppId == null
        ) {
            setInstallQueueActive(false)
        }
    }

    fun startNextQueuedDownload() {
        if (downloadId != null || downloadingApp != null || apps.isEmpty()) return
        val nextId = updateQueueIds.firstOrNull() ?: return
        val app = apps.firstOrNull { it.id == nextId }
        if (app == null || app.release == null) {
            updateQueueIds = updateQueueIds.drop(1)
            writeManualQueueIds(context, updateQueueIds)
            startNextQueuedDownload()
            finishInstallQueueIfIdle()
            return
        }
        if (StoreUpdateNotifications.hasReadyVersion(context, app.id, app.versionCode.toLong())) {
            readyOrder = (readyOrder + app.id).distinct()
            updateQueueIds = updateQueueIds.drop(1)
            writeManualQueueIds(context, updateQueueIds)
            startNextQueuedDownload()
            return
        }
        updateQueueIds = updateQueueIds.drop(1)
        writeManualQueueIds(context, updateQueueIds)
        downloadingApp = app
        activeDownloadAppId = app.id
        downloadPercent = 0
        StoreUpdateTrace.startSession(
            context,
            appId = app.id,
            packageName = app.packageName,
            versionName = app.versionName,
            versionCode = app.versionCode.toLong(),
            reason = "user_queue"
        )
        val request = DownloadManager.Request(Uri.parse(app.downloadUrl))
            .setTitle("${app.name} güncelleniyor")
            .setDescription("${app.versionName} • Sıradaki uygulama")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalFilesDir(
                context,
                android.os.Environment.DIRECTORY_DOWNLOADS,
                "${app.id}-${app.versionCode}.apk"
            )
        val enqueued = (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        downloadId = enqueued
        manualQueuePrefs.edit()
            .putLong(MANUAL_ACTIVE_DOWNLOAD_ID, enqueued)
            .putString(MANUAL_ACTIVE_APP_ID, app.id)
            .apply()
        StoreUpdateTrace.event(
            context, "download", "enqueued", "downloadId=$enqueued",
            code = enqueued.toInt()
        )
    }

    LaunchedEffect(apps) {
        if (apps.isNotEmpty()) {
            readyOrder = StoreUpdateNotifications.readyAppIds(context)
                .filter { id -> apps.any { it.id == id } }
            if (downloadId != null && activeDownloadAppId != null) {
                downloadingApp = apps.firstOrNull { it.id == activeDownloadAppId }
                // The DownloadManager request is already running; the progress effect resumes below.
            } else {
                startNextQueuedDownload()
            }
        }
    }

    LaunchedEffect(activityResumed, readyOrder, installQueueActive, installingAppId, apps) {
        if (activityResumed) launchNextQueuedInstallIfForeground()
    }

    LaunchedEffect(statusRefreshKey) {
        if (statusRefreshKey > 0 && apps.isNotEmpty() && updatePhase != "izin") {
            val completedId = installingAppId
            if (completedId != null) {
                val installedApp = apps.firstOrNull { it.id == completedId }
                val installedVersion = installedApp?.let { getInstalledVersionCode(context, it.packageName) }
                if (installedApp != null && installedVersion != null &&
                    installedVersion >= installedApp.versionCode.toLong()
                ) {
                    StoreUpdateNotifications.removeReady(context, completedId)
                    readyOrder = readyOrder.filterNot { it == completedId }
                    Toast.makeText(context, "${installedApp.name} güncellendi", Toast.LENGTH_SHORT).show()
                    installingAppId = null
                    manualQueuePrefs.edit().remove(MANUAL_INSTALLING_APP_ID).apply()
                    readyInstallAppId = null
                    updatePhase = null
                    activeUpdateAppId = null
                    if (readyOrder.isEmpty() && updateQueueIds.isEmpty() &&
                        downloadId == null && downloadingApp == null && validatingAppId == null
                    ) {
                        setInstallQueueActive(false)
                    }
                } else {
                    // Cancellation pauses the install chain; the validated APK stays ready for retry.
                    installingAppId = null
                    manualQueuePrefs.edit().remove(MANUAL_INSTALLING_APP_ID).apply()
                    readyInstallAppId = null
                    updatePhase = null
                    activeUpdateAppId = null
                    setInstallQueueActive(false)
                }
            } else if (updatePhase == "kuruluyor") {
                updatePhase = null
                activeUpdateAppId = null
            }
            startNextQueuedDownload()
        }
    }

    LaunchedEffect(installRequest) {
        refreshCatalog()
        val installAppIdFromIntent = installRequest?.first
        if (installAppIdFromIntent != null) {
            setInstallQueueActive(true)
            val uri = StoreUpdateNotifications.installUri(context, installAppIdFromIntent)
            if (uri != null) {
                installUri = null
                installPackageName = null
                readyInstallAppId = null
                updatePhase = "kuruluyor"
                activeUpdateAppId = installAppIdFromIntent
                installingAppId = installAppIdFromIntent
                manualQueuePrefs.edit().putString(MANUAL_INSTALLING_APP_ID, installAppIdFromIntent).apply()
                try {
                    val stagedUri = withContext(Dispatchers.IO) {
                        try {
                            createInstallableApkUri(context, uri)
                        } catch (staleDownloadUri: Exception) {
                            val versionCode = StoreUpdateNotifications.readyVersionCode(context, installAppIdFromIntent)
                                ?: apps.firstOrNull { it.id == installAppIdFromIntent }?.versionCode?.toLong()
                                ?: throw staleDownloadUri
                            val downloadedFile = storeDownloadFile(context, installAppIdFromIntent, versionCode)
                            if (!downloadedFile.isFile || downloadedFile.length() <= 0L) throw staleDownloadUri
                            createInstallableApkUri(context, downloadedFile)
                        }
                    }
                    installUri = stagedUri
                    installPackageName = StoreUpdateNotifications.installPackageName(context, installAppIdFromIntent)
                    readyInstallAppId = installAppIdFromIntent
                } catch (error: Exception) {
                    installError = error.message ?: "APK kurulum için hazırlanamadı."
                    installingAppId = null
                    manualQueuePrefs.edit().remove(MANUAL_INSTALLING_APP_ID).apply()
                    updatePhase = null
                    activeUpdateAppId = null
                    setInstallQueueActive(false)
                    finishInstallQueueIfIdle()
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
                            installingAppId = null
                            manualQueuePrefs.edit().remove(MANUAL_INSTALLING_APP_ID).apply()
                            updatePhase = null
                            activeUpdateAppId = null
                            setInstallQueueActive(false)
                        } else {
                            // Keep the validated APK until successful installation is confirmed.
                            readyInstallAppId = installAppIdFromIntent
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(downloadId, downloadingApp?.id) {
        val id = downloadId ?: return@LaunchedEffect
        val activeApp = downloadingApp ?: return@LaunchedEffect
        while (true) {
            val snapshot = withContext(Dispatchers.IO) {
                val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val cursor = manager.query(DownloadManager.Query().setFilterById(id)) ?: return@withContext null
                cursor.use {
                    if (!it.moveToFirst()) return@use null
                    val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val current = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    Pair(status, if (total > 0) (current * 100 / total).toInt().coerceIn(0, 100) else 0)
                }
            }
            if (snapshot == null) {
                installError = "İndirme durumu okunamadı."
                downloadId = null
                downloadingApp = null
                activeDownloadAppId = null
                manualQueuePrefs.edit()
                    .remove(MANUAL_ACTIVE_DOWNLOAD_ID)
                    .remove(MANUAL_ACTIVE_APP_ID)
                    .apply()
                validatingAppId = null
                activeUpdateAppId = null
                startNextQueuedDownload()
                finishInstallQueueIfIdle()
                break
            }
            downloadPercent = snapshot.second
            when (snapshot.first) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val app = activeApp
                    val finishedId = id
                    downloadId = null
                    downloadingApp = null
                    manualQueuePrefs.edit()
                        .remove(MANUAL_ACTIVE_DOWNLOAD_ID)
                        .remove(MANUAL_ACTIVE_APP_ID)
                        .apply()
                    activeDownloadAppId = null
                    scope.launch {
                        val sourceFile = storeDownloadFile(context, app.id, app.versionCode.toLong())
                        if (!sourceFile.isFile || sourceFile.length() <= 0L) {
                            installError = "İndirilen APK dosyası bulunamadı: ${sourceFile.name}"
                            StoreUpdateTrace.event(context, "download", "error", "Downloaded file URI missing")
                            StoreUpdateTrace.endSession(context, "error", "missing uri")
                            validatingAppId = null
                            activeUpdateAppId = null
                            startNextQueuedDownload()
                            finishInstallQueueIfIdle()
                            return@launch
                        }
                        StoreUpdateTrace.event(
                            context, "download", "ok", "Download finished",
                            extra = mapOf("downloadId" to finishedId)
                        )
                        validatingAppId = app.id
                        activeUpdateAppId = app.id
                        installPackageName = app.packageName
                        val stageStarted = System.currentTimeMillis()
                        StoreUpdateTrace.event(context, "validate", "start", "Stage + identity + sha256")
                        val staged = try {
                            withContext(Dispatchers.IO) {
                                val stagedUri = createInstallableApkUri(context, sourceFile)
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
                            validatingAppId = null
                            activeUpdateAppId = null
                            StoreUpdateTrace.endSession(context, "error", validationError ?: "validate failed")
                            startNextQueuedDownload()
                            finishInstallQueueIfIdle()
                        } else {
                            StoreUpdateTrace.event(
                                context, "validate", "ok", "Package ready",
                                extra = mapOf("durationMs" to validateMs)
                            )
                            StoreUpdateNotifications.saveReady(context, app, stagedUri)
                            readyOrder = (readyOrder + app.id).distinct()
                            StoreUpdateTrace.event(context, "ready_apk", "ok", "Validated APK is ready for user-approved installation")
                            StoreUpdateTrace.endSession(context, "ready", "APK prepared; next queued download will start")
                            validatingAppId = null
                            activeUpdateAppId = null
                            startNextQueuedDownload()
                            launchNextQueuedInstallIfForeground()
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
                    activeDownloadAppId = null
                    manualQueuePrefs.edit()
                        .remove(MANUAL_ACTIVE_DOWNLOAD_ID)
                        .remove(MANUAL_ACTIVE_APP_ID)
                        .apply()
                    validatingAppId = null
                    activeUpdateAppId = null
                    startNextQueuedDownload()
                    finishInstallQueueIfIdle()
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
            val refreshTransition = rememberInfiniteTransition(label = "catalog-refresh")
            val refreshRotation by refreshTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(animation = tween(850, easing = LinearEasing)),
                label = "refresh-rotation"
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("MY APP STORE", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                OutlinedButton(
                    onClick = { showSettingsMenu = true },
                    modifier = Modifier.size(52.dp, 48.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("⚙", fontSize = 23.sp)
                }
                OutlinedButton(
                    onClick = { refreshCatalog() },
                    enabled = !loading,
                    modifier = Modifier.size(52.dp, 48.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(
                        "↻",
                        fontSize = 30.sp,
                        modifier = Modifier.graphicsLayer { rotationZ = if (loading) refreshRotation else 0f }
                    )
                }
            }
            if (loading && apps.isEmpty()) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Uygulamalar kontrol ediliyor…", color = StoreMuted, fontSize = 12.sp)
            }
            loadError?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 12.sp) }
            installError?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 12.sp) }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(apps, key = { it.id }) { app ->
                    StoreAppCard(
                        app = app,
                        context = context,
                        busy = false,
                        isUpdating = activeDownloadAppId == app.id && downloadId != null,
                        isValidating = validatingAppId == app.id,
                        isQueued = updateQueueIds.contains(app.id),
                        queuePosition = updateQueueIds.indexOf(app.id) + 1,
                        isReady = app.release?.let {
                            StoreUpdateNotifications.hasReadyVersion(context, app.id, it.versionCode.toLong())
                        } ?: false,
                        isInstalling = installingAppId == app.id,
                        isFirstReady = readyOrder.firstOrNull() == app.id || readyOrder.isEmpty(),
                        downloadPercent = if (activeDownloadAppId == app.id) downloadPercent else 0,
                        refreshKey = "$catalogRevision:$statusRefreshKey",
                        onOpenDetails = { selectedApp = app },
                        onInstall = {
                            installError = null
                            val releaseCode = app.release?.versionCode?.toLong()
                            val readyUri = if (releaseCode != null &&
                                StoreUpdateNotifications.hasReadyVersion(context, app.id, releaseCode)
                            ) StoreUpdateNotifications.installUri(context, app.id) else null
                            when {
                                readyUri != null -> {
                                    setInstallQueueActive(true)
                                    readyOrder = (readyOrder + app.id).distinct()
                                    if (installingAppId != null || readyOrder.firstOrNull() != app.id) {
                                        Toast.makeText(context, "Güncellemeler sırayla kurulacak.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        launchNextQueuedInstallIfForeground()
                                    }
                                }
                                activeDownloadAppId == app.id || updateQueueIds.contains(app.id) -> Unit
                                else -> {
                                    setInstallQueueActive(true)
                                    updateQueueIds = updateQueueIds + app.id
                                    writeManualQueueIds(context, updateQueueIds)
                                    launchNextQueuedInstallIfForeground()
                                    startNextQueuedDownload()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
    if (showSettingsMenu) {
        AlertDialog(
            onDismissRequest = { showSettingsMenu = false },
            title = { Text("Ayarlar", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { showSettingsMenu = false; showSignerSettings = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("İMZALAMA") }
                    OutlinedButton(
                        onClick = { showSettingsMenu = false; showTraceLog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("GÜNCELLEME LOGU") }
                }
            },
            confirmButton = { TextButton(onClick = { showSettingsMenu = false }) { Text("KAPAT") } },
            containerColor = StorePanel,
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
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
    isValidating: Boolean = false,
    isQueued: Boolean = false,
    queuePosition: Int = 0,
    isReady: Boolean = false,
    isInstalling: Boolean = false,
    isFirstReady: Boolean = true,
    downloadPercent: Int = 0,
    refreshKey: String,
    onOpenDetails: () -> Unit,
    onInstall: () -> Unit
) {
    val installedCode = remember(app.packageName, refreshKey) { getInstalledVersionCode(context, app.packageName) }
    val release = app.release
    val packageState = resolveStorePackageState(installedCode, release?.versionCode?.toLong())
    val status = when {
        isInstalling -> "Kurulum onayı bekleniyor"
        isValidating -> "Paket kontrol ediliyor…"
        isUpdating -> "İndiriliyor • $downloadPercent%"
        isQueued -> "Sırada • $queuePosition"
        isReady -> "Kurulmaya hazır"
        else -> when (packageState) {
            StorePackageState.NOT_INSTALLED -> if (release == null) "Kurulu değil • Release bekleniyor" else "Yüklenmedi"
            StorePackageState.INSTALLED -> "Kurulu • Sürüm bilgisi yok"
            StorePackageState.UPDATE_AVAILABLE -> "Güncelleme mevcut"
            StorePackageState.UP_TO_DATE -> "Yüklü • Güncel"
        }
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = StorePanel),
        shape = RoundedCornerShape(13.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenDetails)) {
                StoreRealAppIcon(
                    packageName = app.packageName,
                    fallbackEmoji = app.icon,
                    size = 34.dp,
                    fontSize = 25.sp
                )
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(app.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(app.description, color = StoreMuted, fontSize = 11.sp)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(release?.versionName ?: "Sürüm bekleniyor", color = StoreMuted, fontSize = 10.sp)
                Text(
                    status,
                    color = if (isUpdating || isValidating || isReady || isInstalling || isQueued ||
                        packageState == StorePackageState.UPDATE_AVAILABLE || packageState == StorePackageState.UP_TO_DATE
                    ) StoreGreen else StoreMuted,
                    fontSize = 10.sp
                )
            }
            if (release != null) {
                val canStartUpdate = packageState == StorePackageState.NOT_INSTALLED ||
                    packageState == StorePackageState.UPDATE_AVAILABLE
                // The install FIFO must not block downloading another app.
                // Only a prepared APK waits for its turn to open Android's installer.
                val buttonEnabled = !busy && !isUpdating && !isValidating && !isQueued && !isInstalling &&
                    if (isReady) isFirstReady else canStartUpdate
                val buttonShouldBeGreen = isReady && isFirstReady || !isReady && canStartUpdate
                val buttonLabel = when {
                    isInstalling -> "KURULUM BEKLENİYOR…"
                    isValidating -> "PAKET KONTROL EDİLİYOR…"
                    isUpdating -> "İNDİRİLİYOR • $downloadPercent%"
                    isQueued -> "SIRADA • $queuePosition"
                    isReady -> "KUR • ${release.versionName}"
                    packageState == StorePackageState.NOT_INSTALLED -> "YÜKLE"
                    packageState == StorePackageState.INSTALLED -> "KURULU"
                    packageState == StorePackageState.UPDATE_AVAILABLE -> "GÜNCELLE • ${release.versionName}"
                    else -> "ZATEN GÜNCEL"
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (buttonShouldBeGreen && buttonEnabled) StoreGreen else Color(0xFF2E3943))
                        .clickable(enabled = buttonEnabled, onClick = onInstall),
                    contentAlignment = Alignment.Center
                ) {
                    if (isUpdating) {
                        Box(
                            Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxHeight()
                                .fillMaxWidth((downloadPercent.coerceIn(0, 100) / 100f))
                                .background(StoreGreen)
                        )
                    }
                    Text(
                        buttonLabel,
                        color = if (isUpdating || buttonShouldBeGreen && buttonEnabled) Color(0xFF07130D) else Color.White,
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
            StoreRealAppIcon(
                packageName = app.packageName,
                fallbackEmoji = app.icon,
                size = 40.dp,
                fontSize = 27.sp
            )
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
