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

private val StoreBackground = Color(0xFF080D12)
private val StorePanel = Color(0xFF121B24)
private val StoreGreen = Color(0xFF38D98A)
private val StoreMuted = Color(0xFF91A1B2)

class StoreActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(8, 13, 18)
        window.navigationBarColor = android.graphics.Color.BLACK
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = StoreGreen,
                background = StoreBackground,
                surface = StorePanel,
                onSurface = Color.White
            )) {
                StoreScreen()
            }
        }
    }
}

@Composable
private fun StoreScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<StoreAppListing>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var downloadId by remember { mutableStateOf<Long?>(null) }
    var downloadingApp by remember { mutableStateOf<StoreAppListing?>(null) }
    var downloadPercent by remember { mutableIntStateOf(0) }
    var installUri by remember { mutableStateOf<Uri?>(null) }
    var installError by remember { mutableStateOf<String?>(null) }

    fun refreshCatalog() {
        if (loading) return
        loading = true
        loadError = null
        scope.launch {
            try {
                apps = StoreCatalogManager.loadCatalog()
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
            launchApkInstaller(context, uri)
        } else if (uri != null) {
            installError = "APK kurulum izni verilmedi."
        }
    }

    LaunchedEffect(Unit) { refreshCatalog() }

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
                            installUri = uri
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                                !context.packageManager.canRequestPackageInstalls()
                            ) {
                                installPermissionLauncher.launch(
                                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                                )
                            } else {
                                launchApkInstaller(context, uri)
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
}

@Composable
private fun StoreAppCard(
    app: StoreAppListing,
    context: Context,
    busy: Boolean,
    onInstall: () -> Unit
) {
    val installedCode = getInstalledVersionCode(context, app.packageName)
    val release = app.release
    val status = when {
        release == null -> "Henüz GitHub Release yayınlanmamış"
        installedCode == null -> "Yüklenmedi"
        installedCode < release.versionCode -> "Güncelleme mevcut"
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
                    enabled = !busy && (installedCode == null || installedCode < release.versionCode),
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        when {
                            installedCode == null -> "APK İNDİR VE KUR"
                            installedCode < release.versionCode -> "GÜNCELLE • ${release.versionName}"
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

@Suppress("DEPRECATION")
private fun getInstalledVersionCode(context: Context, packageName: String): Long? = try {
    val info = if (Build.VERSION.SDK_INT >= 33) {
        context.packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
    } else {
        context.packageManager.getPackageInfo(packageName, 0)
    }
    if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
} catch (_: Exception) {
    null
}

private fun validateDownloadedApk(context: Context, uri: Uri, app: StoreAppListing): String? {
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

private fun launchApkInstaller(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
        data = uri
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
    }
    context.startActivity(intent)
}
