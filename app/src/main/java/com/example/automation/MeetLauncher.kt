package com.example.automation

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.data.MeetingCodeParser

object MeetLauncher {
    const val GOOGLE_MEET_PACKAGE = "com.google.android.apps.meetings"

    fun isGoogleMeetInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(GOOGLE_MEET_PACKAGE, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun buildMeetIntent(
        context: Context,
        meetingCode: String,
        preferredGmail: String = ""
    ): Intent {
        val baseUrl = MeetingCodeParser.toMeetUrl(meetingCode)
        val cleanEmail = preferredGmail.trim()
        val finalUrl = if (cleanEmail.contains("@")) {
            "$baseUrl?authuser=${Uri.encode(cleanEmail)}"
        } else {
            baseUrl
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        if (isGoogleMeetInstalled(context)) {
            intent.setPackage(GOOGLE_MEET_PACKAGE)
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
