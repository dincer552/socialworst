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
    var keyId by remember { mutableStateOf("my-app-store") }
    var packageName by remember { mutableStateOf("com.dincer552.hattrickssh") }
    var repository by remember { mutableStateOf("dincer552/socialworst") }
    var keyAlias by remember { mutableStateOf("hattrickssh") }
    var keyPassword by remember { mutableStateOf("") }
    var keystoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var status by remember { mutableStateOf("Mevcut uygulama imzasını korumak için orijinal JKS dosyasını kullanın.") }
    var busy by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        keystoreUri = it
        if (it != null) status = "JKS dosyası seçildi."
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Merkezi APK imzalama") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("JKS dosyası güvenli HTTPS bağlantısıyla imza sunucusuna gönderilir; uygulamanın içine kaydedilmez.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = serviceUrl, onValueChange = { serviceUrl = it },
                    label = { Text("İmza servisi HTTPS adresi") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(
                    value = adminToken, onValueChange = { adminToken = it },
                    label = { Text("Yönetici token") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(value = keyId, onValueChange = { keyId = it },
                    label = { Text("Key ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = packageName, onValueChange = { packageName = it },
                    label = { Text("Android paket adı") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = repository, onValueChange = { repository = it },
                    label = { Text("GitHub deposu (owner/repo)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = keyAlias, onValueChange = { keyAlias = it },
                    label = { Text("JKS alias") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(
                    value = keyPassword, onValueChange = { keyPassword = it },
                    label = { Text("JKS parolası") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Button(onClick = { filePicker.launch(arrayOf("*/*")) }, enabled = !busy) {
                    Text(if (keystoreUri == null) "JKS DOSYASI SEÇ" else "JKS DOSYASI DEĞİŞTİR")
                }
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && keystoreUri != null && serviceUrl.startsWith("https://") &&
                    adminToken.isNotBlank() && keyPassword.isNotBlank(),
                onClick = {
                    val uri = keystoreUri ?: return@Button
                    busy = true
                    status = "JKS güvenli bağlantıyla kaydediliyor…"
                    scope.launch {
                        try {
                            val response = SignerKeyUploader.upload(
                                context, serviceUrl, adminToken, keyId, packageName,
                                repository, keyAlias, keyPassword, uri
                            )
                            SignerSettings.save(context, serviceUrl, adminToken)
                            status = "Anahtar sunucuya kaydedildi: $response"
                            keyPassword = ""
                        } catch (error: Exception) {
                            status = error.message ?: "Anahtar yüklenemedi."
                        } finally {
                            busy = false
                        }
                    }
                }
            ) { Text(if (busy) "KAYDEDİLİYOR…" else "ANAHTARI SUNUCUYA KAYDET") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("KAPAT") } },
        shape = RoundedCornerShape(18.dp)
    )
}
