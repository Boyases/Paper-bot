package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.TradingRepository
import com.example.data.market.MarketDataRepository
import com.example.data.market.MarketFeedState
import com.example.data.market.MarketPreferencesStore
import com.example.data.model.AssetClass
import com.example.data.model.BotStatus
import com.example.data.model.BotStrategy
import com.example.data.model.ExchangeConnection
import com.example.data.model.ExchangeVenue
import com.example.data.model.ExecutionMode
import com.example.data.model.MarketAsset
import com.example.data.model.MarketTarget
import com.example.data.model.RiskProfile
import com.example.data.model.StrategyCombineMode
import com.example.data.model.TradeExecution
import com.example.data.model.TradingBot
import com.example.data.model.WalletAccount
import com.example.data.model.WalletTransaction
import com.example.data.security.ExchangeCredentialVault
import com.example.domain.engine.BotAutomationEngine
import com.example.domain.engine.EngineDecision
import com.example.domain.engine.PortfolioAnalytics
import com.example.ui.components.PriceChartStyle
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class TradingViewModel(
    private val tradingRepository: TradingRepository,
    private val marketRepository: MarketDataRepository,
    private val credentialVault: ExchangeCredentialVault,
    val currentUserId: String,
    private val marketPreferencesStore: MarketPreferencesStore? = null
) : ViewModel() {

    val marketFeed: StateFlow<MarketFeedState> = marketRepository.feedState

    val botsState: StateFlow<UiState<List<TradingBot>>> = tradingRepository.observeBots(currentUserId)
        .map<List<TradingBot>, UiState<List<TradingBot>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = unwrapFirestoreError(error)
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing bots", root)
            emit(UiState.Error(root.message ?: "Failed to load trading bots"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val tradesState: StateFlow<UiState<List<TradeExecution>>> = tradingRepository.observeTrades(currentUserId)
        .map<List<TradeExecution>, UiState<List<TradeExecution>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = unwrapFirestoreError(error)
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing trades", root)
            emit(UiState.Error(root.message ?: "Failed to load execution log"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val riskProfile: StateFlow<RiskProfile> = tradingRepository.observeRiskProfile(currentUserId)
        .map { it ?: RiskProfile(userId = currentUserId) }
        .catch { error ->
            val firestoreError = unwrapFirestoreError(error)
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing risk profile", firestoreError ?: error)
            emit(RiskProfile(userId = currentUserId))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = RiskProfile(userId = currentUserId)
        )

    val exchangesState: StateFlow<List<ExchangeConnection>> = tradingRepository.observeExchanges(currentUserId)
        .catch { error ->
            val firestoreError = unwrapFirestoreError(error)
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing exchanges", firestoreError ?: error)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val walletState: StateFlow<WalletAccount> = tradingRepository.observeWallet(currentUserId)
        .map { it ?: WalletAccount(userId = currentUserId) }
        .catch { error ->
            val firestoreError = unwrapFirestoreError(error)
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing wallet", firestoreError ?: error)
            emit(WalletAccount(userId = currentUserId))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = WalletAccount(userId = currentUserId)
        )

    val walletTransactionsState: StateFlow<List<WalletTransaction>> = tradingRepository.observeWalletTransactions(currentUserId)
        .catch { error ->
            val firestoreError = unwrapFirestoreError(error)
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing wallet transactions", firestoreError ?: error)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val analytics: StateFlow<PortfolioAnalytics> = combine(
        botsState,
        tradesState,
        marketFeed
    ) { botsUi, tradesUi, feed ->
        val bots = (botsUi as? UiState.Success)?.data ?: emptyList()
        val trades = (tradesUi as? UiState.Success)?.data ?: emptyList()
        val assetMap = feed.assets.associateBy { "${it.exchange.id}:${it.symbol}" }
        BotAutomationEngine.computePortfolioMetrics(bots, trades, assetMap)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = PortfolioAnalytics()
    )

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _selectedMarketKey = MutableStateFlow<String?>(null)
    val selectedMarketKey: StateFlow<String?> = _selectedMarketKey.asStateFlow()

    private val fallbackPinnedMarkets = MutableStateFlow<Set<String>>(emptySet())
    val pinnedMarketKeys: StateFlow<Set<String>> =
        marketPreferencesStore?.pinnedMarketKeys?.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptySet()
        ) ?: fallbackPinnedMarkets.asStateFlow()

    private val fallbackChartStyle = MutableStateFlow(PriceChartStyle.CANDLES)
    val preferredChartStyle: StateFlow<PriceChartStyle> =
        marketPreferencesStore?.preferredChartStyle?.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = PriceChartStyle.CANDLES
        ) ?: fallbackChartStyle.asStateFlow()

    private val _isWalletSubmitting = MutableStateFlow(false)
    val isWalletSubmitting: StateFlow<Boolean> = _isWalletSubmitting.asStateFlow()

    private val lastTradeTimeByBot = mutableMapOf<String, Long>()

    init {
        viewModelScope.launch {
            tradingRepository.ensureDefaultSetupForUser()
        }
        startLiveMarketAndAutomationLoop()
    }

    private fun startLiveMarketAndAutomationLoop() {
        viewModelScope.launch {
            while (isActive) {
                marketRepository.refreshAllMarkets()
                evaluateAllRunningBots()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun refreshMarketsNow() {
        viewModelScope.launch {
            marketRepository.refreshAllMarkets()
            evaluateAllRunningBots()
        }
    }

    fun selectMarketAsset(asset: MarketAsset) {
        _selectedMarketKey.value = asset.marketKey
    }

    fun togglePinnedMarket(asset: MarketAsset) {
        val key = asset.marketKey
        viewModelScope.launch {
            if (marketPreferencesStore != null) {
                marketPreferencesStore.togglePinnedMarket(key)
            } else {
                val cur = fallbackPinnedMarkets.value
                fallbackPinnedMarkets.value = if (key in cur) cur - key else cur + key
            }
        }
    }

    fun clearPinnedMarkets() {
        viewModelScope.launch {
            if (marketPreferencesStore != null) {
                marketPreferencesStore.setPinnedMarkets(emptySet())
            } else {
                fallbackPinnedMarkets.value = emptySet()
            }
        }
    }

    fun setChartStyle(style: PriceChartStyle) {
        viewModelScope.launch {
            if (marketPreferencesStore != null) {
                marketPreferencesStore.setChartStyle(style)
            } else {
                fallbackChartStyle.value = style
            }
        }
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun depositFunds(
        amountUsd: Double,
        method: String,
        note: String,
        onComplete: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            _isWalletSubmitting.value = true
            val res = tradingRepository.depositFunds(amountUsd, method, note)
            _isWalletSubmitting.value = false
            if (res.isSuccess) {
                val tx = res.getOrThrow()
                _statusBanner.value = "Deposited $${"%.2f".format(tx.amountUsd)} via ${tx.method}"
                onComplete(true, null)
            } else {
                val msg = res.exceptionOrNull()?.localizedMessage ?: "Deposit failed"
                _statusBanner.value = msg
                onComplete(false, msg)
            }
        }
    }

    fun withdrawFunds(
        amountUsd: Double,
        method: String,
        note: String,
        onComplete: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            _isWalletSubmitting.value = true
            val res = tradingRepository.withdrawFunds(amountUsd, method, note)
            _isWalletSubmitting.value = false
            if (res.isSuccess) {
                val tx = res.getOrThrow()
                _statusBanner.value = "Withdrew $${"%.2f".format(tx.amountUsd)} via ${tx.method}"
                onComplete(true, null)
            } else {
                val msg = res.exceptionOrNull()?.localizedMessage ?: "Withdrawal failed"
                _statusBanner.value = msg
                onComplete(false, msg)
            }
        }
    }

    fun createBots(
        name: String,
        targets: List<MarketTarget>,
        strategies: List<BotStrategy>,
        strategyMode: StrategyCombineMode,
        executionMode: ExecutionMode,
        allocatedCapitalUsd: Double,
        leverage: Double,
        stopLossPct: Double,
        takeProfitPct: Double,
        trailingStopPct: Double,
        maxPositionPct: Double,
        rsiOversold: Long,
        rsiOverbought: Long,
        gridLevels: Long
    ) {
        val cleanTargets = targets.distinctBy { it.marketKey }
        if (cleanTargets.isEmpty()) return
        val cleanStrategies = strategies.distinct().ifEmpty { listOf(BotStrategy.EMA_CROSS) }
        val strategyIds = cleanStrategies.map { it.id }
        val primaryStrategy = cleanStrategies.first()
        val strategyBadge = if (cleanStrategies.size == 1) {
            primaryStrategy.shortCode
        } else {
            "${cleanStrategies.size}x(${strategyMode.id})"
        }

        viewModelScope.launch {
            val cappedLeverage = leverage.coerceIn(1.0, riskProfile.value.maxLeverageCap)
            val createdIds = mutableListOf<String>()
            var lastError: String? = null

            cleanTargets.forEach { target ->
                val resolvedName = when {
                    name.isBlank() -> "${target.symbol.substringBefore("/")} $strategyBadge Bot"
                    cleanTargets.size == 1 -> name.trim()
                    else -> "${name.trim()} (${target.symbol.substringBefore("/")})"
                }.take(60)
                val bot = TradingBot(
                    userId = currentUserId,
                    name = resolvedName,
                    assetClass = target.assetClass.id,
                    exchange = target.exchange.id,
                    symbol = target.symbol,
                    strategy = primaryStrategy.id,
                    strategies = strategyIds,
                    strategyMode = strategyMode.id,
                    executionMode = executionMode.id,
                    status = BotStatus.RUNNING.id,
                    allocatedCapitalUsd = allocatedCapitalUsd,
                    leverage = cappedLeverage,
                    stopLossPct = stopLossPct,
                    takeProfitPct = takeProfitPct,
                    trailingStopPct = trailingStopPct,
                    maxPositionPct = maxPositionPct,
                    rsiOversold = rsiOversold,
                    rsiOverbought = rsiOverbought,
                    gridLevels = gridLevels
                )
                val res = tradingRepository.createBot(bot)
                if (res.isSuccess) {
                    createdIds += res.getOrThrow()
                } else {
                    lastError = res.exceptionOrNull()?.localizedMessage
                }
            }

            if (createdIds.isNotEmpty()) {
                _statusBanner.value = if (createdIds.size == 1) {
                    val t = cleanTargets.first()
                    "Deployed ${t.symbol} ($strategyBadge) on ${t.exchange.displayName}"
                } else {
                    "Deployed ${createdIds.size} bots ($strategyBadge) across ${createdIds.size} selected markets"
                }
                createdIds.forEach { id -> evaluateSingleBotById(id) }
            } else {
                _statusBanner.value = lastError ?: "Failed to create bot"
            }
        }
    }

    fun createBot(
        name: String,
        assetClass: AssetClass,
        exchange: ExchangeVenue,
        symbol: String,
        strategy: BotStrategy,
        executionMode: ExecutionMode,
        allocatedCapitalUsd: Double,
        leverage: Double,
        stopLossPct: Double,
        takeProfitPct: Double,
        trailingStopPct: Double,
        maxPositionPct: Double,
        rsiOversold: Long,
        rsiOverbought: Long,
        gridLevels: Long
    ) {
        createBots(
            name = name,
            targets = listOf(MarketTarget(assetClass, exchange, symbol)),
            strategies = listOf(strategy),
            strategyMode = StrategyCombineMode.ANY,
            executionMode = executionMode,
            allocatedCapitalUsd = allocatedCapitalUsd,
            leverage = leverage,
            stopLossPct = stopLossPct,
            takeProfitPct = takeProfitPct,
            trailingStopPct = trailingStopPct,
            maxPositionPct = maxPositionPct,
            rsiOversold = rsiOversold,
            rsiOverbought = rsiOverbought,
            gridLevels = gridLevels
        )
    }

    fun toggleBotStatus(bot: TradingBot) {
        viewModelScope.launch {
            if (bot.status != BotStatus.RUNNING.id && riskProfile.value.circuitBreakerTriggered) {
                _statusBanner.value = "Reset Global Circuit Breaker in Risk Settings before starting bots."
                return@launch
            }
            val next = if (bot.status == BotStatus.RUNNING.id) {
                BotStatus.PAUSED.id
            } else {
                BotStatus.RUNNING.id
            }
            tradingRepository.updateBotStatus(bot.id, next)
        }
    }

    fun deleteBot(botId: String) {
        viewModelScope.launch {
            val res = tradingRepository.deleteBot(botId)
            if (res.isSuccess) {
                _statusBanner.value = "Bot removed"
            }
        }
    }

    fun evaluateBotNow(bot: TradingBot) {
        viewModelScope.launch {
            val executed = evaluateBotInternal(bot, forceIgnoreCooldown = true)
            if (!executed) {
                val assetMap = marketFeed.value.assets.associateBy { "${it.exchange.id}:${it.symbol}" }
                val asset = BotAutomationEngine.findMatchingAsset(bot, assetMap)
                _statusBanner.value = if (asset == null) {
                    "Waiting for live market tick on ${bot.symbol}"
                } else {
                    "Checked ${bot.symbol} (${asset.indicators.signalLabel}, RSI ${"%.1f".format(asset.indicators.rsi14)}) — holding"
                }
            }
        }
    }

    fun closeBotPositionNow(bot: TradingBot) {
        viewModelScope.launch {
            if (bot.openPositionSide == "NONE" || bot.openQuantity <= 0.0) return@launch
            val assetMap = marketFeed.value.assets.associateBy { "${it.exchange.id}:${it.symbol}" }
            val asset = BotAutomationEngine.findMatchingAsset(bot, assetMap) ?: run {
                _statusBanner.value = "Live spot quote unavailable to close ${bot.symbol}"
                return@launch
            }
            val price = asset.price
            val unrealized = bot.unrealizedPnlUsd(price)
            val notional = bot.openQuantity * price
            val fee = notional * 0.0008
            val netPnl = unrealized - fee
            val closed = applyClosePosition(
                bot = bot,
                orderType = "MARKET",
                side = "SELL",
                price = price,
                quantity = bot.openQuantity,
                notional = notional,
                fee = fee,
                pnl = netPnl,
                isWin = netPnl > 0.0,
                reason = "Manual terminal market close"
            )
            if (closed) {
                _statusBanner.value = "Closed ${bot.symbol} position (${if (netPnl >= 0) "+" else ""}${"%.2f".format(netPnl)} USD)"
            } else {
                _statusBanner.value = "Failed to close ${bot.symbol} position"
            }
        }
    }

    fun saveRiskProfile(updated: RiskProfile) {
        viewModelScope.launch {
            val res = tradingRepository.saveRiskProfile(updated.copy(userId = currentUserId))
            _statusBanner.value = if (res.isSuccess) {
                "Risk management rules saved to cloud"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Failed to save risk settings"
            }
        }
    }

    fun triggerEmergencyHaltAll() {
        viewModelScope.launch {
            val currentBots = (botsState.value as? UiState.Success)?.data ?: emptyList()
            currentBots.filter { it.status == BotStatus.RUNNING.id }.forEach { bot ->
                tradingRepository.updateBotStatus(bot.id, BotStatus.HALTED_RISK.id)
            }
            tradingRepository.saveRiskProfile(
                riskProfile.value.copy(
                    userId = currentUserId,
                    circuitBreakerTriggered = true
                )
            )
            _statusBanner.value = "KILL SWITCH ACTIVATED: All bots halted"
        }
    }

    fun resetCircuitBreaker() {
        viewModelScope.launch {
            tradingRepository.saveRiskProfile(
                riskProfile.value.copy(
                    userId = currentUserId,
                    circuitBreakerTriggered = false
                )
            )
            _statusBanner.value = "Circuit breaker reset. Bots can now be resumed."
        }
    }

    fun configureExchangeCredentials(
        connection: ExchangeConnection,
        apiKey: String,
        apiSecret: String,
        isPaperMode: Boolean
    ) {
        viewModelScope.launch {
            val label = if (apiKey.isNotBlank() && apiSecret.isNotBlank()) {
                credentialVault.saveCredentials(connection.id, apiKey, apiSecret)
            } else {
                connection.apiKeyLabel.ifBlank { "paper_live_feed" }
            }
            tradingRepository.upsertExchangeConnection(
                connection.copy(
                    apiKeyLabel = label,
                    isPaperMode = isPaperMode,
                    isConnected = true
                )
            )
            _statusBanner.value = "${connection.exchangeName} updated (${if (isPaperMode) "Paper" else "Live API"} mode)"
        }
    }

    fun disconnectExchange(connection: ExchangeConnection) {
        viewModelScope.launch {
            credentialVault.clearCredentials(connection.id)
            tradingRepository.upsertExchangeConnection(
                connection.copy(
                    apiKeyLabel = "paper_live_feed",
                    isPaperMode = true,
                    isConnected = true
                )
            )
            _statusBanner.value = "${connection.exchangeName} API credentials cleared (reverted to Paper Feed)"
        }
    }

    private suspend fun evaluateAllRunningBots() {
        val bots = (botsState.value as? UiState.Success)?.data ?: return
        bots.filter { it.status == BotStatus.RUNNING.id }.forEach { bot ->
            evaluateBotInternal(bot, forceIgnoreCooldown = false)
        }
    }

    private suspend fun evaluateSingleBotById(botId: String) {
        delay(400)
        val bots = (botsState.value as? UiState.Success)?.data ?: return
        val bot = bots.firstOrNull { it.id == botId } ?: return
        evaluateBotInternal(bot, forceIgnoreCooldown = true)
    }

    private suspend fun evaluateBotInternal(
        bot: TradingBot,
        forceIgnoreCooldown: Boolean
    ): Boolean {
        val feed = marketFeed.value
        if (feed.assets.isEmpty()) return false
        val assetMap = feed.assets.associateBy { "${it.exchange.id}:${it.symbol}" }
        val asset = BotAutomationEngine.findMatchingAsset(bot, assetMap) ?: return false

        val allBots = (botsState.value as? UiState.Success)?.data ?: emptyList()
        val allTrades = (tradesState.value as? UiState.Success)?.data ?: emptyList()
        val openPositions = allBots.count { it.openPositionSide != "NONE" }
        val todayStartSec = startOfTodayEpochSeconds()
        val todayRealized = allTrades
            .filter { (it.createdAt?.seconds ?: 0L) >= todayStartSec }
            .sumOf { it.pnlUsd }
        val totalUnrealized = allBots.sumOf { b ->
            val m = BotAutomationEngine.findMatchingAsset(b, assetMap)
            if (m != null) b.unrealizedPnlUsd(m.price) else 0.0
        }

        val consecutiveLosses = allTrades
            .asSequence()
            .filter { it.botId == bot.id && it.side == "SELL" }
            .takeWhile { it.pnlUsd <= 0.0 }
            .count()

        val lastTradeMs = if (forceIgnoreCooldown) 0L else (lastTradeTimeByBot[bot.id] ?: 0L)
        val decision = BotAutomationEngine.evaluate(
            bot = bot,
            asset = asset,
            riskProfile = riskProfile.value,
            openPositionsCount = openPositions,
            todayRealizedPnlUsd = todayRealized,
            totalUnrealizedPnlUsd = totalUnrealized,
            lastBotTradeEpochMs = lastTradeMs,
            nowEpochMs = System.currentTimeMillis(),
            consecutiveLosses = consecutiveLosses
        )

        return when (decision) {
            is EngineDecision.OpenPosition -> {
                val positionSide = if (decision.side == "SHORT" || decision.side == "SELL") {
                    "SHORT"
                } else {
                    "LONG"
                }
                val updateRes = tradingRepository.updateBot(
                    bot.copy(
                        openPositionSide = positionSide,
                        openEntryPrice = decision.price,
                        openQuantity = decision.quantity,
                        peakUnrealizedPct = 0.0
                    )
                )
                if (updateRes.isFailure) {
                    return false
                }
                tradingRepository.recordTradeExecution(
                    TradeExecution(
                        userId = currentUserId,
                        botId = bot.id,
                        botName = bot.name,
                        assetClass = bot.assetClass,
                        exchange = bot.exchange,
                        symbol = bot.symbol,
                        side = decision.side,
                        orderType = "MARKET",
                        executionMode = bot.executionMode,
                        price = decision.price,
                        quantity = decision.quantity,
                        notionalUsd = decision.notionalUsd,
                        feeUsd = decision.feeUsd,
                        pnlUsd = 0.0,
                        reason = decision.reason
                    )
                )
                lastTradeTimeByBot[bot.id] = System.currentTimeMillis()
                _statusBanner.value = "${bot.name}: ${decision.side} ${bot.symbol} @ ${decision.price}"
                true
            }

            is EngineDecision.ClosePosition -> {
                val closed = applyClosePosition(
                    bot = bot,
                    orderType = decision.orderType,
                    side = decision.side,
                    price = decision.price,
                    quantity = decision.quantity,
                    notional = decision.notionalUsd,
                    fee = decision.feeUsd,
                    pnl = decision.pnlUsd,
                    isWin = decision.isWin,
                    reason = decision.reason
                )
                if (!closed) return false
                lastTradeTimeByBot[bot.id] = System.currentTimeMillis()
                _statusBanner.value = "${bot.name}: ${decision.orderType} ${bot.symbol} (${if (decision.pnlUsd >= 0) "+" else ""}${"%.2f".format(decision.pnlUsd)} USD)"
                true
            }

            is EngineDecision.HaltBotForRisk -> {
                tradingRepository.updateBotStatus(bot.id, BotStatus.HALTED_RISK.id)
                _statusBanner.value = "${bot.name} halted: ${decision.reason}"
                true
            }

            is EngineDecision.TripGlobalCircuitBreaker -> {
                triggerEmergencyHaltAll()
                _statusBanner.value = "Circuit Breaker Tripped: ${decision.reason}"
                true
            }

            is EngineDecision.NoAction -> {
                if (decision.updatedPeakPct != null) {
                    tradingRepository.updateBot(bot.copy(peakUnrealizedPct = decision.updatedPeakPct))
                }
                false
            }
        }
    }

    private suspend fun applyClosePosition(
        bot: TradingBot,
        orderType: String,
        side: String,
        price: Double,
        quantity: Double,
        notional: Double,
        fee: Double,
        pnl: Double,
        isWin: Boolean,
        reason: String
    ): Boolean {
        val updateRes = tradingRepository.updateBot(
            bot.copy(
                openPositionSide = "NONE",
                openEntryPrice = 0.0,
                openQuantity = 0.0,
                peakUnrealizedPct = 0.0,
                realizedPnlUsd = bot.realizedPnlUsd + pnl,
                totalTrades = bot.totalTrades + 1L,
                winningTrades = bot.winningTrades + if (isWin) 1L else 0L
            )
        )
        if (updateRes.isFailure) {
            return false
        }
        tradingRepository.recordTradeExecution(
            TradeExecution(
                userId = currentUserId,
                botId = bot.id,
                botName = bot.name,
                assetClass = bot.assetClass,
                exchange = bot.exchange,
                symbol = bot.symbol,
                side = side,
                orderType = orderType,
                executionMode = bot.executionMode,
                price = price,
                quantity = quantity,
                notionalUsd = notional,
                feeUsd = fee,
                pnlUsd = pnl,
                reason = reason
            )
        )
        return true
    }

    private fun startOfTodayEpochSeconds(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis / 1000L
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()

    private companion object {
        const val TAG = "TradingViewModel"
        const val POLL_INTERVAL_MS = 8000L
    }
}
