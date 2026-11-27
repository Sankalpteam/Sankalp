package com.indoone.authentication

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.indoone.MainActivity
import com.indoone.authentication.login.LoginScreen
import com.indoone.authentication.signup.SignUpScreen
import kotlinx.coroutines.launch

enum class AuthBusyAction {
    SEND_OTP,
    PASSWORD_LOGIN,
    CREATE_ACCOUNT,
    VERIFY_OTP,
    RESEND_OTP,
}

class AuthActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val session = AuthSessionStore(applicationContext)
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null && session.isOtpVerified()) {
            openMain()
            return
        }

        setContent {
            MaterialTheme {
                var showingSignup by remember { mutableStateOf(false) }
                var busyAction by remember { mutableStateOf<AuthBusyAction?>(null) }
                var otpVisible by remember { mutableStateOf(false) }
                var otpEmail by remember { mutableStateOf("") }
                var status by remember { mutableStateOf("") }
                var error by remember { mutableStateOf("") }
                val scope = rememberCoroutineScope()
                val service = remember { IndooneAuthService(auth, FirebaseDatabase.getInstance()) }

                LaunchedEffect(showingSignup) {
                    busyAction = null
                    otpVisible = false
                    otpEmail = ""
                    status = ""
                    error = ""
                }

                val busy = busyAction != null

                // The system back button did nothing here. AuthActivity is the launcher
                // activity, so back closed the app - from the OTP step, with a code already
                // sent and about to be typed, the whole flow was gone. The screen has its own
                // back arrow; this makes the gesture and the hardware key do the same thing,
                // and only leaves the app from the first step.
                BackHandler(enabled = true) {
                    when {
                        busy -> Unit
                        otpVisible -> {
                            otpVisible = false
                            otpEmail = ""
                            status = ""
                            error = ""
                        }
                        showingSignup -> showingSignup = false
                        else -> finish()
                    }
                }

                if (showingSignup) {
                    SignUpScreen(
                        busy = busy,
                        busyAction = busyAction,
                        status = status,
                        error = error,
                        otpVisible = otpVisible,
                        otpEmail = otpEmail,
                        onSendOtp = { email, mobile, password ->
                            busyAction = AuthBusyAction.SEND_OTP
                            error = ""
                            scope.launch {
                                runCatching { service.startSignup(email, mobile, password) }
                                    .onSuccess { destination ->
                                        otpEmail = destination
                                        otpVisible = true
                                        status = ""
                                    }
                                    .onFailure { error = it.message ?: "Could not start signup." }
                                busyAction = null
                            }
                        },
                        onCreateWithPassword = { email, mobile, password ->
                            busyAction = AuthBusyAction.CREATE_ACCOUNT
                            error = ""
                            scope.launch {
                                runCatching { service.createSignupWithPassword(email, mobile, password) }
                                    .onSuccess { uid ->
                                        if (uid.isBlank()) {
                                            error = "The account was created, but the login " +
                                                "session could not be created. Please sign in."
                                        } else {
                                            session.setVerified(uid)
                                            // The tick was made before there was a uid to
                                            // write it against; this is the first moment
                                            // there is one. Off the main thread, because
                                            // Consent.record waits on a Firebase write and
                                            // waiting on the main thread is an ANR.
                                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                Consent.record(
                                                    FirebaseDatabase.getInstance(),
                                                    uid,
                                                    ageConfirmed = true,
                                                )
                                            }
                                            openMain()
                                        }
                                    }
                                    .onFailure { error = it.message ?: "Could not create account." }
                                busyAction = null
                            }
                        },
                        onVerifyOtp = { otp ->
                            busyAction = AuthBusyAction.VERIFY_OTP
                            error = ""
                            scope.launch {
                                runCatching { service.verifySignupOtp(otp) }
                                    .onSuccess { uid ->
                                        if (uid.isBlank()) {
                                            error = "The account was created, but the login " +
                                                "session could not be created. Please sign in."
                                        } else {
                                            session.setVerified(uid)
                                            // The tick was made before there was a uid to
                                            // write it against; this is the first moment
                                            // there is one. Off the main thread, because
                                            // Consent.record waits on a Firebase write and
                                            // waiting on the main thread is an ANR.
                                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                Consent.record(
                                                    FirebaseDatabase.getInstance(),
                                                    uid,
                                                    ageConfirmed = true,
                                                )
                                            }
                                            openMain()
                                        }
                                    }
                                    .onFailure { error = it.message ?: "Could not create account." }
                                busyAction = null
                            }
                        },
                        onResendOtp = {
                            busyAction = AuthBusyAction.RESEND_OTP
                            error = ""
                            scope.launch {
                                runCatching { service.resendSignupOtp() }
                                    .onSuccess { destination ->
                                        otpEmail = destination
                                        status = "New OTP sent to your email."
                                        Toast.makeText(this@AuthActivity, "New OTP sent to your email.", Toast.LENGTH_SHORT).show()
                                    }
                                    .onFailure { error = it.message ?: "Could not resend OTP." }
                                busyAction = null
                            }
                        },
                        onLogin = { showingSignup = false },
                    )
                } else {
                    LoginScreen(
                        busy = busy,
                        busyAction = busyAction,
                        status = status,
                        error = error,
                        otpVisible = otpVisible,
                        otpEmail = otpEmail,
                        onSendOtp = { identifier ->
                            busyAction = AuthBusyAction.SEND_OTP
                            error = ""
                            scope.launch {
                                runCatching { service.startLoginOtp(identifier) }
                                    .onSuccess { destination ->
                                        otpEmail = destination
                                        otpVisible = true
                                        status = ""
                                    }
                                    .onFailure { error = it.message ?: "Could not send OTP." }
                                busyAction = null
                            }
                        },
                        onBackToLogin = {
                            otpVisible = false
                            otpEmail = ""
                            status = ""
                            error = ""
                        },
                        onPasswordLogin = { identifier, password ->
                            busyAction = AuthBusyAction.PASSWORD_LOGIN
                            error = ""
                            scope.launch {
                                runCatching { service.loginWithPassword(identifier, password) }
                                    .onSuccess { uid ->
                                        if (uid.isBlank()) {
                                            error = "Login failed. Please try again."
                                        } else {
                                            session.setVerified(uid)
                                            openMain()
                                        }
                                    }
                                    .onFailure { error = it.message ?: "Login failed." }
                                busyAction = null
                            }
                        },
                        onVerifyOtp = { otp ->
                            busyAction = AuthBusyAction.VERIFY_OTP
                            error = ""
                            scope.launch {
                                runCatching { service.verifyLoginOtp(otp) }
                                    .onSuccess {
                                        // The code was verified; the session is a second thing
                                        // that can fail on its own. Opening the app without
                                        // one showed the chat screen to a client Firebase did
                                        // not recognise, so every call in it was refused and
                                        // the next launch asked for a code again. The web
                                        // already refused to continue here.
                                        val uid = auth.currentUser?.uid
                                        if (uid.isNullOrBlank()) {
                                            error = "OTP was verified, but the login session " +
                                                "could not be created. Please try again."
                                        } else {
                                            session.setVerified(uid)
                                            openMain()
                                        }
                                    }
                                    .onFailure { error = it.message ?: "Login failed." }
                                busyAction = null
                            }
                        },
                        onResendOtp = {
                            busyAction = AuthBusyAction.RESEND_OTP
                            error = ""
                            scope.launch {
                                runCatching { service.resendLoginOtp() }
                                    .onSuccess { destination ->
                                        otpEmail = destination
                                        status = "New OTP sent to your email."
                                        Toast.makeText(this@AuthActivity, "New OTP sent to your email.", Toast.LENGTH_SHORT).show()
                                    }
                                    .onFailure { error = it.message ?: "Could not resend OTP." }
                                busyAction = null
                            }
                        },
                        onCreateAccount = { showingSignup = true },
                    )
                }
            }
        }
    }

    private fun openMain() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        finish()
    }
}
