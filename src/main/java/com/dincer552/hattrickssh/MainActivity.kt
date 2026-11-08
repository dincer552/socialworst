package com.dincer552.hattrickssh

import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalUriHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dincer552.hattrickssh.security.SshKeyImportManager

private val AppBackground = Color(0xFF080D12)
private val AppPanel = Color(0xFF121B24)
private val AppGreen = Color(0xFF38D98A)
private val AppMuted = Color(0xFF91A1B2)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = AppGreen,
                    onPrimary = Color(0xFF06130C),
                    background = AppBackground,
                    surface = AppPanel,
                    onSurface = Color.White
                )
            ) {
                MainScreen(
                    onConnect = { startActivity(Intent(this, TerminalActivity::class.java)) }
                )
            }
        }
    }
}

@Composable
private fun MainScreen(onConnect: () -> Unit) {
    val context = LocalContext.current
    val keyManager = remember { SshKeyImportManager(context.applicationContext) }
    var keyImported by remember { mutableStateOf(keyManager.hasPrivateKey()) }
    var showSettings by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<ReleaseUpdate?>(null) }
    var updateStatus by remember { mutableStateOf("Güncelleme kontrol ediliyor…") }
    var updateError by remember { mutableStateOf<String?>(null) }
    var downloadId by remember { mutableStateOf<Long?>(null) }
    var downloadPercent by remember { mutableIntStateOf(0) }
    var pendingInstallUri by remember { mutableStateOf<Uri?>(null) }

    val installPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val uri = pendingInstallUri
        if (uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.packageManager.canRequestPackageInstalls()
        ) {
            openApkInstaller(context, uri)
        } else if (uri != null) {
            updateError = "APK kurulum izni verilmedi."
        }
    }

    LaunchedEffect(Unit) {
        try {
            val update = AppUpdateManager.checkForUpdate()
            availableUpdate = update
            updateStatus = if (update == null) "Uygulama güncel" else "Yeni sürüm: ${update.versionName}"
            updateError = null
        } catch (error: Exception) {
            updateStatus = "Güncelleme kontrol edilemedi"
            updateError = error.message ?: "Bağlantı hatası"
        }
    }

    LaunchedEffect(downloadId) {
        val id = downloadId ?: return@LaunchedEffect
        while (true) {
            val snapshot = withContext(Dispatchers.IO) {
                AppUpdateManager.getDownloadSnapshot(context, id)
            }
            if (snapshot == null) {
                updateError = "İndirme durumu okunamadı."
                downloadId = null
                break
            }
            downloadPercent = snapshot.progressPercent
            when (snapshot.status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val uri = withContext(Dispatchers.IO) {
                        AppUpdateManager.getDownloadedUri(context, id)
                    }
                    downloadId = null
                    if (uri == null) {
                        updateError = "İndirilen APK dosyası bulunamadı."
                    } else {
                        pendingInstallUri = uri
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                            !context.packageManager.canRequestPackageInstalls()
                        ) {
                            installPermissionLauncher.launch(
                                Intent(
                                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        } else {
                            openApkInstaller(context, uri)
                        }
                    }
                    break
                }
                DownloadManager.STATUS_FAILED -> {
                    updateError = "APK indirilemedi. İnternet bağlantısını kontrol edip tekrar dene."
                    downloadId = null
                    break
                }
                else -> {
                    updateStatus = "İndiriliyor… %${downloadPercent}"
                    delay(450)
                }
            }
        }
    }

    val keyPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val result = keyManager.importFromUri(uri)
            result.onSuccess {
                keyImported = true
                Toast.makeText(context, "SSH anahtarı güvenli şekilde kaydedildi.", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(
                    context,
                    error.message ?: "SSH anahtarı içe aktarılamadı.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = AppBackground) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("HATTRICK SSH", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text("Private server terminal", color = AppMuted, fontSize = 11.sp)
                }
                Text("SSH", color = AppGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(13.dp),
                colors = CardDefaults.cardColors(containerColor = AppPanel)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text("AZURE LINUX VM", color = AppMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    Text(Constants.VM_NAME, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(Constants.DEFAULT_HOST, color = AppMuted, fontSize = 12.sp)
                    Row {
                        Text("${Constants.DEFAULT_USERNAME}@${Constants.DEFAULT_PORT}", color = AppMuted, fontSize = 11.sp)
                        Spacer(Modifier.width(12.dp))
                        Text("Cloudflare Access", color = AppGreen, fontSize = 11.sp)
                    }
                }
            }

            Text(
                if (keyImported) "●  RSA anahtarı hazır" else "●  SSH anahtarı gerekli",
                color = if (keyImported) AppGreen else Color(0xFFFFC36B),
                fontSize = 12.sp
            )

            Button(
                onClick = {
                    if (keyImported) onConnect()
                    else Toast.makeText(context, "Önce Azure'dan indirdiğin .pem dosyasını seç.", Toast.LENGTH_LONG).show()
                },
                enabled = keyImported,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppGreen,
                    contentColor = Color(0xFF06130C),
                    disabledContainerColor = Color(0xFF35423B),
                    disabledContentColor = Color(0xFF9DA9A1)
                )
            ) {
                Text("BAĞLAN", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = { keyPicker.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(if (keyImported) "SSH KEY DEĞİŞTİR" else "SSH PRIVATE KEY SEÇ", fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = { showSettings = true },
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("BAĞLANTI AYARLARI", fontSize = 11.sp)
            }

            Text(
                "Sürüm ${BuildConfig.VERSION_NAME} • $updateStatus",
                color = if (availableUpdate != null) AppGreen else AppMuted,
                fontSize = 10.sp
            )

            if (availableUpdate != null && downloadId == null) {
                OutlinedButton(
                    onClick = {
                        updateError = null
                        downloadPercent = 0
                        try {
                            downloadId = AppUpdateManager.enqueueDownload(context, availableUpdate!!)
                        } catch (error: Exception) {
                            updateError = error.message ?: "İndirme başlatılamadı."
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("GÜNCELLE • ${availableUpdate!!.versionName}", fontSize = 11.sp)
                }
            } else if (availableUpdate == null && updateError != null) {
                OutlinedButton(
                    onClick = {
                        updateStatus = "Güncelleme kontrol ediliyor…"
                        updateError = null
                        // Recreate the screen check through the explicit retry coroutine below.
                    },
                    modifier = Modifier.fillMaxWidth().height(38.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("GÜNCELLEME KONTROLÜ BAŞARISIZ", fontSize = 10.sp)
                }
            }

            if (downloadId != null) {
                LinearProgressIndicator(
                    progress = { downloadPercent / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("APK indiriliyor: %${downloadPercent}", color = AppGreen, fontSize = 10.sp)
            }
            updateError?.let {
                Text(it, color = Color(0xFFFF8A80), fontSize = 10.sp)
            }

            Spacer(Modifier.weight(1f))
            Text(
                "Private key cihaz içinde Android Keystore ile şifrelenir. GitHub'a yüklenmez.",
                color = AppMuted,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Bağlantı ayarları", fontSize = 17.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Host: ${Constants.DEFAULT_HOST}", fontSize = 12.sp)
                    Text("Port: ${Constants.DEFAULT_PORT}", fontSize = 12.sp)
                    Text("User: ${Constants.DEFAULT_USERNAME}", fontSize = 12.sp)
                    Text("Transport: Cloudflare Access + SSH", fontSize = 12.sp)
                }
            },
            confirmButton = { Button(onClick = { showSettings = false }) { Text("Tamam") } },
            containerColor = AppPanel
        )
    }
}


private fun openApkInstaller(context: android.content.Context, apkUri: Uri) {
    val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
        data = apkUri
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
    }
    context.startActivity(installIntent)
}
