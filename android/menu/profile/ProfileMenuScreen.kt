package com.indoone.menu.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.google.firebase.auth.FirebaseAuth
import com.indoone.menu.data.UserDataRepository
import kotlinx.coroutines.launch

private val LabelColor = Color(0xFF77717F)
private val ValueColor = Color(0xFF201B28)
// The brand purple. This screen used to be blue (#2168D6), which nothing else
// in the app is - the drawer, Settings, the welcome and Plugins are all purple.
private val AccentColor = Color(0xFF703BE2)

@Composable
fun ProfileMenuScreen(onBack: () -> Unit) {
    val repository = remember { UserDataRepository() }
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var profession by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { repository.readProfile() }
            .onSuccess { profile ->
                name = profile["name"]?.toString().orEmpty()
                nickname = profile["nickname"]?.toString().orEmpty()
                profession = profile["profession"]?.toString().orEmpty()
            }
            .onFailure { status = it.message ?: "Could not load profile." }
    }

    val displayName = name.trim().ifBlank { nickname.trim() }.ifBlank { "Sankalp User" }
    val initials = displayName
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "I" }

    fun save() {
        if (saving) return
        saving = true
        scope.launch {
            status = "Saving…"
            runCatching {
                repository.updateProfileFields(name, nickname, profession)
            }.onSuccess {
                // Best effort: the backend then knows the name, so a reply can use it.
                runCatching { repository.publishNameToBackend(name, nickname) }
                status = "Profile saved."
                editing = false
            }.onFailure {
                status = it.message ?: "Could not save profile."
            }
            saving = false
        }
    }

    Surface(Modifier.fillMaxSize(), color = Color.White) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
                Text(
                    "Profile",
                    color = Color(0xFF211B29),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.size(8.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFF8F3FF),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        modifier = Modifier.size(76.dp),
                        shape = CircleShape,
                        color = Color(0xFFF0E7FF),
                    ) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                initials,
                                color = AccentColor,
                                fontSize = 25.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Text(
                        displayName,
                        modifier = Modifier.padding(top = 10.dp),
                        color = ValueColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    // The account's own email, which is what the web's hero shows.
                    // With no email it keeps the line it had.
                    Text(
                        FirebaseAuth.getInstance().currentUser?.email
                            ?.takeIf { it.isNotBlank() }
                            ?: "Your Sankalp profile",
                        modifier = Modifier.padding(top = 3.dp),
                        color = LabelColor,
                        fontSize = 12.sp,
                    )
                }
            }

            // The heading keeps the edit control on its right: one round button that
            // opens editing, and turns into the save button once editing is open.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Personal details",
                    modifier = Modifier.weight(1f),
                    color = ValueColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (editing) {
                    RoundActionButton(
                        icon = Icons.Outlined.Close,
                        description = "Cancel editing",
                        tint = Color(0xFF6E6777),
                    ) {
                        status = null
                        editing = false
                        // Reload the saved values, so a cancelled edit is discarded.
                        scope.launch {
                            runCatching { repository.readProfile() }
                                .onSuccess { profile ->
                                    name = profile["name"]?.toString().orEmpty()
                                    nickname = profile["nickname"]?.toString().orEmpty()
                                    profession = profile["profession"]?.toString().orEmpty()
                                }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    RoundActionButton(
                        icon = Icons.Outlined.Check,
                        description = "Save profile",
                        tint = AccentColor,
                    ) { save() }
                } else {
                    RoundActionButton(
                        icon = Icons.Outlined.Edit,
                        description = "Edit profile",
                        tint = AccentColor,
                    ) {
                        status = null
                        editing = true
                    }
                }
            }

            if (editing) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; status = null },
                    label = { Text("Name") },
                    leadingIcon = { Icon(Icons.Outlined.PersonOutline, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Spacer(Modifier.size(10.dp))

                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it; status = null },
                    label = { Text("Nickname") },
                    leadingIcon = { Icon(Icons.Outlined.PersonOutline, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Spacer(Modifier.size(10.dp))

                OutlinedTextField(
                    value = profession,
                    onValueChange = { profession = it; status = null },
                    label = { Text("Profession") },
                    leadingIcon = { Icon(Icons.Outlined.WorkOutline, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFFF7F5FB),
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        DetailRow(Icons.Outlined.PersonOutline, "Name", name)
                        Spacer(Modifier.size(12.dp))
                        DetailRow(Icons.Outlined.PersonOutline, "Nickname", nickname)
                        Spacer(Modifier.size(12.dp))
                        DetailRow(Icons.Outlined.WorkOutline, "Profession", profession)
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                shape = RoundedCornerShape(15.dp),
                color = Color(0xFFF9F7FC),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.PersonOutline,
                        contentDescription = null,
                        tint = Color(0xFF6B3FD1),
                    )
                    Text(
                        "These profile fields stay under your control.",
                        modifier = Modifier.padding(start = 10.dp),
                        color = Color(0xFF6E6777),
                        fontSize = 12.sp,
                    )
                }
            }

            status?.let {
                Text(
                    it,
                    modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
                    color = if (it == "Profile saved.") Color(0xFF287A46) else Color(0xFFD93025),
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun RoundActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.size(36.dp),
        shape = CircleShape,
        color = Color(0xFFEFEBF8),
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
            Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF756D80), modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = LabelColor, fontSize = 11.sp, lineHeight = 14.sp)
            Text(
                value.trim().ifBlank { "Not set" },
                modifier = Modifier.padding(top = 2.dp),
                color = ValueColor,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
