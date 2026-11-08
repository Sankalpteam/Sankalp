package com.indoone

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.WorkInfo
import com.indoone.lobby.accounts.AccountItem
import com.indoone.lobby.accounts.AccountRecord
import com.indoone.lobby.accounts.AccountsScreen
import com.indoone.lobby.accounts.AccountsViewModel
import com.indoone.lobby.accounts.accounts.AccountDetailActions
import com.indoone.lobby.accounts.accounts.AccountRemovalService
import com.indoone.lobby.accounts.accounts.edit.EditAccountScreen
import com.indoone.lobby.accounts.accounts.edit.EditAccountViewModel
import com.indoone.lobby.accounts.accounts.list.AccountDetailsScreen
import com.indoone.lobby.accounts.accounts.list.AccountTotpGenerator
import com.indoone.lobby.accounts.addaccount.AddAccountScreen
import com.indoone.lobby.accounts.addaccount.AddAccountViewModel
import com.indoone.lobby.accounts.addaccount.entersetupkey.EnterSetupKeyScreen
import com.indoone.lobby.accounts.addaccount.entersetupkey.EnterSetupKeyViewModel
import com.indoone.lobby.accounts.addaccount.importuri.ImportOtpUriScreen
import com.indoone.lobby.accounts.addaccount.importuri.ImportOtpUriViewModel
import com.indoone.lobby.accounts.addaccount.scanqr.QrManualPrefill
import com.indoone.lobby.accounts.addaccount.scanqr.QrScanResultHandler
import com.indoone.lobby.accounts.addaccount.scanqr.ScanQrScreen
import com.indoone.lobby.accounts.addaccount.scanqr.ScanQrViewModel
import com.indoone.lobby.accounts.addaccount.scanqr.accountdetails.AccountDetailsScreen
import com.indoone.lobby.accounts.addaccount.scanqr.accountdetails.AccountDetailsViewModel
import com.indoone.lobby.accounts.addaccount.scanqr.accountdetails.AccountSaveCoordinator
import com.indoone.lobby.accounts.search.SearchViewModel
import com.indoone.lobby.accounts.storage.AccountRepositoryProvider
import com.indoone.home.HomeScreen
import com.indoone.vibe.VibeScreen
import com.indoone.menu.MenuDrawer
import com.indoone.menu.plugins.PluginsMenuScreen
import com.indoone.menu.plugins.zoho_mail.ZohoMailPluginMenuScreen
import com.indoone.notifications.NotificationPreferences
import com.indoone.notifications.NotificationCenterScreen
import com.indoone.notifications.NotificationRepository
import com.indoone.notifications.NotificationSettingsScreen
import com.indoone.menu.favorites.FavoritesScreen
import com.indoone.menu.privacypolicy.PrivacyPolicyScreen
import com.indoone.menu.security.SecurityScreen
import com.indoone.settings.SettingsScreen
import com.indoone.settings.update.AppUpdateManager
import com.indoone.settings.diagnostics.CrashLogStore
import com.indoone.settings.profile.ProfileScreen
import com.indoone.settings.profile.ProfileViewModel
import com.indoone.settings.profile.change_email.ChangeEmailScreen
import com.indoone.settings.profile.change_mobile_number.ChangeMobileNumberScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AppRoute {
    HOME,
    LOBBY,
    SETTINGS,
    PROFILE,
    PLUGINS,
    ZOHO_MAIL_PLUGIN,
    CHANGE_MOBILE,
    CHANGE_EMAIL,
    FAVORITES,
    SECURITY,
    PRIVACY_POLICY,
    ADD_ACCOUNT,
    SCAN_QR,
    QR_ACCOUNT_DETAILS,
    ENTER_SETUP_KEY,
    IMPORT_OTP_URI,
    IMPORT_ACCOUNT_DETAILS,
    ACCOUNT_DETAILS,
    EDIT_ACCOUNT,
    VIBE,
    NOTIFICATIONS,
    NOTIFICATION_CENTER,
}

class MainActivity : ComponentActivity() {
    /** Feeds Auto-Lock's inactivity timer: any touch or key counts as activity. */
    override fun onUserInteraction() {
        super.onUserInteraction()
        (application as? IndooneApplication)?.onUserInteraction(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureSystemBarsDirectly()
        setContent {
            MaterialTheme {
                val repository = remember { AccountRepositoryProvider(applicationContext) }
                val detailActions = remember { AccountDetailActions(repository, applicationContext) }
                val removalService = remember { AccountRemovalService(repository) }
                val coroutineScope = rememberCoroutineScope()
                val accountsViewModel: AccountsViewModel = viewModel()
                val addAccountViewModel: AddAccountViewModel = viewModel()
                val scanQrViewModel: ScanQrViewModel = viewModel()
                val enterSetupKeyViewModel: EnterSetupKeyViewModel = viewModel()
                val importOtpUriViewModel: ImportOtpUriViewModel = viewModel()
                val searchViewModel: SearchViewModel = viewModel()
                val profileViewModel: ProfileViewModel = viewModel()
                var route by remember { mutableStateOf(AppRoute.HOME) }

                // The screens that show live authenticator codes are flagged secure, so the OS
                // refuses screenshots and hides the app in the recents thumbnail. The web has
                // asked for this all along through window.IndooneNative.setSecureScreen; nothing
                // on the phone ever answered, so the codes could be captured. The flag is set
                // and cleared here rather than once for the whole app: a PIN dialog on a
                // settings screen is not a secret worth a black screenshot.
                val secureRoutes = remember {
                    setOf(
                        AppRoute.LOBBY,
                        AppRoute.ACCOUNT_DETAILS,
                        AppRoute.EDIT_ACCOUNT,
                        AppRoute.QR_ACCOUNT_DETAILS,
                        AppRoute.IMPORT_ACCOUNT_DETAILS,
                        AppRoute.ADD_ACCOUNT,
                        AppRoute.SCAN_QR,
                        AppRoute.ENTER_SETUP_KEY,
                        AppRoute.IMPORT_OTP_URI,
                        AppRoute.FAVORITES,
                        AppRoute.HOME,
                    )
                }

                LaunchedEffect(route) {
                    if (route in secureRoutes) {
                        window.setFlags(
                            WindowManager.LayoutParams.FLAG_SECURE,
                            WindowManager.LayoutParams.FLAG_SECURE,
                        )
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }

                var menuOpen by remember { mutableStateOf(false) }
                var menuOriginRoute by remember { mutableStateOf(AppRoute.HOME) }
                var settingsOpenAppLock by remember { mutableStateOf(false) }
                var availableUpdate by remember { mutableStateOf<AppUpdateManager.UpdateInfo?>(null) }
                var isCheckingUpdate by remember { mutableStateOf(false) }
                var updateDialogHidden by remember { mutableStateOf(false) }
                var selectedAccount by remember { mutableStateOf<AccountRecord?>(null) }
                var accountDetailReturnRoute by remember { mutableStateOf(AppRoute.LOBBY) }
                var qrAccountDetailsViewModel by remember { mutableStateOf<AccountDetailsViewModel?>(null) }
                var importAccountDetailsViewModel by remember { mutableStateOf<AccountDetailsViewModel?>(null) }
                var editAccountViewModel by remember { mutableStateOf<EditAccountViewModel?>(null) }
                val notificationPreferences = remember { NotificationPreferences(this@MainActivity) }
                val notificationRepository = remember { NotificationRepository.getInstance(applicationContext) }
                val unreadNotificationCount by notificationRepository.observeUnreadCount().collectAsState(initial = 0)
                // One prompt for everything the app needs, shown once right after
                // sign-in. Declining is fine: the app carries on, and the screen that
                // needs a permission asks for it again when it is used.
                val permissionBatchLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) { }

                // The download runs here, inside the dialog, on the screen's own
                // coroutine. It is deliberately not a foreground service: Android 14
                // and later refuse to start one without a declared type, and that is
                // what was closing the app the moment an update was started. Running
                // it here costs only that leaving the app stops the download.
                var isDownloadingUpdate by remember { mutableStateOf(false) }
                var downloadedBytes by remember { mutableStateOf(0L) }
                var totalBytes by remember { mutableStateOf(0L) }
                var updateReady by remember { mutableStateOf(false) }

                fun checkForUpdates(showUpToDateMessage: Boolean) {
                    if (isCheckingUpdate || isDownloadingUpdate) return
                    isCheckingUpdate = true
                    coroutineScope.launch {
                        runCatching {
                            AppUpdateManager.checkForUpdate(this@MainActivity)
                        }.onSuccess { update ->
                            availableUpdate = update
                            if (update != null) updateDialogHidden = false
                            if (update == null && showUpToDateMessage) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "You're up to date.",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }.onFailure { error ->
                            if (showUpToDateMessage) {
                                Toast.makeText(
                                    this@MainActivity,
                                    error.message ?: "Could not check for updates.",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                        isCheckingUpdate = false
                    }
                }

                fun startUpdateDownload(update: AppUpdateManager.UpdateInfo) {
                    if (isDownloadingUpdate) return
                    isDownloadingUpdate = true
                    updateReady = false
                    downloadedBytes = 0L
                    totalBytes = update.sizeBytes
                    updateDialogHidden = false
                    availableUpdate = null
                    coroutineScope.launch {
                        runCatching {
                            AppUpdateManager.downloadApk(this@MainActivity, update) { copied, total ->
                                withContext(Dispatchers.Main.immediate) {
                                    downloadedBytes = copied
                                    if (total > 0L) totalBytes = total
                                }
                            }
                        }.onSuccess {
                            updateReady = true
                        }.onFailure { error ->
                            // A failed download used to end as a toast, and the person was
                            // left with no way to install the update at all. The message now
                            // says what happened and offers the release page, which is the one
                            // route that does not depend on this app's download working.
                            Toast.makeText(
                                this@MainActivity,
                                error.message ?: "Could not download the update.",
                                Toast.LENGTH_LONG,
                            ).show()

                            runCatching {
                                startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(AppUpdateManager.RELEASE_PAGE_URL),
                                    ),
                                )
                            }
                        }
                        isDownloadingUpdate = false
                    }
                }

                LaunchedEffect(Unit) {
                    checkForUpdates(showUpToDateMessage = false)
                }

                LaunchedEffect(Unit) {
                    val wanted = buildList {
                        add(Manifest.permission.RECORD_AUDIO)
                        add(Manifest.permission.CAMERA)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    val missing = wanted.filter { permission ->
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            permission,
                        ) != PackageManager.PERMISSION_GRANTED
                    }

                    if (missing.isEmpty()) {
                        notificationPreferences.markPermissionRequested()
                        return@LaunchedEffect
                    }
                    if (notificationPreferences.wasPermissionRequested()) return@LaunchedEffect

                    notificationPreferences.markPermissionRequested()
                    permissionBatchLauncher.launch(missing.toTypedArray())
                }

                fun navigate(target: AppRoute) {
                    menuOpen = false
                    route = target
                }

                fun openMenu() {
                    menuOriginRoute = route
                    menuOpen = true
                }

                fun backToMenu() {
                    route = menuOriginRoute
                    menuOpen = true
                }

                fun menuToast(message: String) {
                    menuOpen = false
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                }

                fun loadAccounts() {
                    coroutineScope.launch {
                        runCatching {
                            repository.getAll()
                        }.onSuccess { records ->
                            val uiAccounts = records.toUiAccounts()
                            accountsViewModel.setAccounts(uiAccounts)
                            searchViewModel.setAccounts(uiAccounts)
                        }.onFailure { error ->
                            Toast.makeText(
                                this@MainActivity,
                                error.message ?: "Could not load accounts.",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }

                LaunchedEffect(Unit) { loadAccounts() }
                LaunchedEffect(repository) {
                    while (true) {
                        runCatching { repository.getAll() }
                            .onSuccess { records ->
                                val uiAccounts = records.toUiAccounts(System.currentTimeMillis())
                                accountsViewModel.setAccounts(uiAccounts)
                                searchViewModel.setAccounts(uiAccounts)
                            }
                        delay(1_000L)
                    }
                }

                when (route) {
                    AppRoute.HOME -> HomeScreen(
                        onMenuClick = ::openMenu,
                        onLobbyClick = { navigate(AppRoute.LOBBY) },
                        onSettingsClick = { navigate(AppRoute.SETTINGS) },
                        startNewChat = intent.getBooleanExtra("start_new_chat", false),
                        openConversationId = intent.getStringExtra("open_conversation_id"),
                        onVibeClick = { navigate(AppRoute.VIBE) },
                        hasUnreadNotifications = unreadNotificationCount > 0,
                        onNotificationClick = { navigate(AppRoute.NOTIFICATION_CENTER) },
                    )

                    AppRoute.VIBE -> VibeScreen(
                        onClose = { navigate(AppRoute.HOME) },
                    )

                    AppRoute.LOBBY -> {
                        val state by accountsViewModel.state.collectAsState()
                        AccountsScreen(
                            state = state,
                            onSearchChanged = accountsViewModel::updateSearchQuery,
                            onClearSearch = accountsViewModel::clearSearch,
                            onSort = accountsViewModel::toggleSort,
                            onToggleFavorite = { id ->
                                // Flip the star straight away. Waiting for the write
                                // and a full reload made the tap feel slow.
                                val item = accountsViewModel.state.value.accounts.firstOrNull { it.id == id }
                                if (item != null) {
                                    val nextFavorite = !item.favorite
                                    accountsViewModel.toggleFavorite(id)
                                    coroutineScope.launch {
                                        repository.getAll().firstOrNull { it.id == id }?.let { record ->
                                            detailActions.setFavorite(record, nextFavorite)
                                                .onFailure { loadAccounts() }
                                        }
                                    }
                                }
                            },
                            onAccountClick = { item ->
                                coroutineScope.launch {
                                    selectedAccount = repository.getAll().firstOrNull { it.id == item.id }
                                    if (selectedAccount != null) {
                                        accountDetailReturnRoute = AppRoute.LOBBY
                                        route = AppRoute.ACCOUNT_DETAILS
                                    }
                                }
                            },
                            onAddAccount = {
                                addAccountViewModel.clearImportUri()
                                route = AppRoute.ADD_ACCOUNT
                            },
                            onSearchClick = {},
                            onMenuClick = ::openMenu,
                            onAccountsClick = { navigate(AppRoute.HOME) },
                            onLobbyClick = { navigate(AppRoute.LOBBY) },
                            onVibeClick = { navigate(AppRoute.VIBE) },
                            onSettingsClick = { navigate(AppRoute.SETTINGS) },
                        )
                    }

                    AppRoute.SETTINGS -> SettingsScreen(
                        onMenuClick = ::openMenu,
                        onProfileClick = {
                            profileViewModel.loadProfile()
                            navigate(AppRoute.PROFILE)
                        },
                        onAccountsClick = { navigate(AppRoute.HOME) },
                        onLobbyClick = { navigate(AppRoute.LOBBY) },
                        onVibeClick = { navigate(AppRoute.VIBE) },
                        onSettingsClick = { navigate(AppRoute.SETTINGS) },
                        onNotificationsClick = { navigate(AppRoute.NOTIFICATIONS) },
                        onCheckForUpdates = { checkForUpdates(showUpToDateMessage = true) },
                        startAtAppLock = settingsOpenAppLock,
                        onStartAtAppLockHandled = { settingsOpenAppLock = false },
                    )

                    AppRoute.NOTIFICATIONS -> NotificationSettingsScreen(
                        onBack = { navigate(AppRoute.SETTINGS) },
                    )

                    AppRoute.NOTIFICATION_CENTER -> NotificationCenterScreen(
                        repository = notificationRepository,
                        onBack = { navigate(AppRoute.HOME) },
                    )

                    AppRoute.PROFILE -> {
                        val state by profileViewModel.state.collectAsState()
                        ProfileScreen(
                            state = state,
                            onBack = { navigate(AppRoute.SETTINGS) },
                            onMobileClick = {
                                profileViewModel.clearFeedback()
                                route = AppRoute.CHANGE_MOBILE
                            },
                            onEmailClick = {
                                profileViewModel.clearFeedback()
                                route = AppRoute.CHANGE_EMAIL
                            },
                            onPluginsClick = { navigate(AppRoute.PLUGINS) },
                            onAccountsClick = { navigate(AppRoute.HOME) },
                            onLobbyClick = { navigate(AppRoute.LOBBY) },
                            onVibeClick = { navigate(AppRoute.VIBE) },
                            onSettingsClick = { navigate(AppRoute.SETTINGS) },
                        )
                    }

                    AppRoute.PLUGINS -> PluginsMenuScreen(
                        onBack = { navigate(AppRoute.PROFILE) },
                    )

                    AppRoute.ZOHO_MAIL_PLUGIN -> ZohoMailPluginMenuScreen(
                        onBack = { navigate(AppRoute.PLUGINS) },
                    )

                    AppRoute.CHANGE_MOBILE -> {
                        val state by profileViewModel.state.collectAsState()
                        ChangeMobileNumberScreen(
                            state = state,
                            onBack = {
                                profileViewModel.loadProfile()
                                route = AppRoute.PROFILE
                            },
                            onSave = profileViewModel::updateMobile,
                            onAccountsClick = { navigate(AppRoute.HOME) },
                            onLobbyClick = { navigate(AppRoute.LOBBY) },
                            onVibeClick = { navigate(AppRoute.VIBE) },
                            onSettingsClick = { navigate(AppRoute.SETTINGS) },
                        )
                    }

                    AppRoute.CHANGE_EMAIL -> {
                        val state by profileViewModel.state.collectAsState()
                        ChangeEmailScreen(
                            state = state,
                            onBack = {
                                profileViewModel.loadProfile()
                                route = AppRoute.PROFILE
                            },
                            onSave = profileViewModel::updateEmail,
                            onAccountsClick = { navigate(AppRoute.HOME) },
                            onLobbyClick = { navigate(AppRoute.LOBBY) },
                            onVibeClick = { navigate(AppRoute.VIBE) },
                            onSettingsClick = { navigate(AppRoute.SETTINGS) },
                        )
                    }

                    AppRoute.FAVORITES -> {
                        FavoritesRoute(
                            repository = repository,
                            onBack = ::backToMenu,
                            onAccountClick = { account ->
                                selectedAccount = account
                                accountDetailReturnRoute = AppRoute.FAVORITES
                                route = AppRoute.ACCOUNT_DETAILS
                            },
                            onToggleFavorite = { account ->
                                coroutineScope.launch {
                                    detailActions.setFavorite(account, false).onSuccess {
                                        loadAccounts()
                                    }
                                }
                            },
                            onAccountsClick = { navigate(AppRoute.HOME) },
                            onLobbyClick = { navigate(AppRoute.LOBBY) },
                            onVibeClick = { navigate(AppRoute.VIBE) },
                            onSettingsClick = { navigate(AppRoute.SETTINGS) },
                        )
                    }

                    AppRoute.SECURITY -> SecurityScreen(
                        onBack = ::backToMenu,
                        onAccountsClick = { navigate(AppRoute.HOME) },
                        onLobbyClick = { navigate(AppRoute.LOBBY) },
                        onVibeClick = { navigate(AppRoute.VIBE) },
                        onSettingsClick = { navigate(AppRoute.SETTINGS) },
                    )

                    AppRoute.PRIVACY_POLICY -> PrivacyPolicyScreen(
                        onBack = ::backToMenu,
                        onAccountsClick = { navigate(AppRoute.HOME) },
                        onLobbyClick = { navigate(AppRoute.LOBBY) },
                        onVibeClick = { navigate(AppRoute.VIBE) },
                        onSettingsClick = { navigate(AppRoute.SETTINGS) },
                    )

                    AppRoute.ACCOUNT_DETAILS -> {
                        val account = selectedAccount
                        val uiAccount = account?.let {
                            accountsViewModel.state.value.accounts.firstOrNull { item -> item.id == account.id }
                        }
                        if (account == null || uiAccount == null) {
                            route = accountDetailReturnRoute
                        } else {
                            AccountDetailsScreen(
                                account = account,
                                code = uiAccount.code,
                                secondsRemaining = uiAccount.secondsRemaining,
                                onBack = { route = accountDetailReturnRoute },
                                onEdit = {
                                    editAccountViewModel = EditAccountViewModel(account)
                                    route = AppRoute.EDIT_ACCOUNT
                                },
                                onCopy = { detailActions.copyCode(uiAccount.code) },
                                onToggleFavorite = {
                                    coroutineScope.launch {
                                        detailActions.setFavorite(account, !account.favorite).onSuccess { updated ->
                                            selectedAccount = updated
                                            loadAccounts()
                                        }
                                    }
                                },
                                onDelete = {
                                    coroutineScope.launch {
                                        removalService.remove(account.id).onSuccess {
                                            selectedAccount = null
                                            loadAccounts()
                                            route = accountDetailReturnRoute
                                        }
                                    }
                                },
                            )
                        }
                    }

                    AppRoute.EDIT_ACCOUNT -> {
                        val vm = editAccountViewModel
                        if (vm == null) {
                            route = AppRoute.ACCOUNT_DETAILS
                        } else {
                            val state by vm.state.collectAsState()
                            EditAccountScreen(
                                state = state,
                                onNameChanged = vm::onNameChanged,
                                onEmailChanged = vm::onEmailChanged,
                                onSecretChanged = vm::onSecretChanged,
                                onDigitsChanged = vm::onDigitsChanged,
                                onPeriodChanged = vm::onPeriodChanged,
                                onAlgorithmChanged = vm::onAlgorithmChanged,
                                onBack = { route = AppRoute.ACCOUNT_DETAILS },
                                onSave = {
                                    vm.prepareSave()
                                        .onFailure { e -> vm.onSaveFailed(e.message ?: "Invalid account details.") }
                                        .onSuccess { request ->
                                            vm.onSaveStarted()
                                            coroutineScope.launch {
                                                runCatching {
                                                    val old = selectedAccount ?: error("Account not found.")
                                                    val updated = old.copy(
                                                        name = request.name,
                                                        email = request.email,
                                                        secret = request.secret,
                                                        digits = request.digits,
                                                        period = request.period,
                                                        algorithm = request.algorithm,
                                                        updatedAt = System.currentTimeMillis(),
                                                    )
                                                    repository.save(updated)
                                                    selectedAccount = updated
                                                }.onSuccess {
                                                    vm.onSaveCompleted()
                                                    loadAccounts()
                                                    route = AppRoute.ACCOUNT_DETAILS
                                                }.onFailure { e ->
                                                    vm.onSaveFailed(e.message ?: "Could not save account.")
                                                }
                                            }
                                        }
                                },
                            )
                        }
                    }

                    AppRoute.ADD_ACCOUNT -> {
                        val state by addAccountViewModel.state.collectAsState()
                        AddAccountScreen(
                            state = state,
                            onOtpUriChanged = addAccountViewModel::updateOtpUri,
                            onBack = { route = AppRoute.LOBBY },
                            onScanQr = { route = AppRoute.SCAN_QR },
                            onEnterSetupKey = { route = AppRoute.ENTER_SETUP_KEY },
                            onImportOtpUri = { uri ->
                                importOtpUriViewModel.onUriChanged(uri)
                                route = AppRoute.IMPORT_OTP_URI
                            },
                            onMenuClick = ::openMenu,
                            onSearchClick = {},
                            onAccountsClick = { navigate(AppRoute.HOME) },
                            onLobbyClick = { navigate(AppRoute.LOBBY) },
                            onVibeClick = { navigate(AppRoute.VIBE) },
                            onSettingsClick = { navigate(AppRoute.SETTINGS) },
                        )
                    }

                    AppRoute.IMPORT_OTP_URI -> {
                        val state by importOtpUriViewModel.state.collectAsState()
                        ImportOtpUriScreen(
                            state = state,
                            onUriChanged = importOtpUriViewModel::onUriChanged,
                            onBack = { route = AppRoute.ADD_ACCOUNT },
                            onContinue = {
                                importOtpUriViewModel.parse().onSuccess { result ->
                                    importAccountDetailsViewModel = AccountDetailsViewModel(result)
                                    route = AppRoute.IMPORT_ACCOUNT_DETAILS
                                }
                            },
                        )
                    }

                    AppRoute.IMPORT_ACCOUNT_DETAILS -> {
                        val vm = importAccountDetailsViewModel
                        if (vm == null) {
                            route = AppRoute.IMPORT_OTP_URI
                        } else {
                            val state by vm.state.collectAsState()
                            AccountDetailsScreen(
                                state = state,
                                onNameChanged = vm::onNameChanged,
                                onEmailChanged = vm::onEmailChanged,
                                onSecretChanged = vm::onSecretChanged,
                                onDigitsChanged = vm::onDigitsChanged,
                                onPeriodChanged = vm::onPeriodChanged,
                                onAlgorithmChanged = vm::onAlgorithmChanged,
                                onBack = { route = AppRoute.IMPORT_OTP_URI },
                                onSave = {
                                    saveQrLikeAccount(vm, state, repository, coroutineScope) {
                                        loadAccounts()
                                        route = AppRoute.LOBBY
                                    }
                                },
                            )
                        }
                    }

                    AppRoute.SCAN_QR -> {
                        val state by scanQrViewModel.state.collectAsState()
                        ScanQrScreen(
                            state = state,
                            onCameraPermissionChanged = scanQrViewModel::onCameraPermissionChanged,
                            onCameraStarting = scanQrViewModel::onCameraStarting,
                            onCameraReady = scanQrViewModel::onCameraReady,
                            onCameraError = scanQrViewModel::onCameraError,
                            onQrDetected = { rawValue ->
                                QrScanResultHandler(
                                    onAccountDetailsReady = { prefill ->
                                        qrAccountDetailsViewModel = AccountDetailsViewModel(prefill.toQrAccountResult())
                                        scanQrViewModel.onQrDetected()
                                        route = AppRoute.QR_ACCOUNT_DETAILS
                                    },
                                    onInvalidQr = scanQrViewModel::onCameraError,
                                ).handle(rawValue)
                            },
                            onCancelScan = {
                                scanQrViewModel.onScanCancelled()
                                route = AppRoute.ADD_ACCOUNT
                            },
                        )
                    }

                    AppRoute.QR_ACCOUNT_DETAILS -> {
                        val vm = qrAccountDetailsViewModel
                        if (vm == null) {
                            route = AppRoute.ADD_ACCOUNT
                        } else {
                            val state by vm.state.collectAsState()
                            AccountDetailsScreen(
                                state = state,
                                onNameChanged = vm::onNameChanged,
                                onEmailChanged = vm::onEmailChanged,
                                onSecretChanged = vm::onSecretChanged,
                                onDigitsChanged = vm::onDigitsChanged,
                                onPeriodChanged = vm::onPeriodChanged,
                                onAlgorithmChanged = vm::onAlgorithmChanged,
                                onBack = { route = AppRoute.SCAN_QR },
                                onSave = {
                                    saveQrLikeAccount(vm, state, repository, coroutineScope) {
                                        loadAccounts()
                                        route = AppRoute.LOBBY
                                    }
                                },
                            )
                        }
                    }

                    AppRoute.ENTER_SETUP_KEY -> {
                        val state by enterSetupKeyViewModel.state.collectAsState()
                        EnterSetupKeyScreen(
                            state = state,
                            onNameChanged = enterSetupKeyViewModel::onNameChanged,
                            onEmailChanged = enterSetupKeyViewModel::onEmailChanged,
                            onSecretChanged = enterSetupKeyViewModel::onSecretChanged,
                            onDigitsChanged = enterSetupKeyViewModel::onDigitsChanged,
                            onPeriodChanged = enterSetupKeyViewModel::onPeriodChanged,
                            onAlgorithmChanged = enterSetupKeyViewModel::onAlgorithmChanged,
                            onBack = { route = AppRoute.ADD_ACCOUNT },
                            onSave = {
                                enterSetupKeyViewModel.prepareSave()
                                    .onFailure { e -> enterSetupKeyViewModel.onSaveFailed(e.message ?: "Invalid account details.") }
                                    .onSuccess { request ->
                                        enterSetupKeyViewModel.onSaveStarted()
                                        coroutineScope.launch {
                                            runCatching { repository.save(AccountRecordMapper.from(request)) }
                                                .onSuccess {
                                                    enterSetupKeyViewModel.onSaveCompleted()
                                                    loadAccounts()
                                                    route = AppRoute.LOBBY
                                                }
                                                .onFailure { e ->
                                                    enterSetupKeyViewModel.onSaveFailed(e.message ?: "Could not save account.")
                                                }
                                        }
                                    }
                            },
                        )
                    }
                }

                val showUpdateDialog =
                    (availableUpdate != null || isDownloadingUpdate || updateReady) &&
                        !updateDialogHidden

                if (showUpdateDialog) {
                    val update = availableUpdate
                    val canInstall = AppUpdateManager.canInstall(this@MainActivity)
                    val sizeLine = AppUpdateManager.formatMegabytes(totalBytes)
                    val remainingLine = AppUpdateManager.formatMegabytes(
                        (totalBytes - downloadedBytes).coerceAtLeast(0L),
                    )

                    AlertDialog(
                        // The download page stays put while it runs: it is the only
                        // place the progress is shown. The download stops if the app
                        // is closed, so the page is not dismissable while it runs.
                        onDismissRequest = { if (!isDownloadingUpdate) updateDialogHidden = true },
                        title = {
                            Text(
                                when {
                                    updateReady -> "Update ready to install"
                                    isDownloadingUpdate -> "Downloading update"
                                    else -> "New update available"
                                },
                            )
                        },
                        text = {
                            val body = when {
                                updateReady ->
                                    "$sizeLine downloaded. Tap Install to open the " +
                                        "system installer."

                                isDownloadingUpdate ->
                                    "Downloaded " +
                                        "${AppUpdateManager.formatMegabytes(downloadedBytes)} " +
                                        "of $sizeLine · $remainingLine left"

                                else -> "Download size $sizeLine"
                            }
                            val permissionNote =
                                if (canInstall) "" else "\n\nIndoone is not allowed to " +
                                    "install updates yet."
                            Text(body + permissionNote)
                        },
                        confirmButton = {
                            when {
                                updateReady && canInstall -> TextButton(
                                    onClick = {
                                        AppUpdateManager.installApk(
                                            this@MainActivity,
                                            AppUpdateManager.apkFile(this@MainActivity),
                                        )
                                    },
                                ) {
                                    Text("Install")
                                }

                                updateReady -> TextButton(
                                    onClick = {
                                        AppUpdateManager.openInstallPermissionSettings(
                                            this@MainActivity,
                                        )
                                    },
                                ) {
                                    Text("Allow installing")
                                }

                                !isDownloadingUpdate && update != null -> TextButton(
                                    onClick = { startUpdateDownload(update) },
                                ) {
                                    Text("Update")
                                }
                            }
                        },
                        dismissButton = {
                            // Hidden while the download runs: this page is where the
                            // progress lives, and this download stops with the app.
                            if (!isDownloadingUpdate) {
                                TextButton(onClick = { updateDialogHidden = true }) {
                                    Text("Later")
                                }
                            }
                        },
                    )
                }

                // If the app closed on its own last time, say so and show why.
                // Without this there is nothing to look at: the app has no crash
                // reporting, so the reason was lost every time.
                var lastCrash by remember {
                    mutableStateOf(CrashLogStore.last(this@MainActivity))
                }
                if (lastCrash != null) {
                    AlertDialog(
                        onDismissRequest = {
                            CrashLogStore.clear(this@MainActivity)
                            lastCrash = null
                        },
                        title = { Text("Indoone closed unexpectedly") },
                        text = {
                            Text(
                                CrashLogStore.summary(lastCrash.orEmpty()) +
                                    "\n\nPlease screenshot this and send it.",
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    CrashLogStore.clear(this@MainActivity)
                                    lastCrash = null
                                },
                            ) {
                                Text("Close")
                            }
                        },
                    )
                }

                if (menuOpen) {
                    MenuDrawer(
                        onDismiss = { menuOpen = false },
                        onAccounts = { navigate(AppRoute.LOBBY) },
                        onFavorites = { navigate(AppRoute.FAVORITES) },
                        onTrash = { menuToast("Trash is coming next") },
                        onSecurity = { navigate(AppRoute.SECURITY) },
                        onTerms = { menuToast("Terms of Use is coming next") },
                        onPrivacy = { navigate(AppRoute.PRIVACY_POLICY) },
                        onAbout = { navigate(AppRoute.SETTINGS) },
                        onLock = {
                            settingsOpenAppLock = true
                            navigate(AppRoute.SETTINGS)
                        },
                        onDangerZone = { menuToast("Danger Zone is coming next") },
                        onLogout = { menuToast("Log out is coming next") },
                    )
                }
            }
        }
    }

    private fun configureSystemBarsDirectly() {
        // Keep the system bars aligned with the app's light lavender-purple palette.
        window.statusBarColor = Color.rgb(248, 244, 255)
        window.navigationBarColor = Color.rgb(248, 244, 255)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }
}

@Composable
private fun FavoritesRoute(
    repository: com.indoone.lobby.accounts.AccountRepository,
    onBack: () -> Unit,
    onAccountClick: (AccountRecord) -> Unit,
    onToggleFavorite: (AccountRecord) -> Unit,
    onAccountsClick: () -> Unit,
    onLobbyClick: () -> Unit,
    onVibeClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    var accounts by remember { mutableStateOf<List<AccountRecord>>(emptyList()) }
    LaunchedEffect(repository) {
        accounts = runCatching { repository.getAll() }.getOrDefault(emptyList())
    }
    FavoritesScreen(
        accounts = accounts,
        onBack = onBack,
        onAccountClick = onAccountClick,
        onToggleFavorite = onToggleFavorite,
        onAccountsClick = onAccountsClick,
        onLobbyClick = onLobbyClick,
        onVibeClick = onVibeClick,
        onSettingsClick = onSettingsClick,
    )
}

private fun saveQrLikeAccount(
    viewModel: AccountDetailsViewModel,
    state: com.indoone.lobby.accounts.addaccount.scanqr.accountdetails.AccountDetailsState,
    repository: com.indoone.lobby.accounts.AccountRepository,
    scope: kotlinx.coroutines.CoroutineScope,
    onSuccess: () -> Unit,
) {
    viewModel.prepareSave()
        .onFailure { error -> viewModel.onSaveFailed(error.message ?: "Invalid account details.") }
        .onSuccess {
            viewModel.onSaveStarted()
            scope.launch {
                AccountSaveCoordinator(repository).save(state)
                    .onSuccess {
                        viewModel.onSaveCompleted()
                        onSuccess()
                    }
                    .onFailure { error -> viewModel.onSaveFailed(error.message ?: "Could not save account.") }
            }
        }
}

private fun QrManualPrefill.toQrAccountResult(): com.indoone.lobby.accounts.addaccount.scanqr.QrAccountResult =
    com.indoone.lobby.accounts.addaccount.scanqr.QrAccountResult(
        name = name,
        email = email,
        secret = secret,
        algorithm = algorithm,
        digits = digits,
        period = period,
        provider = provider,
        service = service,
    )

private fun List<AccountRecord>.toUiAccounts(nowMillis: Long = System.currentTimeMillis()): List<AccountItem> =
    map { record ->
        val period = record.period.coerceAtLeast(1)
        val nowSeconds = nowMillis / 1000L
        val elapsed = (nowSeconds % period).toInt()
        val secondsRemaining = (period - elapsed).coerceIn(1, period)
        val code = runCatching {
            AccountTotpGenerator.generate(record.secret, nowMillis, period, record.digits, record.algorithm)
        }.getOrDefault("------")
        AccountItem(
            id = record.id,
            name = record.name,
            email = record.email,
            code = code,
            secondsRemaining = secondsRemaining,
            periodSeconds = period,
            favorite = record.favorite,
            icon = record.icon.ifBlank { record.name.firstOrNull()?.uppercase() ?: "?" },
            serviceClass = record.cls,
        )
    }
