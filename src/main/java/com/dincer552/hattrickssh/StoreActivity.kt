package com.dincer552.hattrickssh

import android.app.DownloadManager
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
        captureInstallRequest(intent)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = StoreGreen,
                background = StoreBackground,
                surface = StorePanel,
                onSurface = Color.White
            )) {
                StoreScreen(installRequest = installRequestState.value)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureInstallRequest(intent)
    }
}

@Composable
private fun StoreScreen(installRequest: Pair<String, Int>? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<StoreAppListing>>(emptyList()) }
    var catalogRevision by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var downloadId by remember { mutableStateOf<Long?>(null) }
    var downloadingApp by remember { mutableStateOf<StoreAppListing?>(null) }
    var downloadPercent by remember { mutableIntStateOf(0) }
    var installUri by remember { mutableStateOf<Uri?>(null) }
    var installError by remember { mutableStateOf<String?>(null) }
    var showSignerSettings by remember { mutableStateOf(false) }

    fun refreshCatalog() {
        if (loading) return
        loading = true
        loadError = null
        scope.launch {
            try {
                apps = StoreCatalogManager.loadCatalog()
                catalogRevision++
                if (apps.isEmpty()) loadError = "Katalog boş. app-catalog.json dosyasını kontrol edin."
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
        if (uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.packageManager.canRequestPackageInstalls()
        ) {
            launchApkInstaller(context, uri)?.let { installError = it }
        } else if (uri != null) {
            installError = "APK kurulum izni verilmedi. Ayarlardan bu kaynağa izin ver."
        }
    }

    // Check for ready downloads from background worker notifications
    LaunchedEffect(installRequest) {
        refreshCatalog()
        val installAppIdFromIntent = installRequest?.first
        if (installAppIdFromIntent != null) {
            val uri = StoreUpdateNotifications.installUri(context, installAppIdFromIntent)
            if (uri != null) {
                try {
                    installUri = withContext(Dispatchers.IO) { createInstallableApkUri(context, uri) }
                    StoreUpdateNotifications.removeReady(context, installAppIdFromIntent)
                } catch (error: Exception) {
                    installError = error.message ?: "APK kurulum için hazırlanamadı."
                }
                if (installUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    !context.packageManager.canRequestPackageInstalls()
                ) {
                    installPermissionLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    )
                } else {
                    installUri?.let { stagedUri ->
                        launchApkInstaller(context, stagedUri)?.let { installError = it }
                    }
                }
            }
        }
    }

    // Also refresh when app comes to foreground
    LaunchedEffect(apps) { }

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
                break
            }
            downloadPercent = snapshot.second
            when (snapshot.first) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val uri = snapshot.third
                    val app = downloadingApp
                    downloadId = null
                    downloadingApp = null
                    if (uri == null || app == null) {
                        installError = "İndirilen APK bulunamadı."
                    } else {
                        val validation = withContext(Dispatchers.IO) {
                            validateDownloadedApk(context, uri, app)
                        }
                        if (validation != null) {
                            installError = validation
                        } else {
                            installUri = try {
                                withContext(Dispatchers.IO) { createInstallableApkUri(context, uri) }
                            } catch (error: Exception) {
                                installError = error.message ?: "APK kurulum için hazırlanamadı."
                                null
                            }
                            if (installUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                                !context.packageManager.canRequestPackageInstalls()
                            ) {
                                installPermissionLauncher.launch(
                                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                                )
                            } else {
                                installUri?.let { stagedUri ->
                                    launchApkInstaller(context, stagedUri)?.let { installError = it }
                                }
                            }
                        }
                    }
                    break
                }
                DownloadManager.STATUS_FAILED -> {
                    installError = "APK indirilemedi. GitHub bağlantısını kontrol edin."
                    downloadId = null
                    downloadingApp = null
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
                    Text("Kendi Android uygulamaların", color = StoreMuted, fontSize = 12.sp)
                }
                OutlinedButton(onClick = { showSignerSettings = true }) {
                    Text("İMZALAMA", fontSize = 9.sp)
                }
                OutlinedButton(onClick = { refreshCatalog() }, enabled = !loading) {
                    Text(if (loading) "Kontrol…" else "YENİLE")
                }
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF14271F)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Kişisel uygulama mağazan", color = StoreGreen, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("GitHub Releases üzerinden APK indir. Kurulum ve güncellemeler Android'in güvenli yükleyicisiyle onaylanır.", color = Color(0xFFD2E5DB), fontSize = 11.sp)
                }
            }
            if (loading && apps.isEmpty()) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Uygulamalar kontrol ediliyor…", color = StoreMuted, fontSize = 12.sp)
            }
            loadError?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 12.sp) }
            installError?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 12.sp) }
            if (downloadId != null) {
                Text("${downloadingApp?.name ?: "APK"} indiriliyor • $downloadPercent%", color = StoreGreen, fontSize = 12.sp)
                LinearProgressIndicator(progress = { downloadPercent / 100f }, modifier = Modifier.fillMaxWidth())
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(apps, key = { it.id }) { app ->
                    StoreAppCard(
                        app = app,
                        context = context,
                        busy = downloadId != null,
                        refreshKey = catalogRevision,
                        onInstall = {
                            installError = null
                            downloadingApp = app
                            val request = DownloadManager.Request(Uri.parse(app.downloadUrl))
                                .setTitle("${app.name} • APK")
                                .setDescription("${app.versionName} indiriliyor")
                                .setMimeType("application/vnd.android.package-archive")
                                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                .setAllowedOverMetered(true)
                                .setAllowedOverRoaming(false)
                                .setDestinationInExternalFilesDir(context, android.os.Environment.DIRECTORY_DOWNLOADS, "${app.id}-${app.versionCode}.apk")
                            downloadId = (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                        }
                    )
                }
            }
            Text("Mağaza sürümü ${BuildConfig.VERSION_NAME} • Paketler GitHub'dan alınır", color = StoreMuted, fontSize = 10.sp)
        }
    }
    if (showSignerSettings) {
        SignerSetupDialog(onDismiss = { showSignerSettings = false })
    }
}

@Composable
private fun StoreAppCard(
    app: StoreAppListing,
    context: Context,
    busy: Boolean,
    refreshKey: Int,
    onInstall: () -> Unit
) {
    val installedCode = remember(app.packageName, refreshKey) { getInstalledVersionCode(context, app.packageName) }
    val release = app.release
    val packageState = resolveStorePackageState(installedCode, release?.versionCode?.toLong())
    val status = when {
        release == null -> "Henüz GitHub Release yayınlanmamış"
        packageState == StorePackageState.NOT_INSTALLED -> "Yüklenmedi"
        packageState == StorePackageState.UPDATE_AVAILABLE -> "Güncelleme mevcut"
        else -> "Yüklü • Güncel"
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = StorePanel),
        shape = RoundedCornerShape(13.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(app.icon, fontSize = 25.sp)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(app.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(app.description, color = StoreMuted, fontSize = 11.sp)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(release?.versionName ?: "Sürüm bekleniyor", color = StoreMuted, fontSize = 10.sp)
                Text(status, color = if (release == null) StoreMuted else StoreGreen, fontSize = 10.sp)
            }
            if (release != null) {
                Button(
                    onClick = onInstall,
                    enabled = !busy && packageState != StorePackageState.UP_TO_DATE,
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        when (packageState) {
                            StorePackageState.NOT_INSTALLED -> "APK İNDİR VE KUR"
                            StorePackageState.UPDATE_AVAILABLE -> "GÜNCELLE • ${release.versionName}"
                            StorePackageState.UP_TO_DATE -> "ZATEN GÜNCEL"
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

private fun launchApkInstaller(context: Context, uri: Uri): String? {
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
        "APK indirildi ancak kurulum ekranı açılamadı (${error.javaClass.simpleName}): ${error.message ?: "neden belirtilmedi"}. My App Store için 'Bilinmeyen uygulamaları yükle' iznini kontrol et."
    }
}
