package com.example.mediapipeapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.mediapipeapp.InferenceMode
import com.example.mediapipeapp.wifi.QRCodeScannerDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    inferenceMode: InferenceMode,
    onInferenceModeChange: (InferenceMode) -> Unit,
    desktopServerUrl: String,
    onDesktopServerUrlChange: (String) -> Unit,
    autoDetectDesktop: Boolean,
    onAutoDetectDesktopChange: (Boolean) -> Unit,
    mobileGpuAcceleration: Boolean,
    onMobileGpuAccelerationChange: (Boolean) -> Unit,
    onWifiQrScanned: (String) -> Unit = {}
) {
    var showQrScanner by remember { mutableStateOf(false) }
    var settingsView by remember { mutableStateOf(inferenceMode) }
    if (isVisible) {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Divider()

                    // Inference Mode Section
                    Text(
                        text = "Inference Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            onClick = { settingsView = InferenceMode.MOBILE },
                            label = { Text("📱 Mobile") },
                            selected = settingsView == InferenceMode.MOBILE,
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            onClick = { settingsView = InferenceMode.DESKTOP },
                            label = { Text("🖥️ Desktop") },
                            selected = settingsView == InferenceMode.DESKTOP,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Mobile GPU Acceleration Section
                    if (settingsView == InferenceMode.MOBILE) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "GPU Acceleration",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "May cause crashes on some devices",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )
                                    }
                                    Switch(
                                        checked = mobileGpuAcceleration,
                                        onCheckedChange = onMobileGpuAccelerationChange
                                    )
                                }
                            }
                        }
                    }

                    // Desktop Server Section
                    if (settingsView == InferenceMode.DESKTOP) {
                        Text(
                            text = "Desktop Server",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )

                        OutlinedTextField(
                            value = desktopServerUrl,
                            onValueChange = onDesktopServerUrlChange,
                            label = { Text("Server URL") },
                            placeholder = { Text("http://192.168.0.33:8000") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                    }

                    // Wi-Fi Pairing Section
                    Divider()
                    
                    Text(
                        text = "Wi-Fi Pairing",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Offline Connection",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Connect to desktop server without internet using Wi-Fi hotspot",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            
                            OutlinedButton(
                                onClick = { showQrScanner = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Default.AccountBox,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Scan QR Code")
                            }
                        }
                    }

                    Divider()

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Close")
                        }
                    }
                }
            }
        }
        
        // QR Scanner Dialog
        QRCodeScannerDialog(
            isVisible = showQrScanner,
            onDismiss = { showQrScanner = false },
            onQrCodeDetected = { qrCode ->
                onWifiQrScanned(qrCode)
                showQrScanner = false
            }
        )
    }
}