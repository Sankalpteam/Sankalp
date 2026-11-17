package com.indoone.authentication

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
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
                                        session.setVerified(uid)
                                        openMain()
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
                                        session.setVerified(uid)
                                        openMain()
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
                                        session.setVerified(uid)
                                        openMain()
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
                                        auth.currentUser?.uid?.let(session::setVerified)
                                        openMain()
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
