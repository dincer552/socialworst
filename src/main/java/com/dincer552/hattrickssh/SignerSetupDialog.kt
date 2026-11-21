package com.dincer552.hattrickssh

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun SignerSetupDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var serviceUrl by remember { mutableStateOf(SignerSettings.baseUrl(context)) }
    var adminToken by remember { mutableStateOf(SignerSettings.adminToken(context)) }
    var repositoryUrl by remember { mutableStateOf("") }
    var keyId by remember { mutableStateOf("my-app-store") }
    var packageName by remember { mutableStateOf("com.dincer552.hattrickssh") }
    var keyAlias by remember { mutableStateOf("hattrickssh") }
    var keyPassword by remember { mutableStateOf("") }
    var keystoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var status by remember { mutableStateOf("GitHub repo bağlantısını gir. Kayıtlı anahtar varsa JKS seçmeden bağlanır.") }
    var busy by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        keystoreUri = it
        if (it != null) status = "JKS seçildi. Repo ve anahtar tek işlemde kaydedilecek."
    }

    fun applyKnownRepositoryDefaults(value: String) {
        val cleaned = value.trim().trimEnd('/').removeSuffix(".git").lowercase()
        when (cleaned) {
            "https://github.com/dincer552/socialworst", "dincer552/socialworst" -> {
                keyId = "my-app-store"
                packageName = "com.dincer552.hattrickssh"
                keyAlias = "hattrickssh"
            }
            "https://github.com/dincer552/dyssh", "dincer552/dyssh" -> {
                keyId = "dyssh"
                packageName = "com.dincer552.dyssh"
                keyAlias = "hattrickssh"
            }
            "https://github.com/dincer552/instaviewer", "dincer552/instaviewer" -> {
                keyId = "instaviewer"
                packageName = "com.dincer552.instaviewer"
                keyAlias = "hattrickssh"
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("GitHub deposunu imzalamaya bağla", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Repo linkini yapıştır. VM'de kayıtlı anahtar varsa doğrudan bağlanır; ilk kayıt için JKS'yi bir kez seç.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = serviceUrl, onValueChange = { serviceUrl = it },
                    label = { Text("İmza sunucusu HTTPS adresi") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = adminToken, onValueChange = { adminToken = it },
                    label = { Text("Yönetici token") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = repositoryUrl,
                    onValueChange = { repositoryUrl = it; applyKnownRepositoryDefaults(it) },
                    label = { Text("GitHub repo bağlantısı") },
                    placeholder = { Text("https://github.com/kullanici/proje") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = keyId, onValueChange = { keyId = it },
                    label = { Text("Anahtar profili") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall
                )
                TextButton(
                    onClick = { filePicker.launch(arrayOf("*/*")) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (keystoreUri == null) "İLK KAYIT / FARKLI ANAHTAR İÇİN JKS SEÇ" else "JKS DOSYASI SEÇİLDİ • DEĞİŞTİR")
                }
                if (keystoreUri != null) {
                    OutlinedTextField(
                        value = packageName, onValueChange = { packageName = it },
                        label = { Text("Android paket adı") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = keyAlias, onValueChange = { keyAlias = it },
                        label = { Text("JKS alias") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = keyPassword, onValueChange = { keyPassword = it },
                        label = { Text("JKS / anahtar parolası") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                }
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && serviceUrl.startsWith("https://") &&
                    adminToken.isNotBlank() && repositoryUrl.isNotBlank() &&
                    keyId.matches(Regex("[a-z0-9][a-z0-9._-]{1,63}")) &&
                    (keystoreUri == null || (packageName.isNotBlank() && keyAlias.isNotBlank() && keyPassword.isNotBlank())),
                onClick = {
                    busy = true
                    status = "Repo VM'deki imzalama servisine kaydediliyor…"
                    scope.launch {
                        try {
                            val result = if (keystoreUri != null) {
                                SignerKeyUploader.upload(
                                    context, serviceUrl, adminToken, keyId, packageName,
                                    repositoryUrl, keyAlias, keyPassword, keystoreUri!!
                                )
                            } else {
                                SignerKeyUploader.registerRepository(
                                    serviceUrl, adminToken, keyId, repositoryUrl
                                )
                            }
                            SignerSettings.save(context, serviceUrl, adminToken)
                            status = "İmzalama bağlantısı hazır: $result"
                            keyPassword = ""
                        } catch (error: Exception) {
                            status = error.message ?: "Repo bağlanamadı."
                        } finally {
                            busy = false
                        }
                    }
                }
            ) {
                Text(
                    when {
                        busy -> "BAĞLANIYOR…"
                        keystoreUri != null -> "REPOYU BAĞLA VE JKS'Yİ KAYDET"
                        else -> "REPOYU BAĞLA"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("KAPAT") }
        },
        shape = RoundedCornerShape(18.dp)
    )
}
