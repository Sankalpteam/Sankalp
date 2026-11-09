package com.indoone.authentication

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class IndooneAuthService(
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase,
) {
    private val apiBase = "https://indoverification-production.up.railway.app"
    private val appId = "indoone"

    private var loginPending: LoginPending? = null
    private var signupPending: SignupPending? = null

    suspend fun loginWithPassword(identifier: String, password: String): String {
        val raw = identifier.trim()
        if (raw.isBlank() || password.isBlank()) {
            throw AuthException("Enter your email/mobile number and password.")
        }

        return withContext(Dispatchers.IO) {
            try {
                val mobileIdentity = if (raw.contains('@')) null else resolveMobile(raw)
                val email = mobileIdentity?.email ?: raw.lowercase()
                val user = await(auth.signInWithEmailAndPassword(email, password)).user
                    ?: throw AuthException("Login session expired. Please login again.")
                if (mobileIdentity != null && mobileIdentity.uid.isNotBlank() && mobileIdentity.uid != user.uid) {
                    throw AuthException("This mobile number is not linked to this Sankalp account.")
                }
                user.uid
            } catch (error: Throwable) {
                auth.signOut()
                throw normalizeError(error)
            }
        }
    }

    suspend fun startLoginOtp(identifier: String): String {
        val raw = identifier.trim()
        if (raw.isBlank()) throw AuthException("Enter your email/mobile number.")

        return withContext(Dispatchers.IO) {
            try {
                val email = resolveLoginEmail(raw)
                val result = post("/api/auth/login/request-otp", JSONObject().apply {
                    put("email", email)
                    put("name", "Sankalp user")
                })
                val challengeId = result.optString("challengeId")
                if (challengeId.isBlank()) throw AuthException("OTP service did not return a challenge ID.")
                loginPending = LoginPending(email, challengeId)
                email
            } catch (error: Throwable) {
                throw normalizeError(error)
            }
        }
    }

    suspend fun verifyLoginOtp(otp: String) {
        val pending = loginPending ?: throw AuthException("Please request a new OTP.")
        val code = otp.filter(Char::isDigit)
        if (code.length != 6) throw AuthException("Enter the 6-digit OTP.")

        withContext(Dispatchers.IO) {
            try {
                val result = post("/api/auth/login/verify-otp", JSONObject().apply {
                    put("email", pending.email)
                    put("challengeId", pending.challengeId)
                    put("otp", code)
                    put("name", "Sankalp user")
                })
                if (!result.optBoolean("verified", false)) {
                    throw AuthException(result.optString("error").ifBlank { "OTP verification failed." })
                }

                val customToken = sequenceOf(
                    result.optString("customToken"),
                    result.optString("firebaseCustomToken"),
                    result.optString("firebaseToken"),
                    result.optString("token"),
                ).firstOrNull { it.isNotBlank() }

                val user = if (!customToken.isNullOrBlank()) {
                    await(auth.signInWithCustomToken(customToken)).user
                        ?: throw AuthException("OTP verified, but login session could not be created.")
                } else {
                    auth.currentUser
                        ?: throw AuthException(
                            "OTP was verified, but the verification service did not return a login session."
                        )
                }

                loginPending = null
            } catch (error: Throwable) {
                auth.signOut()
                loginPending = null
                throw normalizeError(error)
            }
        }
    }

    private suspend fun resolveLoginEmail(raw: String): String {
        if (raw.contains('@')) {
            return raw.lowercase()
        }
        return resolveMobile(raw).email
    }

    suspend fun resendLoginOtp(): String {
        val pending = loginPending ?: throw AuthException("Login session expired. Please login again.")
        withContext(Dispatchers.IO) {
            val result = post("/api/auth/resend-otp", JSONObject().apply {
                put("email", pending.email)
                put("purpose", "login")
            })
            val challengeId = result.optString("challengeId")
            if (challengeId.isBlank()) throw AuthException("OTP service did not return a new challenge ID.")
            loginPending = pending.copy(challengeId = challengeId)
        }
        return pending.email
    }

    suspend fun startSignup(emailValue: String, mobileValue: String, password: String): String {
        val email = emailValue.trim().lowercase()
        val mobile = normalizeMobile(mobileValue)
        if (!email.contains('@')) throw AuthException("Enter a valid email address.")
        if (!mobile.matches(Regex("\\+91\\d{10}"))) throw AuthException("Enter a valid 10-digit Indian mobile number.")
        if (password.length < 6) throw AuthException("Password should be at least 6 characters.")

        withContext(Dispatchers.IO) {
            if (identityExists(email, mobile)) throw AuthException("An account already exists with this email or mobile number.")
            val result = post("/api/auth/signup/request-otp", JSONObject().apply {
                put("email", email)
                put("name", "Sankalp user")
            })
            val challengeId = result.optString("challengeId")
            if (challengeId.isBlank()) throw AuthException("OTP service did not return a challenge ID.")
            signupPending = SignupPending(email, mobile, password, challengeId)
        }
        return email
    }

    suspend fun createSignupWithPassword(emailValue: String, mobileValue: String, password: String): String {
        val email = emailValue.trim().lowercase()
        val mobile = normalizeMobile(mobileValue)
        if (!email.contains('@')) throw AuthException("Enter a valid email address.")
        if (!mobile.matches(Regex("\\+91\\d{10}"))) throw AuthException("Enter a valid 10-digit Indian mobile number.")
        if (password.length < 6) throw AuthException("Password should be at least 6 characters.")

        return withContext(Dispatchers.IO) {
            try {
                if (identityExists(email, mobile)) {
                    throw AuthException("An account already exists with this email or mobile number.")
                }
                val user = await(auth.createUserWithEmailAndPassword(email, password)).user
                    ?: throw AuthException("Could not create the Sankalp account.")
                syncProfile(user.uid, email, mobile)
                user.uid
            } catch (error: Throwable) {
                auth.signOut()
                throw normalizeError(error)
            }
        }
    }

    suspend fun resendSignupOtp(): String {
        val pending = signupPending ?: throw AuthException("Signup session expired. Enter your details again.")
        withContext(Dispatchers.IO) {
            val result = post("/api/auth/resend-otp", JSONObject().apply {
                put("email", pending.email)
                put("purpose", "signup")
            })
            val challengeId = result.optString("challengeId")
            if (challengeId.isBlank()) throw AuthException("OTP service did not return a new challenge ID.")
            signupPending = pending.copy(challengeId = challengeId)
        }
        return pending.email
    }

    suspend fun verifySignupOtp(otp: String): String {
        val pending = signupPending ?: throw AuthException("Signup session expired. Enter your details again.")
        val code = otp.filter(Char::isDigit)
        if (code.length != 6) throw AuthException("Enter the 6-digit OTP.")

        return withContext(Dispatchers.IO) {
            try {
                val result = post("/api/auth/signup/verify-otp", JSONObject().apply {
                    put("email", pending.email)
                    put("challengeId", pending.challengeId)
                    put("otp", code)
                    put("name", "Sankalp user")
                })
                if (!result.optBoolean("verified", false)) {
                    throw AuthException(result.optString("error").ifBlank { "OTP verification failed." })
                }
                if (identityExists(pending.email, pending.mobile)) {
                    throw AuthException("An account already exists with this email or mobile number.")
                }
                val user = await(auth.createUserWithEmailAndPassword(pending.email, pending.password)).user
                    ?: throw AuthException("Could not create the Sankalp account.")
                syncProfile(user.uid, pending.email, pending.mobile)
                result.optString("welcomeToken").takeIf { it.isNotBlank() }?.let { welcomeToken ->
                    runCatching {
                        post("/api/auth/signup/welcome", JSONObject().apply {
                            put("email", pending.email)
                            put("welcomeToken", welcomeToken)
                            put("name", "Sankalp user")
                        })
                    }
                }
                signupPending = null
                user.uid
            } catch (error: Throwable) {
                auth.signOut()
                signupPending = null
                throw normalizeError(error)
            }
        }
    }

    private fun identityExists(email: String, mobile: String): Boolean {
        val methods = runCatching { Tasks.await(auth.fetchSignInMethodsForEmail(email)) }.getOrNull()
        if (!methods?.signInMethods.isNullOrEmpty()) return true
        return mobileCandidates(mobile).any { candidate ->
            val key = URLEncoder.encode(candidate, Charsets.UTF_8.name())
            val snapshot = runCatching { Tasks.await(database.reference.child("mobileIndex").child(key).get()) }.getOrNull()
            snapshot?.exists() == true
        }
    }

    private fun syncProfile(uid: String, email: String, mobile: String?) {
        val updates = hashMapOf<String, Any>(
            "uid" to uid,
            "email" to email,
            "updatedAt" to System.currentTimeMillis(),
        )
        if (!mobile.isNullOrBlank()) updates["mobile"] = mobile
        Tasks.await(database.reference.child("users").child(uid).child("profile").updateChildren(updates))
        if (!mobile.isNullOrBlank()) {
            val key = URLEncoder.encode(mobile, Charsets.UTF_8.name())
            Tasks.await(database.reference.child("mobileIndex").child(key).setValue(
                mapOf("uid" to uid, "email" to email, "updatedAt" to System.currentTimeMillis())
            ))
        }
    }

    private fun resolveMobile(value: String): MobileIdentity {
        for (candidate in mobileCandidates(value)) {
            val key = URLEncoder.encode(candidate, Charsets.UTF_8.name())
            val snapshot = Tasks.await(database.reference.child("mobileIndex").child(key).get())
            val node = snapshot.value as? Map<*, *> ?: continue
            val email = node["email"]?.toString()?.trim()?.lowercase().orEmpty()
            val uid = node["uid"]?.toString().orEmpty()
            if (email.isNotBlank()) return MobileIdentity(email, uid)
        }
        throw AuthException("No Indoone account is linked to this mobile number.")
    }

    private fun normalizeMobile(value: String): String {
        val digits = value.filter(Char::isDigit)
        return when {
            digits.matches(Regex("^91\\d{10}$")) -> "+$digits"
            digits.matches(Regex("^\\d{10}$")) -> "+91$digits"
            else -> value.replace(" ", "").replace("-", "")
        }
    }

    private fun mobileCandidates(value: String): List<String> {
        val normalized = normalizeMobile(value)
        val digits = normalized.filter(Char::isDigit)
        val ten = digits.takeLast(10)
        return linkedSetOf(normalized, "+91$ten", ten, "91$ten").filter { it.isNotBlank() }
    }

    private fun post(path: String, body: JSONObject): JSONObject {
        val connection = (URL(apiBase + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 45_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Indo-App-Id", appId)
        }
        return try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val text = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            val result = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (connection.responseCode !in 200..299 || (result.has("ok") && !result.optBoolean("ok", false))) {
                throw AuthException(result.optString("error").ifBlank { "OTP service request failed (${connection.responseCode})." })
            }
            result
        } finally {
            connection.disconnect()
        }
    }

    private fun normalizeError(error: Throwable): Throwable {
        if (error is AuthException) return error
        val message = error.message.orEmpty()
        return when {
            message.contains("INVALID_EMAIL", true) -> AuthException("Enter a valid email address.")
            message.contains("USER_NOT_FOUND", true) -> AuthException("No Indoone account was found.")
            message.contains("WRONG_PASSWORD", true) || message.contains("INVALID_CREDENTIAL", true) -> AuthException("Email or password is incorrect.")
            message.contains("TOO_MANY_REQUESTS", true) -> AuthException("Too many attempts. Please try again later.")
            message.contains("NETWORK_REQUEST_FAILED", true) -> AuthException("Network error. Check your connection and try again.")
            message.contains("PERMISSION_DENIED", true) || message.contains("Permission denied", true) ->
                AuthException("Indoone account data is temporarily unavailable. Please try again or use your email address.")
            message.contains("INVALID_LOGIN_CREDENTIALS", true) -> AuthException("Email or password is incorrect.")
            message.contains("EMAIL_ALREADY_IN_USE", true) -> AuthException("An account already exists with this email.")
            message.contains("WEAK_PASSWORD", true) -> AuthException("Password should be at least 6 characters.")
            error is java.net.SocketTimeoutException -> AuthException("OTP service request timed out. Please try again.")
            else -> AuthException(message.ifBlank { "Authentication failed." })
        }
    }

    private suspend fun <T> await(task: Task<T>): T = withContext(Dispatchers.IO) { Tasks.await(task) }

    data class AuthException(override val message: String) : Exception(message)
    private data class LoginPending(val email: String, val challengeId: String)
    private data class SignupPending(val email: String, val mobile: String, val password: String, val challengeId: String)
    private data class MobileIdentity(val email: String, val uid: String)
}
