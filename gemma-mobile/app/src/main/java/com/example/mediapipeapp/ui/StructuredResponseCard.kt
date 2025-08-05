package com.example.mediapipeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun StructuredResponseCard(
    data: Map<String, Any>,
    onDismiss: () -> Unit
) {
    var showDialog by remember { mutableStateOf(true) }

    if (showDialog) {
        Dialog(onDismissRequest = { /* Prevent dismissing by clicking outside */ }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column {
                    Text(
                        text = "Extracted Genomic Data",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                    Divider()
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(data.entries.toList()) { (key, value) ->
                            DataRow(entryKey = key, entryValue = value)
                        }
                    }
                    Divider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {
                            showDialog = false
                            onDismiss()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            showDialog = false
                            onDismiss()
                        }) {
                            Icon(Icons.Default.Check, contentDescription = "Accept")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Accept")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DataRow(entryKey: String, entryValue: Any?) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = formatKey(entryKey),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = formatValue(entryValue),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

private fun formatKey(key: String): String {
    return key.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

@Suppress("UNCHECKED_CAST")
private fun formatValue(value: Any?): String {
    return when (value) {
        is String -> value.ifEmpty { "N/A" }
        is List<*> -> if (value.isNotEmpty()) value.joinToString(", ") else "N/A"
        is Map<*, *> -> (value as Map<String, Any>).entries.joinToString("\n") { (k, v) ->
            "${formatKey(k)}: ${formatValue(v)}"
        }
        null -> "N/A"
        else -> value.toString()
    }
}
