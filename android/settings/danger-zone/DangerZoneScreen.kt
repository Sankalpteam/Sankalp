package com.indoone.settings.dangerzone

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.indoone.authentication.AuthSessionStore
import com.indoone.menu.AppChevron
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

private enum class DangerView { ROOT, ACCOUNT_DELETE }

@Composable
fun DangerZoneScreen(
    onBack: () -> Unit,
    onAccountsClick: () -> Unit,
    onLobbyClick: () -> Unit,
    onVibeClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    var view by remember { mutableStateOf(DangerView.ROOT) }
    when (view) {
        DangerView.ROOT -> DangerZoneRoot(onBack, { view = DangerView.ACCOUNT_DELETE })
        DangerView.ACCOUNT_DELETE -> DeleteAccountScreen({ view = DangerView.ROOT }, onAccountsClick)
    }
    @Suppress("UNUSED_VARIABLE")
    val keepNavigationContract = onLobbyClick to onVibeClick to onSettingsClick
}

@Composable
private fun DangerZoneRoot(onBack: () -> Unit, onDeleteAccount: () -> Unit) {
    DangerModalShell(onBack) {
        Text("This permanently removes your Sankalp account, your cloud data, and everything Sankalp keeps on this device. Continue only when you are sure.", color = Color(0xFF8A8492), fontSize = 12.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(12.dp))
        DangerRow("Delete Sankalp account", "Permanently delete your Sankalp account, cloud data and local files", onDeleteAccount)
    }
}

@Composable
private fun DangerRow(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0xFFECE9F0)),
        shadowElevation = 2.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 15.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3A3442))
                Spacer(Modifier.height(3.dp))
                Text(subtitle, fontSize = 11.sp, lineHeight = 16.sp, color = Color(0xFF8A8492))
            }
            AppChevron()
        }
    }
}

@Composable
private fun DeleteAccountScreen(onBack: () -> Unit, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseDatabase.getInstance().reference }
    val user = auth.currentUser
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }

    if (user == null) {
        DangerModalShell(onBack, "Delete Sankalp account?") {
            Text("Please login first.", color = Color(0xFF8A8492), fontSize = 12.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Close", fontWeight = FontWeight.Bold, color = Color(0xFF6330DB)) }
        }
        return
    }

    DangerModalShell(onBack, "Delete Sankalp account?") {
        Text("This permanently deletes your Sankalp account, your cloud data and everything stored on this device. This action cannot be undone.", color = Color(0xFF8A8492), fontSize = 12.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(password, { password = it }, label = { Text("ACCOUNT PASSWORD") }, placeholder = { Text("Enter your password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !working, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(confirmation, { confirmation = it.uppercase() }, label = { Text("TYPE DELETE TO CONFIRM") }, placeholder = { Text("DELETE") }, singleLine = true, enabled = !working, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
        status?.let { Spacer(Modifier.height(10.dp)); Text(it, color = Color(0xFFB42318), fontSize = 12.sp, lineHeight = 18.sp) }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = {
                if (working) return@Button
                if (password.isBlank()) { status = "Enter your account password"; return@Button }
                if (confirmation.trim().uppercase() != "DELETE") { status = "Type DELETE to confirm"; return@Button }
                val email = user.email
                if (email.isNullOrBlank()) { status = "This account cannot be re-authenticated here."; return@Button }
                working = true; status = "Verifying your account…"
                deleteAccount(context, auth, db, password, { status = it }, { working = false; onDeleted() }, { working = false; status = it })
            },
            enabled = !working,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB42318)),
        ) { Text("Delete Sankalp account", fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack, enabled = !working, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Cancel", fontWeight = FontWeight.Bold, color = Color(0xFF6330DB)) }
    }
}

@Composable
private fun DangerModalShell(onBack: () -> Unit, title: String? = null, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x5519141F))
            .clickable(onClick = onBack),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp)
                .clickable(onClick = {}),
            color = Color.White,
            shadowElevation = 14.dp,
            shape = RoundedCornerShape(25.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 21.dp, vertical = 20.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title ?: "Danger Zone",
                        Modifier.weight(1f),
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E1A22),
                    )
                    Box(
                        Modifier
                            .size(35.dp)
                            .background(Color(0xFFF5F2F8), RoundedCornerShape(11.dp))
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("×", fontSize = 23.sp, lineHeight = 23.sp, color = Color(0xFF5D5666))
                    }
                }
                Spacer(Modifier.height(14.dp))
                content()
            }
        }
    }
}

/**
 * Wipes everything Sankalp keeps on this device, permanently — nothing goes to the
 * Trash on the way out. The file and database names mirror the stores that own
 * them: accounts.enc and trash.enc in EncryptedAccountRepository, last-crash.txt in
 * CrashLogStore, indoone_notification_history.db in NotificationRepository.
 */
private fun clearLocalData(context: Context) {
    // The account vault, and the trash that holds accounts deleted earlier.
    context.filesDir.resolve("accounts.enc").delete()
    context.filesDir.resolve("trash.enc").delete()
    // The crash log.
    context.filesDir.resolve("last-crash.txt").delete()
    // Every preference file the app writes.
    listOf(
        "indoone_app_lock",
        "indoone_biometric_unlock",
        "indoone_auto_lock",
        "indoone_notifications",
        "indoone_auth_session",
    ).forEach { name ->
        context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().apply()
    }
    runCatching { AuthSessionStore(context).clear() }
    // The notification history database.
    context.deleteDatabase("indoone_notification_history.db")
    // Caches: a downloaded app update, and photos taken for chat.
    runCatching { context.cacheDir.resolve("updates").deleteRecursively() }
    runCatching { context.cacheDir.resolve("camera").deleteRecursively() }
}

private fun deleteAccount(
    context: Context,
    auth: FirebaseAuth,
    db: com.google.firebase.database.DatabaseReference,
    password: String,
    onStatus: (String) -> Unit,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
) {
    val user = auth.currentUser ?: run { onError("Login session expired. Please login again."); return }
    val email = user.email ?: run { onError("This account cannot be re-authenticated here."); return }

    user.reauthenticate(EmailAuthProvider.getCredential(email, password))
        .addOnSuccessListener {
            user.reload().addOnSuccessListener {
                onStatus("Removing your Sankalp cloud data…")
                val uid = user.uid
                db.child("users").child(uid).child("profile").get()
                    .addOnCompleteListener { profileTask ->
                        // The mobile is only needed to clear the shared mobile index, so a
                        // failed read is not fatal.
                        val mobile = if (profileTask.isSuccessful) {
                            profileTask.result?.child("mobile")?.getValue(String::class.java)?.trim().orEmpty()
                        } else {
                            ""
                        }
                        // Fire and forget. This is cleanup of other nodes and must never
                        // hold up the deletion itself.
                        sweepDuplicateAccounts(db, uid, email)
                        removeOwnCloudData(context, auth, db, uid, mobile, onStatus, onSuccess, onError)
                    }
            }.addOnFailureListener { e -> onError(formatAuthError(e.code, e.message ?: "Account verification failed.")) }
        }
        .addOnFailureListener { e -> onError(formatAuthError(e.code, e.message ?: "Account verification failed.")) }
}

/**
 * Removes the signed-in user's own cloud data, then the Firebase account, then the
 * local files. The order matters: once the account is gone the client has no
 * permission to write anything, so the data has to go first.
 *
 * Only paths this user is allowed to write are in the update — their own users node
 * and their own mobile-index entry. Other users' nodes used to be mixed into the same
 * atomic update, which made the whole thing fail with a permission error, and the
 * deletion never ran.
 */
private fun removeOwnCloudData(
    context: Context,
    auth: FirebaseAuth,
    db: com.google.firebase.database.DatabaseReference,
    uid: String,
    mobile: String,
    onStatus: (String) -> Unit,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
) {
    val updates = hashMapOf<String, Any?>()
    updates["users/$uid"] = null
    if (mobile.isNotBlank()) {
        updates["mobileIndex/${java.net.URLEncoder.encode(mobile, Charsets.UTF_8.name())}"] = null
    }
    db.updateChildren(updates)
        .addOnSuccessListener {
            onStatus("Deleting your Sankalp account…")
            val current = auth.currentUser
            if (current == null) {
                clearLocalData(context)
                onSuccess()
            } else {
                current.delete()
                    .addOnSuccessListener { clearLocalData(context); onSuccess() }
                    .addOnFailureListener { e -> onError(formatAuthError(e.code, e.message ?: "Account deletion failed.")) }
            }
        }
        .addOnFailureListener { e -> onError(e.message ?: "The cloud refused the deletion. Please try again, or contact support.") }
}

/**
 * Best effort: clears any other users node that carries the same email. That needs a
 * query on users ordered by profile/email, so the rules must carry
 * ".indexOn": "profile/email" — without it the query is refused outright. Either way
 * the outcome is ignored: this is cleanup, and it must not be able to block a
 * deletion the way it used to.
 */
private fun sweepDuplicateAccounts(
    db: com.google.firebase.database.DatabaseReference,
    uid: String,
    email: String,
) {
    runCatching {
        db.child("users")
            .orderByChild("profile/email")
            .equalTo(email.trim().lowercase())
            .get()
            .addOnSuccessListener { matches ->
                val updates = hashMapOf<String, Any?>()
                matches.children.forEach { child ->
                    val key = child.key
                    if (key != null && key != uid) updates["users/$key"] = null
                }
                if (updates.isNotEmpty()) db.updateChildren(updates)
            }
    }
}

private fun formatAuthError(code: String, fallback: String): String = when (code) {
    "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" -> "Incorrect account password."
    "ERROR_USER_MISMATCH" -> "The account credentials do not match this Sankalp account."
    "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Please try again later."
    "ERROR_NETWORK_REQUEST_FAILED" -> "Network error. Check your connection and try again."
    else -> fallback
}
