package com.example.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.BuildConfig

object MapsConfigHelper {
    private val PLACEHOLDER_VALUES = setOf(
        "MY_MAPS_API_KEY",
        "MAPS_API_KEY_DEFAULT_VALUE",
        "YOUR_API_KEY",
        "\${MAPS_API_KEY}"
    )

    fun isUsableApiKey(rawKey: String?): Boolean {
        val clean = rawKey?.trim().orEmpty()
        return clean.isNotBlank() && clean !in PLACEHOLDER_VALUES
    }

    fun resolveMapsApiKey(context: Context): String {
        val buildConfigKey = BuildConfig.MAPS_API_KEY.trim()
        if (isUsableApiKey(buildConfigKey)) return buildConfigKey

        val manifestKey = runCatching {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getApplicationInfo(
                    context.packageName,
                    PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo(
                    context.packageName,
                    PackageManager.GET_META_DATA
                )
            }
            appInfo.metaData?.getString("com.google.android.geo.API_KEY")?.trim()
        }.getOrNull()

        if (isUsableApiKey(manifestKey)) return manifestKey.orEmpty()
        return ""
    }

    fun hasValidMapsApiKey(context: Context): Boolean =
        resolveMapsApiKey(context).isNotBlank()
}
