package com.confused.anikuta.core.testcontroller

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.confused.anikuta.core.testapi.DeviceInfo
import com.confused.anikuta.core.testapi.ScreenSize

/**
 * Builds [DeviceInfo] from the app context + Build constants. Used by `ping` + `get_device_info`.
 */
object DeviceInfoProvider {

    @Suppress("DEPRECATION")
    fun get(context: Context, isDebugBuild: Boolean): DeviceInfo {
        val pm = context.packageManager
        val pkg: PackageInfo = runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                pm.getPackageInfo(context.packageName, 0)
            }
        }.getOrNull() ?: throw IllegalStateException("package info not found for ${context.packageName}")

        val dm = context.resources.displayMetrics
        val abis = if (Build.VERSION.SDK_INT >= 21) {
            Build.SUPPORTED_ABIS.toList()
        } else {
            @Suppress("DEPRECATION") listOf(Build.CPU_ABI, Build.CPU_ABI2).filter { it.isNotBlank() }
        }

        val versionName = pkg.versionName ?: "unknown"
        val versionCode = if (Build.VERSION.SDK_INT >= 28) pkg.longVersionCode.toInt() else @Suppress("DEPRECATION") pkg.versionCode

        return DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            sdkInt = Build.VERSION.SDK_INT,
            release = Build.VERSION.RELEASE,
            screenSize = ScreenSize(width = dm.widthPixels, height = dm.heightPixels, density = dm.density),
            abis = abis,
            appVersionName = versionName,
            appVersionCode = versionCode,
            appPackageName = context.packageName,
            isDebugBuild = isDebugBuild,
        )
    }
}
