package com.dincer552.hattrickssh

import android.content.Intent
import android.os.Bundle
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
