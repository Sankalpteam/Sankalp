package com.indoone.menu.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.indoone.menu.data.UserDataRepository
import kotlinx.coroutines.launch

private data class MemoryItem(val key: String, val value: String)

// A memory is stored under its key, and the key is a storage detail -
// "favourite_colour". The person reading the list did not type that, so the
// label is the key made readable. The key itself is untouched.
private fun memoryLabel(key: String): String {
    val words = key.replace(Regex("[_-]+"), " ").trim()

    return words.ifBlank { key }.replaceFirstChar { it.uppercase() }
}

@Composable
fun MemoryMenuScreen(onBack: () -> Unit) {
    val repository = remember { UserDataRepository() }
    val scope = rememberCoroutineScope()
    var memories by remember { mutableStateOf<List<MemoryItem>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var newKey by remember { mutableStateOf("") }
    var newValue by remember { mutableStateOf("") }

    fun reload() {
        scope.launch {
            runCatching { repository.readMemory() }
                .onSuccess { raw ->
                    memories = raw.mapNotNull { (key, value) ->
                        val map = value as? Map<*, *> ?: return@mapNotNull null
                        val text = map["value"]?.toString().orEmpty()
                        if (text.isBlank()) null else MemoryItem(key, text)
                    }.sortedBy { it.key }
                    status = null
                }
                .onFailure { status = it.message ?: "Could not load memory." }
        }
    }

    LaunchedEffect(Unit) { reload() }

    Surface(Modifier.fillMaxSize(), color = Color.White) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
                Text(
                    "Memory",
                    color = Color(0xFF211B29),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )

                // How much is remembered, at a glance.
                if (memories.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.padding(start = 10.dp),
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFFF0E7FF),
                    ) {
                        Text(
                            "${memories.size} saved",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            color = Color(0xFF5E2DD2),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFFF8F3FF),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFE9E0FF),
                    ) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.Memory,
                                contentDescription = null,
                                tint = Color(0xFF6B3FD1),
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                    ) {
                        Text(
                            "Saved memories",
                            color = Color(0xFF201B28),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Manage the information you want Sankalp to remember.",
                            modifier = Modifier.padding(top = 3.dp),
                            color = Color(0xFF6E6777),
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                shape = RoundedCornerShape(15.dp),
                color = Color(0xFFF6F0FF),
            ) {
                TextButton(
                    onClick = { showAdd = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, tint = Color(0xFF703BE2))
                    Spacer(Modifier.size(7.dp))
                    Text(
                        "Add memory",
                        color = Color(0xFF703BE2),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            status?.let {
                Text(
                    it,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 3.dp),
                    color = Color(0xFFD93025),
                    fontSize = 12.sp,
                )
            }

            if (memories.isEmpty() && status == null) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = Color(0xFF9A91A6),
                    )
                    Text(
                        "No memories saved yet.",
                        modifier = Modifier.padding(top = 10.dp),
                        color = Color(0xFF4F4857),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 5.dp, bottom = 18.dp),
                ) {
                    items(memories, key = { it.key }) { memory ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            shadowElevation = 1.dp,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 13.dp, bottom = 13.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        memoryLabel(memory.key),
                                        color = Color(0xFF6B3FD1),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        memory.value,
                                        color = Color(0xFF29232F),
                                        fontSize = 14.sp,
                                        lineHeight = 19.sp,
                                        modifier = Modifier.padding(top = 5.dp),
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            runCatching { repository.deleteMemory(memory.key) }
                                                .onSuccess { reload() }
                                                .onFailure {
                                                    status = it.message ?: "Could not delete memory."
                                                }
                                        }
                                    },
                                ) {
                                    Icon(
                                        Icons.Outlined.DeleteOutline,
                                        contentDescription = "Delete memory",
                                        tint = Color(0xFFD93025),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add memory") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newKey,
                        onValueChange = { newKey = it },
                        label = { Text("Key") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = newValue,
                        onValueChange = { newValue = it },
                        label = { Text("Value") },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            runCatching { repository.setMemory(newKey, newValue) }
                                .onSuccess {
                                    showAdd = false
                                    newKey = ""
                                    newValue = ""
                                    reload()
                                }
                                .onFailure {
                                    status = it.message ?: "Could not save memory."
                                }
                        }
                    },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } },
        )
    }
}
