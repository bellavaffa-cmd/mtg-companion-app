package com.mtgcompanion.app.update

import android.content.Context
import android.os.Build

/**
 * Where this copy of the app came from, and so whether it may update itself.
 *
 * The GitHub APKs update themselves ([UpdateManager]). A copy from Google Play must not: Play's
 * policy forbids an app installing updates from anywhere but Play. The Play build is made with the
 * in-app updater off ([com.mtgcompanion.app.BuildConfig.PLAY_STORE]); the installer check is a
 * second guard, for a GitHub-built APK that Play somehow installed (internal app sharing, say).
 */
object InstallSource {
    /** The Google Play Store's package name, as Android reports it for apps Play installed. */
    const val PLAY_STORE = "com.android.vending"

    /** The in-app GitHub updater runs unless this is the Play build or Play installed it. */
    fun selfUpdateAllowed(playBuild: Boolean, installer: String?): Boolean =
        !playBuild && installer != PLAY_STORE

    /** The package that installed this app (Play, a file manager, a browser…); null when unknown or sideloaded by adb. */
    fun installerOf(context: Context): String? = runCatching {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(context.packageName)
        }
    }.getOrNull()
}
