package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.AssetClass
import com.example.data.model.ExchangeConnection
import com.example.data.model.ExchangeVenue
import com.example.data.model.RiskProfile
import com.example.data.model.StrategyCombineMode
import com.example.data.model.TradeExecution
import com.example.data.model.TradingBot
import com.example.data.model.WalletAccount
import com.example.data.model.WalletTransaction
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class TradingRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun botsCollection(uid: String) =
        db.collection("users").document(uid).collection("bots")

    private fun tradesCollection(uid: String) =
        db.collection("users").document(uid).collection("trades")

    private fun riskCollection(uid: String) =
        db.collection("users").document(uid).collection("riskConfig")

    private fun exchangesCollection(uid: String) =
        db.collection("users").document(uid).collection("exchanges")

    private fun walletCollection(uid: String) =
        db.collection("users").document(uid).collection("wallet")

    private fun walletTransactionsCollection(uid: String) =
        db.collection("users").document(uid).collection("walletTransactions")

    fun observeBots(userId: String): Flow<List<TradingBot>> = flow {
        val path = "users/$userId/bots"
        emitAll(
            botsCollection(userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        TradingBot::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observeTrades(userId: String): Flow<List<TradeExecution>> = flow {
        val path = "users/$userId/trades"
        emitAll(
            tradesCollection(userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        TradeExecution::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending {
                        (it.createdAt?.seconds ?: 0L) * 1_000_000_000L + (it.createdAt?.nanoseconds ?: 0)
                    }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observeRiskProfile(userId: String): Flow<RiskProfile?> = flow {
        val docRef = riskCollection(userId).document(GLOBAL_RISK_DOC_ID)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            RiskProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    logStreamError(error, OperationType.GET, docRef.path, userId)
                    throw error
                }
        )
    }

    fun observeExchanges(userId: String): Flow<List<ExchangeConnection>> = flow {
        val path = "users/$userId/exchanges"
        emitAll(
            exchangesCollection(userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ExchangeConnection::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.exchangeName }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observeWallet(userId: String): Flow<WalletAccount?> = flow {
        val docRef = walletCollection(userId).document(WALLET_SUMMARY_DOC_ID)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            WalletAccount::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    logStreamError(error, OperationType.GET, docRef.path, userId)
                    throw error
                }
        )
    }

    fun observeWalletTransactions(userId: String): Flow<List<WalletTransaction>> = flow {
        val path = "users/$userId/walletTransactions"
        emitAll(
            walletTransactionsCollection(userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        WalletTransaction::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending {
                        (it.createdAt?.seconds ?: 0L) * 1_000_000_000L + (it.createdAt?.nanoseconds ?: 0)
                    }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    suspend fun createBot(bot: TradingBot): Result<String> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        val botId = bot.id.takeIf { it.isNotBlank() } ?: "bot_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val docRef = botsCollection(uid).document(botId)
        val effectiveStrategies = bot.effectiveStrategies().map { it.id }
        val combineMode = StrategyCombineMode.fromId(bot.strategyMode).id
        val payload = mapOf(
            "userId" to uid,
            "name" to bot.name.trim().take(60),
            "assetClass" to bot.assetClass,
            "exchange" to bot.exchange,
            "symbol" to bot.symbol.trim().take(20),
            "strategy" to effectiveStrategies.first(),
            "strategies" to effectiveStrategies,
            "strategyMode" to combineMode,
            "executionMode" to bot.executionMode,
            "status" to bot.status,
            "allocatedCapitalUsd" to bot.allocatedCapitalUsd.coerceIn(10.0, 10_000_000.0),
            "leverage" to bot.leverage.coerceIn(1.0, 20.0),
            "stopLossPct" to bot.stopLossPct.coerceIn(0.1, 50.0),
            "takeProfitPct" to bot.takeProfitPct.coerceIn(0.1, 500.0),
            "trailingStopPct" to bot.trailingStopPct.coerceIn(0.0, 25.0),
            "maxPositionPct" to bot.maxPositionPct.coerceIn(1.0, 100.0),
            "rsiOversold" to bot.rsiOversold.coerceIn(5L, 45L),
            "rsiOverbought" to bot.rsiOverbought.coerceIn(55L, 95L),
            "gridLevels" to bot.gridLevels.coerceIn(2L, 50L),
            "realizedPnlUsd" to bot.realizedPnlUsd,
            "totalTrades" to bot.totalTrades.coerceAtLeast(0L),
            "winningTrades" to bot.winningTrades.coerceAtLeast(0L),
            "openPositionSide" to bot.openPositionSide,
            "openEntryPrice" to bot.openEntryPrice.coerceAtLeast(0.0),
            "openQuantity" to bot.openQuantity.coerceAtLeast(0.0),
            "peakUnrealizedPct" to bot.peakUnrealizedPct,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(botId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateBot(bot: TradingBot): Result<Unit> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        require(bot.id.isNotBlank()) { "Bot ID must not be blank for update" }
        val docRef = botsCollection(uid).document(bot.id)
        val effectiveStrategies = bot.effectiveStrategies().map { it.id }
        val combineMode = StrategyCombineMode.fromId(bot.strategyMode).id
        val normalizedPositionSide = when (bot.openPositionSide) {
            "NONE", "LONG", "SHORT" -> bot.openPositionSide
            "BUY" -> "LONG"
            "SELL" -> "SHORT"
            else -> "NONE"
        }
        val updates = mapOf(
            "name" to bot.name.trim().ifBlank { bot.symbol }.take(60),
            "assetClass" to bot.assetClass,
            "exchange" to bot.exchange,
            "symbol" to bot.symbol.trim().take(20),
            "strategy" to effectiveStrategies.first(),
            "strategies" to effectiveStrategies,
            "strategyMode" to combineMode,
            "executionMode" to bot.executionMode,
            "status" to bot.status,
            "allocatedCapitalUsd" to bot.allocatedCapitalUsd.coerceIn(10.0, 10_000_000.0),
            "leverage" to bot.leverage.coerceIn(1.0, 20.0),
            "stopLossPct" to bot.stopLossPct.coerceIn(0.1, 50.0),
            "takeProfitPct" to bot.takeProfitPct.coerceIn(0.1, 500.0),
            "trailingStopPct" to bot.trailingStopPct.coerceIn(0.0, 25.0),
            "maxPositionPct" to bot.maxPositionPct.coerceIn(1.0, 100.0),
            "rsiOversold" to bot.rsiOversold.coerceIn(5L, 45L),
            "rsiOverbought" to bot.rsiOverbought.coerceIn(55L, 95L),
            "gridLevels" to bot.gridLevels.coerceIn(2L, 50L),
            "realizedPnlUsd" to bot.realizedPnlUsd,
            "totalTrades" to bot.totalTrades.coerceAtLeast(0L),
            "winningTrades" to bot.winningTrades.coerceAtLeast(0L),
            "openPositionSide" to normalizedPositionSide,
            "openEntryPrice" to bot.openEntryPrice.coerceAtLeast(0.0),
            "openQuantity" to bot.openQuantity.coerceAtLeast(0.0),
            "peakUnrealizedPct" to bot.peakUnrealizedPct,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateBotStatus(botId: String, newStatus: String): Result<Unit> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        val docRef = botsCollection(uid).document(botId)
        return try {
            docRef.update(
                mapOf(
                    "status" to newStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteBot(botId: String): Result<Unit> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        val docRef = botsCollection(uid).document(botId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun recordTradeExecution(trade: TradeExecution): Result<String> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        val tradeId = trade.id.takeIf { it.isNotBlank() } ?: "trd_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val docRef = tradesCollection(uid).document(tradeId)
        val payload = mapOf(
            "userId" to uid,
            "botId" to trade.botId,
            "botName" to trade.botName.take(60),
            "assetClass" to trade.assetClass,
            "exchange" to trade.exchange.take(40),
            "symbol" to trade.symbol.take(20),
            "side" to trade.side,
            "orderType" to trade.orderType,
            "executionMode" to trade.executionMode,
            "price" to trade.price.coerceAtLeast(0.000001),
            "quantity" to trade.quantity.coerceAtLeast(0.000001),
            "notionalUsd" to trade.notionalUsd.coerceAtLeast(0.0),
            "feeUsd" to trade.feeUsd.coerceAtLeast(0.0),
            "pnlUsd" to trade.pnlUsd,
            "reason" to trade.reason.ifBlank { "Automated signal execution" }.take(160),
            "createdAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(tradeId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun saveRiskProfile(profile: RiskProfile): Result<Unit> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        val docRef = riskCollection(uid).document(GLOBAL_RISK_DOC_ID)
        return try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "maxDailyLossUsd" to profile.maxDailyLossUsd.coerceIn(10.0, 1_000_000.0),
                        "maxPortfolioDrawdownPct" to profile.maxPortfolioDrawdownPct.coerceIn(1.0, 90.0),
                        "maxOpenPositions" to profile.maxOpenPositions.coerceIn(1L, 50L),
                        "maxLeverageCap" to profile.maxLeverageCap.coerceIn(1.0, 20.0),
                        "defaultStopLossPct" to profile.defaultStopLossPct.coerceIn(0.1, 50.0),
                        "defaultTakeProfitPct" to profile.defaultTakeProfitPct.coerceIn(0.1, 500.0),
                        "defaultTrailingStopPct" to profile.defaultTrailingStopPct.coerceIn(0.0, 25.0),
                        "slippageTolerancePct" to profile.slippageTolerancePct.coerceIn(0.01, 10.0),
                        "cooldownMinutes" to profile.cooldownMinutes.coerceIn(0L, 1440L),
                        "circuitBreakerTriggered" to profile.circuitBreakerTriggered,
                        "autoPauseOnHighVolatility" to profile.autoPauseOnHighVolatility,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val payload = mapOf(
                    "userId" to uid,
                    "maxDailyLossUsd" to profile.maxDailyLossUsd.coerceIn(10.0, 1_000_000.0),
                    "maxPortfolioDrawdownPct" to profile.maxPortfolioDrawdownPct.coerceIn(1.0, 90.0),
                    "maxOpenPositions" to profile.maxOpenPositions.coerceIn(1L, 50L),
                    "maxLeverageCap" to profile.maxLeverageCap.coerceIn(1.0, 20.0),
                    "defaultStopLossPct" to profile.defaultStopLossPct.coerceIn(0.1, 50.0),
                    "defaultTakeProfitPct" to profile.defaultTakeProfitPct.coerceIn(0.1, 500.0),
                    "defaultTrailingStopPct" to profile.defaultTrailingStopPct.coerceIn(0.0, 25.0),
                    "slippageTolerancePct" to profile.slippageTolerancePct.coerceIn(0.01, 10.0),
                    "cooldownMinutes" to profile.cooldownMinutes.coerceIn(0L, 1440L),
                    "circuitBreakerTriggered" to profile.circuitBreakerTriggered,
                    "autoPauseOnHighVolatility" to profile.autoPauseOnHighVolatility,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun upsertExchangeConnection(connection: ExchangeConnection): Result<Unit> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        val exId = connection.id.takeIf { it.isNotBlank() }
            ?: "ex_${connection.exchangeName.lowercase().replace(Regex("[^a-z0-9]"), "_")}"
        val docRef = exchangesCollection(uid).document(exId)
        return try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "exchangeName" to connection.exchangeName.take(40),
                        "assetClass" to connection.assetClass,
                        "apiKeyLabel" to connection.apiKeyLabel.take(40),
                        "isPaperMode" to connection.isPaperMode,
                        "isConnected" to connection.isConnected,
                        "latencyMs" to connection.latencyMs.coerceIn(0L, 60_000L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val payload = mapOf(
                    "userId" to uid,
                    "exchangeName" to connection.exchangeName.take(40),
                    "assetClass" to connection.assetClass,
                    "apiKeyLabel" to connection.apiKeyLabel.take(40),
                    "isPaperMode" to connection.isPaperMode,
                    "isConnected" to connection.isConnected,
                    "latencyMs" to connection.latencyMs.coerceIn(0L, 60_000L),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun ensureDefaultSetupForUser(): Result<Unit> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        return try {
            val riskRef = riskCollection(uid).document(GLOBAL_RISK_DOC_ID)
            if (!riskRef.get().await().exists()) {
                saveRiskProfile(RiskProfile(userId = uid))
            }
            val walletRef = walletCollection(uid).document(WALLET_SUMMARY_DOC_ID)
            if (!walletRef.get().await().exists()) {
                walletRef.set(
                    mapOf(
                        "userId" to uid,
                        "availableCashUsd" to 10_000.0,
                        "totalDepositedUsd" to 10_000.0,
                        "totalWithdrawnUsd" to 0.0,
                        "currency" to "USD",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            val existingExchanges = exchangesCollection(uid).get().await()
            val existingIds = existingExchanges.documents.map { it.id }.toSet()
            val defaults = listOf(
                ExchangeConnection(
                    id = "ex_binance",
                    userId = uid,
                    exchangeName = ExchangeVenue.BINANCE.displayName,
                    assetClass = AssetClass.CRYPTO.id,
                    apiKeyLabel = "paper_live_feed",
                    isPaperMode = true,
                    isConnected = true,
                    latencyMs = 34L
                ),
                ExchangeConnection(
                    id = "ex_coinbase",
                    userId = uid,
                    exchangeName = ExchangeVenue.COINBASE.displayName,
                    assetClass = AssetClass.CRYPTO.id,
                    apiKeyLabel = "paper_live_feed",
                    isPaperMode = true,
                    isConnected = true,
                    latencyMs = 42L
                ),
                ExchangeConnection(
                    id = "ex_kraken",
                    userId = uid,
                    exchangeName = ExchangeVenue.KRAKEN.displayName,
                    assetClass = AssetClass.CRYPTO.id,
                    apiKeyLabel = "paper_live_feed",
                    isPaperMode = true,
                    isConnected = true,
                    latencyMs = 51L
                ),
                ExchangeConnection(
                    id = "ex_bybit",
                    userId = uid,
                    exchangeName = ExchangeVenue.BYBIT.displayName,
                    assetClass = AssetClass.CRYPTO.id,
                    apiKeyLabel = "paper_live_feed",
                    isPaperMode = true,
                    isConnected = true,
                    latencyMs = 47L
                ),
                ExchangeConnection(
                    id = "ex_okx",
                    userId = uid,
                    exchangeName = ExchangeVenue.OKX.displayName,
                    assetClass = AssetClass.CRYPTO.id,
                    apiKeyLabel = "paper_live_feed",
                    isPaperMode = true,
                    isConnected = true,
                    latencyMs = 49L
                ),
                ExchangeConnection(
                    id = "ex_metals_spot",
                    userId = uid,
                    exchangeName = ExchangeVenue.METALS_SPOT.displayName,
                    assetClass = AssetClass.METALS.id,
                    apiKeyLabel = "spot_bullion_feed",
                    isPaperMode = true,
                    isConnected = true,
                    latencyMs = 68L
                ),
                ExchangeConnection(
                    id = "ex_forex_interbank",
                    userId = uid,
                    exchangeName = ExchangeVenue.FOREX_INTERBANK.displayName,
                    assetClass = AssetClass.FOREX.id,
                    apiKeyLabel = "fx_interbank_feed",
                    isPaperMode = true,
                    isConnected = true,
                    latencyMs = 54L
                ),
                ExchangeConnection(
                    id = "ex_fxpro",
                    userId = uid,
                    exchangeName = ExchangeVenue.FXPRO.displayName,
                    assetClass = AssetClass.FOREX.id,
                    apiKeyLabel = "fxpro_ctrader_feed",
                    isPaperMode = true,
                    isConnected = true,
                    latencyMs = 46L
                ),
                ExchangeConnection(
                    id = "ex_octafx",
                    userId = uid,
                    exchangeName = ExchangeVenue.OCTAFX.displayName,
                    assetClass = AssetClass.FOREX.id,
                    apiKeyLabel = "octafx_mt5_feed",
                    isPaperMode = true,
                    isConnected = true,
                    latencyMs = 52L
                )
            )
            defaults.filter { it.id !in existingIds }.forEach { upsertExchangeConnection(it) }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid")
            Result.failure(e)
        }
    }

    suspend fun depositFunds(
        amountUsd: Double,
        method: String,
        note: String = ""
    ): Result<WalletTransaction> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        if (amountUsd < 1.0 || amountUsd > 1_000_000.0) {
            return Result.failure(IllegalArgumentException("Deposit amount must be between $1 and $1,000,000."))
        }
        val cleanMethod = method.trim().ifBlank { "ACH Bank Transfer" }.take(40)
        val cleanNote = note.trim().take(120)
        val walletRef = walletCollection(uid).document(WALLET_SUMMARY_DOC_ID)
        val txId = "wtx_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val txRef = walletTransactionsCollection(uid).document(txId)

        return try {
            val createdTx = db.runTransaction { transaction ->
                val snapshot = transaction.get(walletRef)
                val currentCash = snapshot.getDouble("availableCashUsd") ?: 10_000.0
                val currentDeposited = snapshot.getDouble("totalDepositedUsd") ?: currentCash
                val currentWithdrawn = snapshot.getDouble("totalWithdrawnUsd") ?: 0.0
                val newCash = (currentCash + amountUsd).coerceAtMost(100_000_000.0)
                val newDeposited = (currentDeposited + amountUsd).coerceAtMost(100_000_000.0)

                if (snapshot.exists()) {
                    transaction.update(
                        walletRef,
                        mapOf(
                            "availableCashUsd" to newCash,
                            "totalDepositedUsd" to newDeposited,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    transaction.set(
                        walletRef,
                        mapOf(
                            "userId" to uid,
                            "availableCashUsd" to newCash,
                            "totalDepositedUsd" to newDeposited,
                            "totalWithdrawnUsd" to currentWithdrawn,
                            "currency" to "USD",
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                val txPayload = mapOf(
                    "userId" to uid,
                    "type" to "DEPOSIT",
                    "method" to cleanMethod,
                    "amountUsd" to amountUsd,
                    "balanceAfterUsd" to newCash,
                    "status" to "COMPLETED",
                    "note" to cleanNote,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                transaction.set(txRef, txPayload)
                WalletTransaction(
                    id = txId,
                    userId = uid,
                    type = "DEPOSIT",
                    method = cleanMethod,
                    amountUsd = amountUsd,
                    balanceAfterUsd = newCash,
                    status = "COMPLETED",
                    note = cleanNote
                )
            }.await()
            Result.success(createdTx)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, txRef.path)
            Result.failure(e)
        }
    }

    suspend fun withdrawFunds(
        amountUsd: Double,
        method: String,
        note: String = ""
    ): Result<WalletTransaction> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        if (amountUsd < 1.0 || amountUsd > 1_000_000.0) {
            return Result.failure(IllegalArgumentException("Withdraw amount must be between $1 and $1,000,000."))
        }
        val cleanMethod = method.trim().ifBlank { "ACH Bank Transfer" }.take(40)
        val cleanNote = note.trim().take(120)
        val walletRef = walletCollection(uid).document(WALLET_SUMMARY_DOC_ID)
        val txId = "wtx_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val txRef = walletTransactionsCollection(uid).document(txId)

        return try {
            val createdTx = db.runTransaction { transaction ->
                val snapshot = transaction.get(walletRef)
                val currentCash = if (snapshot.exists()) (snapshot.getDouble("availableCashUsd") ?: 0.0) else 10_000.0
                val currentDeposited = if (snapshot.exists()) (snapshot.getDouble("totalDepositedUsd") ?: currentCash) else 10_000.0
                val currentWithdrawn = if (snapshot.exists()) (snapshot.getDouble("totalWithdrawnUsd") ?: 0.0) else 0.0

                if (amountUsd > currentCash) {
                    throw IllegalStateException("Insufficient available balance ($${"%.2f".format(currentCash)} available).")
                }
                val newCash = (currentCash - amountUsd).coerceAtLeast(0.0)
                val newWithdrawn = (currentWithdrawn + amountUsd).coerceAtMost(100_000_000.0)

                if (snapshot.exists()) {
                    transaction.update(
                        walletRef,
                        mapOf(
                            "availableCashUsd" to newCash,
                            "totalWithdrawnUsd" to newWithdrawn,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    transaction.set(
                        walletRef,
                        mapOf(
                            "userId" to uid,
                            "availableCashUsd" to newCash,
                            "totalDepositedUsd" to currentDeposited,
                            "totalWithdrawnUsd" to newWithdrawn,
                            "currency" to "USD",
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                val txPayload = mapOf(
                    "userId" to uid,
                    "type" to "WITHDRAW",
                    "method" to cleanMethod,
                    "amountUsd" to amountUsd,
                    "balanceAfterUsd" to newCash,
                    "status" to "COMPLETED",
                    "note" to cleanNote,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                transaction.set(txRef, txPayload)
                WalletTransaction(
                    id = txId,
                    userId = uid,
                    type = "WITHDRAW",
                    method = cleanMethod,
                    amountUsd = amountUsd,
                    balanceAfterUsd = newCash,
                    status = "COMPLETED",
                    note = cleanNote
                )
            }.await()
            Result.success(createdTx)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, txRef.path)
            Result.failure(e)
        }
    }

    private fun logStreamError(
        error: Throwable,
        operationType: OperationType,
        path: String,
        expectedUid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
    }

    companion object {
        const val GLOBAL_RISK_DOC_ID = "global"
        const val WALLET_SUMMARY_DOC_ID = "summary"
    }
}
