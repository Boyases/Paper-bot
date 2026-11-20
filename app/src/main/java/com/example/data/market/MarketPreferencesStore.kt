package com.example.data.market

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.components.PriceChartStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.marketPrefsDataStore by preferencesDataStore(name = "quantpulse_market_prefs")

class MarketPreferencesStore(private val context: Context) {

    val pinnedMarketKeys: Flow<Set<String>> = context.marketPrefsDataStore.data.map { prefs ->
        prefs[KEY_PINNED_MARKETS] ?: emptySet()
    }

    val preferredChartStyle: Flow<PriceChartStyle> = context.marketPrefsDataStore.data.map { prefs ->
        val raw = prefs[KEY_CHART_STYLE] ?: PriceChartStyle.CANDLES.name
        PriceChartStyle.entries.firstOrNull { it.name == raw } ?: PriceChartStyle.CANDLES
    }

    suspend fun togglePinnedMarket(marketKey: String) {
        context.marketPrefsDataStore.edit { prefs ->
            val current = prefs[KEY_PINNED_MARKETS] ?: emptySet()
            prefs[KEY_PINNED_MARKETS] = if (marketKey in current) {
                current - marketKey
            } else {
                current + marketKey
            }
        }
    }

    suspend fun setPinnedMarkets(keys: Set<String>) {
        context.marketPrefsDataStore.edit { prefs ->
            prefs[KEY_PINNED_MARKETS] = keys
        }
    }

    suspend fun setChartStyle(style: PriceChartStyle) {
        context.marketPrefsDataStore.edit { prefs ->
            prefs[KEY_CHART_STYLE] = style.name
        }
    }

    private companion object {
        val KEY_PINNED_MARKETS = stringSetPreferencesKey("pinned_market_keys")
        val KEY_CHART_STYLE = stringPreferencesKey("preferred_chart_style")
    }
}
