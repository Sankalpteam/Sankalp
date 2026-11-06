package com.indoone.menu.termsofuse

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class TermsSection(val title: String, val body: String)

private val TermsPurple = Color(0xFF6330DB)
private val TermsInk = Color(0xFF211D27)
private val TermsMuted = Color(0xFF756F7F)
private val TermsBorder = Color(0xFFECE7F8)
private val TermsChip = Color(0xFFF3EDFF)

private val TERMS_SECTIONS = listOf(
    TermsSection("1. Account Creation", "When creating an Indoone account, users may provide an email address, mobile number, password, and OTP. The OTP is verified through the IndoVerification system. After successful OTP verification, the Indoone account is created in Firebase."),
    TermsSection("2. Login", "Login uses an existing Indoone account. Users may sign in with their registered email address or mobile number and password. OTP verification may also be required to complete login. Login does not create a new account."),
    TermsSection("3. Account Information", "Users may change their registered email address and mobile number through available account settings. Password change is currently not available. Forgot Password and separate account recovery functionality are currently not fully available."),
    TermsSection("4. Authenticator Accounts", "Indoone allows users to add and manage supported authenticator accounts. Supported setup methods may include QR setup, manual setup, and supported import methods. Indoone may generate and display TOTP verification codes. Users are responsible for keeping authenticator information secure."),
    TermsSection("5. Account Management", "Users may view, search, sort, favorite, and manage their authenticator accounts using available features. Users should review an action before deleting or moving an account."),
    TermsSection("6. Trash and Restore", "When an authenticator account is deleted, it may remain in Trash for up to 30 days. During this period, the user may restore the account from Trash using the available restore functionality. After the retention period, the account may be permanently removed. Trash restoration is not a password recovery or Indoone account recovery service."),
    TermsSection("7. Backup", "Available backup features may help users preserve authenticator-related information. Users are responsible for keeping backup and recovery information secure. Backup functionality does not guarantee complete recovery in every situation."),
    TermsSection("8. Security Features", "Indoone may provide App Lock, PIN protection, biometric unlock, Auto-Lock, and screenshot protection for supported sensitive screens. These features are intended to improve security but do not guarantee complete protection against every security risk."),
    TermsSection("9. Firebase and Other Services", "Indoone uses Firebase and related services for authentication and certain account-related data. Indoone may also use IndoVerification and device or operating-system capabilities for supported features. Service interruptions, network problems, maintenance, or third-party service issues may affect availability."),
    TermsSection("10. Connect and Permissions", "Supported versions of Indoone may provide nearby device connection, device pairing, permissions, and data or file transfer features. Users are responsible for reviewing devices and permissions before granting access. Users must not access, copy, or transfer another person's data without authorization."),
    TermsSection("11. User Responsibilities", "Users must protect their password, OTP, authenticator information, recovery information, and device. Indoone must not be used for unauthorized access, fraud, misuse of another person's account, illegal activity, or attempts to bypass security controls."),
    TermsSection("12. Service Availability", "Indoone is provided with the goal of reasonable reliability. However, continuous, uninterrupted, or error-free operation cannot be guaranteed. Features may temporarily become unavailable because of technical issues, network problems, maintenance, device limitations, or third-party services."),
    TermsSection("13. Data and Account Responsibility", "Users are responsible for their own account credentials, authenticator information, backups, and recovery information. Loss of a device, credentials, deleted data, failed synchronization, or unavailable third-party services may result in loss of access or data."),
    TermsSection("14. Privacy", "Indoone may process account, authentication, and technical information as necessary to provide its features. Users should review the Indoone Privacy Policy for information about data collection, use, storage, and deletion."),
    TermsSection("15. The Assistant's Answers", "Indoone answers using an AI model. Answers are generated, not looked up, and they can be wrong, incomplete or out of date. Do not rely on an answer for a medical, legal, financial or safety decision; consult a qualified professional. Where Indoone gives you a date, a count or a figure from a connected service, that figure came from the service and not from the model, and it is the more reliable of the two."),
    TermsSection("16. Connected Services and Permissions", "You decide which services Indoone may read, and you can disconnect any of them at any time. You are responsible for having the right to access any account you connect, and for what you ask Indoone to do with it. Actions that change something - sending a mail, merging a pull request - require your explicit confirmation and are never performed on the model's initiative."),
    TermsSection("17. Voice", "The voice feature converts your speech to text and the reply to audio using a third-party speech provider. Speech recognition is imperfect, particularly with names, numbers and regional accents. Check anything important that was spoken before acting on it."),
    TermsSection("18. Running Code", "Where the code feature is enabled, you may ask Indoone to run a short snippet. Execution is time- and memory-limited and has no network or filesystem access. Do not use it to attempt to reach systems you do not own or to attack the service; doing so is a breach of these terms and your account may be suspended."),
    TermsSection("19. Fair Use and Limits", "Indoone applies usage limits so that the service stays available and affordable for everyone: a daily allowance for the assistant, and a monthly cap on messages sent through WhatsApp. When a limit is reached you will be told plainly, and the app remains available. Attempting to evade a limit is a breach of these terms."),
    TermsSection("20. Shared Conversations", "A conversation you share becomes readable by anyone with the link, and the link does not expire. You are responsible for what you choose to share. Do not share a conversation containing another person's private information without their consent."),
    TermsSection("21. Changes to the Service", "Indoone is under active development. Features may be added, changed or withdrawn, and an answer that was possible yesterday may not be today. We will not remove a feature you rely on without saying so in the app first."),
    TermsSection("22. Changes to These Terms", "Indoone may update these Terms when its features, services, or legal requirements change. Updated Terms may be published within the application or through an official Indoone channel."),
    TermsSection("23. Contact", "Email: indoone@zohomail.in"),
    TermsSection("24. Acceptance", "By creating an account, logging in, or using Indoone, you acknowledge that you have read and accepted these Terms of Use. If you do not agree with these Terms, please stop using Indoone."),
)

@Composable
fun TermsOfUseScreen(onBack: () -> Unit, onAccountsClick: () -> Unit, onLobbyClick: () -> Unit, onVibeClick: () -> Unit, onSettingsClick: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = Color.White) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                    )
                }
                Text(
                    text = "Terms of Use",
                    modifier = Modifier.padding(start = 4.dp),
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2C2733),
                )
            }
            Surface(
                modifier = Modifier.padding(start = 6.dp),
                shape = RoundedCornerShape(9.dp),
                color = TermsChip,
            ) {
                Text(
                    text = "Last updated 07/11/2026",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    color = TermsPurple,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(TERMS_SECTIONS) { section ->
                    TermsSectionCard(section)
                }
            }
        }
    }

    @Suppress("UNUSED_VARIABLE")
    val keepNavigationContract = onAccountsClick to onLobbyClick to onVibeClick to onSettingsClick
}

@Composable
private fun TermsSectionCard(section: TermsSection) {
    val number = section.title.substringBefore(".", "").trim()
    val heading = section.title.substringAfter(". ", section.title)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, TermsBorder),
        shadowElevation = 1.dp,
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(TermsChip, RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = number,
                    color = TermsPurple,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = heading,
                    color = TermsInk,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = section.body,
                    modifier = Modifier.padding(top = 4.dp),
                    color = TermsMuted,
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                )
            }
        }
    }
}
