package com.google.ai.edge.gallery.common

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

private const val TAG = "AICoreChecker"
private const val AICORE_PACKAGE = "com.google.android.aicore"

fun isAICoreServiceInstalled(context: Context): Boolean {
    return try {
        context.packageManager.getPackageInfo(AICORE_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        Log.w(TAG, "AICore service is not installed.")
        false
    }
}
