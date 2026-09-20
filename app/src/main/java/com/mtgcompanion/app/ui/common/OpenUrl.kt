package com.mtgcompanion.app.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Hands [url] to the browser — shop links, rulings, anything that leaves the app. */
fun openUrl(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
