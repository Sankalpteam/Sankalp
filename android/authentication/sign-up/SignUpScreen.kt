package com.indoone.authentication.signup

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.indoone.authentication.AuthFieldLabel
import com.indoone.authentication.AuthHeading
import com.indoone.authentication.AuthPage
import com.indoone.authentication.AuthPrimaryButton
import com.indoone.authentication.AuthSecondaryButton
import com.indoone.authentication.AuthStatus
import com.indoone.authentication.AuthTextField
import com.indoone.menu.legal.LegalWebScreen
import kotlinx.coroutines.isActive


/**
 * The agreement, above the buttons that create an account.
 *
 * The two documents open in LegalWebScreen - the same component the menu uses, against the same
 * pages, with the same snapshot bundled in the APK. This screen adds the box and the wording; it
 * does not add a second copy of either document.
 *
 * Fully qualified names throughout, which is how this file already refers to Compose types, and
 * which means no new imports: a duplicate import in a Kotlin file is what turned main red twice
 * today.
 */
@Composable
private fun ConsentRow(
    agreed: Boolean,
    onAgreedChange: (Boolean) -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
    enabled: Boolean,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.Top,
    ) {
        androidx.compose.material3.Checkbox(
            checked = agreed,
            onCheckedChange = onAgreedChange,
            enabled = enabled,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(6.dp))
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(
                text = "I am 18 or older and I agree to the",
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = androidx.compose.ui.graphics.Color(0xFF4A4458),
            )
            androidx.compose.foundation.layout.Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onOpenTerms,
                    enabled = enabled,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Text("Terms of Use", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(" and the ", fontSize = 13.sp, color = androidx.compose.ui.graphics.Color(0xFF4A4458))
                TextButton(
                    onClick = onOpenPrivacy,
                    enabled = enabled,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Text("Privacy Policy", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** The wait between OTP sends, the same number the login screen and the web use. */
private const val SIGNUP_RESEND_COOLDOWN_SECONDS = 60

@Composable
fun SignUpScreen(
    busy: Boolean,
    busyAction: AuthBusyAction?,
    status: String,
    error: String,
    otpVisible: Boolean,
    otpEmail: String,
    onSendOtp: (email: String, mobile: String, password: String, confirm: String) -> Unit,
    onCreateWithPassword: (email: String, mobile: String, password: String, confirm: String) -> Unit,
    onVerifyOtp: (otp: String) -> Unit,
    onResendOtp: () -> Unit,
    onLogin: () -> Unit,
    onBackToDetails: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var mobile by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var otp by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var confirmVisible by rememberSaveable { mutableStateOf(false) }
    // The agreement, and which document is open over this screen. The documents are the ones
    // the menu already shows - LegalWebScreen against the live page, with the bundled snapshot
    // as the offline fallback. Nothing is written a second time here.
    var agreed by rememberSaveable { mutableStateOf(false) }
    var openDocument by rememberSaveable { mutableStateOf("") }
    val otpFocusRequester = FocusRequester()

    val hasMobile = mobile.filter(Char::isDigit).isNotEmpty()
    // Ten digits, not "not blank". The field caps at ten and drops anything that is not a
    // digit, so a single digit used to be enough to create an account whose mobile was
    // "+911" - unreachable by the mobile lookup ever after.
    val actionEnabled = !busy && if (otpVisible) otpEmail.isNotBlank() else
        email.isNotBlank() &&
        mobile.length == 10 &&
        password.length >= 6 &&
        password == confirmPassword &&
        agreed

    // Back closes the document first. Compose gives the press to the innermost enabled
    // handler, so this one wins over the activity's while a page is open - without it, reading
    // the terms and pressing back threw away the half-filled form behind them.
    androidx.activity.compose.BackHandler(enabled = openDocument.isNotEmpty()) {
        openDocument = ""
    }

    // The menu's own screens, not copies of them: LegalWebScreen draws the live page and falls
    // back to the snapshot bundled in the APK.
    when (openDocument) {
        "terms" -> LegalWebScreen(
            title = "Terms of Use",
            path = "/legal/terms-of-use.html",
            assetFile = "legal/terms-of-use.html",
            onBack = { openDocument = "" },
        )
        "privacy" -> LegalWebScreen(
            title = "Privacy Policy",
            path = "/legal/privacy-policy.html",
            assetFile = "legal/privacy-policy.html",
            onBack = { openDocument = "" },
        )
        else -> Unit
    }

    LaunchedEffect(otpVisible) {
        if (otpVisible) otpFocusRequester.requestFocus()
    }

    AuthPage {
        AuthBrand()
        Spacer(Modifier.height(44.dp))
        androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxWidth()) {
            var showCreatingAccount by rememberSaveable { mutableStateOf(false) }
            var descriptionVisibleChars by rememberSaveable { mutableStateOf(0) }

            LaunchedEffect(Unit) {
                while (isActive) {
                    kotlinx.coroutines.delay(2000)
                    showCreatingAccount = !showCreatingAccount
                }
            }

            LaunchedEffect(Unit) {
                val description = "Create your Sankalp account and start asking anything, in Kannada or English."
                val delayPerCharacter = 2000L / description.length.coerceAtLeast(1)
                for (index in 1..description.length) {
                    kotlinx.coroutines.delay(delayPerCharacter)
                    descriptionVisibleChars = index
                }
            }

            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                contentAlignment = androidx.compose.ui.Alignment.TopStart,
            ) {
                androidx.compose.animation.Crossfade(
                    targetState = showCreatingAccount,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 500),
                    label = "create_account_title",
                ) { creating ->
                    Text(
                        text = if (creating) "Creating your account" else "Get started with",
                        color = if (creating) {
                            androidx.compose.ui.graphics.Color(0xFF17151D)
                        } else {
                            androidx.compose.ui.graphics.Color(0xFF7650D8)
                        },
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp,
                        lineHeight = 40.sp,
                    )
                }
            }
            val description = "Create your Sankalp account and start asking anything, in Kannada or English."
            Text(
                description.take(descriptionVisibleChars),
                color = androidx.compose.ui.graphics.Color(0xFF77717F),
                fontSize = 14.sp,
                lineHeight = 22.sp,
            )
        }
        Spacer(Modifier.height(20.dp))

        AuthFieldLabel("EMAIL ID")
        AuthTextField(
            value = email,
            onValueChange = { email = it.take(320) },
            placeholder = "you@example.com",
            enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        )

        Spacer(Modifier.height(14.dp))
        AuthFieldLabel("MOBILE NUMBER")
        AuthTextField(
            value = mobile,
            onValueChange = { mobile = it.filter { char -> char.isDigit() }.take(10) },
            placeholder = "98765 43210",
            enabled = !busy,
            leadingContent = if (hasMobile) {
                { Text("+91", fontSize = 13.sp) }
            } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
        )

        Spacer(Modifier.height(14.dp))
        AuthFieldLabel("PASSWORD")
        AuthTextField(
            value = password,
            onValueChange = { password = it.take(128) },
            placeholder = "Create a strong password",
            enabled = !busy,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingContent = {
                TextButton(onClick = { passwordVisible = !passwordVisible }, enabled = !busy) {
                    Text(if (passwordVisible) "◌" else "◉", fontSize = 17.sp)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
        )

        // The recovery screen has asked for the new password twice since it existed; signup,
        // where the password is chosen for the first time, asked once. A typo here makes an
        // account whose password nobody knows.
        Spacer(Modifier.height(14.dp))
        AuthFieldLabel("CONFIRM PASSWORD")
        AuthTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it.take(128) },
            placeholder = "Type it again",
            enabled = !busy,
            visualTransformation = if (confirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingContent = {
                TextButton(onClick = { confirmVisible = !confirmVisible }, enabled = !busy) {
                    Text(if (confirmVisible) "◌" else "◉", fontSize = 17.sp)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            // Done now belongs to the last field, which is the one a person finishes on.
            keyboardActions = KeyboardActions(
                onDone = {
                    if (actionEnabled) {
                        onSendOtp(email, "+91${mobile.filter(Char::isDigit).take(10)}", password, confirmPassword)
                    }
                },
            ),
        )

        if (!otpVisible) {
        }

        Spacer(Modifier.height(20.dp))
        if (!otpVisible) {
            AuthPrimaryButton(
                text = if (busyAction == AuthBusyAction.SEND_OTP) "Sending OTP…" else "Continue with OTP",
                enabled = actionEnabled,
                onClick = { onSendOtp(email, "+91${mobile.filter(Char::isDigit).take(10)}", password, confirmPassword) },
            )
            Spacer(Modifier.height(8.dp))
            AuthPrimaryButton(
                text = if (busyAction == AuthBusyAction.CREATE_ACCOUNT) "Creating account…" else "Continue with Password",
                enabled = actionEnabled,
                onClick = { onCreateWithPassword(email, "+91${mobile.filter(Char::isDigit).take(10)}", password, confirmPassword) },
            )
        }

        if (otpVisible) {
            TextButton(onClick = onBackToDetails, enabled = !busy) {
                Text("← Back", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                buildAnnotatedString {
                    append("OTP sent to ")
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(otpEmail) }
                },
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(12.dp))
            AuthFieldLabel("VERIFICATION OTP")
            AuthTextField(
                value = otp,
                onValueChange = { otp = it.filter(Char::isDigit).take(6) },
                placeholder = "Enter 6-digit OTP",
                enabled = !busy,
                modifier = Modifier.focusRequester(otpFocusRequester),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { if (!busy && otp.length == 6 && agreed) onVerifyOtp(otp) },
                ),
            )
            Spacer(Modifier.height(4.dp))
            AuthPrimaryButton(
                text = if (busyAction == AuthBusyAction.VERIFY_OTP) "Verifying…" else "Verify & Create Account",
                // The box is still on this screen and can still be unticked; Verify honours
                // it, so a withdrawn tick cannot end in a consent record saying otherwise.
                enabled = !busy && otp.length == 6 && agreed,
                onClick = { onVerifyOtp(otp) },
            )

            // The same sixty seconds as the login screen. Without it a person taps Resend
            // while the first mail is still in flight, and the third answer from Firebase is
            // TOO_MANY_REQUESTS - a lockout far longer than the minute this waits.
            var resendSecondsLeft by rememberSaveable {
                mutableStateOf(SIGNUP_RESEND_COOLDOWN_SECONDS)
            }
            // Keyed on the status as well: a successful resend changes it, and the countdown
            // has to start again from there. Keyed on otpVisible alone it never restarted, so
            // the button was ready to press the instant the new code went out - the exact
            // tapping the cooldown exists to prevent.
            LaunchedEffect(otpVisible, status) {
                if (!otpVisible) return@LaunchedEffect
                resendSecondsLeft = SIGNUP_RESEND_COOLDOWN_SECONDS
                while (resendSecondsLeft > 0) {
                    kotlinx.coroutines.delay(1_000)
                    resendSecondsLeft -= 1
                }
            }
            AuthPrimaryButton(
                text = when {
                    busyAction == AuthBusyAction.RESEND_OTP -> "Sending…"
                    resendSecondsLeft > 0 -> "Resend in ${resendSecondsLeft}s"
                    else -> "Resend OTP"
                },
                enabled = actionEnabled && resendSecondsLeft == 0,
                onClick = { onResendOtp() },
            )

            AuthStatus(status, error = false)
        }

        // On screen for both steps, and under them: inside the details block it vanished the
        // moment a code was requested, and the two clients disagreed about whether the box
        // existed on that screen.
        Spacer(Modifier.height(4.dp))
        ConsentRow(
            agreed = agreed,
            onAgreedChange = { agreed = it },
            onOpenTerms = { openDocument = "terms" },
            onOpenPrivacy = { openDocument = "privacy" },
            enabled = !busy,
        )

        AuthStatus(error, error = true)
        Spacer(Modifier.height(10.dp))
        AuthSecondaryButton(
            text = "Already have an account? Login",
            enabled = !busy,
            onClick = onLogin,
            textColor = androidx.compose.ui.graphics.Color(0xFF2E7D32),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Continue with OTP verifies your email. Continue with Password creates the account directly using your password.",
            color = androidx.compose.ui.graphics.Color(0xFF76717D),
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
    }
}
