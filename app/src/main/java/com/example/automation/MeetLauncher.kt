package com.example.automation

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.data.MeetingCodeParser

object MeetLauncher {
    const val GOOGLE_MEET_PACKAGE = "com.google.android.apps.meetings"
    const val GOOGLE_MEET_UNIFIED_PACKAGE = "com.google.android.apps.tachyon"

    fun resolveMeetPackage(context: Context): String? {
        val pm = context.packageManager
        val testUri = Uri.parse("https://meet.google.com/abc-defg-hij")
        for (pkg in listOf(GOOGLE_MEET_UNIFIED_PACKAGE, GOOGLE_MEET_PACKAGE)) {
            val installed = try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (_: Exception) {
                false
            }
            if (installed) {
                val probe = Intent(Intent.ACTION_VIEW, testUri).setPackage(pkg)
                if (probe.resolveActivity(pm) != null) {
                    return pkg
                }
            }
        }
        return null
    }

    fun isGoogleMeetInstalled(context: Context): Boolean {
        return resolveMeetPackage(context) != null || isPackageInstalled(context, GOOGLE_MEET_PACKAGE) ||
            isPackageInstalled(context, GOOGLE_MEET_UNIFIED_PACKAGE)
    }

    private fun isPackageInstalled(context: Context, pkg: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun buildMeetIntent(
        context: Context,
        meetingCode: String,
        @Suppress("UNUSED_PARAMETER") preferredGmail: String = ""
    ): Intent {
        val baseUrl = MeetingCodeParser.toMeetUrl(meetingCode)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(baseUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        resolveMeetPackage(context)?.let { meetPkg ->
            intent.setPackage(meetPkg)
        }
        return intent
    }

    fun launchMeet(
        context: Context,
        meetingCode: String,
        preferredGmail: String = ""
    ): Boolean {
        return try {
            val intent = buildMeetIntent(context, meetingCode, preferredGmail)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            try {
                val fallbackIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(MeetingCodeParser.toMeetUrl(meetingCode))
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
