package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.CandlestickChart
import androidx.compose.material.icons.outlined.PrecisionManufacturing
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.firebase.TradingRepository
import com.example.data.market.MarketDataRepository
import com.example.data.market.MarketPreferencesStore
import com.example.data.model.AssetClass
import com.example.data.model.ExchangeVenue
import com.example.data.model.MarketTarget
import com.example.data.security.ExchangeCredentialVault
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutUser
import com.example.ui.screens.BotsScreen
import com.example.ui.screens.CreateBotSheet
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DepositWithdrawSheet
import com.example.ui.screens.MarketsScreen
import com.example.ui.screens.RiskAndSettingsScreen
import com.example.ui.screens.WalletTransferMode
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.QuantPulseTheme
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.TradingViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

object Routes {
    const val OVERVIEW = "overview"
    const val BOTS = "bots"
    const val MARKETS = "markets"
    const val RISK = "risk"
}

private data class NavTab(
    val label: String,
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QuantPulseTheme {
                AppNavigation()
            }
        }
    }
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            QuantPulseTerminalShell(
                currentUserId = user.uid,
                userEmail = user.email
            )
        }
    }
}

@Composable
private fun QuantPulseTerminalShell(
    currentUserId: String,
    userEmail: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: TradingViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                TradingViewModel(
                    tradingRepository = TradingRepository(db, Firebase.auth),
                    marketRepository = MarketDataRepository(),
                    credentialVault = ExchangeCredentialVault(app),
                    currentUserId = currentUserId,
                    marketPreferencesStore = MarketPreferencesStore(app)
                )
            }
        }
    )

    val botsState by viewModel.botsState.collectAsStateWithLifecycle()
    val tradesState by viewModel.tradesState.collectAsStateWithLifecycle()
    val riskProfile by viewModel.riskProfile.collectAsStateWithLifecycle()
    val exchanges by viewModel.exchangesState.collectAsStateWithLifecycle()
    val marketFeed by viewModel.marketFeed.collectAsStateWithLifecycle()
    val analytics by viewModel.analytics.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val selectedMarketKey by viewModel.selectedMarketKey.collectAsStateWithLifecycle()
    val pinnedMarketKeys by viewModel.pinnedMarketKeys.collectAsStateWithLifecycle()
    val preferredChartStyle by viewModel.preferredChartStyle.collectAsStateWithLifecycle()
    val wallet by viewModel.walletState.collectAsStateWithLifecycle()
    val walletTransactions by viewModel.walletTransactionsState.collectAsStateWithLifecycle()
    val isWalletSubmitting by viewModel.isWalletSubmitting.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusBanner) {
        statusBanner?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    var showCreateBotSheet by remember { mutableStateOf(false) }
    var sheetInitialTargets by remember {
        mutableStateOf(listOf(MarketTarget(AssetClass.CRYPTO, ExchangeVenue.BINANCE, "BTC/USDT")))
    }
    var showWalletSheet by remember { mutableStateOf(false) }
    var walletSheetMode by remember { mutableStateOf(WalletTransferMode.DEPOSIT) }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestRoute = backStackEntry?.destination?.route ?: ""

    val tabs = remember {
        listOf(
            NavTab("Overview", Routes.OVERVIEW, Icons.Filled.Analytics, Icons.Outlined.Analytics, "nav_overview"),
            NavTab("Bots", Routes.BOTS, Icons.Filled.PrecisionManufacturing, Icons.Outlined.PrecisionManufacturing, "nav_bots"),
            NavTab("Markets", Routes.MARKETS, Icons.Filled.CandlestickChart, Icons.Outlined.CandlestickChart, "nav_markets"),
            NavTab("Risk & API", Routes.RISK, Icons.Filled.Shield, Icons.Outlined.Shield, "nav_risk")
        )
    }

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(Routes.OVERVIEW) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = ObsidianBg,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = SurfaceDark,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        tabs.forEach { tab ->
                            val selected = currentDestRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = ElectricCyan,
                                    selectedTextColor = ElectricCyan,
                                    indicatorColor = SurfaceElevated,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .let { base ->
                        if (useNavRail) base.windowInsetsPadding(WindowInsets.statusBars) else base
                    }
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = SurfaceDark,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        tabs.forEach { tab ->
                            val selected = currentDestRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = ElectricCyan,
                                    selectedTextColor = ElectricCyan,
                                    indicatorColor = SurfaceElevated,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    NavHost(
                        navController = navController,
                        startDestination = Routes.OVERVIEW
                    ) {
                        composable(Routes.OVERVIEW) {
                            DashboardScreen(
                                analytics = analytics,
                                marketFeed = marketFeed,
                                selectedMarketKey = selectedMarketKey,
                                pinnedMarketKeys = pinnedMarketKeys,
                                chartStyle = preferredChartStyle,
                                botsState = botsState,
                                tradesState = tradesState,
                                riskProfile = riskProfile,
                                wallet = wallet,
                                walletTransactions = walletTransactions,
                                onSelectMarketAsset = viewModel::selectMarketAsset,
                                onTogglePinMarket = viewModel::togglePinnedMarket,
                                onChartStyleChange = viewModel::setChartStyle,
                                onRefreshMarkets = viewModel::refreshMarketsNow,
                                onEmergencyHaltAll = viewModel::triggerEmergencyHaltAll,
                                onResetCircuitBreaker = viewModel::resetCircuitBreaker,
                                onOpenDeposit = {
                                    walletSheetMode = WalletTransferMode.DEPOSIT
                                    showWalletSheet = true
                                },
                                onOpenWithdraw = {
                                    walletSheetMode = WalletTransferMode.WITHDRAW
                                    showWalletSheet = true
                                },
                                onDeployBotOnAsset = { asset ->
                                    sheetInitialTargets = listOf(
                                        MarketTarget(asset.assetClass, asset.exchange, asset.symbol)
                                    )
                                    showCreateBotSheet = true
                                },
                                onNavigateToBots = { navigateToTab(Routes.BOTS) },
                                onOpenCreateBot = {
                                    val pinnedTargets = marketFeed.assets
                                        .filter { it.marketKey in pinnedMarketKeys }
                                        .map { MarketTarget(it.assetClass, it.exchange, it.symbol) }
                                    sheetInitialTargets = pinnedTargets.ifEmpty {
                                        listOf(MarketTarget(AssetClass.CRYPTO, ExchangeVenue.BINANCE, "BTC/USDT"))
                                    }
                                    showCreateBotSheet = true
                                }
                            )
                        }
                        composable(Routes.BOTS) {
                            BotsScreen(
                                botsState = botsState,
                                marketFeed = marketFeed,
                                onToggleBotStatus = viewModel::toggleBotStatus,
                                onEvaluateBotNow = viewModel::evaluateBotNow,
                                onClosePositionNow = viewModel::closeBotPositionNow,
                                onDeleteBot = viewModel::deleteBot,
                                onOpenCreateBot = {
                                    val pinnedTargets = marketFeed.assets
                                        .filter { it.marketKey in pinnedMarketKeys }
                                        .map { MarketTarget(it.assetClass, it.exchange, it.symbol) }
                                    sheetInitialTargets = pinnedTargets.ifEmpty {
                                        listOf(MarketTarget(AssetClass.CRYPTO, ExchangeVenue.BINANCE, "BTC/USDT"))
                                    }
                                    showCreateBotSheet = true
                                }
                            )
                        }
                        composable(Routes.MARKETS) {
                            MarketsScreen(
                                marketFeed = marketFeed,
                                selectedMarketKey = selectedMarketKey,
                                pinnedMarketKeys = pinnedMarketKeys,
                                chartStyle = preferredChartStyle,
                                onChartStyleChange = viewModel::setChartStyle,
                                onSelectAsset = viewModel::selectMarketAsset,
                                onTogglePinMarket = viewModel::togglePinnedMarket,
                                onClearPinnedMarkets = viewModel::clearPinnedMarkets,
                                onRefreshMarkets = viewModel::refreshMarketsNow,
                                onDeployBotOnMarkets = { targets ->
                                    sheetInitialTargets = targets.ifEmpty {
                                        listOf(MarketTarget(AssetClass.CRYPTO, ExchangeVenue.BINANCE, "BTC/USDT"))
                                    }
                                    showCreateBotSheet = true
                                }
                            )
                        }
                        composable(Routes.RISK) {
                            RiskAndSettingsScreen(
                                riskProfile = riskProfile,
                                exchanges = exchanges,
                                userEmail = userEmail,
                                onSaveRiskProfile = viewModel::saveRiskProfile,
                                onTriggerEmergencyHalt = viewModel::triggerEmergencyHaltAll,
                                onResetCircuitBreaker = viewModel::resetCircuitBreaker,
                                onConfigureExchange = viewModel::configureExchangeCredentials,
                                onDisconnectExchange = viewModel::disconnectExchange,
                                onSignOut = {
                                    signOutUser(
                                        credentialManager = credentialManager,
                                        scope = scope
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateBotSheet) {
        CreateBotSheet(
            riskProfile = riskProfile,
            initialTargets = sheetInitialTargets,
            onDismiss = { showCreateBotSheet = false },
            onCreateBots = { name, targets, strats, stratMode, mode, cap, lev, sl, tp, trail, maxPos, rsiOs, rsiOb, grid ->
                viewModel.createBots(
                    name = name,
                    targets = targets,
                    strategies = strats,
                    strategyMode = stratMode,
                    executionMode = mode,
                    allocatedCapitalUsd = cap,
                    leverage = lev,
                    stopLossPct = sl,
                    takeProfitPct = tp,
                    trailingStopPct = trail,
                    maxPositionPct = maxPos,
                    rsiOversold = rsiOs,
                    rsiOverbought = rsiOb,
                    gridLevels = grid
                )
                showCreateBotSheet = false
                navigateToTab(Routes.BOTS)
            }
        )
    }

    if (showWalletSheet) {
        DepositWithdrawSheet(
            initialMode = walletSheetMode,
            wallet = wallet,
            isSubmitting = isWalletSubmitting,
            onDismiss = { if (!isWalletSubmitting) showWalletSheet = false },
            onConfirmDeposit = { amount, method, note ->
                viewModel.depositFunds(amount, method, note) { success, _ ->
                    if (success) showWalletSheet = false
                }
            },
            onConfirmWithdraw = { amount, method, note ->
                viewModel.withdrawFunds(amount, method, note) { success, _ ->
                    if (success) showWalletSheet = false
                }
            }
        )
    }
}
