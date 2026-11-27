package com.indoone.authentication.login

import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.indoone.authentication.AuthBrand
import com.indoone.authentication.AuthBusyAction
import com.indoone.authentication.ConsentRow
import com.indoone.authentication.AuthFieldLabel
import com.indoone.authentication.AuthPage
import com.indoone.authentication.AuthPrimaryButton
import com.indoone.authentication.AuthSecondaryButton
import com.indoone.authentication.AuthStatus
import com.indoone.authentication.AuthTextField

private enum class LoginPage {
    LOGIN,
    OTP,
    PASSWORD,
}

@Composable
fun LoginScreen(
    busy: Boolean,
    busyAction: AuthBusyAction?,
    status: String,
    error: String,
    otpVisible: Boolean,
    otpEmail: String,
    onSendOtp: (identifier: String) -> Unit,
    onPasswordLogin: (identifier: String, password: String) -> Unit,
    onVerifyOtp: (otp: String) -> Unit,
    onResendOtp: () -> Unit,
    onBackToLogin: () -> Unit,
    onCreateAccount: () -> Unit,
) {
    var identifier by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var otp by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    // The same agreement the signup screen asks for. A person coming back to an account they
    // already have still has to accept the terms they are signing in under.
    var agreed by rememberSaveable { mutableStateOf(false) }
    var openDocument by rememberSaveable { mutableStateOf("") }
    var page by rememberSaveable { mutableStateOf(LoginPage.LOGIN) }

    val otpFocusRequester = FocusRequester()

    // While a document is open, back closes it rather than leaving the form behind it.
    androidx.activity.compose.BackHandler(enabled = openDocument.isNotEmpty()) {
        openDocument = ""
    }

    // The same live pages the signup screen and the menu open, not copies.
    when (openDocument) {
        "terms" -> com.indoone.menu.legal.LegalWebScreen(
            title = "Terms of Use",
            path = "/legal/terms-of-use.html",
            assetFile = "legal/terms-of-use.html",
            onBack = { openDocument = "" },
        )
        "privacy" -> com.indoone.menu.legal.LegalWebScreen(
            title = "Privacy Policy",
            path = "/legal/privacy-policy.html",
            assetFile = "legal/privacy-policy.html",
            onBack = { openDocument = "" },
        )
        else -> Unit
    }

    LaunchedEffect(otpVisible) {
        if (otpVisible) {
            page = LoginPage.OTP
            otpFocusRequester.requestFocus()
        } else if (page == LoginPage.OTP) {
            page = LoginPage.LOGIN
        }
    }

    AuthPage(showCard = false) {
        PageSwitch(
            page = page,
            pageContent = { currentPage ->
                AuthBrand()
                Spacer(Modifier.height(24.dp))

                when (currentPage) {
                    LoginPage.LOGIN -> {
                        LoginEntryPage(
                            identifier = identifier,
                            busy = busy,
                            actionEnabled = !busy && identifier.isNotBlank(),
                            // The web caps this at 320; nothing capped it here, so a paste
                            // went to Firebase whole. The longest legal address is 254.
                            onIdentifierChanged = { identifier = it.take(MAX_IDENTIFIER_LENGTH) },
                            onSendOtp = { onSendOtp(identifier) },
                            onContinueWithPassword = { page = LoginPage.PASSWORD },
                            onCreateAccount = onCreateAccount,
                        )
                    }

                    LoginPage.OTP -> {
                        LoginOtpPage(
                            busy = busy,
                            busyAction = busyAction,
                            otpEmail = otpEmail,
                            otp = otp,
                            status = status,
                            error = error,
                            otpFocusRequester = otpFocusRequester,
                            onOtpChanged = { otp = it.filter(Char::isDigit).take(6) },
                            onVerifyOtp = { onVerifyOtp(otp) },
                            onResendOtp = onResendOtp,
                            onBack = onBackToLogin,
                        )
                    }

                    LoginPage.PASSWORD -> {
                        LoginPasswordPage(
                            identifier = identifier,
                            password = password,
                            busy = busy,
                            busyAction = busyAction,
                            passwordVisible = passwordVisible,
                            // The web caps this at 320; nothing capped it here, so a paste
                            // went to Firebase whole. The longest legal address is 254.
                            onIdentifierChanged = { identifier = it.take(MAX_IDENTIFIER_LENGTH) },
                            onPasswordChanged = { password = it.take(MAX_PASSWORD_LENGTH) },
                            onPasswordVisibilityChanged = { passwordVisible = !passwordVisible },
                            onCompleteLogin = { onPasswordLogin(identifier, password) },
                            onBack = { page = LoginPage.LOGIN },
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                LoginPageIndicator(currentPage = page)
            },
        )

        AuthStatus(error, error = true)
    }
}

@Composable
private fun PageSwitch(
    page: LoginPage,
    pageContent: @Composable androidx.compose.foundation.layout.ColumnScope.(LoginPage) -> Unit,
) {
    // A plain crossfade between the login pages. This used to turn the card over in
    // 3D like a book page, which read as the screen spinning rather than as a step
    // forward, so it is a straight switch now.
    Crossfade(
        targetState = page,
        animationSpec = tween(durationMillis = 220),
        label = "login_page_switch",
    ) { visiblePage ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 14.dp,
                    shape = RoundedCornerShape(18.dp),
                    clip = false,
                )
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFFFDF7),
                            Color(0xFFF7F0E3),
                        ),
                    ),
                    shape = RoundedCornerShape(18.dp),
                )
                .border(
                    width = 1.dp,
                    color = Color(0xFFD8CBB8),
                    shape = RoundedCornerShape(18.dp),
                )
                .padding(horizontal = 22.dp, vertical = 24.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                pageContent(visiblePage)
            }
        }
    }
}

@Composable
private fun LoginEntryPage(
    identifier: String,
    busy: Boolean,
    actionEnabled: Boolean,
    onIdentifierChanged: (String) -> Unit,
    onSendOtp: () -> Unit,
    onContinueWithPassword: () -> Unit,
    onCreateAccount: () -> Unit,
) {
    var welcomeTextIndex by rememberSaveable { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(2000)
            welcomeTextIndex = if (welcomeTextIndex == 0) 1 else 0
        }
    }

    androidx.compose.animation.AnimatedContent(
        targetState = welcomeTextIndex,
        transitionSpec = {
            (
                fadeIn(animationSpec = tween(350)) +
                    slideInHorizontally(
                        animationSpec = tween(350),
                        initialOffsetX = { width -> width / 4 },
                    )
            ).togetherWith(
                fadeOut(animationSpec = tween(250)) +
                    slideOutHorizontally(
                        animationSpec = tween(250),
                        targetOffsetX = { width -> -width / 4 },
                    )
            )
        },
        label = "login_welcome_text",
    ) { index ->
        Text(
            text = buildAnnotatedString {
                withStyle(
                    if (index == 1) {
                        SpanStyle(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF6330DB),
                                    Color(0xFF9147ED),
                                ),
                            ),
                        )
                    } else {
                        SpanStyle(color = Color(0xFF17151D))
                    }
                ) {
                    append(
                        if (index == 0) {
                            "Welcome to your Sankalp account"
                        } else {
                            "Continue with your Sankalp account"
                        }
                    )
                }
            },
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.7).sp,
            lineHeight = 36.sp,
        )
    }

    Spacer(Modifier.height(20.dp))
    AuthFieldLabel("EMAIL OR MOBILE NUMBER")
    val mobileOnly = identifier.isNotBlank() && identifier.none(Char::isLetter) && identifier.none { it == '@' }

    AuthTextField(
        value = identifier,
        onValueChange = onIdentifierChanged,
        placeholder = "you@example.com or 98765 43210",
        enabled = !busy,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Next,
        ),
        leadingContent = if (mobileOnly) {
            { Text("+91", fontSize = 13.sp) }
        } else null,
    )

    Spacer(Modifier.height(10.dp))
    ConsentRow(
        agreed = agreed,
        onAgreedChange = { agreed = it },
        onOpenTerms = { openDocument = "terms" },
        onOpenPrivacy = { openDocument = "privacy" },
        enabled = !busy,
    )

    Spacer(Modifier.height(18.dp))
    // One under the other, full width. Side by side they had to shrink their own labels to
    // 12sp to fit a phone, and the second one still ran to the edges of its button.
    AuthPrimaryButton(
        text = if (busy && actionEnabled) "Sending…" else "Continue with OTP",
        enabled = actionEnabled && agreed,
        onClick = onSendOtp,
    )

    Spacer(Modifier.height(8.dp))
    AuthPrimaryButton(
        text = "Continue with Password",
        enabled = actionEnabled && agreed,
        onClick = onContinueWithPassword,
    )

    Spacer(Modifier.height(18.dp))
    AuthSecondaryButton(
        text = "Create Account",
        enabled = !busy,
        onClick = onCreateAccount,
        textColor = Color(0xFF2E7D32),
    )
}

/** The wait between OTP sends. The web uses the same number, in login.js. */
private const val RESEND_COOLDOWN_SECONDS = 60

/** The longest address RFC 5321 allows, and the same cap the web puts on the field. */
private const val MAX_IDENTIFIER_LENGTH = 320

/** Long enough for any passphrase a person will type, short enough to not be a payload. */
private const val MAX_PASSWORD_LENGTH = 128

@Composable
private fun LoginOtpPage(
    busy: Boolean,
    busyAction: AuthBusyAction?,
    otpEmail: String,
    otp: String,
    status: String,
    error: String,
    otpFocusRequester: FocusRequester,
    onOtpChanged: (String) -> Unit,
    onVerifyOtp: () -> Unit,
    onResendOtp: () -> Unit,
    onBack: () -> Unit,
) {
    LoginBackButton(onBack = onBack)

    Spacer(Modifier.height(4.dp))
    Text(
        text = "Enter OTP",
        color = Color(0xFF17151D),
        fontSize = 30.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.7).sp,
        lineHeight = 36.sp,
    )

    Spacer(Modifier.height(8.dp))
    Text(
        text = buildAnnotatedString {
            append("We have sent a 6-digit OTP to\n")
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(otpEmail) }
        },
        color = Color(0xFF675C8A),
        fontSize = 14.sp,
        lineHeight = 21.sp,
    )

    Spacer(Modifier.height(22.dp))
    AuthFieldLabel("VERIFICATION OTP")
    AuthTextField(
        value = otp,
        onValueChange = onOtpChanged,
        placeholder = "Enter 6-digit OTP",
        enabled = !busy,
        modifier = Modifier.focusRequester(otpFocusRequester),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
        ),
        // Done submits, on the same terms as the button: six digits in, nothing in flight.
        // Asking the keyboard for Done and ignoring the press left the person with a key that
        // did nothing.
        keyboardActions = KeyboardActions(
            onDone = { if (!busy && otp.length == 6) onVerifyOtp() },
        ),
    )

    Spacer(Modifier.height(16.dp))
    AuthPrimaryButton(
        text = if (busyAction == AuthBusyAction.VERIFY_OTP) "Verifying…" else "Verify & Login",
        enabled = !busy && otp.length == 6 && agreed,
        onClick = onVerifyOtp,
    )

    Spacer(Modifier.height(8.dp))
    // Sixty seconds between sends. Without the wait a person taps Resend three times in ten
    // seconds, Firebase answers TOO_MANY_REQUESTS, and the address is locked out for far
    // longer than a minute. The remaining time is on the button so the wait is visible.
    var resendSecondsLeft by rememberSaveable(otpEmail) {
        mutableStateOf(RESEND_COOLDOWN_SECONDS)
    }
    // Keyed on the status as well as the address: a resend answers with the same address, so
    // otpEmail does not change and the countdown never restarted - the button was ready again
    // the moment the new code went out.
    LaunchedEffect(otpEmail, status) {
        // Arriving here means an OTP was just sent, so the clock starts now.
        resendSecondsLeft = RESEND_COOLDOWN_SECONDS
        while (resendSecondsLeft > 0) {
            kotlinx.coroutines.delay(1_000)
            resendSecondsLeft -= 1
        }
    }
    AuthSecondaryButton(
        text = when {
            busyAction == AuthBusyAction.RESEND_OTP -> "Sending…"
            resendSecondsLeft > 0 -> "Resend in ${resendSecondsLeft}s"
            else -> "Resend OTP"
        },
        enabled = !busy && resendSecondsLeft == 0,
        onClick = onResendOtp,
    )

    AuthStatus(status, error = false)
    AuthStatus(error, error = true)
}

@Composable
private fun LoginPasswordPage(
    identifier: String,
    password: String,
    busy: Boolean,
    busyAction: AuthBusyAction?,
    passwordVisible: Boolean,
    onIdentifierChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onPasswordVisibilityChanged: () -> Unit,
    onCompleteLogin: () -> Unit,
    onBack: () -> Unit,
) {
    LoginBackButton(onBack = onBack)

    Spacer(Modifier.height(4.dp))
    Text(
        text = "Enter Password",
        color = Color(0xFF17151D),
        fontSize = 30.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.7).sp,
        lineHeight = 36.sp,
    )

    Spacer(Modifier.height(6.dp))
    Text(
        text = buildAnnotatedString {
            withStyle(
                SpanStyle(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF6330DB),
                            Color(0xFF9147ED),
                        ),
                    ),
                )
            ) {
                append("Continue with your Sankalp account")
            }
        },
        fontSize = 14.sp,
        lineHeight = 21.sp,
    )

    Spacer(Modifier.height(20.dp))
    AuthFieldLabel("EMAIL OR MOBILE NUMBER")
    AuthTextField(
        value = identifier,
        onValueChange = onIdentifierChanged,
        placeholder = "you@example.com or 98765 43210",
        enabled = !busy,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
    )

    Spacer(Modifier.height(14.dp))
    AuthFieldLabel("PASSWORD")
    AuthTextField(
        value = password,
        onValueChange = onPasswordChanged,
        placeholder = "Enter your password",
        enabled = !busy,
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingContent = {
            TextButton(
                onClick = onPasswordVisibilityChanged,
                enabled = !busy,
            ) {
                Text(if (passwordVisible) "◌" else "◉", fontSize = 17.sp)
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        // Done on the password field submits, on the same terms as the button below it.
        keyboardActions = KeyboardActions(
            onDone = {
                if (!busy && password.isNotBlank() && identifier.isNotBlank()) {
                    onCompleteLogin()
                }
            },
        ),
    )

    Spacer(Modifier.height(18.dp))
    AuthPrimaryButton(
        text = if (busyAction == AuthBusyAction.PASSWORD_LOGIN) "Logging in…" else "Complete Login",
        enabled = !busy && password.isNotBlank() && identifier.isNotBlank(),
        onClick = onCompleteLogin,
    )
}

@Composable
private fun LoginBackButton(onBack: () -> Unit) {
    TextButton(onClick = onBack) {
        Text("← Back", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LoginPageIndicator(currentPage: LoginPage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LoginPageDot(active = currentPage == LoginPage.LOGIN)
        Spacer(Modifier.size(7.dp))
        LoginPageDot(active = currentPage == LoginPage.OTP)
        Spacer(Modifier.size(7.dp))
        LoginPageDot(active = currentPage == LoginPage.PASSWORD)
    }
}

@Composable
private fun LoginPageDot(active: Boolean) {
    Spacer(
        modifier = Modifier
            .size(if (active) 9.dp else 7.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(
                if (active) Color(0xFF6330DB) else Color(0xFFD8D1EB)
            )
    )
}
