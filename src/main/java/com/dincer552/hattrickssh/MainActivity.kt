package com.dincer552.hattrickssh

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dincer552.hattrickssh.security.SshKeyImportManager
import com.dincer552.hattrickssh.ssh.AuthenticationMethod
import com.dincer552.hattrickssh.ssh.SshServerProfile
import com.dincer552.hattrickssh.ssh.SshServerProfileStore
import com.dincer552.hattrickssh.game.EmulatorActivity
import com.google.android.play.core.appupdate.AppUpdateManager as PlayAppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val AppBackground = Color(0xFF080D12)
private val AppPanel = Color(0xFF121B24)
private val AppGreen = Color(0xFF38D98A)
private val AppMuted = Color(0xFF91A1B2)

class MainActivity : ComponentActivity() {
    private lateinit var playUpdateManager: PlayAppUpdateManager
    private lateinit var updateLauncher: ActivityResultLauncher<IntentSenderRequest>
    private var playUpdateAvailable by mutableStateOf(false)
    private var playUpdateStatus by mutableStateOf("Play Store güncellemeleri yönetir")

    @SuppressLint("InvalidFragmentVersionForActivityResult")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.PLAY_DISTRIBUTION) {
            playUpdateManager = AppUpdateManagerFactory.create(this)
            updateLauncher = registerForActivityResult(
                ActivityResultContracts.StartIntentSenderForResult()
            ) { result ->
                if (result.resultCode != Activity.RESULT_OK) checkForPlayUpdate()
            }
        }
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
                    onConnect = { profile ->
                        startActivity(
                            Intent(this, TerminalActivity::class.java)
                                .putExtra(TerminalActivity.EXTRA_PROFILE_ID, profile.id)
                        )
                    },
                    playUpdateAvailable = playUpdateAvailable,
                    playUpdateStatus = playUpdateStatus,
                    onPlayUpdate = ::startPlayUpdate
                )
            }
        }
        if (BuildConfig.PLAY_DISTRIBUTION) checkForPlayUpdate()
    }

    override fun onResume() {
        super.onResume()
        if (!BuildConfig.PLAY_DISTRIBUTION || !::playUpdateManager.isInitialized) return
        playUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                launchImmediateUpdate(info)
            }
        }
    }

    private fun checkForPlayUpdate() {
        playUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                playUpdateAvailable = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                playUpdateStatus = if (playUpdateAvailable) "Google Play'de güncelleme var" else "Google Play sürümü güncel"
            }
            .addOnFailureListener { playUpdateStatus = "Güncelleme durumu şu an alınamıyor" }
    }

    private fun startPlayUpdate() {
        playUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                ) launchImmediateUpdate(info) else checkForPlayUpdate()
            }
            .addOnFailureListener { playUpdateStatus = "Google Play güncellemesi başlatılamadı" }
    }

    private fun launchImmediateUpdate(info: com.google.android.play.core.appupdate.AppUpdateInfo) {
        playUpdateManager.startUpdateFlowForResult(
            info,
            updateLauncher,
            AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
        )
    }
}

@Composable
private fun MainScreen(
    onConnect: (SshServerProfile) -> Unit,
    playUpdateAvailable: Boolean,
    playUpdateStatus: String,
    onPlayUpdate: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { SshServerProfileStore(context) }
    val keyManager = remember { SshKeyImportManager(context.applicationContext) }
    var profiles by remember { mutableStateOf(store.all()) }
    var editingProfile by remember { mutableStateOf<SshServerProfile?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<SshServerProfile?>(null) }
    var keyProfileId by remember { mutableStateOf<String?>(null) }
    var githubUpdate by remember { mutableStateOf<ReleaseUpdate?>(null) }
    var githubUpdateStatus by remember { mutableStateOf("Güncelleme kontrol ediliyor…") }
    var updateError by remember { mutableStateOf<String?>(null) }
    var downloadId by remember { mutableStateOf<Long?>(null) }
    var downloadPercent by remember { mutableIntStateOf(0) }
    var pendingInstallUri by remember { mutableStateOf<Uri?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    val updateScope = rememberCoroutineScope()
    val checkGitHubUpdates: () -> Unit = {
        if (!BuildConfig.DEBUG && !BuildConfig.PLAY_DISTRIBUTION && !isCheckingUpdate) {
            isCheckingUpdate = true
            updateError = null
            updateScope.launch {
                try {
                    githubUpdate = AppUpdateManager.checkForUpdate()
                    githubUpdateStatus = if (githubUpdate == null) "G\u00FCncel" else "Yeni s\u00FCr\u00FCm ${githubUpdate?.versionName} mevcut"
                } catch (error: Exception) {
                    githubUpdateStatus = "G\u00FCncelleme kontrol edilemedi"
                    updateError = error.message ?: "A\u011F hatas\u0131"
                } finally {
                    isCheckingUpdate = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        store.migrateLegacyInstallIfNeeded()
        profiles = store.all()
    }

    val installPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val uri = pendingInstallUri
        if (uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.packageManager.canRequestPackageInstalls()
        ) openApkInstaller(context, uri)
        else if (uri != null) updateError = "APK kurulum izni verilmedi."
    }

    val keyPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val profileId = keyProfileId
        if (uri != null && profileId != null) {
            keyManager.importFromUri(uri, profileId).onSuccess {
                Toast.makeText(context, "SSH anahtarı güvenli şekilde kaydedildi.", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(context, error.message ?: "SSH anahtarı içe aktarılamadı.", Toast.LENGTH_LONG).show()
            }
        }
        keyProfileId = null
    }

    LaunchedEffect(Unit) {
        if (!BuildConfig.DEBUG && !BuildConfig.PLAY_DISTRIBUTION) {
            isCheckingUpdate = true
            try {
                githubUpdate = AppUpdateManager.checkForUpdate()
                githubUpdateStatus = if (githubUpdate == null) "Uygulama güncel" else "Yeni sürüm ${githubUpdate?.versionName}"
                updateError = null
            } catch (error: Exception) {
                githubUpdateStatus = "Güncelleme kontrol edilemedi"
                updateError = error.message ?: "Ağ bağlantısı hatası"
            }
            isCheckingUpdate = false
        }
    }

    LaunchedEffect(downloadId) {
        val id = downloadId ?: return@LaunchedEffect
        while (true) {
            val snapshot = withContext(Dispatchers.IO) { AppUpdateManager.getDownloadSnapshot(context, id) }
            if (snapshot == null) {
                updateError = "İndirme durumu okunamadı."
                downloadId = null
                break
            }
            downloadPercent = snapshot.progressPercent
            when (snapshot.status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val uri = withContext(Dispatchers.IO) { AppUpdateManager.getDownloadedUri(context, id) }
                    downloadId = null
                    if (uri == null) updateError = "İndirilen APK bulunamadı."
                    else {
                        pendingInstallUri = uri
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                            !context.packageManager.canRequestPackageInstalls()
                        ) installPermissionLauncher.launch(
                            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        ) else openApkInstaller(context, uri)
                    }
                    break
                }
                DownloadManager.STATUS_FAILED -> {
                    updateError = "APK indirilemedi. Bağlantıyı kontrol edip tekrar deneyin."
                    downloadId = null
                    break
                }
                else -> delay(500)
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = AppBackground) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("SSH CLIENT", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Sunucularınız", color = AppMuted, fontSize = 12.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = { editingProfile = null; showEditor = true }) { Text("+ SUNUCU") }
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(context, EmulatorActivity::class.java))
                        },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
                    ) { Text("EMULATOR", fontSize = 10.sp) }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("BUCKY O'HARE · NES", color = Color(0xFF38D98A), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Orijinal NES emülatörü", color = Color(0xFF91A1B2), fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            context.startActivity(Intent(context, EmulatorActivity::class.java))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))
                    ) {
                        Text("BAŞLAT 🎮", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (profiles.isEmpty()) {
                    item {
                        Text(
                            "Henüz sunucu eklenmedi. Host, kullanıcı ve kimlik doğrulama bilgilerinizi ekleyerek başlayın.",
                            color = AppMuted,
                            fontSize = 13.sp
                        )
                    }
                }
                items(profiles, key = { it.id }) { profile ->
                    Card(colors = CardDefaults.cardColors(containerColor = AppPanel), shape = RoundedCornerShape(10.dp)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(profile.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("${profile.username}@${profile.host}:${profile.port}", color = AppMuted, fontSize = 11.sp)
                            if (profile.description.isNotBlank()) Text(profile.description, color = AppMuted, fontSize = 10.sp)
                            Text(
                                "${if (profile.authenticationMethod == AuthenticationMethod.PRIVATE_KEY) "Private key" else "Parola"} • ${if (profile.useCloudflareAccess) "Cloudflare Access" else "Doğrudan SSH"}",
                                color = AppGreen,
                                fontSize = 10.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Button(
                                    onClick = {
                                        if (profile.authenticationMethod == AuthenticationMethod.PRIVATE_KEY && !keyManager.hasPrivateKey(profile.id)) {
                                            Toast.makeText(context, "Bu profil için önce private key seçin.", Toast.LENGTH_LONG).show()
                                        } else onConnect(profile)
                                    },
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
                                ) { Text("BAĞLAN", fontSize = 10.sp) }
                                if (profile.authenticationMethod == AuthenticationMethod.PRIVATE_KEY) {
                                    OutlinedButton(
                                        onClick = { keyProfileId = profile.id; keyPicker.launch(arrayOf("*/*")) },
                                        modifier = Modifier.weight(1f).height(40.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)
                                    ) { Text("ANAHTAR", fontSize = 9.sp) }
                                }
                                TextButton(onClick = { editingProfile = profile; showEditor = true }) { Text("DÜZENLE", fontSize = 9.sp) }
                                TextButton(onClick = { deleteCandidate = profile }) { Text("SİL", color = Color(0xFFFF8A80), fontSize = 9.sp) }
                            }
                        }
                    }
                }
            }
            if (!BuildConfig.PLAY_DISTRIBUTION) {
                OutlinedButton(
                    onClick = checkGitHubUpdates,
                    enabled = !BuildConfig.DEBUG && !isCheckingUpdate && downloadId == null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        when {
                            BuildConfig.DEBUG -> "Release APK required for updates"
                            isCheckingUpdate -> "G\u00FCncellemeler kontrol ediliyor..."
                            else -> "G\u00FCncellemeleri kontrol et"
                        }
                    )
                }
            }
            val updateStatus = if (BuildConfig.PLAY_DISTRIBUTION) playUpdateStatus else githubUpdateStatus
            Text("${BuildConfig.VERSION_NAME} • $updateStatus", color = AppMuted, fontSize = 10.sp)
            if (BuildConfig.PLAY_DISTRIBUTION && playUpdateAvailable) {
                OutlinedButton(onClick = onPlayUpdate, modifier = Modifier.fillMaxWidth()) { Text("PLAY STORE'DAN GÜNCELLE") }
            } else if (!BuildConfig.DEBUG && !BuildConfig.PLAY_DISTRIBUTION && githubUpdate != null && downloadId == null) {
                OutlinedButton(
                    onClick = {
                        updateError = null
                        downloadId = AppUpdateManager.enqueueDownload(context, githubUpdate!!)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("İMZALI APK'Yİ GÜNCELLE • ${githubUpdate!!.versionName}") }
            } else if (!BuildConfig.DEBUG && !BuildConfig.PLAY_DISTRIBUTION && updateError != null && downloadId == null) {
                OutlinedButton(
                    onClick = checkGitHubUpdates,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("GÜNCELLEMEYİ TEKRAR DENE") }
            }
            if (downloadId != null) LinearProgressIndicator({ downloadPercent / 100f }, Modifier.fillMaxWidth())
            updateError?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 11.sp) }
            Text("Parolalar ve profiller cihazda şifreli saklanır. Private key GitHub'a gönderilmez.", color = AppMuted, fontSize = 10.sp)
        }
    }

    if (showEditor) {
        ProfileEditor(
            initial = editingProfile,
            keyManager = keyManager,
            onDismiss = { showEditor = false },
            onSave = { profile ->
                if (store.save(profile)) {
                    profiles = store.all()
                    showEditor = false
                } else Toast.makeText(context, "Profil güvenli şekilde kaydedilemedi.", Toast.LENGTH_LONG).show()
            }
        )
    }

    deleteCandidate?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Sunucu silinsin mi?") },
            text = { Text("${profile.name} profili ve bu profile ait şifrelenmiş anahtar cihazdan silinecek.") },
            confirmButton = {
                TextButton(onClick = {
                    if (store.delete(profile.id)) profiles = store.all()
                    else Toast.makeText(context, "Profil silinemedi.", Toast.LENGTH_LONG).show()
                    deleteCandidate = null
                }) { Text("Sil") }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("İptal") } }
        )
    }
}

@Composable
private fun ProfileEditor(
    initial: SshServerProfile?,
    keyManager: SshKeyImportManager,
    onDismiss: () -> Unit,
    onSave: (SshServerProfile) -> Unit
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var host by remember(initial?.id) { mutableStateOf(initial?.host.orEmpty()) }
    var port by remember(initial?.id) { mutableStateOf(initial?.port?.toString() ?: "22") }
    var username by remember(initial?.id) { mutableStateOf(initial?.username.orEmpty()) }
    var description by remember(initial?.id) { mutableStateOf(initial?.description.orEmpty()) }
    var password by remember(initial?.id) { mutableStateOf(initial?.password.orEmpty()) }
    var method by remember(initial?.id) { mutableStateOf(initial?.authenticationMethod ?: AuthenticationMethod.PRIVATE_KEY) }
    var cloudflare by remember(initial?.id) { mutableStateOf(initial?.useCloudflareAccess ?: false) }
    var error by remember(initial?.id) { mutableStateOf<String?>(null) }
    val profileId = remember(initial?.id) { initial?.id ?: java.util.UUID.randomUUID().toString() }
    var keyReady by remember(initial?.id) {
        mutableStateOf(initial?.let { keyManager.hasPrivateKey(it.id) } ?: false)
    }
    val context = LocalContext.current
    val privateKeyPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            keyManager.importFromUri(uri, profileId)
                .onSuccess {
                    keyReady = true
                    error = null
                    Toast.makeText(context, "Private key güvenli şekilde kaydedildi.", Toast.LENGTH_SHORT).show()
                }
                .onFailure {
                    keyReady = false
                    error = it.message ?: "Private key içe aktarılamadı."
                }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "SSH sunucusu ekle" else "Sunucuyu düzenle") },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("Sunucu adı") }, singleLine = true)
                OutlinedTextField(host, { host = it }, label = { Text("Host / IP") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, label = { Text("Port") }, modifier = Modifier.weight(0.7f), singleLine = true)
                    OutlinedTextField(username, { username = it }, label = { Text("Kullanıcı adı") }, modifier = Modifier.weight(1.3f), singleLine = true)
                }
                OutlinedTextField(description, { description = it }, label = { Text("Açıklama (isteğe bağlı)") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { method = AuthenticationMethod.PRIVATE_KEY }) { Text("Private key") }
                    TextButton(onClick = { method = AuthenticationMethod.PASSWORD }) { Text("Parola") }
                }
                if (method == AuthenticationMethod.PASSWORD) {
                    OutlinedTextField(password, { password = it }, label = { Text("SSH parolası") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { privateKeyPicker.launch(arrayOf("*/*")) }) {
                            Text(if (keyReady) "Private key değiştir" else "Private key dosyası seç")
                        }
                        Text(
                            if (keyReady) "Anahtar hazır" else "Anahtar seçilmedi",
                            color = if (keyReady) AppGreen else AppMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = cloudflare, onCheckedChange = { cloudflare = it })
                    Text("Cloudflare Access proxy kullan", color = AppMuted, fontSize = 12.sp)
                }
                error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 11.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsedPort = port.toIntOrNull()
                error = when {
                    name.isBlank() -> "Sunucu adı gerekli."
                    host.isBlank() || host.any(Char::isWhitespace) -> "Geçerli bir host/IP girin."
                    parsedPort == null || parsedPort !in 1..65535 -> "Port 1–65535 arasında olmalı."
                    username.isBlank() -> "Kullanıcı adı gerekli."
                    method == AuthenticationMethod.PASSWORD && password.isBlank() -> "SSH parolası gerekli."
                    method == AuthenticationMethod.PRIVATE_KEY && !keyReady -> "Devam etmek için private key dosyası seçin."
                    else -> null
                }
                if (error == null) onSave(
                    SshServerProfile(
                        id = profileId,
                        name = name.trim(),
                        host = host.trim(),
                        port = parsedPort!!,
                        username = username.trim(),
                        authenticationMethod = method,
                        password = password,
                        description = description.trim(),
                        useCloudflareAccess = cloudflare
                    )
                )
            }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } },
        containerColor = AppPanel
    )
}

private fun openApkInstaller(context: android.content.Context, apkUri: Uri) {
    val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
        data = apkUri
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
    }
    context.startActivity(intent)
}
