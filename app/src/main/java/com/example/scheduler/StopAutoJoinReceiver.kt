package com.example.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.automation.AutoJoinCoordinator

class StopAutoJoinReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AutoJoinCoordinator.stopSession(
            context = context.applicationContext,
            reason = "Stopped from notification"
        )
    }
}
