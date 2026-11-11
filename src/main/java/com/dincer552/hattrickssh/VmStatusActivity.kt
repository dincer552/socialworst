package com.dincer552.hattrickssh

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.dincer552.hattrickssh.security.SshKeyImportManager
import com.dincer552.hattrickssh.ssh.AuthenticationMethod
import com.dincer552.hattrickssh.ssh.SshCallback
import com.dincer552.hattrickssh.ssh.SshConnectionManager
import com.dincer552.hattrickssh.ssh.SshServerProfile
import com.dincer552.hattrickssh.ssh.SshServerProfileStore
import com.jcraft.jsch.JSchException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale

private val VmBackground = Color(0xFF080D12)
private val VmPanel = Color(0xFF121B24)
private val VmGreen = Color(0xFF38D98A)
private val VmMuted = Color(0xFF91A1B2)
private val VmRed = Color(0xFFFF8A80)

private data class VmMetrics(
    val hostname: String,
    val cpuPercent: Float,
    val cpuCores: Int,
    val memoryTotal: Long,
    val memoryUsed: Long,
    val diskTotal: Long,
    val diskUsed: Long,
    val uptimeSeconds: Long,
    val loadAverage: String,
    val rxBytes: Long,
    val txBytes: Long,
    val rxPerSecond: Double,
    val txPerSecond: Double
)

class VmStatusActivity : ComponentActivity(), SshCallback {

    private lateinit var profile: SshServerProfile
    private lateinit var sshManager: SshConnectionManager

    private var connectionStatus by mutableStateOf("SSH bağlantısı hazırlanıyor…")
    private var isConnected by mutableStateOf(false)
    private var isRefreshing by mutableStateOf(false)
    private var autoRefresh by mutableStateOf(true)
    private var metrics by mutableStateOf<VmMetrics?>(null)
    private var metricsError by mutableStateOf<String?>(null)
    private var lastUpdated by mutableStateOf("Henüz ölçüm alınmadı")
    private var lastSampleAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
        profile = profileId?.let { SshServerProfileStore(applicationContext).find(it) } ?: run {
            finish()
            return
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = VmGreen,
                    onPrimary = Color(0xFF06130C),
                    background = VmBackground,
                    surface = VmPanel,
                    onSurface = Color.White
                )
            ) {
                VmDashboard(
                    profile = profile,
                    connectionStatus = connectionStatus,
                    isConnected = isConnected,
                    isRefreshing = isRefreshing,
                    autoRefresh = autoRefresh,
                    metrics = metrics,
                    metricsError = metricsError,
                    lastUpdated = lastUpdated,
                    onRefresh = ::refreshMetrics,
                    onAutoRefreshChange = { autoRefresh = it },
                    onDisconnect = { finish() }
                )
            }
        }

        if (profile.authenticationMethod == AuthenticationMethod.PRIVATE_KEY &&
            !SshKeyImportManager(applicationContext).hasPrivateKey(profile.id)
        ) {
            connectionStatus = "SSH anahtarı bulunamadı"
            metricsError = "Bu sunucu profili için önce private key seçmelisin."
            return
        }

        connectToServer()
    }

    private fun connectToServer() {
        connectionStatus = "SSH / Cloudflare bağlantısı kuruluyor…"
        sshManager = SshConnectionManager(applicationContext)
        sshManager.connect(profile, this)
    }

    private fun refreshMetrics() {
        if (!isConnected || isRefreshing || !::sshManager.isInitialized) return
        isRefreshing = true
        metricsError = null
        sshManager.executeCommand(metricsCommand()) { result ->
            runOnUiThread {
                isRefreshing = false
                result.onSuccess { output ->
                    try {
                        val json = JSONObject(output.trim())
                        val now = System.currentTimeMillis()
                        val previous = metrics
                        val elapsed = if (lastSampleAt > 0L) (now - lastSampleAt) / 1000.0 else 0.0
                        val rx = json.getLong("rx_bytes")
                        val tx = json.getLong("tx_bytes")
                        val rxRate = if (previous != null && elapsed > 0.0 && rx >= previous.rxBytes) {
                            (rx - previous.rxBytes) / elapsed
                        } else 0.0
                        val txRate = if (previous != null && elapsed > 0.0 && tx >= previous.txBytes) {
                            (tx - previous.txBytes) / elapsed
                        } else 0.0

                        metrics = VmMetrics(
                            hostname = json.optString("hostname", profile.host),
                            cpuPercent = json.getDouble("cpu_percent").toFloat().coerceIn(0f, 100f),
                            cpuCores = json.optInt("cpu_cores", 1).coerceAtLeast(1),
                            memoryTotal = json.getLong("memory_total"),
                            memoryUsed = json.getLong("memory_used"),
                            diskTotal = json.getLong("disk_total"),
                            diskUsed = json.getLong("disk_used"),
                            uptimeSeconds = json.getLong("uptime_seconds"),
                            loadAverage = json.getString("load_average"),
                            rxBytes = rx,
                            txBytes = tx,
                            rxPerSecond = rxRate,
                            txPerSecond = txRate
                        )
                        lastSampleAt = now
                        lastUpdated = "Son güncelleme: " +
                            java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(java.util.Date(now))
                    } catch (error: Exception) {
                        metricsError = "Sunucu verileri okunamadı: ${error.message ?: "geçersiz yanıt"}"
                    }
                }.onFailure { error ->
                    metricsError = error.message ?: "VM bilgileri alınamadı."
                }
            }
        }
    }

    override fun onConnecting() {
        runOnUiThread { connectionStatus = "SSH / Cloudflare bağlantısı kuruluyor…" }
    }

    override fun onConnected() {
        runOnUiThread {
            isConnected = true
            connectionStatus = "Bağlı • ${profile.username}@${profile.host}"
            refreshMetrics()
        }
    }

    override fun onDisconnected() {
        runOnUiThread {
            isConnected = false
            if (!isFinishing) connectionStatus = "SSH bağlantısı kesildi"
        }
    }

    override fun onError(message: String) {
        runOnUiThread {
            isConnected = false
            connectionStatus = "Bağlantı hatası"
            metricsError = message
        }
    }

    override fun onAuthRequired(uri: Uri) {
        runOnUiThread {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
                connectionStatus = "Cloudflare girişini tarayıcıda tamamla"
            } catch (_: Exception) {
                metricsError = "Cloudflare giriş bağlantısı açılamadı."
            }
        }
    }

    override fun onHostKeyVerification(message: String, answer: (Boolean) -> Unit) {
        runOnUiThread {
            val changed = message.contains("REMOTE HOST IDENTIFICATION HAS CHANGED", ignoreCase = true) ||
                message.contains("host key has been changed", ignoreCase = true)
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(if (changed) "SSH sunucu anahtarı değişti" else "İlk SSH sunucu doğrulaması")
                .setMessage(
                    "$message\n\n" + if (changed) {
                        "Güvenlik nedeniyle değişmiş anahtar kabul edilmeyecek."
                    } else {
                        "Parmak izini Azure VM'in gerçek SSH fingerprint değeriyle karşılaştırmadan kabul etme."
                    }
                )
                .setNegativeButton(if (changed) "Kapat" else "Reddet") { _, _ -> answer(false) }
                .setOnCancelListener { answer(false) }
                .apply {
                    if (!changed) setPositiveButton("Parmak izini kaydet") { _, _ -> answer(true) }
                }
                .show()
        }
    }

    override fun onDiagnostic(message: String) {
        // Connection diagnostics are intentionally not shown in the monitoring dashboard.
    }

    override fun onDestroy() {
        if (::sshManager.isInitialized) sshManager.disconnect()
        super.onDestroy()
    }

    private fun metricsCommand(): String {
        val encoded = Base64.encodeToString(PYTHON_METRICS.trimIndent().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "python3 -c \"import base64;exec(base64.b64decode('$encoded'))\""
    }

    companion object {
        const val EXTRA_PROFILE_ID = "com.dincer552.hattrickssh.EXTRA_VM_PROFILE_ID"

        private val PYTHON_METRICS = """
            import json, os, socket, time
            def cpu_values():
                return list(map(int, open('/proc/stat').readline().split()[1:]))
            a = cpu_values()
            time.sleep(0.35)
            b = cpu_values()
            total_delta = sum(b) - sum(a)
            idle_a = a[3] + (a[4] if len(a) > 4 else 0)
            idle_b = b[3] + (b[4] if len(b) > 4 else 0)
            cpu_percent = 0.0 if total_delta <= 0 else (1.0 - (idle_b - idle_a) / total_delta) * 100.0
            memory = {}
            for line in open('/proc/meminfo'):
                parts = line.split()
                if len(parts) >= 2 and parts[0].rstrip(':') in ('MemTotal', 'MemAvailable'):
                    memory[parts[0].rstrip(':')] = int(parts[1]) * 1024
            memory_total = memory.get('MemTotal', 0)
            memory_used = max(0, memory_total - memory.get('MemAvailable', memory_total))
            disk = os.statvfs('/')
            disk_total = disk.f_blocks * disk.f_frsize
            disk_free = disk.f_bavail * disk.f_frsize
            rx_bytes = 0
            tx_bytes = 0
            for line in open('/proc/net/dev').readlines()[2:]:
                name, values = line.split(':', 1)
                if name.strip() != 'lo':
                    fields = list(map(int, values.split()))
                    rx_bytes += fields[0]
                    tx_bytes += fields[8]
            load_average = ' '.join(open('/proc/loadavg').read().split()[:3])
            uptime_seconds = int(float(open('/proc/uptime').read().split()[0]))
            print(json.dumps({
                'hostname': socket.gethostname(),
                'cpu_percent': round(max(0.0, min(100.0, cpu_percent)), 1),
                'cpu_cores': os.cpu_count() or 1,
                'memory_total': memory_total,
                'memory_used': memory_used,
                'disk_total': disk_total,
                'disk_used': max(0, disk_total - disk_free),
                'uptime_seconds': uptime_seconds,
                'load_average': load_average,
                'rx_bytes': rx_bytes,
                'tx_bytes': tx_bytes
            }))
        """.trimIndent()
    }
}

@androidx.compose.runtime.Composable
private fun VmDashboard(
    profile: SshServerProfile,
    connectionStatus: String,
    isConnected: Boolean,
    isRefreshing: Boolean,
    autoRefresh: Boolean,
    metrics: VmMetrics?,
    metricsError: String?,
    lastUpdated: String,
    onRefresh: () -> Unit,
    onAutoRefreshChange: (Boolean) -> Unit,
    onDisconnect: () -> Unit
) {
    LaunchedEffect(isConnected, autoRefresh) {
        while (isConnected && autoRefresh) {
            delay(15_000)
            onRefresh()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VmBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("VM DURUMU", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(profile.name, color = VmMuted, fontSize = 12.sp)
            }
            Text(
                if (isConnected) "● BAĞLI" else "● BAĞLANIYOR",
                color = if (isConnected) VmGreen else Color(0xFFFFC56D),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = VmPanel),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(metrics?.hostname ?: profile.host, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(connectionStatus, color = VmMuted, fontSize = 11.sp)
                if (metrics != null) {
                    Text("${metrics.cpuCores} vCPU  •  Load ${metrics.loadAverage}", color = VmGreen, fontSize = 11.sp)
                    Text("Çalışma süresi: ${formatUptime(metrics.uptimeSeconds)}", color = VmMuted, fontSize = 11.sp)
                }
            }
        }

        if (metrics == null && isConnected && isRefreshing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = VmGreen)
            Text("Sunucu bilgileri okunuyor…", color = VmMuted, fontSize = 12.sp)
        }

        if (metrics != null) {
            MetricCard(
                title = "İŞLEMCİ (CPU)",
                value = "${metrics.cpuPercent.toInt()}%",
                detail = "${metrics.cpuCores} sanal işlemci",
                progress = metrics.cpuPercent / 100f
            )
            MetricCard(
                title = "BELLEK (RAM)",
                value = "${formatBytes(metrics.memoryUsed)} / ${formatBytes(metrics.memoryTotal)}",
                detail = "${percent(metrics.memoryUsed, metrics.memoryTotal)}% kullanılıyor",
                progress = ratio(metrics.memoryUsed, metrics.memoryTotal)
            )
            MetricCard(
                title = "DİSK KAPASİTESİ  •  /",
                value = "${formatBytes(metrics.diskUsed)} / ${formatBytes(metrics.diskTotal)}",
                detail = "${percent(metrics.diskUsed, metrics.diskTotal)}% kullanılıyor",
                progress = ratio(metrics.diskUsed, metrics.diskTotal)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = VmPanel),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AĞ TRAFİĞİ", color = VmMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("↓ İndirme", color = Color.White, fontSize = 12.sp)
                        Text("${formatRate(metrics.rxPerSecond)}", color = VmGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("↑ Gönderme", color = Color.White, fontSize = 12.sp)
                        Text("${formatRate(metrics.txPerSecond)}", color = VmGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text("Hız değerleri ölçümler arasındaki farktan hesaplanır.", color = VmMuted, fontSize = 10.sp)
                }
            }
        }

        metricsError?.let {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF321D22))) {
                Text(it, modifier = Modifier.padding(12.dp), color = VmRed, fontSize = 11.sp)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Otomatik yenileme", color = Color.White, fontSize = 12.sp)
                Text("15 saniyede bir", color = VmMuted, fontSize = 10.sp)
            }
            Switch(checked = autoRefresh, onCheckedChange = onAutoRefreshChange, enabled = isConnected)
        }

        Text(lastUpdated, color = VmMuted, fontSize = 10.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onRefresh,
                enabled = isConnected && !isRefreshing,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isRefreshing) "OKUNUYOR…" else "YENİLE")
            }
            OutlinedButton(onClick = onDisconnect, modifier = Modifier.weight(1f)) {
                Text("KAPAT")
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@androidx.compose.runtime.Composable
private fun MetricCard(title: String, value: String, detail: String, progress: Float) {
    val safeProgress = progress.coerceIn(0f, 1f)
    val color = when {
        safeProgress >= 0.90f -> VmRed
        safeProgress >= 0.75f -> Color(0xFFFFC56D)
        else -> VmGreen
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VmPanel),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, color = VmMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(
                progress = { safeProgress },
                modifier = Modifier.fillMaxWidth().height(7.dp),
                color = color,
                trackColor = Color(0xFF263442)
            )
            Text(detail, color = VmMuted, fontSize = 11.sp)
        }
    }
}

private fun formatBytes(bytes: Long): String =
    String.format(Locale.getDefault(), "%.1f GiB", bytes.toDouble() / (1024.0 * 1024.0 * 1024.0))

private fun formatRate(bytesPerSecond: Double): String =
    String.format(Locale.getDefault(), "%.2f MiB/s", bytesPerSecond / (1024.0 * 1024.0))

private fun ratio(used: Long, total: Long): Float =
    if (total <= 0L) 0f else (used.toDouble() / total).toFloat().coerceIn(0f, 1f)

private fun percent(used: Long, total: Long): Int = (ratio(used, total) * 100).toInt()

private fun formatUptime(seconds: Long): String {
    val days = seconds / 86_400
    val hours = (seconds % 86_400) / 3_600
    val minutes = (seconds % 3_600) / 60
    return if (days > 0) "${days}g ${hours}sa ${minutes}dk"
    else "${hours}sa ${minutes}dk"
}
