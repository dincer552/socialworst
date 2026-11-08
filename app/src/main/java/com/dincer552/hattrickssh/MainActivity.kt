package com.dincer552.hattrickssh

import android.os.Bundle
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Background = Color(0xFF090D12)
private val Panel = Color(0xFF111923)
private val Accent = Color(0xFF39D98A)
private val Muted = Color(0xFF8B9AAA)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                HattrickSshApp()
            }
        }
    }
}

@Composable
private fun HattrickSshApp() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "SSH CLIENT",
                        color = Color.White,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Secure server connections",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
                Text("SSH", color = Accent, fontWeight = FontWeight.Bold)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Panel, RoundedCornerShape(14.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text("SSH SERVER", color = Muted, fontSize = 11.sp)
                Text(
                    "No server selected",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text("Add a server profile to connect", color = Muted, fontSize = 12.sp)
                Text("Host • port • username", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "●  Not connected",
                    color = Color(0xFFFFC46B),
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = { /* SSH connection is added in the networking milestone. */ },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = Color(0xFF07130D)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("CONNECT", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = { /* Secure key import is added in the security milestone. */ },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("IMPORT SSH PRIVATE KEY", color = Color.White, fontSize = 12.sp)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF05080B), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "This historical scaffold is not included in the active build.",
                    color = Muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }

            Text(
                "The active Android app is built from src/main.",
                color = Muted,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}
