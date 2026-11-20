package com.example.data.firebase

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.AssetClass
import com.example.data.model.BotStatus
import com.example.data.model.BotStrategy
import com.example.data.model.ExecutionMode
import com.example.data.model.ExchangeVenue
import com.example.data.model.RiskProfile
import com.example.data.model.StrategyCombineMode
import com.example.data.model.TradeExecution
import com.example.data.model.TradingBot
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class TradingRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createAndObserveBots_authenticatedOwner_succeedsAndEmitsUpdates() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = TradingRepository(firestore, auth)
        val botId = "bot_${UUID.randomUUID().toString().replace("-", "").take(12)}"

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createBot(
                TradingBot(
                    id = botId,
                    name = "BTC Trend Alpha",
                    assetClass = AssetClass.CRYPTO.id,
                    exchange = ExchangeVenue.BINANCE.id,
                    symbol = "BTC/USDT",
                    strategy = BotStrategy.EMA_CROSS.id,
                    executionMode = ExecutionMode.PAPER.id,
                    status = BotStatus.RUNNING.id,
                    allocatedCapitalUsd = 3000.0
                )
            )
        }
        assertTrue("Expected bot creation to succeed: ${createResult.exceptionOrNull()}", createResult.isSuccess)

        val bots = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeBots(uid).first { list -> list.any { it.id == botId } }
        }
        assertTrue(bots.any { it.id == botId && it.name == "BTC Trend Alpha" })

        val statusUpdate = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateBotStatus(botId, BotStatus.PAUSED.id)
        }
        assertTrue(statusUpdate.isSuccess)

        val savedBot = bots.first { it.id == botId }
        val openPositionUpdate = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateBot(
                savedBot.copy(
                    status = BotStatus.RUNNING.id,
                    openPositionSide = "LONG",
                    openEntryPrice = 91500.0,
                    openQuantity = 0.03,
                    peakUnrealizedPct = 0.0
                )
            )
        }
        assertTrue("Expected opening LONG position updateBot to succeed: ${openPositionUpdate.exceptionOrNull()}", openPositionUpdate.isSuccess)

        val openedBot = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeBots(uid).first { list ->
                list.any { it.id == botId && it.openPositionSide == "LONG" }
            }.first { it.id == botId }
        }
        assertEquals(91500.0, openedBot.openEntryPrice, 0.01)

        val closePositionUpdate = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateBot(
                openedBot.copy(
                    openPositionSide = "NONE",
                    openEntryPrice = 0.0,
                    openQuantity = 0.0,
                    peakUnrealizedPct = 0.0,
                    realizedPnlUsd = 55.25,
                    totalTrades = 1L,
                    winningTrades = 1L
                )
            )
        }
        assertTrue("Expected closing position updateBot to succeed: ${closePositionUpdate.exceptionOrNull()}", closePositionUpdate.isSuccess)

        val deleteResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.deleteBot(botId)
        }
        assertTrue(deleteResult.isSuccess)
    }

    @Test
    fun createBots_fxproMartingaleAndOctafxTradingView21_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = TradingRepository(firestore, auth)
        val fxproBotId = "bot_fxpro_${UUID.randomUUID().toString().replace("-", "").take(8)}"
        val octafxBotId = "bot_octafx_${UUID.randomUUID().toString().replace("-", "").take(8)}"

        val fxproRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createBot(
                TradingBot(
                    id = fxproBotId,
                    name = "FxPro EUR Martingale",
                    assetClass = AssetClass.FOREX.id,
                    exchange = ExchangeVenue.FXPRO.id,
                    symbol = "EUR/USD",
                    strategy = BotStrategy.MARTINGALE.id,
                    executionMode = ExecutionMode.PAPER.id,
                    status = BotStatus.RUNNING.id,
                    allocatedCapitalUsd = 4000.0
                )
            )
        }
        assertTrue("Expected FxPro Martingale bot creation to succeed: ${fxproRes.exceptionOrNull()}", fxproRes.isSuccess)

        val octafxRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createBot(
                TradingBot(
                    id = octafxBotId,
                    name = "OctaFX Gold TV21",
                    assetClass = AssetClass.METALS.id,
                    exchange = ExchangeVenue.OCTAFX.id,
                    symbol = "XAU/USD",
                    strategy = BotStrategy.TRADINGVIEW_21.id,
                    executionMode = ExecutionMode.PAPER.id,
                    status = BotStatus.RUNNING.id,
                    allocatedCapitalUsd = 3500.0
                )
            )
        }
        assertTrue("Expected OctaFX TV21 bot creation to succeed: ${octafxRes.exceptionOrNull()}", octafxRes.isSuccess)

        val bots = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeBots(uid).first { list ->
                list.any { it.id == fxproBotId } && list.any { it.id == octafxBotId }
            }
        }
        assertTrue(bots.any { it.id == fxproBotId && it.exchange == "FXPRO" && it.strategy == "MARTINGALE" })
        assertTrue(bots.any { it.id == octafxBotId && it.exchange == "OCTAFX" && it.strategy == "TRADINGVIEW_21" })
    }

    @Test
    fun createBot_multiStrategyWithCombineMode_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = TradingRepository(firestore, auth)
        val multiBotId = "bot_multi_${UUID.randomUUID().toString().replace("-", "").take(8)}"

        val createRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createBot(
                TradingBot(
                    id = multiBotId,
                    name = "BTC Multi Alpha",
                    assetClass = AssetClass.CRYPTO.id,
                    exchange = ExchangeVenue.BINANCE.id,
                    symbol = "BTC/USDT",
                    strategy = BotStrategy.EMA_CROSS.id,
                    strategies = listOf(
                        BotStrategy.EMA_CROSS.id,
                        BotStrategy.TRADINGVIEW_21.id,
                        BotStrategy.MARTINGALE.id
                    ),
                    strategyMode = StrategyCombineMode.ALL.id,
                    executionMode = ExecutionMode.PAPER.id,
                    status = BotStatus.RUNNING.id,
                    allocatedCapitalUsd = 5000.0
                )
            )
        }
        assertTrue("Expected multi-strategy bot creation to succeed: ${createRes.exceptionOrNull()}", createRes.isSuccess)

        val bots = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeBots(uid).first { list -> list.any { it.id == multiBotId } }
        }
        val saved = bots.first { it.id == multiBotId }
        assertEquals(3, saved.effectiveStrategies().size)
        assertEquals(StrategyCombineMode.ALL.id, saved.strategyMode)
    }

    @Test
    fun recordTradeAndRiskProfile_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = TradingRepository(firestore, auth)
        val tradeId = "trd_${UUID.randomUUID().toString().replace("-", "").take(12)}"

        val tradeResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.recordTradeExecution(
                TradeExecution(
                    id = tradeId,
                    botId = "bot_alpha",
                    botName = "Gold RSI Guard",
                    assetClass = AssetClass.METALS.id,
                    exchange = ExchangeVenue.METALS_SPOT.id,
                    symbol = "XAU/USD",
                    side = "BUY",
                    orderType = "MARKET",
                    executionMode = ExecutionMode.PAPER.id,
                    price = 2645.20,
                    quantity = 0.5,
                    notionalUsd = 1322.60,
                    feeUsd = 1.32,
                    pnlUsd = 0.0,
                    reason = "RSI(14)=28.4 below oversold 30"
                )
            )
        }
        assertTrue("Expected trade record to succeed: ${tradeResult.exceptionOrNull()}", tradeResult.isSuccess)

        val trades = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeTrades(uid).first { list -> list.any { it.id == tradeId } }
        }
        assertTrue(trades.any { it.id == tradeId && it.symbol == "XAU/USD" })

        val riskResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveRiskProfile(
                RiskProfile(
                    userId = uid,
                    maxDailyLossUsd = 600.0,
                    maxPortfolioDrawdownPct = 10.0,
                    maxOpenPositions = 5L,
                    maxLeverageCap = 3.0
                )
            )
        }
        assertTrue("Expected risk profile save to succeed: ${riskResult.exceptionOrNull()}", riskResult.isSuccess)
    }

    @Test
    fun depositAndWithdrawFunds_authenticatedOwner_updatesWalletAndAppendsLedger() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = TradingRepository(firestore, auth)

        val depositRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.depositFunds(amountUsd = 1500.0, method = "ACH Bank Transfer", note = "Margin top-up")
        }
        assertTrue("Expected deposit to succeed: ${depositRes.exceptionOrNull()}", depositRes.isSuccess)
        assertEquals(11500.0, depositRes.getOrThrow().balanceAfterUsd, 0.01)

        val withdrawRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.withdrawFunds(amountUsd = 500.0, method = "Wire Transfer", note = "Profit sweep")
        }
        assertTrue("Expected withdrawal to succeed: ${withdrawRes.exceptionOrNull()}", withdrawRes.isSuccess)
        assertEquals(11000.0, withdrawRes.getOrThrow().balanceAfterUsd, 0.01)

        // Over-withdrawal must fail cleanly
        val overdraftRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.withdrawFunds(amountUsd = 50_000.0, method = "Wire Transfer", note = "Too large")
        }
        assertTrue("Expected overdraft withdrawal to fail", overdraftRes.isFailure)

        val wallet = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeWallet(uid).first { it != null && it.availableCashUsd == 11000.0 }
        }
        assertEquals(11000.0, wallet!!.availableCashUsd, 0.01)

        val ledger = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeWalletTransactions(uid).first { it.size >= 2 }
        }
        assertTrue(ledger.any { it.type == "DEPOSIT" && it.amountUsd == 1500.0 })
        assertTrue(ledger.any { it.type == "WITHDRAW" && it.amountUsd == 500.0 })
    }

    @Test
    fun observeBots_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = TradingRepository(firestore, auth)
        val botId = "bot_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createBot(TradingBot(id = botId, name = "Alice Secret Bot"))
        }.getOrThrow()

        signInTestUser(BOB_EMAIL)
        val bobRepo = TradingRepository(firestore, auth)
        val exception = expectFailure<Exception> {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeBots(aliceUid).first()
            }
        }
        val firestoreException = checkNotNull(exception.unwrapFirestoreException()) {
            "Expected FirebaseFirestoreException, got $exception"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    @Test
    fun observeBots_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = TradingRepository(firestore, auth)

        val exception = expectFailure<Exception> {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeBots("alice_uid_placeholder").first()
            }
        }
        val firestoreException = checkNotNull(exception.unwrapFirestoreException()) {
            "Expected FirebaseFirestoreException, got $exception"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    private suspend inline fun <reified T : Throwable> expectFailure(crossinline block: suspend () -> Unit): T {
        try {
            block()
        } catch (t: Throwable) {
            if (t is T) return t
            fail("Expected ${T::class.java.simpleName}, but caught ${t::class.java.simpleName}: ${t.message}")
        }
        fail("Expected ${T::class.java.simpleName}, but block succeeded")
        throw AssertionError("Unreachable")
    }

    private companion object {
        const val ALICE_EMAIL = "alice@quantpulse.test"
        const val BOB_EMAIL = "bob@quantpulse.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
