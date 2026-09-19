package com.mtgcompanion.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.mtgcompanion.app.data.Money
import com.mtgcompanion.app.data.Prices

/** How prices show right now (Settings → Prices), re-read whenever the currency or its rate changes. */
@Composable
fun rememberMoney(): Money = Prices.money.collectAsState().value
