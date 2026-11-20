const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-quantpul-06af1115-a300-426f-93ca-5361f95d6bdd";
const ALICE_UID = "alice_quant_1";
const BOB_UID = "bob_quant_2";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function tsNow() {
  return new Date(Date.now() - 1000);
}

function validBotPayload(uid) {
  return {
    userId: uid,
    name: "BTC Momentum Alpha",
    assetClass: "CRYPTO",
    exchange: "BINANCE",
    symbol: "BTC/USDT",
    strategy: "EMA_CROSS",
    executionMode: "PAPER",
    status: "RUNNING",
    allocatedCapitalUsd: 2500.0,
    leverage: 2.0,
    stopLossPct: 2.5,
    takeProfitPct: 6.0,
    trailingStopPct: 1.5,
    maxPositionPct: 25.0,
    rsiOversold: 30,
    rsiOverbought: 70,
    gridLevels: 10,
    realizedPnlUsd: 0.0,
    totalTrades: 0,
    winningTrades: 0,
    openPositionSide: "NONE",
    openEntryPrice: 0.0,
    openQuantity: 0.0,
    peakUnrealizedPct: 0.0,
    createdAt: tsNow(),
    updatedAt: tsNow(),
  };
}

function validTradePayload(uid, botId) {
  return {
    userId: uid,
    botId,
    botName: "BTC Momentum Alpha",
    assetClass: "CRYPTO",
    exchange: "BINANCE",
    symbol: "BTC/USDT",
    side: "BUY",
    orderType: "MARKET",
    executionMode: "PAPER",
    price: 91250.5,
    quantity: 0.02,
    notionalUsd: 1825.01,
    feeUsd: 1.82,
    pnlUsd: 0.0,
    reason: "EMA(9) crossed above EMA(21)",
    createdAt: tsNow(),
  };
}

function validRiskPayload(uid) {
  return {
    userId: uid,
    maxDailyLossUsd: 500.0,
    maxPortfolioDrawdownPct: 12.0,
    maxOpenPositions: 6,
    maxLeverageCap: 5.0,
    defaultStopLossPct: 2.5,
    defaultTakeProfitPct: 6.0,
    defaultTrailingStopPct: 1.5,
    slippageTolerancePct: 0.35,
    cooldownMinutes: 5,
    circuitBreakerTriggered: false,
    autoPauseOnHighVolatility: true,
    createdAt: tsNow(),
    updatedAt: tsNow(),
  };
}

function validExchangePayload(uid) {
  return {
    userId: uid,
    exchangeName: "Binance",
    assetClass: "CRYPTO",
    apiKeyLabel: "paper_engine",
    isPaperMode: true,
    isConnected: true,
    latencyMs: 38,
    createdAt: tsNow(),
    updatedAt: tsNow(),
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot list or write bots, trades, riskConfig, or exchanges", async () => {
  const db = testEnv.unauthenticatedContext().firestore(DATABASE_ID);
  await assertFails(db.collection(`users/${ALICE_UID}/bots`).get());
  await assertFails(db.collection(`users/${ALICE_UID}/trades`).get());
  await assertFails(db.collection(`users/${ALICE_UID}/riskConfig`).get());
  await assertFails(db.collection(`users/${ALICE_UID}/exchanges`).get());
  await assertFails(db.doc(`users/${ALICE_UID}/bots/bot_1`).set(validBotPayload(ALICE_UID)));
});

test("Authenticated user can CRUD own bots and rejects ghost fields or invalid risk bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const botRef = aliceDb.doc(`users/${ALICE_UID}/bots/bot_1`);

  await assertSucceeds(botRef.set(validBotPayload(ALICE_UID)));
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/bots`).get());
  await assertSucceeds(botRef.update({ status: "PAUSED", updatedAt: tsNow() }));

  // Ghost field attack
  await assertFails(aliceDb.doc(`users/${ALICE_UID}/bots/bot_ghost`).set({
    ...validBotPayload(ALICE_UID),
    isAdmin: true,
  }));

  // Out-of-bounds leverage attack
  await assertFails(aliceDb.doc(`users/${ALICE_UID}/bots/bot_lev`).set({
    ...validBotPayload(ALICE_UID),
    leverage: 100.0,
  }));

  // Invalid doc ID attack
  await assertFails(aliceDb.doc(`users/${ALICE_UID}/bots/bad$id`).set(validBotPayload(ALICE_UID)));

  // FxPro + Martingale, OctaFX + 21 TradingView, and Grid Bot allowlisted combos
  await assertSucceeds(aliceDb.doc(`users/${ALICE_UID}/bots/bot_fxpro_mart`).set({
    ...validBotPayload(ALICE_UID),
    name: "EUR Martingale",
    assetClass: "FOREX",
    exchange: "FXPRO",
    symbol: "EUR/USD",
    strategy: "MARTINGALE",
  }));
  await assertSucceeds(aliceDb.doc(`users/${ALICE_UID}/bots/bot_octafx_tv21`).set({
    ...validBotPayload(ALICE_UID),
    name: "XAU TV21",
    assetClass: "METALS",
    exchange: "OCTAFX",
    symbol: "XAU/USD",
    strategy: "TRADINGVIEW_21",
  }));
  await assertSucceeds(aliceDb.doc(`users/${ALICE_UID}/bots/bot_grid`).set({
    ...validBotPayload(ALICE_UID),
    strategy: "GRID",
  }));

  // Multi-strategy bot (ALL confluence mode) and updates
  await assertSucceeds(aliceDb.doc(`users/${ALICE_UID}/bots/bot_multi`).set({
    ...validBotPayload(ALICE_UID),
    name: "Multi Strat Confluence",
    strategy: "MARTINGALE",
    strategies: ["MARTINGALE", "TRADINGVIEW_21", "GRID"],
    strategyMode: "ALL",
  }));
  await assertSucceeds(aliceDb.doc(`users/${ALICE_UID}/bots/bot_multi`).update({
    strategies: ["EMA_CROSS", "RSI_REVERSION"],
    strategyMode: "ANY",
    updatedAt: tsNow(),
  }));

  // Unknown broker or strategy rejected, plus invalid strategies list or mode
  await assertFails(aliceDb.doc(`users/${ALICE_UID}/bots/bot_bad_ex`).set({
    ...validBotPayload(ALICE_UID),
    exchange: "UNKNOWN_BROKER",
  }));
  await assertFails(aliceDb.doc(`users/${ALICE_UID}/bots/bot_bad_strat`).set({
    ...validBotPayload(ALICE_UID),
    strategy: "UNKNOWN_STRAT",
  }));
  await assertFails(aliceDb.doc(`users/${ALICE_UID}/bots/bot_empty_strats`).set({
    ...validBotPayload(ALICE_UID),
    strategies: [],
  }));
  await assertFails(aliceDb.doc(`users/${ALICE_UID}/bots/bot_bad_strats_el`).set({
    ...validBotPayload(ALICE_UID),
    strategies: ["EMA_CROSS", "FAKE_STRAT"],
  }));
  await assertFails(aliceDb.doc(`users/${ALICE_UID}/bots/bot_bad_mode`).set({
    ...validBotPayload(ALICE_UID),
    strategyMode: "XOR",
  }));

  // Partial updates for position lifecycle (LONG open -> peak -> NONE close) and legacy backfill
  await assertSucceeds(botRef.update({
    openPositionSide: "LONG",
    openEntryPrice: 91500.0,
    openQuantity: 0.025,
    peakUnrealizedPct: 1.8,
    strategies: ["EMA_CROSS"],
    strategyMode: "ANY",
    updatedAt: tsNow(),
  }));
  await assertSucceeds(botRef.update({
    openPositionSide: "NONE",
    openEntryPrice: 0.0,
    openQuantity: 0.0,
    peakUnrealizedPct: 0.0,
    realizedPnlUsd: 42.5,
    totalTrades: 1,
    winningTrades: 1,
    updatedAt: tsNow(),
  }));

  // Invalid openPositionSide ('BUY' instead of 'LONG'/'SHORT'/'NONE') rejected
  await assertFails(botRef.update({
    openPositionSide: "BUY",
    updatedAt: tsNow(),
  }));

  await assertSucceeds(botRef.delete());
});

test("Cross-user isolation blocks Bob from reading or modifying Alice's bots, riskConfig, trades, or exchanges", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);

  await assertSucceeds(aliceDb.doc(`users/${ALICE_UID}/bots/bot_1`).set(validBotPayload(ALICE_UID)));
  await assertSucceeds(aliceDb.doc(`users/${ALICE_UID}/riskConfig/global`).set(validRiskPayload(ALICE_UID)));
  await assertSucceeds(aliceDb.doc(`users/${ALICE_UID}/trades/trade_1`).set(validTradePayload(ALICE_UID, "bot_1")));
  await assertSucceeds(aliceDb.doc(`users/${ALICE_UID}/exchanges/binance`).set(validExchangePayload(ALICE_UID)));

  await assertFails(bobDb.collection(`users/${ALICE_UID}/bots`).get());
  await assertFails(bobDb.doc(`users/${ALICE_UID}/bots/bot_1`).get());
  await assertFails(bobDb.doc(`users/${ALICE_UID}/bots/bot_1`).update({ status: "PAUSED", updatedAt: tsNow() }));
  await assertFails(bobDb.doc(`users/${ALICE_UID}/riskConfig/global`).get());
  await assertFails(bobDb.doc(`users/${ALICE_UID}/trades/trade_1`).get());
  await assertFails(bobDb.doc(`users/${ALICE_UID}/exchanges/binance`).get());

  // Identity spoofing attempt by Bob under Bob's path with Alice's userId
  await assertFails(bobDb.doc(`users/${BOB_UID}/bots/spoof_1`).set(validBotPayload(ALICE_UID)));
});

test("Trades are append-only (updates and deletes rejected)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const tradeRef = aliceDb.doc(`users/${ALICE_UID}/trades/trade_1`);

  await assertSucceeds(tradeRef.set(validTradePayload(ALICE_UID, "bot_1")));
  await assertFails(tradeRef.update({ pnlUsd: 9999.0 }));
  await assertFails(tradeRef.delete());
});

test("RiskProfile and ExchangeConnection enforce valid bounds and immutable createdAt", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const riskRef = aliceDb.doc(`users/${ALICE_UID}/riskConfig/global`);
  const exRef = aliceDb.doc(`users/${ALICE_UID}/exchanges/ex_binance`);

  await assertSucceeds(riskRef.set(validRiskPayload(ALICE_UID)));
  await assertSucceeds(riskRef.update({ circuitBreakerTriggered: true, updatedAt: tsNow() }));
  await assertFails(riskRef.update({ createdAt: tsNow(), updatedAt: tsNow() }));

  await assertSucceeds(exRef.set(validExchangePayload(ALICE_UID)));
  await assertSucceeds(exRef.update({ isConnected: false, updatedAt: tsNow() }));
});

test("Wallet and walletTransactions enforce owner isolation, balance bounds, and append-only ledger", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  const walletRef = aliceDb.doc(`users/${ALICE_UID}/wallet/summary`);
  const txRef = aliceDb.doc(`users/${ALICE_UID}/walletTransactions/wtx_1`);

  await assertSucceeds(walletRef.set({
    userId: ALICE_UID,
    availableCashUsd: 10000.0,
    totalDepositedUsd: 10000.0,
    totalWithdrawnUsd: 0.0,
    currency: "USD",
    createdAt: tsNow(),
    updatedAt: tsNow(),
  }));

  // Negative balance rejected
  await assertFails(walletRef.update({ availableCashUsd: -50.0, updatedAt: tsNow() }));

  await assertSucceeds(txRef.set({
    userId: ALICE_UID,
    type: "DEPOSIT",
    method: "ACH Bank Transfer",
    amountUsd: 1500.0,
    balanceAfterUsd: 11500.0,
    status: "COMPLETED",
    note: "Top-up",
    createdAt: tsNow(),
  }));

  // Append-only ledger & cross-user isolation
  await assertFails(txRef.update({ amountUsd: 99999.0 }));
  await assertFails(txRef.delete());
  await assertFails(bobDb.doc(`users/${ALICE_UID}/wallet/summary`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/walletTransactions`).get());
});
