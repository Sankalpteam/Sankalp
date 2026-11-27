package com.indoone.settings.profile

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ServerValue
import com.google.firebase.database.DatabaseReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProfileViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference
    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    init { loadProfile() }

    fun loadProfile() {
        val user = auth.currentUser
        _state.value = _state.value.copy(
            email = user?.email ?: "Email not available",
            mobile = "Mobile number not set",
            loading = user != null,
            error = null,
            message = null,
        )
        if (user == null) return
        database.child("users").child(user.uid).child("profile").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val mobile = normalizeMobile(snapshot.child("mobile").getValue(String::class.java) ?: "")
                _state.value = _state.value.copy(
                    email = user.email ?: snapshot.child("email").getValue(String::class.java) ?: "Email not available",
                    mobile = mobile.ifBlank { "Mobile number not set" },
                    loading = false,
                )
            }
            override fun onCancelled(error: DatabaseError) {
                _state.value = _state.value.copy(loading = false, error = "Mobile number not available")
            }
        })
    }

    fun clearFeedback() { _state.value = _state.value.copy(error = null, message = null) }

    /**
     * Changes the mobile number, but only after the current password is verified.
     *
     * The number is now a separate value from the one on the account: the screen shows
     * the number already saved and this takes the new one. Nothing is written until
     * reauthentication succeeds.
     */
    fun updateMobile(rawValue: String, password: String, onFinished: () -> Unit = {}) {
        val user = auth.currentUser ?: run {
            _state.value = _state.value.copy(error = "Please sign in again before changing your mobile number.")
            return
        }
        val value = normalizeMobile(rawValue)
        if (!Regex("^\\+91\\d{10}$").matches(value)) {
            _state.value = _state.value.copy(error = "Enter a valid 10-digit Indian mobile number.")
            return
        }
        if (password.isBlank()) {
            _state.value = _state.value.copy(error = "Enter your current password")
            return
        }
        val oldMobile = normalizeMobile(_state.value.mobile.takeIf { !it.contains("not set", true) }.orEmpty())
        if (value == oldMobile) {
            _state.value = _state.value.copy(error = "Enter a different mobile number.")
            return
        }
        _state.value = _state.value.copy(busy = true, error = null, message = null)
        val credential = EmailAuthProvider.getCredential(user.email ?: "", password)
        user.reauthenticate(credential).addOnCompleteListener { reauth ->
            if (!reauth.isSuccessful) {
                _state.value = _state.value.copy(busy = false, error = mapAuthError(reauth.exception, "Could not verify your password."))
                onFinished()
                return@addOnCompleteListener
            }
            val candidates = listOf(value, value.filter(Char::isDigit), value.removePrefix("+"), value.removePrefix("+91")).distinct()
            checkMobileCandidates(candidates, user.uid, 0) { error ->
                if (error != null) {
                    _state.value = _state.value.copy(busy = false, error = error)
                    onFinished()
                    return@checkMobileCandidates
                }
                val now = ServerValue.TIMESTAMP
                val updates = hashMapOf<String, Any?>(
                    "users/${user.uid}/profile/mobile" to value,
                    "users/${user.uid}/profile/uid" to user.uid,
                    "users/${user.uid}/profile/email" to (user.email ?: ""),
                    "users/${user.uid}/profile/updatedAt" to now,
                    "mobileIndex/${encode(value)}" to mapOf("uid" to user.uid, "email" to (user.email ?: ""), "updatedAt" to now),
                )
                database.updateChildren(updates).addOnCompleteListener { task ->
                    _state.value = if (task.isSuccessful) {
                        _state.value.copy(busy = false, mobile = value, message = "Mobile number updated.")
                    } else {
                        _state.value.copy(busy = false, error = task.exception?.message ?: "Could not change mobile number.")
                    }
                    // The old entry is cleared afterwards and on its own. In the same update
                    // it decides whether the change happens at all: a stale entry under the
                    // old number that names another uid is refused by the rules, and that
                    // refusal takes the profile change with it.
                    if (task.isSuccessful && oldMobile.isNotBlank() && oldMobile != value) {
                        database.child("mobileIndex").child(encode(oldMobile)).removeValue()
                    }
                    onFinished()
                }
            }
        }
    }

    private fun checkMobileCandidates(candidates: List<String>, uid: String, index: Int, done: (String?) -> Unit) {
        if (index >= candidates.size) { done(null); return }
        database.child("mobileIndex").child(encode(candidates[index])).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val existingUid = snapshot.child("uid").getValue(String::class.java)
                val existingEmail = snapshot.child("email").getValue(String::class.java)
                if ((!existingUid.isNullOrBlank() && existingUid != uid) || (existingUid.isNullOrBlank() && !existingEmail.isNullOrBlank())) {
                    done("That mobile number is already linked to another account.")
                } else {
                    checkMobileCandidates(candidates, uid, index + 1, done)
                }
            }
            override fun onCancelled(error: DatabaseError) { done(error.message.ifBlank { "Could not validate mobile number." }) }
        })
    }

    fun updateEmail(rawEmail: String, password: String, onFinished: () -> Unit = {}) {
        val user = auth.currentUser ?: run {
            _state.value = _state.value.copy(error = "Please sign in again before changing your email.")
            return
        }
        val value = rawEmail.trim().lowercase()
        if (!Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(value)) {
            _state.value = _state.value.copy(error = "Enter a valid email address.")
            return
        }
        if (password.isBlank()) {
            _state.value = _state.value.copy(error = "Enter your current password")
            return
        }
        if (value == user.email?.trim()?.lowercase()) {
            _state.value = _state.value.copy(error = "Enter a different email address")
            return
        }
        _state.value = _state.value.copy(busy = true, error = null, message = null)
        val credential = EmailAuthProvider.getCredential(user.email ?: "", password)
        user.reauthenticate(credential).addOnCompleteListener { reauth ->
            if (!reauth.isSuccessful) {
                finishEmail(false, mapAuthError(reauth.exception, "Could not change email"), onFinished)
                return@addOnCompleteListener
            }
            user.updateEmail(value).addOnCompleteListener { update ->
                if (!update.isSuccessful) {
                    finishEmail(false, mapAuthError(update.exception, "Could not change email"), onFinished)
                    return@addOnCompleteListener
                }
                val profile = database.child("users").child(user.uid).child("profile")
                profile.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val updates = hashMapOf<String, Any?>(
                            "email" to value,
                            "uid" to user.uid,
                            "updatedAt" to ServerValue.TIMESTAMP,
                        )
                        val mobile = normalizeMobile(snapshot.child("mobile").getValue(String::class.java) ?: "")
                        if (mobile.isNotBlank()) {
                            updates["mobile"] = mobile
                            database.child("mobileIndex").child(encode(mobile)).setValue(mapOf("uid" to user.uid, "email" to value, "updatedAt" to ServerValue.TIMESTAMP))
                        }
                        profile.updateChildren(updates).addOnCompleteListener {
                            user.sendEmailVerification().addOnCompleteListener {
                                _state.value = _state.value.copy(busy = false, email = value, message = "Email changed. A verification email was sent.")
                                onFinished()
                            }
                        }
                    }
                    override fun onCancelled(error: DatabaseError) { finishEmail(false, error.message, onFinished) }
                })
            }
        }
    }

    private fun finishEmail(success: Boolean, message: String?, done: () -> Unit) {
        _state.value = if (success) _state.value.copy(busy = false, message = message) else _state.value.copy(busy = false, error = message ?: "Could not change email")
        done()
    }

    private fun mapAuthError(error: Exception?, fallback: String): String = when ((error as? com.google.firebase.auth.FirebaseAuthException)?.errorCode) {
        "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" -> "Current password is incorrect."
        "ERROR_REQUIRES_RECENT_LOGIN" -> "Please sign in again, then try this change."
        "ERROR_EMAIL_ALREADY_IN_USE" -> "An account already exists with this email."
        "ERROR_INVALID_EMAIL" -> "Enter a valid email address."
        else -> error?.message ?: fallback
    }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

    private fun normalizeMobile(raw: String): String {
        val digits = raw.filter(Char::isDigit)
        return when {
            digits.matches(Regex("91\\d{10}")) -> "+$digits"
            digits.matches(Regex("\\d{10}")) -> "+91$digits"
            else -> raw.replace(Regex("[^0-9+]"), "").replaceFirst("00", "+")
        }
    }
}
