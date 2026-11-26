package com.example.automation

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat

data class DeviceReadinessStatus(
    val googleMeetInstalled: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val exactAlarmAllowed: Boolean = false,
    val notificationsAllowed: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
    val manufacturerName: String = Build.MANUFACTURER,
    val deviceModel: String = Build.MODEL,
    val androidVersionRelease: String = Build.VERSION.RELEASE ?: "16",
    val sdkInt: Int = Build.VERSION.SDK_INT,
    val isOppoOrColorOs: Boolean = false,
    val isReno11: Boolean = false
) {
    val coreReady: Boolean
        get() = accessibilityEnabled && exactAlarmAllowed && notificationsAllowed

    val readyCount: Int
        get() = listOf(
            googleMeetInstalled,
            accessibilityEnabled,
            exactAlarmAllowed,
            notificationsAllowed,
            batteryOptimizationIgnored
        ).count { it }

    val deviceDisplayLabel: String
        get() = when {
            isReno11 -> "OPPO Reno11 5G • ColorOS / Android $androidVersionRelease"
            isOppoOrColorOs -> "$manufacturerName $deviceModel • ColorOS / Android $androidVersionRelease"
            else -> "$manufacturerName $deviceModel • Android $androidVersionRelease"
        }
}

object DeviceReadinessHelper {

    fun inspect(context: Context): DeviceReadinessStatus {
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val brand = Build.BRAND.orEmpty()
        val model = Build.MODEL.orEmpty()
        val isOppo = manufacturer.contains("oppo", ignoreCase = true) ||
            brand.contains("oppo", ignoreCase = true) ||
            manufacturer.contains("realme", ignoreCase = true) ||
            manufacturer.contains("oneplus", ignoreCase = true)
        val isReno11 = model.contains("CPH2599", ignoreCase = true) ||
            model.contains("Reno11", ignoreCase = true) ||
            model.contains("Reno 11", ignoreCase = true)

        return DeviceReadinessStatus(
            googleMeetInstalled = MeetLauncher.isGoogleMeetInstalled(context),
            accessibilityEnabled = isAccessibilityServiceEnabled(context),
            exactAlarmAllowed = canScheduleExactAlarms(context),
            notificationsAllowed = isNotificationsAllowed(context),
            batteryOptimizationIgnored = isIgnoringBatteryOptimizations(context),
            manufacturerName = manufacturer.ifBlank { "Android" },
            deviceModel = model,
            androidVersionRelease = Build.VERSION.RELEASE ?: "16",
            sdkInt = Build.VERSION.SDK_INT,
            isOppoOrColorOs = isOppo,
            isReno11 = isReno11
        )
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val expectedComponent = ComponentName(context, MeetAutoJoinAccessibilityService::class.java)
        val enabledServices = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .orEmpty()
        if (enabledServices.any {
                val info = it.resolveInfo?.serviceInfo
                info != null && info.packageName == expectedComponent.packageName &&
                    info.name == expectedComponent.className
            }
        ) {
            return true
        }

        val settingValue = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return settingValue.contains(expectedComponent.flattenToString(), ignoreCase = true) ||
            settingValue.contains(expectedComponent.flattenToShortString(), ignoreCase = true)
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.canScheduleExactAlarms() == true
        } else {
            true
        }
    }

    fun isNotificationsAllowed(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }.onFailure {
                openAppDetailsSettings(context)
            }
        } else {
            openAppDetailsSettings(context)
        }
    }

    fun openNotificationSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openBatteryOptimizationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }.onFailure {
            openAppDetailsSettings(context)
        }
    }

    fun openColorOsAutoLaunchOrAppInfo(context: Context) {
        val colorOsIntents = listOf(
            Intent().setComponent(
                ComponentName(
                    "com.oplus.battery",
                    "com.oplus.powermanager.fuelgaue.PowerControlActivity"
                )
            ),
            Intent().setComponent(
                ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                )
            ),
            Intent().setComponent(
                ComponentName(
                    "com.oplus.safecenter",
                    "com.oplus.safecenter.permission.startup.StartupAppListActivity"
                )
            )
        )
        for (candidate in colorOsIntents) {
            candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val launched = runCatching {
                context.startActivity(candidate)
                true
            }.getOrDefault(false)
            if (launched) return
        }
        openAppDetailsSettings(context)
    }

    fun openAppDetailsSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openGoogleMeetStorePage(context: Context) {
        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=${MeetLauncher.GOOGLE_MEET_UNIFIED_PACKAGE}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(marketIntent) }.onFailure {
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=${MeetLauncher.GOOGLE_MEET_UNIFIED_PACKAGE}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(webIntent) }
        }
    }
}
